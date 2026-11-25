package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class AddCardActivity extends Activity {
    private static final int NEXT_PAGE = 1, PICK_PHOTO = 2;
    private static final int CHOOSE_GAME = 0, SEARCH = 1, NEW_GAME = 2, NEW_CARD = 3;
    private final int[] gradeIds = {R.id.grade_ungraded, R.id.grade_nine, R.id.grade_ten};
    private final ArrayList<AuctionStore.CatalogCard> editions = new ArrayList<>();
    private final ArrayList<AuctionStore.CatalogCard> matches = new ArrayList<>();
    private int page;
    private String game, cardId, grade, photoUri, editionKey;
    private AuctionStore.CatalogCard selected;
    private EditText search;
    private ListView suggestions;
    private ArrayAdapter<AuctionStore.CatalogCard> adapter;
    private boolean selecting, reopenSuggestions;
    private Task<Void> pendingSave;
    private ListenerRegistration listener;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable catalogOpening = this::loadCatalog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (AccountStore.uid() == null) { finish(); return; }
        setContentView(R.layout.activity_add_card);
        page = getIntent().getIntExtra("page", CHOOSE_GAME);
        game = getIntent().getStringExtra("game");
        grade = state == null ? BinderStore.CONDITIONS[0] : state.getString("grade", BinderStore.CONDITIONS[0]);
        photoUri = state == null ? "" : state.getString("photo", "");
        editionKey = state == null ? "" : state.getString("edition", "");
        cardId = state == null ? UUID.randomUUID().toString() : state.getString("card_id");
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.add_title)).setText(page == NEW_GAME || page == NEW_CARD ? "Add new card" : "Add to Binder");
        FrameLayout body = findViewById(R.id.add_page);
        if (page == CHOOSE_GAME || page == NEW_GAME) {
            getLayoutInflater().inflate(R.layout.add_card_games, body, true);
            int[] tiles = {R.id.select_pokemon, R.id.select_onepiece, R.id.select_yugioh, R.id.select_magic};
            for (int i = 0; i < tiles.length; i++) {
                String choice = BinderStore.GAMES[i];
                findViewById(tiles[i]).setOnClickListener(v -> next(page == NEW_GAME ? NEW_CARD : SEARCH, choice));
            }
            return;
        }
        if (game == null || CardAppearance.logo(game) == 0) { finish(); return; }
        ImageView logo = findViewById(R.id.add_game_logo);
        logo.setImageResource(CardAppearance.logo(game));
        logo.setContentDescription(game);
        logo.setVisibility(View.VISIBLE);
        findViewById(R.id.add_footer).setVisibility(View.VISIBLE);
        getLayoutInflater().inflate(page == SEARCH ? R.layout.add_card_search : R.layout.add_card_new, body, true);
        if (page == SEARCH) bindSearch(state);
        else bindNewCard();
        bindGrades();
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof Task<?>) {
            @SuppressWarnings("unchecked") Task<Void> task = (Task<Void>) retained;
            observeSave(task);
        }
    }

    private void next(int destination, String chosenGame) {
        Intent intent = new Intent(this, AddCardActivity.class);
        intent.putExtra("page", destination);
        if (chosenGame != null) intent.putExtra("game", chosenGame);
        startActivityForResult(intent, NEXT_PAGE);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void bindSearch(Bundle state) {
        search = findViewById(R.id.card_catalog_search);
        suggestions = findViewById(R.id.card_suggestions);
        adapter = new ArrayAdapter<AuctionStore.CatalogCard>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, matches) {
            @Override public View getView(int position, View row, ViewGroup parent) {
                if (row == null) row = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
                AuctionItem item = getItem(position).artwork;
                TextView name = row.findViewById(android.R.id.text1);
                TextView set = row.findViewById(android.R.id.text2);
                name.setText(item.name);
                set.setText(item.set.isEmpty() ? "Set not recorded" : item.set);
                return row;
            }
        };
        suggestions.setAdapter(adapter);
        suggestions.setOnItemClickListener((parent, row, position, id) -> select(matches.get(position)));
        if (state != null) search.setText(state.getString("query", ""));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void afterTextChanged(Editable s) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (selecting) return;
                boolean hadSelection = selected != null;
                selected = null;
                editionKey = "";
                if (hadSelection) showSelection();
                filterSuggestions();
            }
        });
        search.setOnClickListener(v -> {
            reopenSuggestions = true;
            filterSuggestions();
        });
        View addNew = findViewById(R.id.new_card_button);
        addNew.setVisibility(View.VISIBLE);
        addNew.setOnClickListener(v -> next(NEW_GAME, null));
        findViewById(R.id.save_card_button).setOnClickListener(v -> {
            if (selected != null) save(selected.copy(cardId, grade));
        });
    }

    private void loadCatalog() {
        clock.removeCallbacks(catalogOpening);
        long nextOpening = AuctionStore.nextCatalogOpening(game);
        if (nextOpening != Long.MAX_VALUE) {
            clock.postDelayed(catalogOpening, Math.max(1L, nextOpening - System.currentTimeMillis()));
        }
        editions.clear();
        editions.addAll(AuctionStore.catalog(this, game));
        selected = null;
        for (AuctionStore.CatalogCard card : editions) {
            if (card.key.equals(editionKey)) { selected = card; break; }
        }
        showSelection();
        filterSuggestions();
    }

    private void filterSuggestions() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        matches.clear();
        if ((selected == null || reopenSuggestions) && !query.isEmpty()) {
            for (AuctionStore.CatalogCard card : editions) {
                if ((card.artwork.name + " " + card.artwork.set).toLowerCase(Locale.ROOT).contains(query)) matches.add(card);
            }
        }
        adapter.notifyDataSetChanged();
        suggestions.setVisibility(matches.isEmpty() ? View.GONE : View.VISIBLE);
        suggestions.setSelection(0);
    }

    private void select(AuctionStore.CatalogCard card) {
        selected = card;
        reopenSuggestions = false;
        editionKey = card.key;
        selecting = true;
        search.setText(card.artwork.name);
        search.setSelection(search.length());
        selecting = false;
        if (grade.equals(card.artwork.condition)) showSelection();
        else setGrade(card.artwork.condition);
        filterSuggestions();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
        ((android.widget.ScrollView) findViewById(R.id.card_search_scroll)).scrollTo(0, 0);
    }

    private void showSelection() {
        findViewById(R.id.edition_fields).setVisibility(selected == null ? View.GONE : View.VISIBLE);
        if (selected == null) {
            ImageView preview = findViewById(R.id.add_preview);
            preview.setImageResource(R.drawable.deckbid_card_placeholder);
            preview.setContentDescription("Card preview");
            return;
        }
        BinderStore.Card copy = selected.copy(cardId, grade);
        ((TextView) findViewById(R.id.edition_set)).setText(copy.set.isEmpty() ? "Set not recorded" : copy.set);
        BinderStore.image(this, findViewById(R.id.add_preview), copy);
    }

    private void bindNewCard() {
        showPhoto();
        findViewById(R.id.pick_photo_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_PHOTO);
        });
        View submit = findViewById(R.id.submit_new_card);
        submit.setVisibility(View.VISIBLE);
        submit.setOnClickListener(v -> {
            EditText name = findViewById(R.id.add_name);
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { name.setError("Enter a card name"); name.requestFocus(); return; }
            String set = ((EditText) findViewById(R.id.add_set)).getText().toString().trim();
            BinderStore.Card card = new BinderStore.Card(cardId, title, set, game, grade);
            card.uri = photoUri;
            save(card);
        });
    }

    private void showPhoto() {
        BinderStore.Card card = new BinderStore.Card(cardId, "Card preview", "", game, grade);
        card.uri = photoUri;
        BinderStore.image(this, findViewById(R.id.add_preview), card);
    }

    private void bindGrades() {
        RadioGroup group = findViewById(R.id.grade_choices);
        for (int i = 0; i < gradeIds.length; i++) {
            RadioButton chip = findViewById(gradeIds[i]);
            CardAppearance.condition(chip, BinderStore.CONDITIONS[i]);
            chip.setContentDescription(BinderStore.CONDITIONS[i]);
        }
        setGrade(grade);
        group.setOnCheckedChangeListener((g, checked) -> {
            for (int i = 0; i < gradeIds.length; i++) if (gradeIds[i] == checked) grade = BinderStore.CONDITIONS[i];
            markGrade();
            if (selected != null) showSelection();
        });
    }

    private void setGrade(String value) {
        grade = value;
        RadioGroup group = findViewById(R.id.grade_choices);
        for (int i = 0; i < gradeIds.length; i++) if (BinderStore.CONDITIONS[i].equals(grade)) group.check(gradeIds[i]);
        markGrade();
    }

    private void markGrade() {
        for (int i = 0; i < gradeIds.length; i++) {
            RadioButton chip = findViewById(gradeIds[i]);
            String label = BinderStore.CONDITIONS[i];
            chip.setAlpha(chip.isChecked() ? 1f : 0.72f);
            chip.setText(chip.isChecked() ? "✓ " + label : label);
        }
    }

    private void save(BinderStore.Card card) {
        if (pendingSave == null) observeSave(BinderStore.add(this, card));
    }

    private void observeSave(Task<Void> task) {
        pendingSave = task;
        View submit = findViewById(page == SEARCH ? R.id.save_card_button : R.id.submit_new_card);
        submit.setEnabled(false);
        findViewById(R.id.new_card_button).setEnabled(false);
        task.addOnCompleteListener(done -> {
            if (isDestroyed() || isFinishing()) return;
            pendingSave = null;
            if (done.isSuccessful()) {
                Toast.makeText(this, "Card added to Binder", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                submit.setEnabled(true);
                findViewById(R.id.new_card_button).setEnabled(true);
                Toast.makeText(this, "Could not save: " + done.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override public Object onRetainNonConfigurationInstance() { return pendingSave; }

    @Override protected void onStart() {
        super.onStart();
        if (page == SEARCH && search != null) {
            loadCatalog();
            listener = AuctionStore.listen(this, this::loadCatalog);
        }
    }

    @Override protected void onStop() {
        if (listener != null) { listener.remove(); listener = null; }
        clock.removeCallbacks(catalogOpening);
        super.onStop();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK) return;
        if (request == NEXT_PAGE) { setResult(RESULT_OK); finish(); return; }
        if (request != PICK_PHOTO || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        photoUri = uri.toString();
        showPhoto();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("card_id", cardId);
        state.putString("grade", grade);
        state.putString("edition", editionKey);
        state.putString("photo", photoUri);
        if (search != null) state.putString("query", search.getText().toString());
    }
}
