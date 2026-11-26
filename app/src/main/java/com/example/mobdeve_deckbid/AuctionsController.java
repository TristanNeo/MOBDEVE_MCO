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
import android.widget.Space;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import android.widget.RadioGroup;
import java.util.Comparator;
import java.util.Locale;

public class AuctionsController {
    private final HomeActivity activity;
    private final EditText search;
    private final Spinner category, condition, price;
    private ArrayList<AuctionItem> auctions;
    private final ArrayList<TextView> clocks = new ArrayList<>();
    private int status = 0;
    private final java.util.HashMap<String, Integer> phases = new java.util.HashMap<>();

    public AuctionsController(HomeActivity activity) {
        this.activity = activity;
        search = activity.findViewById(R.id.auction_search);
        category = activity.findViewById(R.id.auction_category);
        condition = activity.findViewById(R.id.auction_condition);
        price = activity.findViewById(R.id.auction_price);
        bind(category, "Game", AuctionStore.CATEGORIES);
        bind(condition, "Condition", AuctionStore.CONDITIONS);
        bind(price, "Price", AuctionStore.PRICES);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { renderGrid(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        category.setOnItemSelectedListener(listener);
        condition.setOnItemSelectedListener(listener);
        price.setOnItemSelectedListener(listener);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderGrid(); }
            public void afterTextChanged(Editable s) { }
        });
        activity.findViewById(R.id.auction_clear).setOnClickListener(v -> filter(0, 0));
        activity.findViewById(R.id.home_browse).setOnClickListener(v -> activity.openAuctions(0, 0));
        RadioGroup roles = activity.findViewById(R.id.home_auction_roles);
        roles.check(R.id.home_selling);
        roles.setOnCheckedChangeListener((group, checked) -> {
            if (auctions != null) renderDashboard();
        });
        activity.findViewById(R.id.home_show_more).setOnClickListener(v -> {
            ActiveAuctions active = new ActiveAuctions(AuctionStore.load(activity), System.currentTimeMillis());
            if (!active.canExpand()) {
                refresh();
                return;
            }
            boolean bidding = roles.getCheckedRadioButtonId() == R.id.home_bidding;
            if (active.items(bidding).size() < ActiveAuctions.PREVIEW_LIMIT) bidding = !bidding;
            Intent intent = new Intent(activity, ActiveAuctionsActivity.class);
            intent.putExtra(ActiveAuctionsActivity.EXTRA_BIDDING, bidding);
            activity.startActivity(intent);
        });
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

    private void bind(Spinner spinner, String title, String[] choices) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, R.layout.auction_spinner_item, choices) {
            @Override public View getView(int position, View previous, android.view.ViewGroup parent) {
                TextView label = (TextView) super.getView(position, previous, parent);
                label.setText((position == 0 ? title : choices[position]) + " ▾");
                label.setContentDescription(title + ", " + choices[position]);
                return label;
            }
        };
        adapter.setDropDownViewResource(R.layout.binder_spinner_item);
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
        renderGrid();
    }

    public void refresh() {
        auctions = AuctionStore.load(activity);
        clocks.clear();
        phases.clear();
        long now = System.currentTimeMillis();
        for (AuctionItem a : auctions) phases.put(a.id, a.isPending(now) ? 0 : a.isActive(now) ? 1 : 2);
        renderDashboard();
        renderWatchlist();
        renderGrid();
    }

    public void updateTimes() {
        ArrayList<AuctionItem> current = AuctionStore.load(activity);
        long now = System.currentTimeMillis();
        boolean changed = current.size() != phases.size();
        for (AuctionItem a : current) {
            int phase = a.isPending(now) ? 0 : a.isActive(now) ? 1 : 2;
            if (!java.util.Objects.equals(phases.get(a.id), phase)) changed = true;
        }
        if (changed) { refresh(); return; }
        for (TextView clock : clocks) clock.setText(AuctionStore.timeLeft((AuctionItem) clock.getTag()));
    }

    private boolean matches(AuctionItem a) {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!(a.name + " " + a.set + " " + a.game).toLowerCase(Locale.ROOT).contains(query)) return false;
        if (category.getSelectedItemPosition() > 0 && !a.game.equals(category.getSelectedItem())) return false;
        if (condition.getSelectedItemPosition() > 0 && !a.condition.equals(condition.getSelectedItem())) return false;
        int p = price.getSelectedItemPosition();
        if (p == 1 && a.currentBid >= 1000) return false;
        if (p == 2 && (a.currentBid < 1000 || a.currentBid > 5000)) return false;
        return p != 3 || a.currentBid > 5000;
    }

    private void renderGrid() {
        if (auctions == null) return;
        GridLayout grid = activity.findViewById(R.id.auction_grid);
        LinearLayout mine = activity.findViewById(R.id.my_auction_rows);
        removeClocks(grid); removeClocks(mine);
        grid.removeAllViews(); mine.removeAllViews();
        ArrayList<AuctionItem> filtered = new ArrayList<>(), owned = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (AuctionItem a : auctions) {
            if (!matches(a)) continue;
            if (a.isOwned()) { if (status == 0) owned.add(a); continue; }
            if (a.isPending(now)) continue;
            if (status == 0 && !a.isActive(now)) continue;
            if (status == 1 && (!a.hasBid() || !a.isActive(now))) continue;
            if (status == 2 && (a.endsAt > now || !a.isLeading())) continue;
            filtered.add(a);
        }
        filtered.sort(Comparator.comparingLong(a -> a.endsAt));
        owned.sort((a, b) -> {
            boolean endedA = a.endsAt <= now, endedB = b.endsAt <= now;
            if (endedA != endedB) return endedA ? 1 : -1;
            return Long.compare(a.startsAt, b.startsAt);
        });
        for (AuctionItem a : owned) {
            View row = activity.getLayoutInflater().inflate(R.layout.item_auction_row, mine, false);
            bindCard(row, a); mine.addView(row);
        }
        TextView ownTitle = activity.findViewById(R.id.my_auctions_title);
        ownTitle.setText("My Auctions · " + owned.size());
        ownTitle.setVisibility(status == 0 ? View.VISIBLE : View.GONE);
        mine.setVisibility(status == 0 ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.my_auctions_empty).setVisibility(status == 0 && owned.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < filtered.size(); i++) {
            View tile = activity.getLayoutInflater().inflate(R.layout.item_auction_card, grid, false);
            grid.addView(tile, tileParams(i));
            bindCard(tile, filtered.get(i));
        }
        if (filtered.size() % 2 == 1) {
            Space unusedColumn = new Space(activity);
            GridLayout.LayoutParams params = tileParams(filtered.size());
            params.height = 0;
            grid.addView(unusedColumn, params);
        }
        TextView title = activity.findViewById(R.id.live_auctions_title);
        title.setText(status == 1 ? "Your active bids" : status == 2 ? "Your wins" : "Live Auctions");
        activity.findViewById(R.id.auction_empty).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private GridLayout.LayoutParams tileParams(int index) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        int column = index % 2;
        params.width = 0;
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(column, GridLayout.FILL, 1f);
        params.rowSpec = GridLayout.spec(index / 2);
        params.setMargins(column == 0 ? 0 : dp(5), 0, column == 0 ? dp(5) : 0, dp(10));
        return params;
    }

    private void removeClocks(android.view.ViewGroup group) {
        for (int i = clocks.size() - 1; i >= 0; i--) {
            android.view.ViewParent p = clocks.get(i).getParent();
            while (p != null && p != group) p = p.getParent();
            if (p == group) clocks.remove(i);
        }
    }

    private void bindCard(View view, AuctionItem a) {
        clocks.add(CardAppearance.bindAuction(activity, view, a));
        ImageButton heart = view.findViewById(R.id.auction_save);
        heart.setImageResource(a.saved ? R.drawable.auction_heart_saved : R.drawable.auction_heart);
        heart.setContentDescription(a.saved ? "Remove from watchlist" : "Save to watchlist");
        heart.setVisibility(a.isOwned() ? View.GONE : View.VISIBLE);
        heart.setOnClickListener(v -> {
            heart.setEnabled(false);
            AuctionStore.setSaved(a.id, !a.saved).addOnCompleteListener(task -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                heart.setEnabled(true);
                if (!task.isSuccessful()) Toast.makeText(activity, "Could not save: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                refresh();
            });
        });
    }

    private void renderDashboard() {
        int saved = 0, won = 0;
        long now = System.currentTimeMillis();
        ActiveAuctions active = new ActiveAuctions(auctions, now);
        for (AuctionItem a : auctions) {
            if (a.saved) saved++;
            if (a.endsAt <= now && a.isLeading()) won++;
        }
        ((TextView) activity.findViewById(R.id.home_binder_stat)).setText(BinderStore.load(activity).size() + "\nCards");
        ((TextView) activity.findViewById(R.id.home_watch_stat)).setText(saved + "\nSaved");
        ((TextView) activity.findViewById(R.id.home_bids_stat)).setText(active.bidding.size() + "\nBidding");
        ((TextView) activity.findViewById(R.id.home_won_stat)).setText(won + "\nWon");
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        ((TextView) activity.findViewById(R.id.home_greeting)).setText(hour < 12 ? "Good morning," : hour < 18 ? "Good afternoon," : "Good evening,");
        RadioGroup roles = activity.findViewById(R.id.home_auction_roles);
        boolean bidding = roles.getCheckedRadioButtonId() == R.id.home_bidding;
        CardAppearance.roleCounts(roles, active);
        activity.findViewById(R.id.home_show_more).setVisibility(active.canExpand() ? View.VISIBLE : View.GONE);
        LinearLayout list = activity.findViewById(R.id.home_active_list);
        removeClocks(list);
        list.removeAllViews();
        for (AuctionItem item : active.preview(bidding)) {
            View row = activity.getLayoutInflater().inflate(R.layout.item_auction_row, list, false);
            clocks.add(CardAppearance.bindActiveRow(activity, row, item));
            list.addView(row);
        }
        if (active.items(bidding).isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(ActiveAuctions.emptyMessage(bidding));
            empty.setTextColor(activity.getColor(R.color.muted));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(8), 0, dp(8));
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
