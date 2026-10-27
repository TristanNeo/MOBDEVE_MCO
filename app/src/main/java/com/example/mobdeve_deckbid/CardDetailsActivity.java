package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class CardDetailsActivity extends Activity {
    private BinderStore.Card card;
    private Spinner condition;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_card_details);
        String id = getIntent().getStringExtra("card_id");
        for (BinderStore.Card candidate : BinderStore.load(this)) {
            if (candidate.id.equals(id)) card = candidate;
        }
        if (card == null) {
            finish();
            return;
        }
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        BinderStore.image(this, (ImageView) findViewById(R.id.detail_image), card);
        ((TextView) findViewById(R.id.detail_name)).setText(card.name);
        ((TextView) findViewById(R.id.detail_set)).setText(card.game + "\n" + card.set);
        condition = findViewById(R.id.detail_condition);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.binder_spinner_item, BinderStore.CONDITIONS);
        adapter.setDropDownViewResource(R.layout.binder_spinner_item);
        condition.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < BinderStore.CONDITIONS.length; i++) {
            if (BinderStore.CONDITIONS[i].equals(card.condition)) selected = i;
        }
        if (state != null) selected = state.getInt("condition", selected);
        condition.setSelection(selected);
        condition.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { render(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        render();
    }

    private void render() {
        LinearLayout highest = findViewById(R.id.highest_table);
        LinearLayout active = findViewById(R.id.active_table);
        highest.removeAllViews();
        active.removeAllViews();
        TextView value = findViewById(R.id.detail_value);
        TextView change = findViewById(R.id.detail_change);
        change.setText(BinderStore.change(card));
        change.setTextColor(BinderStore.changeColor(card));
        if (!card.demo) {
            value.setText("No sales yet");
            ((TextView) findViewById(R.id.detail_note)).setText("No matching auction history available.");
            row(highest, new String[]{"No completed auctions for this card."}, false);
            row(active, new String[]{"No active auctions for this card."}, false);
            return;
        }
        String selectedCondition = condition.getSelectedItem().toString();
        double multiplier = selectedCondition.equals("PSA 10") ? 2.4 : selectedCondition.equals("PSA 9") ? 1.6 : 1;
        double average = card.average * multiplier;
        value.setText(BinderStore.money(average));
        ((TextView) findViewById(R.id.detail_note)).setText("Sample data · " + selectedCondition + " · change vs. previous 30 days\nAverage of the five completed auctions below.");
        row(highest, new String[]{"#", "Price", "Date", "Condition"}, true);
        double[] prices = {1.4, 1.15, 1.0, 0.8, 0.65};
        String[] dates = {"Oct 24, 2026", "Oct 21, 2026", "Oct 18, 2026", "Oct 14, 2026", "Oct 09, 2026"};
        for (int i = 0; i < prices.length && i < 5; i++) {
            row(highest, new String[]{String.valueOf(i + 1), BinderStore.money(average * prices[i]), dates[i], selectedCondition}, false);
        }
        row(active, new String[]{"#", "Current Bid", "Time Left", "Condition"}, true);
        String[] times = {"2h 14m", "6h 32m", "1d 4h", "2d 1h", "2d 12h"};
        for (int i = 0; i < times.length && i < 5; i++) {
            row(active, new String[]{String.valueOf(i + 1), BinderStore.money(average * (0.7 + i * 0.05)), times[i], selectedCondition}, false);
        }
    }

    private void row(LinearLayout table, String[] cells, boolean header) {
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
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (condition != null) state.putInt("condition", condition.getSelectedItemPosition());
        super.onSaveInstanceState(state);
    }
}
