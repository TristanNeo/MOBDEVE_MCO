package com.example.mobdeve_deckbid;

import android.content.Context;
import android.graphics.Bitmap;

public class AccountStore implements AutoCloseable {
    private static User account;
    private static String password;
    private static boolean signedIn;
    public static Bitmap registrationPhoto;

    public AccountStore(Context context) { }

    public long register(String username, String birthday, String email, String enteredPassword) {
        account = new User();
        account.id = 1;
        account.username = username;
        account.birthday = birthday;
        account.email = email;
        account.photo = registrationPhoto;
        registrationPhoto = null;
        password = enteredPassword;
        AuctionStore.reset();
        BinderStore.reset();
        return account.id;
    }

    public long login(String email, String enteredPassword) {
        return account != null && account.email.equalsIgnoreCase(email) && password.equals(enteredPassword) ? account.id : -1;
    }

    public void startSession(long id) { signedIn = account != null && account.id == id; }
    public static long currentId(Context context) { return signedIn && account != null ? account.id : -1; }
    public static void logout(Context context) { signedIn = false; }
    public User current() { return signedIn ? account : null; }

    public boolean savePhoto(long id, Bitmap photo) {
        if (account == null || account.id != id) return false;
        account.photo = photo;
        return true;
    }

    public static String username(Context context) { return account == null ? "Collector" : account.username; }
    @Override public void close() { }

    public static class User {
        public long id;
        public String username, birthday, email;
        public Bitmap photo;
    }
}
