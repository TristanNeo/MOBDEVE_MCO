package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        EditText emailInput = findViewById(R.id.email_input);
        EditText passwordInput = findViewById(R.id.password_input);
        Button loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(view -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            if (email.isEmpty()) { emailInput.setError("Enter your email address"); return; }
            if (password.isEmpty()) { passwordInput.setError("Enter your password"); return; }
            loginButton.setEnabled(false);
            new Thread(() -> {
                long accountId = -1;
                String error = "Email or password is incorrect.";
                try (AccountStore store = new AccountStore(this)) {
                    accountId = store.login(email, password);
                    if (accountId > 0) store.startSession(accountId);
                } catch (Exception e) { error = "Could not log in. Please try again."; }
                final boolean success = accountId > 0;
                final String message = error;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    loginButton.setEnabled(true);
                    if (success) {
                        Intent intent = new Intent(this, HomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }).start();
        });
        findViewById(R.id.register_button).setOnClickListener(view -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }
}
