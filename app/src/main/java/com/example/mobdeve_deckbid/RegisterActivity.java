package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import java.util.Calendar;
import java.util.Locale;

public class RegisterActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        if (savedInstanceState == null) AccountStore.registrationPhoto = null;
        showPhoto();
        findViewById(R.id.register_upload_photo).setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, 502);
        });
        EditText usernameInput = findViewById(R.id.username_input);
        EditText birthdayInput = findViewById(R.id.birthday_input);
        EditText emailInput = findViewById(R.id.email_input);
        EditText passwordInput = findViewById(R.id.password_input);
        birthdayInput.setFocusable(false);
        birthdayInput.setOnClickListener(view -> {
            Calendar date = Calendar.getInstance();
            date.add(Calendar.YEAR, -18);
            String[] parts = birthdayInput.getText().toString().split("-");
            if (parts.length == 3) date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            DatePickerDialog picker = new DatePickerDialog(this, (p, year, month, day) -> {
                birthdayInput.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day));
                birthdayInput.setError(null);
            }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });
        Button registerButton = findViewById(R.id.register_button);
        registerButton.setOnClickListener(view -> {
            String username = usernameInput.getText().toString().trim();
            String birthday = birthdayInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            if (!username.matches("[A-Za-z0-9_]{3,24}")) { usernameInput.setError("Use 3–24 letters, numbers or underscores"); return; }
            if (birthday.isEmpty()) { birthdayInput.setError("Select your birthday"); return; }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailInput.setError("Enter a valid email address"); return; }
            if (password.length() < 8) { passwordInput.setError("Use at least 8 characters"); return; }
            AccountStore store = new AccountStore(this);
            long id = store.register(username, birthday, email, password);
            store.startSession(id);
            store.close();
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.login_button).setOnClickListener(view -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
    private void showPhoto() {
        android.widget.ImageView photo = findViewById(R.id.register_photo);
        photo.setClipToOutline(true);
        if (AccountStore.registrationPhoto != null) photo.setImageBitmap(AccountStore.registrationPhoto);
        else photo.setImageResource(R.drawable.ic_nav_profile);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 502 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        findViewById(R.id.register_upload_photo).setEnabled(false);
        findViewById(R.id.register_button).setEnabled(false);
        new Thread(() -> {
            boolean loaded;
            try {
                AccountStore.registrationPhoto = ProfileImages.load(this, data.getData());
                loaded = true;
            } catch (Exception e) { loaded = false; }
            final boolean success = loaded;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                findViewById(R.id.register_upload_photo).setEnabled(true);
                findViewById(R.id.register_button).setEnabled(true);
                if (success) showPhoto();
                else Toast.makeText(this, "Could not open that photo. Try another image.", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

}
