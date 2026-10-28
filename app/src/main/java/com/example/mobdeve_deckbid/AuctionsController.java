package com.example.mobdeve_deckbid;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class AuctionsController {
    private final HomeActivity activity;
    private final EditText search;
    private final Spinner category, condition, price, sort;
    private ArrayList<AuctionItem> auctions;
    private final ArrayList<TextView> clocks = new ArrayList<>();
    private int status = 0;

    public AuctionsController(HomeActivity activity) {
        this.activity = activity;
        search = activity.findViewById(R.id.auction_search);
        category = activity.findViewById(R.id.auction_category);
        condition = activity.findViewById(R.id.auction_condition);
        price = activity.findViewById(R.id.auction_price);
        sort = activity.findViewById(R.id.auction_sort);
        bind(category, AuctionStore.CATEGORIES);
        bind(condition, AuctionStore.CONDITIONS);
        bind(price, AuctionStore.PRICES);
        bind(sort, AuctionStore.SORTS);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { renderGrid(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        category.setOnItemSelectedListener(listener);
        condition.setOnItemSelectedListener(listener);
        price.setOnItemSelectedListener(listener);
        sort.setOnItemSelectedListener(listener);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderGrid(); }
            public void afterTextChanged(Editable s) { }
        });
        activity.findViewById(R.id.auction_clear).setOnClickListener(v -> filter(0, 0));
        activity.findViewById(R.id.home_browse).setOnClickListener(v -> activity.openAuctions(0, 0));
        activity.findViewById(R.id.home_see_all).setOnClickListener(v -> activity.openAuctions(0, 0));
        activity.findViewById(R.id.home_binder_stat).setOnClickListener(v -> activity.openTab(3));
        activity.findViewById(R.id.home_watch_stat).setOnClickListener(v -> activity.openTab(1));
        activity.findViewById(R.id.home_bids_stat).setOnClickListener(v -> activity.openAuctions(0, 1));
        activity.findViewById(R.id.home_won_stat).setOnClickListener(v -> activity.openAuctions(0, 2));
        int[] categories = {R.id.category_pokemon, R.id.category_onepiece, R.id.category_yugioh, R.id.category_mtg};
        for (int i = 0; i < categories.length; i++) {
            final int position = i + 1;
            activity.findViewById(categories[i]).setOnClickListener(v -> activity.openAuctions(position, 0));
        }
        refresh();
    }

    private void bind(Spinner spinner, String[] choices) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.auction_spinner_item, choices);
        adapter.setDropDownViewResource(R.layout.auction_spinner_item);
        spinner.setAdapter(adapter);
    }

    public int getStatus() { return status; }

    public void setStatus(int value) { status = value; }

    public void filter(int game, int status) {
        this.status = status;
        search.setText("");
        category.setSelection(game);
        condition.setSelection(0);
        price.setSelection(0);
        sort.setSelection(0);
        renderGrid();
    }

    public void refresh() {
        auctions = AuctionStore.load(activity);
        clocks.clear();
        renderDashboard();
        renderWatchlist();
        renderGrid();
    }

    public void updateTimes() {
        boolean justEnded = false;
        for (TextView clock : clocks) {
            AuctionItem a = (AuctionItem) clock.getTag();
            String remaining = AuctionStore.timeLeft(a);
            if (remaining.equals("Ended") && !clock.getText().toString().equals("Ended")) justEnded = true;
            clock.setText(remaining);
        }
        if (justEnded) refresh();
    }

    private void renderGrid() {
        if (auctions == null) return;
        GridLayout grid = activity.findViewById(R.id.auction_grid);
        removeClocks(grid);
        grid.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<AuctionItem> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (AuctionItem a : auctions) {
            if (!(a.name + " " + a.game).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (category.getSelectedItemPosition() > 0 && !a.game.equals(category.getSelectedItem())) continue;
            if (condition.getSelectedItemPosition() > 0 && !a.condition.equals(condition.getSelectedItem())) continue;
            int p = price.getSelectedItemPosition();
            if (p == 1 && a.currentBid >= 1000) continue;
            if (p == 2 && (a.currentBid < 1000 || a.currentBid > 5000)) continue;
            if (p == 3 && a.currentBid <= 5000) continue;
            if (status == 1 && (!a.hasBid() || !a.isActive(now))) continue;
            if (status == 2 && (a.endsAt > now || !a.isLeading())) continue;
            filtered.add(a);
        }
        int order = sort.getSelectedItemPosition();
        Collections.sort(filtered, (a, b) -> {
            if (a.isActive(now) != b.isActive(now)) return a.isActive(now) ? -1 : 1;
            if (order == 1) return Long.compare(a.currentBid, b.currentBid);
            if (order == 2) return Long.compare(b.currentBid, a.currentBid);
            return Long.compare(a.endsAt, b.endsAt);
        });
        for (int i = 0; i < filtered.size(); i++) {
            View tile = activity.getLayoutInflater().inflate(R.layout.item_auction_card, grid, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(i % 2, 1f);
            params.rowSpec = GridLayout.spec(i / 2);
            params.setMargins(i % 2 == 0 ? 0 : dp(5), 0, i % 2 == 0 ? dp(5) : 0, dp(10));
            tile.setLayoutParams(params);
            bindCard(tile, filtered.get(i));
            grid.addView(tile);
        }
        String suffix = status == 1 ? "Your active bids" : status == 2 ? "Your wins" : "All listings";
        ((TextView) activity.findViewById(R.id.auction_result_count)).setText(filtered.size() + " auctions · " + suffix);
        activity.findViewById(R.id.auction_empty).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void removeClocks(android.view.ViewGroup group) {
        for (int i = clocks.size() - 1; i >= 0; i--) {
            android.view.ViewParent p = clocks.get(i).getParent();
            while (p != null && p != group) p = p.getParent();
            if (p == group) clocks.remove(i);
        }
    }

    private void bindCard(View view, AuctionItem a) {
        AuctionStore.image(activity, (ImageView) view.findViewById(R.id.auction_image), a);
        ((TextView) view.findViewById(R.id.auction_name)).setText(a.name);
        ((TextView) view.findViewById(R.id.auction_game)).setText(a.game);
        ((TextView) view.findViewById(R.id.auction_grade)).setText(a.condition);
        ((TextView) view.findViewById(R.id.auction_bid)).setText(AuctionStore.money(a.currentBid));
        ((TextView) view.findViewById(R.id.auction_bid_count)).setText(a.history.size() + " bids");
        TextView time = view.findViewById(R.id.auction_time);
        time.setText(AuctionStore.timeLeft(a));
        time.setTag(a);
        clocks.add(time);
        ImageButton heart = view.findViewById(R.id.auction_save);
        heart.setImageResource(a.saved ? R.drawable.auction_heart_saved : R.drawable.auction_heart);
        heart.setContentDescription(a.saved ? "Remove from watchlist" : "Save to watchlist");
        heart.setOnClickListener(v -> {
            a.saved = !a.saved;
            if (!AuctionStore.save(activity, a)) Toast.makeText(activity, "Could not save. Try again.", Toast.LENGTH_SHORT).show();
            refresh();
        });
        view.setOnClickListener(v -> {
            Intent intent = new Intent(activity, AuctionDetailsActivity.class);
            intent.putExtra("auction_id", a.id);
            activity.startActivity(intent);
        });
    }

    private void renderDashboard() {
        int saved = 0, bidding = 0, won = 0;
        long now = System.currentTimeMillis();
        ArrayList<AuctionItem> ending = new ArrayList<>();
        for (AuctionItem a : auctions) {
            if (a.saved) saved++;
            if (a.isActive(now)) {
                ending.add(a);
                if (a.hasBid()) bidding++;
            } else if (a.endsAt <= now && a.isLeading()) won++;
        }
        ((TextView) activity.findViewById(R.id.home_binder_stat)).setText(BinderStore.load(activity).size() + "\nCards");
        ((TextView) activity.findViewById(R.id.home_watch_stat)).setText(saved + "\nSaved");
        ((TextView) activity.findViewById(R.id.home_bids_stat)).setText(bidding + "\nBidding");
        ((TextView) activity.findViewById(R.id.home_won_stat)).setText(won + "\nWon");
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        ((TextView) activity.findViewById(R.id.home_greeting)).setText(hour < 12 ? "Good morning," : hour < 18 ? "Good afternoon," : "Good evening,");
        Collections.sort(ending, Comparator.comparingLong(a -> a.endsAt));
        LinearLayout list = activity.findViewById(R.id.home_ending_list);
        list.removeAllViews();
        for (int i = 0; i < Math.min(3, ending.size()); i++) {
            View row = activity.getLayoutInflater().inflate(R.layout.item_auction_row, list, false);
            bindCard(row, ending.get(i));
            row.findViewById(R.id.auction_save).setVisibility(View.GONE);
            row.findViewById(R.id.auction_chevron).setVisibility(View.VISIBLE);
            list.addView(row);
        }
        if (ending.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No active auctions.");
            empty.setTextColor(android.graphics.Color.parseColor("#95A1B3"));
            list.addView(empty);
        }
    }

    private void renderWatchlist() {
        LinearLayout list = activity.findViewById(R.id.watchlist_rows);
        list.removeAllViews();
        int count = 0;
        for (AuctionItem a : auctions) {
            if (!a.saved) continue;
            View row = activity.getLayoutInflater().inflate(R.layout.item_auction_row, list, false);
            bindCard(row, a);
            list.addView(row);
            count++;
        }
        ((TextView) activity.findViewById(R.id.watchlist_count)).setText(count + " saved auctions");
        activity.findViewById(R.id.watchlist_empty).setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
