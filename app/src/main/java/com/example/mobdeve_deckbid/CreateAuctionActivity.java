package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.*;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.util.Calendar;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class CreateAuctionActivity extends Activity {
    private String cardId;
    private Calendar start = Calendar.getInstance();
    private CheckBox immediately;
    private Button date, time, submit;
    private EditText price, increment, duration, notes;
    private Spinner unit;
    private boolean ready;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_create_auction);
        cardId = getIntent().getStringExtra("card_id");
        String uid = AccountStore.uid();
        if (uid == null || cardId == null) { finish(); return; }
        start.add(Calendar.MINUTE, 5);
        if (state != null) start.setTimeInMillis(state.getLong("start", start.getTimeInMillis()));
        immediately = findViewById(R.id.create_start_now);
        date = findViewById(R.id.create_start_date); time = findViewById(R.id.create_start_time);
        submit = findViewById(R.id.create_auction_submit);
        price = findViewById(R.id.create_starting_bid); increment = findViewById(R.id.create_increment);
        duration = findViewById(R.id.create_duration); notes = findViewById(R.id.create_notes);
        unit = findViewById(R.id.create_duration_unit);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.binder_spinner_item, new String[]{"Hours", "Days"});
        adapter.setDropDownViewResource(R.layout.binder_spinner_item); unit.setAdapter(adapter); unit.setSelection(1);
        if (state != null) immediately.setChecked(state.getBoolean("immediately", true));
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        immediately.setOnCheckedChangeListener((v, checked) -> updateStart());
        date.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                start.set(Calendar.YEAR, year); start.set(Calendar.MONTH, month); start.set(Calendar.DAY_OF_MONTH, day); updateStart();
            }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMinDate(System.currentTimeMillis()); picker.show();
        });
        time.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            start.set(Calendar.HOUR_OF_DAY, hour); start.set(Calendar.MINUTE, minute); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0); updateStart();
        }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), false).show());
        submit.setEnabled(false); submit.setOnClickListener(v -> create());
        updateStart();
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("binder").document(cardId).get(Source.SERVER)
                .addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!task.isSuccessful() || !uid.equals(AccountStore.uid()) || !task.getResult().exists()) {
                        Toast.makeText(this, "Could not load your Binder card.", Toast.LENGTH_LONG).show(); finish(); return;
                    }
                    BinderStore.Card card = BinderStore.read(task.getResult());
                    BinderStore.image(this, (ImageView) findViewById(R.id.create_card_image), card);
                    ((TextView) findViewById(R.id.create_card_name)).setText(card.name);
                    ((TextView) findViewById(R.id.create_card_details)).setText(card.game + "\n" + card.set + "\n" + card.condition);
                    ready = true; submit.setEnabled(true);
                });
    }
    private void updateStart() {
        date.setEnabled(!immediately.isChecked()); time.setEnabled(!immediately.isChecked());
        date.setText(new SimpleDateFormat("MMM d, yyyy", Locale.US).format(start.getTime()));
        time.setText(new SimpleDateFormat("h:mm a", Locale.US).format(start.getTime()));
    }
    private void create() {
        if (!ready) return;
        long starting, step, length;
        try { starting = Long.parseLong(price.getText().toString().trim()); }
        catch (NumberFormatException e) { price.setError("Enter a starting bid in whole pesos"); return; }
        try { step = Long.parseLong(increment.getText().toString().trim()); }
        catch (NumberFormatException e) { increment.setError("Enter a bid increment in whole pesos"); return; }
        try { length = Long.parseLong(duration.getText().toString().trim()); }
        catch (NumberFormatException e) { duration.setError("Enter a whole number"); return; }
        long maximum = unit.getSelectedItemPosition() == 0 ? 168 : 7;
        if (length < 1 || length > maximum) { duration.setError("Choose 1–" + maximum + (maximum == 7 ? " days" : " hours")); return; }
        long begins = immediately.isChecked() ? System.currentTimeMillis() : start.getTimeInMillis();
        if (!immediately.isChecked() && begins <= System.currentTimeMillis()) {
            Toast.makeText(this, "Choose a future start date and time.", Toast.LENGTH_LONG).show(); return;
        }
        long milliseconds = length * (unit.getSelectedItemPosition() == 0 ? 3600000L : 86400000L);
        submit.setEnabled(false);
        AuctionStore.create(this, cardId, starting, step, begins, milliseconds, notes.getText().toString()).addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) return;
            submit.setEnabled(true);
            if (!task.isSuccessful()) { Toast.makeText(this, task.getException().getMessage(), Toast.LENGTH_LONG).show(); return; }
            Toast.makeText(this, immediately.isChecked() ? "Auction opened" : "Auction scheduled", Toast.LENGTH_SHORT).show(); finish();
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("start", start.getTimeInMillis()); state.putBoolean("immediately", immediately.isChecked()); super.onSaveInstanceState(state);
    }
}
