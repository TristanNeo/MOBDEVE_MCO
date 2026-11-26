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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AuctionDetailsActivity extends Activity {
    private AuctionItem auction;
    private String auctionId;
    private boolean busy;
    private com.google.firebase.firestore.ListenerRegistration listener;
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
        auctionId = getIntent().getStringExtra("auction_id");
        if (AccountStore.uid() == null || auctionId == null) { finish(); return; }
        auction = AuctionStore.find(this, auctionId);
        findViewById(R.id.auction_back).setOnClickListener(v -> finish());
        findViewById(R.id.auction_save).setOnClickListener(v -> {
            if (auction == null || busy) return;
            busy = true;
            updateStatus();
            AuctionStore.setSaved(auctionId, !auction.saved).addOnCompleteListener(task -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                if (!task.isSuccessful()) showError(task.getException());
                if (auction != null) render();
            });
        });
        findViewById(R.id.auction_place_bid).setOnClickListener(v -> bidDialog(false));
        findViewById(R.id.auction_auto_bid).setOnClickListener(v -> bidDialog(true));
        findViewById(R.id.auction_place_bid).setEnabled(auction != null);
        findViewById(R.id.auction_auto_bid).setEnabled(auction != null);
        if (auction != null) render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (AccountStore.uid() == null || auctionId == null) return;
        listener = AuctionStore.listen(this, () -> {
            auction = AuctionStore.find(this, auctionId);
            if (auction != null) render();
        });
    }

    @Override
    protected void onStop() {
        if (listener != null) { listener.remove(); listener = null; }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void render() {
        AuctionStore.image(this, (ImageView) findViewById(R.id.detail_auction_image), auction);
        label(R.id.detail_auction_name, auction.name);
        TextView set = findViewById(R.id.detail_auction_set);
        set.setText(auction.set);
        set.setVisibility(auction.set.isEmpty() ? View.GONE : View.VISIBLE);
        ImageView logo = findViewById(R.id.detail_auction_game_logo);
        int logoId = CardAppearance.logo(auction.game);
        logo.setImageResource(logoId);
        logo.setContentDescription(auction.game);
        logo.setVisibility(logoId == 0 ? View.GONE : View.VISIBLE);
        TextView condition = findViewById(R.id.detail_auction_grade);
        CardAppearance.condition(condition, auction.condition);
        condition.setShadowLayer(dp(2), 0, 0, (condition.getCurrentTextColor() & 0x00FFFFFF) | 0x66000000);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            condition.setOutlineSpotShadowColor(condition.getCurrentTextColor());
            condition.setOutlineAmbientShadowColor(condition.getCurrentTextColor());
        }
        label(R.id.detail_auction_seller, "by " + auction.seller);
        label(R.id.detail_auction_bid, AuctionStore.money(auction.currentBid));
        label(R.id.detail_auction_count, auction.history.size() + " bids");
        label(R.id.detail_auction_start, AuctionStore.money(auction.startingBid));
        label(R.id.detail_auction_increment, AuctionStore.money(auction.increment));
        label(R.id.auction_notes, auction.notes.isEmpty() ? "No seller notes." : auction.notes);
        ImageButton heart = findViewById(R.id.auction_save);
        heart.setVisibility(auction.isOwned() ? View.GONE : View.VISIBLE);
        heart.setImageResource(auction.saved ? R.drawable.auction_heart_saved : R.drawable.auction_heart);
        heart.setContentDescription(auction.saved ? "Remove from watchlist" : "Save to watchlist");
        SimpleDateFormat timestamp = new SimpleDateFormat("MMM d, yyyy · h:mm:ss a", Locale.US);
        TableLayout summary = findViewById(R.id.auction_summary);
        summary.removeAllViews();
        summaryRow(summary, "Total bids", String.valueOf(auction.history.size()));
        summaryRow(summary, "Start date", timestamp.format(new Date(auction.startsAt)));
        summaryRow(summary, "End date", timestamp.format(new Date(auction.endsAt)));
        TableLayout history = findViewById(R.id.auction_history);
        history.removeAllViews();
        historyRow(history, "Bidder", "Amount", "Time", true);
        for (AuctionItem.Bid bid : auction.history) {
            historyRow(history, bid.bidder, AuctionStore.money(bid.amount), timestamp.format(new Date(bid.time)), false);
        }
        updateStatus();
    }

    private void updateStatus() {
        boolean active = auction.isActive(System.currentTimeMillis());
        ImageView image = findViewById(R.id.detail_auction_image);
        image.setAlpha(auction.isPending(System.currentTimeMillis()) ? 0.55f : 1f);
        if (!auction.isPending(System.currentTimeMillis())) image.clearColorFilter();
        label(R.id.detail_auction_time, AuctionStore.countdown(auction, System.currentTimeMillis()));
        String status;
        if (auction.isPending(System.currentTimeMillis())) status = "Scheduled · opens " + date(auction.startsAt);
        else if (!active) status = auction.isOwned() ? (auction.history.isEmpty() ? "Your auction ended with no bids." : "Your card sold for " + AuctionStore.money(auction.currentBid)) : auction.isLeading() ? "You won this auction." : "This auction has ended.";
        else if (auction.isOwned()) status = "This is your auction.";
        else if (auction.isLeading()) status = "You are the highest bidder.";
        else if (auction.hasBid()) status = "You have been outbid.";
        else status = "";
        if (active && auction.autoBidMax > 0) status += (status.isEmpty() ? "" : "\n") + "Auto-bid maximum: " + AuctionStore.money(auction.autoBidMax);
        label(R.id.detail_auction_status, status);
        findViewById(R.id.detail_auction_status).setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        Button bid = findViewById(R.id.auction_place_bid);
        Button auto = findViewById(R.id.auction_auto_bid);
        bid.setEnabled(active && !busy && !auction.isOwned() && !auction.isLeading());
        bid.setAlpha(bid.isEnabled() ? 1f : 0.5f);
        auto.setEnabled(active && !busy && !auction.isOwned());
        auto.setAlpha(auto.isEnabled() ? 1f : 0.5f);
        findViewById(R.id.auction_save).setEnabled(!busy);
        auto.setText(auction.autoBidMax > 0 ? "Edit Auto-Bid" : "Auto-Bid");
    }

    private void bidDialog(boolean automatic) {
        if (auction == null || busy || auction.isOwned() || !auction.isActive(System.currentTimeMillis())) return;
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
                busy = true;
                updateStatus();
                AuctionStore.clearAutoBid(auctionId).addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy = false;
                    if (!task.isSuccessful()) showError(task.getException());
                    if (auction != null) render();
                });
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
            if (busy) return;
            busy = true;
            updateStatus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            dialog.setCancelable(false);
            AuctionStore.bid(this, auctionId, value, automatic).addOnCompleteListener(task -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                if (auction != null) render();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                dialog.setCancelable(true);
                if (!task.isSuccessful()) {
                    amount.setError(task.getException().getMessage());
                    return;
                }
                Toast.makeText(this, automatic ? "Auto-bid maximum saved" : "Bid placed", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }));
        dialog.show();
    }

    private void showError(Exception e) {
        Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void summaryRow(TableLayout parent, String title, String value) {
        TableRow row = new TableRow(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        TextView left = new TextView(this);
        left.setText(title);
        left.setTextSize(12);
        left.setTextColor(android.graphics.Color.parseColor("#95A1B3"));
        left.setPadding(0, 0, dp(12), 0);
        row.addView(left);
        TextView right = new TextView(this);
        right.setText(value);
        right.setSingleLine(true);
        right.setTextSize(13);
        right.setTypeface(null, android.graphics.Typeface.BOLD);
        right.setGravity(android.view.Gravity.END);
        right.setTextColor(android.graphics.Color.parseColor("#FAF8F2"));
        row.addView(right);
        parent.addView(row);
        divider(parent);
    }

    private void historyRow(TableLayout parent, String bidder, String amount, String time, boolean heading) {
        TableRow row = new TableRow(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        if (heading) row.setBackgroundColor(android.graphics.Color.parseColor("#182B3E"));
        String[] values = {bidder, amount, time};
        for (int i = 0; i < values.length; i++) {
            TextView cell = new TextView(this);
            cell.setText(values[i]);
            cell.setTextSize(heading ? 10 : 11);
            int startPadding = i == 2 ? dp(20) : dp(5);
            cell.setPaddingRelative(startPadding, dp(8), dp(5), dp(8));
            cell.setTextColor(android.graphics.Color.parseColor(heading ? "#95A1B3" : "#FAF8F2"));
            if (!heading && i == 1) cell.setTypeface(null, android.graphics.Typeface.BOLD);
            if (i == 0) cell.setMaxEms(10);
            else cell.setSingleLine(true);
            if (i == 2) cell.setGravity(android.view.Gravity.END);
            row.addView(cell);
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
        return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(new Date(time));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void label(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }
}
