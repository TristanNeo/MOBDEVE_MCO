package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;

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
            AccountStore store = new AccountStore(this);
            long id = store.login(email, password);
            if (id < 0) {
                store.close();
                Toast.makeText(this, "Register first, then use the same email and password for this session.", Toast.LENGTH_LONG).show();
                return;
            }
            store.startSession(id);
            store.close();
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.register_button).setOnClickListener(view -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }

    private void loginUser(String email, String password) {

        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult result) {

                        String uid = result.getUser().getUid();

                        FirebaseFirestore db =
                                FirebaseFirestore.getInstance();

                        db.collection("users").document(uid).get()
                                .addOnSuccessListener(
                                        new OnSuccessListener<DocumentSnapshot>() {
                                            @Override
                                            public void onSuccess(DocumentSnapshot document) {

                                                if (document.exists()) {

                                                    String username =
                                                            document.getString("username");

                                                    Intent intent = new Intent(
                                                            LoginActivity.this,
                                                            HomeActivity.class
                                                    );

                                                    intent.putExtra("username", username);
                                                    startActivity(intent);
                                                    finish();

                                                } else {
                                                    Log.w("DeckBidDB", "Profile not found");
                                                }
                                            }
                                        })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(Exception e) {
                                        Log.w("DeckBidDB",
                                                "Error retrieving profile", e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w("DeckBidDB", "Login failed", e);
                    }
                });
    }
}
