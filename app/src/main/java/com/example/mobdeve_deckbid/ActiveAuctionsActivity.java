package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Collections;
import java.util.List;

public class ActiveAuctionsActivity extends Activity {
    static final String EXTRA_BIDDING = "show_bidding";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AuctionRows adapter = new AuctionRows();
    private ListenerRegistration listener;
    private ListView list;
    private RadioGroup roles;
    private TextView empty;
    private ActiveAuctions active;
    private List<AuctionItem> items = Collections.emptyList();

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (System.currentTimeMillis() >= active.nextChange) {
                refresh();
            } else {
                for (int i = 0; i < list.getChildCount(); i++) {
                    TextView clock = list.getChildAt(i).findViewById(R.id.auction_time);
                    clock.setText(AuctionStore.timeLeft((AuctionItem) clock.getTag()));
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_active_auctions);
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        roles = findViewById(R.id.home_auction_roles);
        list = findViewById(R.id.active_auction_rows);
        empty = findViewById(R.id.active_auction_empty);
        list.setEmptyView(empty);
        list.setAdapter(adapter);
        boolean bidding = state == null ? getIntent().getBooleanExtra(EXTRA_BIDDING, false)
                : state.getBoolean(EXTRA_BIDDING);
        roles.check(bidding ? R.id.home_bidding : R.id.home_selling);
        roles.setOnCheckedChangeListener((group, checked) -> {
            if (active == null) return;
            showRole();
            list.setSelection(0);
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (AccountStore.uid() == null) {
            finish();
            return;
        }
        refresh();
        listener = AuctionStore.listen(this, this::refresh);
        handler.postDelayed(tick, 1000);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(tick);
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean(EXTRA_BIDDING, roles.getCheckedRadioButtonId() == R.id.home_bidding);
        super.onSaveInstanceState(out);
    }

    private void refresh() {
        active = new ActiveAuctions(AuctionStore.load(this), System.currentTimeMillis());
        CardAppearance.roleCounts(roles, active);
        showRole();
    }

    private void showRole() {
        boolean bidding = roles.getCheckedRadioButtonId() == R.id.home_bidding;
        items = active.items(bidding);
        empty.setText(ActiveAuctions.emptyMessage(bidding));
        adapter.notifyDataSetChanged();
    }

    private final class AuctionRows extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public AuctionItem getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View row, ViewGroup parent) {
            if (row == null) row = getLayoutInflater().inflate(R.layout.item_auction_row, parent, false);
            CardAppearance.bindActiveRow(ActiveAuctionsActivity.this, row, getItem(position));
            return row;
        }
    }
}
