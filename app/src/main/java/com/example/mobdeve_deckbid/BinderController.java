package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class BinderController {
    private final Activity activity;
    private final GridLayout grid;
    private final EditText search;
    private ArrayList<BinderStore.Card> cards;

    public BinderController(Activity activity) {
        this.activity = activity;
        grid = activity.findViewById(R.id.binder_grid);
        search = activity.findViewById(R.id.binder_search);
        activity.findViewById(R.id.add_card_button).setOnClickListener(v -> activity.startActivity(new Intent(activity, AddCardActivity.class)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            public void afterTextChanged(Editable s) { }
        });
        refresh();
    }

    public void refresh() {
        cards = BinderStore.load(activity);
        render();
    }

    private void render() {
        if (cards == null) return;
        grid.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (BinderStore.Card card : cards) {
            if (!(card.name + " " + card.set + " " + card.game).toLowerCase(Locale.ROOT).contains(query)) continue;
            View tile = activity.getLayoutInflater().inflate(R.layout.item_binder_card, grid, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(count % 2, 1f);
            params.rowSpec = GridLayout.spec(count / 2);
            int gap = Math.round(6 * activity.getResources().getDisplayMetrics().density);
            params.setMargins(count % 2 == 0 ? 0 : gap, 0, count % 2 == 0 ? gap : 0, gap * 2);
            tile.setLayoutParams(params);
            BinderStore.image(activity, (ImageView) tile.findViewById(R.id.card_image), card);
            ((TextView) tile.findViewById(R.id.card_name)).setText(card.name);
            ((TextView) tile.findViewById(R.id.card_set)).setText(card.set.isEmpty() ? card.game : card.set);
            ((TextView) tile.findViewById(R.id.card_condition)).setText(card.condition);
            ((TextView) tile.findViewById(R.id.card_value)).setText(card.demo ? BinderStore.money(card.average) : "No sales yet");
            TextView change = tile.findViewById(R.id.card_change);
            change.setText(BinderStore.change(card));
            change.setTextColor(BinderStore.changeColor(card));
            tile.setOnClickListener(v -> {
                Intent intent = new Intent(activity, CardDetailsActivity.class);
                intent.putExtra("card_id", card.id);
                activity.startActivity(intent);
            });
            grid.addView(tile);
            count++;
        }
        ((TextView) activity.findViewById(R.id.binder_count)).setText(count + " cards · Sample auction data");
        activity.findViewById(R.id.binder_empty).setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }
}
