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
            registerButton.setEnabled(false);
            new Thread(() -> {
                String error = null;
                try (AccountStore store = new AccountStore(this)) {
                    long id = store.register(username, birthday, email, password);
                    store.startSession(id);
                } catch (android.database.sqlite.SQLiteConstraintException e) {
                    error = "That username or email is already registered.";
                } catch (Exception e) { error = "Could not register. Please try again."; }
                final String message = error;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    registerButton.setEnabled(true);
                    if (message == null) {
                        Intent intent = new Intent(this, HomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }).start();
        });
        findViewById(R.id.login_button).setOnClickListener(view -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
