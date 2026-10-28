package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AuctionDetailsActivity extends Activity {
    private AuctionItem auction;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (auction != null) updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_auction_details);
        auction = AuctionStore.find(this, getIntent().getStringExtra("auction_id"));
        if (auction == null) {
            finish();
            return;
        }
        findViewById(R.id.auction_back).setOnClickListener(v -> finish());
        findViewById(R.id.auction_save).setOnClickListener(v -> {
            auction.saved = !auction.saved;
            persist();
        });
        findViewById(R.id.auction_place_bid).setOnClickListener(v -> bidDialog(false));
        findViewById(R.id.auction_auto_bid).setOnClickListener(v -> bidDialog(true));
        AuctionStore.image(this, (ImageView) findViewById(R.id.detail_auction_image), auction);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        if (auction != null) handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void render() {
        label(R.id.detail_auction_name, auction.name);
        label(R.id.detail_auction_game, auction.game);
        label(R.id.detail_auction_grade, auction.condition);
        label(R.id.detail_auction_seller, "by " + auction.seller);
        label(R.id.detail_auction_bid, AuctionStore.money(auction.currentBid));
        label(R.id.detail_auction_count, auction.history.size() + " bids");
        label(R.id.detail_auction_start, "Starting bid\n" + AuctionStore.money(auction.startingBid));
        label(R.id.detail_auction_increment, "Bid increment\n" + AuctionStore.money(auction.increment));
        String[] notes = {
            "A standout card with artwork that deserves a place at the front of a binder. One of my personal favourites.",
            "A memorable addition for anyone building a collection around this series. The illustration is the highlight for me.",
            "Making room for a new collecting goal. I hope this card finds a home with someone who enjoys it as much as I have.",
            "This card has been a favourite in my collection. A great conversation piece for fellow collectors."
        };
        label(R.id.auction_notes, notes[Math.floorMod(auction.id.hashCode(), notes.length)]);
        ImageButton heart = findViewById(R.id.auction_save);
        heart.setImageResource(auction.saved ? R.drawable.auction_heart_saved : R.drawable.auction_heart);
        heart.setContentDescription(auction.saved ? "Remove from watchlist" : "Save to watchlist");
        LinearLayout summary = findViewById(R.id.auction_summary);
        summary.removeAllViews();
        summaryRow(summary, "Total bids", String.valueOf(auction.history.size()));
        summaryRow(summary, "Start date", date(auction.startsAt));
        summaryRow(summary, "End date", date(auction.endsAt));
        LinearLayout history = findViewById(R.id.auction_history);
        history.removeAllViews();
        historyRow(history, "Bidder", "Amount", "Time", true);
        for (AuctionItem.Bid bid : auction.history) {
            historyRow(history, "You".equals(bid.bidder) ? AccountStore.username(this) : bid.bidder, AuctionStore.money(bid.amount), new SimpleDateFormat("MMM d, HH:mm", Locale.US).format(new Date(bid.time)), false);
        }
        updateStatus();
    }

    private void updateStatus() {
        boolean active = auction.isActive(System.currentTimeMillis());
        label(R.id.detail_auction_time, AuctionStore.timeLeft(auction));
        String status;
        if (!active) status = auction.isLeading() ? "You won this auction." : "This auction has ended.";
        else if (auction.isLeading()) status = "You are the highest bidder.";
        else if (auction.hasBid()) status = "You have been outbid.";
        else status = "Next minimum: " + AuctionStore.money(auction.minimumBid());
        if (active && auction.autoBidMax > 0) status += "\nAuto-bid maximum: " + AuctionStore.money(auction.autoBidMax);
        label(R.id.detail_auction_status, status);
        Button bid = findViewById(R.id.auction_place_bid);
        Button auto = findViewById(R.id.auction_auto_bid);
        bid.setEnabled(active && !auction.isLeading());
        bid.setAlpha(bid.isEnabled() ? 1f : 0.5f);
        auto.setEnabled(active);
        auto.setAlpha(active ? 1f : 0.5f);
        auto.setText(auction.autoBidMax > 0 ? "Edit Auto-Bid" : "Auto-Bid");
    }

    private void bidDialog(boolean automatic) {
        View form = getLayoutInflater().inflate(R.layout.dialog_auction_bid, null);
        EditText amount = form.findViewById(R.id.bid_amount);
        long minimum = automatic && auction.isLeading() ? auction.currentBid : auction.minimumBid();
        String prompt = "Minimum: " + AuctionStore.money(minimum) + "\nWhole pesos only.";
        if (automatic) prompt += "\nSets your maximum bid and places the minimum needed to lead.";
        ((TextView) form.findViewById(R.id.bid_prompt)).setText(prompt);
        amount.setText(String.valueOf(automatic && auction.autoBidMax >= minimum ? auction.autoBidMax : minimum));
        amount.selectAll();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(automatic ? "Set Auto-Bid" : "Place Bid")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(automatic ? "Save maximum" : "Confirm bid", null);
        if (automatic && auction.autoBidMax > 0) {
            builder.setNeutralButton("Turn off", (dialog, which) -> {
                auction.autoBidMax = 0;
                persist();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long value;
            try {
                value = Long.parseLong(amount.getText().toString().trim());
            } catch (NumberFormatException e) {
                amount.setError("Enter a valid amount in whole pesos");
                return;
            }
            try {
                if (automatic) auction.setAutoBid(value, System.currentTimeMillis());
                else auction.placeBid(value, "You", System.currentTimeMillis());
            } catch (IllegalArgumentException e) {
                amount.setError(e.getMessage());
                return;
            }
            if (persist()) {
                Toast.makeText(this, automatic ? "Auto-bid maximum saved" : "Bid placed", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private boolean persist() {
        if (!AuctionStore.save(this, auction)) {
            Toast.makeText(this, "Could not save. Please try again.", Toast.LENGTH_SHORT).show();
            AuctionItem previous = AuctionStore.find(this, auction.id);
            if (previous != null) auction = previous;
            render();
            return false;
        }
        render();
        return true;
    }

    private void summaryRow(LinearLayout parent, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        TextView left = new TextView(this);
        left.setText(title);
        left.setTextSize(12);
        left.setTextColor(android.graphics.Color.parseColor("#95A1B3"));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(13);
        right.setTypeface(null, android.graphics.Typeface.BOLD);
        right.setGravity(android.view.Gravity.END);
        right.setTextColor(android.graphics.Color.parseColor("#FAF8F2"));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1.6f));
        parent.addView(row);
        divider(parent);
    }

    private void historyRow(LinearLayout parent, String bidder, String amount, String time, boolean heading) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        if (heading) row.setBackgroundColor(android.graphics.Color.parseColor("#182B3E"));
        String[] values = {bidder, amount, time};
        float[] weights = {1.3f, 1f, 1.1f};
        for (int i = 0; i < values.length; i++) {
            TextView cell = new TextView(this);
            cell.setText(values[i]);
            cell.setTextSize(heading ? 10 : 11);
            cell.setPadding(dp(5), dp(8), dp(5), dp(8));
            cell.setTextColor(android.graphics.Color.parseColor(heading ? "#95A1B3" : "#FAF8F2"));
            if (!heading && i == 1) cell.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, weights[i]));
        }
        parent.addView(row);
        divider(parent);
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(android.graphics.Color.parseColor("#293548"));
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private String date(long time) {
        return new SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(new Date(time));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void label(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }
}
