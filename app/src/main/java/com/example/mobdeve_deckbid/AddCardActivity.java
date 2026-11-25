package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Locale;

public class AddCardActivity extends Activity {
    private Spinner game, condition;
    private EditText name, set, search;
    private RadioGroup mode;
    private String photoUri = "", selectedId = "";
    private int lastGame, lastCondition;
    private boolean changing;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_add_card);
        game = findViewById(R.id.game_spinner);
        condition = findViewById(R.id.condition_spinner);
        name = findViewById(R.id.add_name);
        set = findViewById(R.id.add_set);
        search = findViewById(R.id.card_catalog_search);
        mode = findViewById(R.id.card_source_mode);
        bind(game, BinderStore.GAMES);
        bind(condition, BinderStore.CONDITIONS);
        if (state != null) {
            lastGame = state.getInt("game"); lastCondition = state.getInt("condition");
            game.setSelection(lastGame); condition.setSelection(lastCondition);
            photoUri = state.getString("photo_uri", ""); selectedId = state.getString("source", "");
            mode.check(state.getBoolean("custom") ? R.id.card_source_new : R.id.card_source_existing);
            name.setText(state.getString("name", "")); set.setText(state.getString("set", ""));
            search.setText(state.getString("query", ""));
        }
        AdapterView.OnItemSelectedListener chosen = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (lastGame != game.getSelectedItemPosition() || lastCondition != condition.getSelectedItemPosition()) {
                    lastGame = game.getSelectedItemPosition(); lastCondition = condition.getSelectedItemPosition();
                    clearSelection();
                }
                renderCatalog();
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        game.setOnItemSelectedListener(chosen); condition.setOnItemSelectedListener(chosen);
        mode.setOnCheckedChangeListener((group, id) -> { clearSelection(); renderMode(); });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!changing && !selectedId.isEmpty()) {
                    selectedId = ""; photoUri = ""; name.setText(""); set.setText(""); preview();
                }
                renderCatalog();
            }
            public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        findViewById(R.id.pick_photo_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, 10);
        });
        findViewById(R.id.save_card_button).setOnClickListener(v -> save());
        renderMode(); preview();
    }
    private boolean custom() { return mode.getCheckedRadioButtonId() == R.id.card_source_new; }
    private void bind(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.binder_spinner_item, values);
        adapter.setDropDownViewResource(R.layout.binder_spinner_item); spinner.setAdapter(adapter);
    }
    private void clearSelection() {
        changing = true;
        selectedId = ""; photoUri = ""; search.setText(""); name.setText(""); set.setText("");
        changing = false; preview();
    }
    private void renderMode() {
        name.setEnabled(custom()); set.setEnabled(custom());
        findViewById(R.id.card_catalog_panel).setVisibility(custom() ? View.GONE : View.VISIBLE);
        renderCatalog();
    }
    private void renderCatalog() {
        if (game.getSelectedItem() == null || condition.getSelectedItem() == null) return;
        LinearLayout rows = findViewById(R.id.card_catalog_results);
        rows.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (AuctionItem a : AuctionStore.catalog(this, game.getSelectedItem().toString(), condition.getSelectedItem().toString())) {
            if (!(a.name + " " + a.set).toLowerCase(Locale.ROOT).contains(query)) continue;
            count++;
            if (count > 10) continue;
            TextView row = new TextView(this);
            row.setText(a.name + "\n" + a.set + " · " + a.condition);
            row.setTextColor(android.graphics.Color.parseColor(a.id.equals(selectedId) ? "#D4A63A" : "#FAF8F2"));
            row.setTextSize(14); int padding = Math.round(12 * getResources().getDisplayMetrics().density);
            row.setPadding(padding, padding, padding, padding);
            row.setBackgroundResource(R.drawable.input_background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = padding / 2;
            rows.addView(row, params);
            row.setOnClickListener(v -> {
                changing = true; search.setText(a.name); changing = false;
                selectedId = a.id; name.setText(a.name); set.setText(a.set); photoUri = a.photo;
                preview(); renderCatalog();
                ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(search.getWindowToken(), 0);
            });
        }
        ((TextView) findViewById(R.id.card_catalog_note)).setText(count == 0
                ? "No matching card history. Choose Add new card."
                : count > 10 ? count + " matches. Type more to narrow the results."
                : count + " matching card" + (count == 1 ? "" : "s") + " · select one below");
    }
    private void preview() {
        BinderStore.Card card = new BinderStore.Card("", "Selected card photo", "", "", "Ungraded");
        card.uri = photoUri; BinderStore.image(this, (ImageView) findViewById(R.id.add_preview), card);
    }
    private void save() {
        if (!custom() && selectedId.isEmpty()) {
            Toast.makeText(this, "Select an existing card or choose Add new card.", Toast.LENGTH_LONG).show(); return;
        }
        String cardName = name.getText().toString().trim();
        if (cardName.isEmpty()) { name.setError("Enter a card name"); name.requestFocus(); return; }
        BinderStore.Card card = new BinderStore.Card(java.util.UUID.randomUUID().toString(), cardName,
                set.getText().toString().trim(), game.getSelectedItem().toString(), condition.getSelectedItem().toString());
        card.uri = photoUri; card.sourceAuctionId = custom() ? "" : selectedId;
        findViewById(R.id.save_card_button).setEnabled(false);
        BinderStore.add(this, card).addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            findViewById(R.id.save_card_button).setEnabled(true);
            if (!task.isSuccessful()) { Toast.makeText(this, "Could not save: " + task.getException().getMessage(), Toast.LENGTH_LONG).show(); return; }
            Toast.makeText(this, "Card added to Binder", Toast.LENGTH_SHORT).show(); finish();
        });
    }
    @Override protected void onStart() { super.onStart(); listener = AuctionStore.listen(this, this::renderCatalog); }
    @Override protected void onStop() { if (listener != null) { listener.remove(); listener = null; } super.onStop(); }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 10 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            photoUri = uri.toString(); preview();
        } catch (SecurityException e) { Toast.makeText(this, "Choose a photo from device storage.", Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("photo_uri", photoUri); state.putString("source", selectedId);
        state.putBoolean("custom", custom()); state.putInt("game", game.getSelectedItemPosition());
        state.putInt("condition", condition.getSelectedItemPosition());
        state.putString("name", name.getText().toString()); state.putString("set", set.getText().toString());
        state.putString("query", search.getText().toString()); super.onSaveInstanceState(state);
    }
}
