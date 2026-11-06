package com.example.mobdeve_deckbid;

import android.content.Context;
import android.graphics.Bitmap;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.util.HashMap;
import java.util.Map;

public class AccountStore implements AutoCloseable {
    private static User account;
    public static Bitmap registrationPhoto;

    public AccountStore(Context context) { }

    public Task<Void> register(String username, String birthday, String email, String password) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        final String photo;
        try { photo = ProfileImages.encode(registrationPhoto); }
        catch (Exception e) { return Tasks.forException(e); }
        return auth.createUserWithEmailAndPassword(email, password).continueWithTask(result -> {
            if (!result.isSuccessful()) throw result.getException();
            FirebaseUser created = result.getResult().getUser();
            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("birthday", birthday);
            data.put("email", email);
            data.put("photo", photo);
            return FirebaseFirestore.getInstance().collection("users").document(created.getUid()).set(data)
                    .continueWithTask(saved -> {
                        if (saved.isSuccessful()) {
                            User loaded = new User();
                            loaded.id = created.getUid();
                            loaded.username = username;
                            loaded.birthday = birthday;
                            loaded.email = email;
                            loaded.photo = registrationPhoto;
                            if (loaded.id.equals(uid())) account = loaded;
                            registrationPhoto = null;
                            return Tasks.<Void>forResult(null);
                        }
                        Exception failure = saved.getException();
                        return created.delete().continueWithTask(deleted -> {
                            auth.signOut();
                            clear();
                            return Tasks.<Void>forException(new Exception(deleted.isSuccessful()
                                    ? "Profile could not be saved. Please register again."
                                    : "Profile could not be saved and the incomplete account could not be removed. Check Firebase before trying again.", failure));
                        });
                    });
        });
    }

    public Task<Void> login(String email, String password) {
        return FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .continueWithTask(result -> {
                    if (!result.isSuccessful()) throw result.getException();
                    return loadProfile();
                });
    }

    public static Task<Void> loadProfile() {
        String uid = uid();
        if (uid == null) return Tasks.forException(new Exception("Please sign in again."));
        return FirebaseFirestore.getInstance().collection("users").document(uid).get(Source.SERVER)
                .continueWith(result -> {
                    if (!result.isSuccessful()) throw result.getException();
                    if (!uid.equals(uid())) throw new Exception("Your session has changed. Please sign in again.");
                    DocumentSnapshot document = result.getResult();
                    if (!document.exists()) throw new Exception("Your account profile is missing. Please complete registration in Firebase before signing in.");
                    User loaded = new User();
                    loaded.id = uid;
                    loaded.username = document.getString("username");
                    if (loaded.username == null || loaded.username.trim().isEmpty()) throw new Exception("Your account profile has no username.");
                    loaded.birthday = document.getString("birthday");
                    loaded.email = document.getString("email");
                    if (loaded.birthday == null) loaded.birthday = "";
                    if (loaded.email == null) loaded.email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                    loaded.photo = ProfileImages.decode(document.getString("photo"));
                    account = loaded;
                    return null;
                });
    }

    public static String uid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public static String currentId(Context context) { return uid(); }
    public static void logout(Context context) {
        FirebaseAuth.getInstance().signOut();
        clear();
    }
    private static void clear() {
        account = null;
        registrationPhoto = null;
        AuctionStore.reset();
        BinderStore.reset();
    }
    public User current() { return account != null && account.id.equals(uid()) ? account : null; }

    public Task<Void> savePhoto(String id, Bitmap photo) {
        if (!id.equals(uid())) return Tasks.forException(new Exception("Please sign in again."));
        final String encoded;
        try { encoded = ProfileImages.encode(photo); }
        catch (Exception e) { return Tasks.forException(e); }
        return FirebaseFirestore.getInstance().collection("users").document(id).update("photo", encoded)
                .continueWith(result -> {
                    if (!result.isSuccessful()) throw result.getException();
                    if (account != null && id.equals(uid()) && id.equals(account.id)) account.photo = photo;
                    return null;
                });
    }

    public static String username(Context context) {
        return account == null || !account.id.equals(uid()) ? "Collector" : account.username;
    }
    @Override public void close() { }

    public static class User {
        public String id, username, birthday, email;
        public Bitmap photo;
    }
}
