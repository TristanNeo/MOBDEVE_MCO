package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import com.google.firebase.firestore.*;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CardDetailsActivity extends Activity {
    private BinderStore.Card card;
    private ListenerRegistration cardListener, auctionListener;
    private AuctionItem listing;
    private long refreshAt = Long.MAX_VALUE;
    private final java.util.Map<TextView, AuctionItem> activeClocks = new java.util.LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() {
            long now = System.currentTimeMillis();
            if (card != null) {
                if (now >= refreshAt) render();
                else updateClocks(now);
            }
            handler.postDelayed(this, 1000);
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_card_details);
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        findViewById(R.id.card_auction_button).setOnClickListener(v -> {
            if (card == null) return;
            Intent intent = new Intent(this, CreateAuctionActivity.class);
            intent.putExtra("card_id", card.id); startActivity(intent);
        });
        findViewById(R.id.card_auction_button).setEnabled(false);
    }
    @Override protected void onStart() {
        super.onStart();
        String uid = AccountStore.uid(), id = getIntent().getStringExtra("card_id");
        if (uid == null || id == null) { finish(); return; }
        cardListener = FirebaseFirestore.getInstance().collection("users").document(uid).collection("binder").document(id)
                .addSnapshotListener((doc, error) -> {
                    if (isFinishing() || isDestroyed() || !uid.equals(AccountStore.uid())) return;
                    if (error != null || doc == null || !doc.exists()) {
                        android.widget.Toast.makeText(this, "Could not load this card.", android.widget.Toast.LENGTH_LONG).show(); finish(); return;
                    }
                    card = BinderStore.read(doc);
                    BinderStore.image(this, (ImageView) findViewById(R.id.detail_image), card);
                    ((TextView) findViewById(R.id.detail_name)).setText(card.name);
                    ImageView logo = findViewById(R.id.detail_game_logo);
                    logo.setImageResource(CardAppearance.logo(card.game));
                    logo.setContentDescription(card.game);
                    TextView set = findViewById(R.id.detail_set);
                    set.setText(card.set);
                    set.setVisibility(card.set.isEmpty() ? View.GONE : View.VISIBLE);
                    TextView condition = findViewById(R.id.detail_condition);
                    CardAppearance.condition(condition, card.condition);
                    condition.setShadowLayer(dp(2), 0, 0, (condition.getCurrentTextColor() & 0x00FFFFFF) | 0x66000000);
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        condition.setOutlineSpotShadowColor(condition.getCurrentTextColor());
                        condition.setOutlineAmbientShadowColor(condition.getCurrentTextColor());
                    }
                    render();
                });
        auctionListener = AuctionStore.listen(this, () -> { if (card != null) render(); });
    }
    @Override protected void onStop() {
        if (cardListener != null) { cardListener.remove(); cardListener = null; }
        if (auctionListener != null) { auctionListener.remove(); auctionListener = null; }
        super.onStop();
    }
    @Override protected void onResume() { super.onResume(); handler.post(ticker); }
    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }
    private void render() {
        long now = System.currentTimeMillis();
        listing = AuctionStore.find(this, card.auctionId);
        refreshAt = AuctionStore.nextCatalogOpening(card.game);
        if (listing != null && listing.endsAt > now) refreshAt = Math.min(refreshAt, listing.endsAt);
        boolean available = listing == null || (listing.endsAt <= now && listing.history.isEmpty());
        ((ImageView) findViewById(R.id.detail_image)).setAlpha(listing != null && listing.isPending(now) ? 0.55f : 1f);
        Button button = findViewById(R.id.card_auction_button);
        button.setEnabled(available);
        button.setVisibility(available ? View.VISIBLE : View.GONE);
        TextView status = findViewById(R.id.card_auction_status);
        String label = BinderStore.status(listing, now);
        status.setText(label);
        status.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.detail_value)).setText(AuctionStore.average(this, card));
        ((TextView) findViewById(R.id.detail_change)).setText("—");
        ArrayList<AuctionItem> completed = AuctionStore.matching(this, card, true);
        ((TextView) findViewById(R.id.detail_note)).setText(completed.isEmpty() ? "No completed auctions for this card and condition." : "Average of " + completed.size() + " completed auctions · " + card.condition);
        LinearLayout highest = findViewById(R.id.highest_table), active = findViewById(R.id.active_table);
        highest.removeAllViews(); active.removeAllViews(); activeClocks.clear();
        if (completed.isEmpty()) row(highest, new String[]{"No completed auctions for this card."}, false);
        else {
            row(highest, new String[]{"#", "Price", "Date", "Condition"}, true);
            for (int i = 0; i < Math.min(5, completed.size()); i++) {
                AuctionItem a = completed.get(i);
                row(highest, new String[]{String.valueOf(i + 1), AuctionStore.money(a.currentBid), new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new Date(a.endsAt)), a.condition}, false);
            }
        }
        ArrayList<AuctionItem> listings = AuctionStore.matching(this, card, false);
        for (AuctionItem item : listings) refreshAt = Math.min(refreshAt, item.endsAt);
        if (listings.isEmpty()) row(active, new String[]{"No active auctions for this card."}, false);
        else {
            row(active, new String[]{"#", "Current Bid", "Time Left", "Condition"}, true);
            for (int i = 0; i < Math.min(5, listings.size()); i++) {
                AuctionItem a = listings.get(i);
                LinearLayout line = row(active, new String[]{String.valueOf(i + 1), AuctionStore.money(a.currentBid), "", a.condition}, false);
                activeClocks.put((TextView) line.getChildAt(2), a);
            }
        }
        updateClocks(now);
    }

    private void updateClocks(long now) {
        TextView clock = findViewById(R.id.card_auction_clock);
        boolean running = listing != null && listing.endsAt > now;
        clock.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) clock.setText(AuctionStore.countdown(listing, now));
        for (java.util.Map.Entry<TextView, AuctionItem> entry : activeClocks.entrySet()) {
            entry.getKey().setText(AuctionStore.timeLeft(entry.getValue()));
        }
    }

    private LinearLayout row(LinearLayout table, String[] cells, boolean header) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        if (header) row.setBackgroundColor(Color.parseColor("#182B3E"));
        float[] weights = {0.35f, 1.3f, 1.35f, 1.05f};
        for (int i = 0; i < cells.length; i++) {
            TextView cell = new TextView(this);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, cells.length == 1 ? 1 : weights[i]));
            cell.setText(cells[i]);
            cell.setTextSize(header ? 10 : 11);
            cell.setPadding(dp(3), dp(8), dp(3), dp(8));
            cell.setTextColor(Color.parseColor(header ? "#95A1B3" : i == 0 && cells.length > 1 ? "#D4A63A" : "#FAF8F2"));
            row.addView(cell);
        }
        table.addView(row);
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#293548"));
        table.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
