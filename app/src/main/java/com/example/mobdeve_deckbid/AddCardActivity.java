package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

public class AddCardActivity extends Activity {
    private Spinner artwork, game, condition;
    private EditText name, set;
    private String photoUri = "";
    private int selectedArtwork = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_add_card);
        artwork = findViewById(R.id.artwork_spinner);
        game = findViewById(R.id.game_spinner);
        condition = findViewById(R.id.condition_spinner);
        name = findViewById(R.id.add_name);
        set = findViewById(R.id.add_set);
        bind(artwork, BinderStore.ARTWORK);
        bind(game, BinderStore.GAMES);
        bind(condition, BinderStore.CONDITIONS);
        if (state != null) {
            photoUri = state.getString("photo_uri", "");
            selectedArtwork = state.getInt("artwork", 0);
            artwork.setSelection(selectedArtwork);
        }
        artwork.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != selectedArtwork) photoUri = "";
                selectedArtwork = position;
                preview();
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        findViewById(R.id.pick_photo_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, 10);
        });
        findViewById(R.id.save_card_button).setOnClickListener(v -> save());
        preview();
    }

    private void bind(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.binder_spinner_item, values);
        adapter.setDropDownViewResource(R.layout.binder_spinner_item);
        spinner.setAdapter(adapter);
    }

    private void preview() {
        BinderStore.Card card = new BinderStore.Card("", "Selected card photo", "", "", "Ungraded", selectedArtwork);
        card.uri = photoUri;
        BinderStore.image(this, (ImageView) findViewById(R.id.add_preview), card);
    }

    private void save() {
        String cardName = name.getText().toString().trim();
        if (cardName.isEmpty()) {
            name.setError("Enter a card name");
            name.requestFocus();
            return;
        }
        BinderStore.Card card = new BinderStore.Card(java.util.UUID.randomUUID().toString(), cardName,
                set.getText().toString().trim(), game.getSelectedItem().toString(),
                condition.getSelectedItem().toString(), artwork.getSelectedItemPosition());
        card.uri = photoUri;
        if (!BinderStore.add(this, card)) {
            Toast.makeText(this, "Could not save. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Card added to Binder", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                photoUri = uri.toString();
                preview();
            } catch (SecurityException e) {
                Toast.makeText(this, "Choose a photo from device storage.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("photo_uri", photoUri);
        state.putInt("artwork", selectedArtwork);
        super.onSaveInstanceState(state);
    }
}
