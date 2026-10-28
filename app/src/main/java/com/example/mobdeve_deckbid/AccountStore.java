package com.example.mobdeve_deckbid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.Locale;

public class AccountStore extends SQLiteOpenHelper {
    private final Context context;

    public AccountStore(Context context) {
        super(context, "deckbid_accounts.db", null, 1);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL COLLATE NOCASE UNIQUE, birthday TEXT NOT NULL, email TEXT NOT NULL UNIQUE, salt TEXT NOT NULL, password_hash TEXT NOT NULL, photo TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public long register(String username, String birthday, String email, String password) throws Exception {
        String salt = Passwords.salt();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("birthday", birthday);
        values.put("email", email.toLowerCase(Locale.ROOT));
        values.put("salt", salt);
        values.put("password_hash", Passwords.hash(password, salt));
        return getWritableDatabase().insertOrThrow("accounts", null, values);
    }

    public long login(String email, String password) throws Exception {
        try (Cursor cursor = getReadableDatabase().query("accounts", null, "email = ?", new String[]{email.toLowerCase(Locale.ROOT)}, null, null, null)) {
            if (!cursor.moveToFirst()) return -1;
            String actual = Passwords.hash(password, cursor.getString(cursor.getColumnIndexOrThrow("salt")));
            String expected = cursor.getString(cursor.getColumnIndexOrThrow("password_hash"));
            return java.security.MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8), expected.getBytes(java.nio.charset.StandardCharsets.UTF_8)) ? cursor.getLong(0) : -1;
        }
    }

    public void startSession(long id) {
        context.getSharedPreferences("deckbid_session", Context.MODE_PRIVATE).edit().putLong("account_id", id).commit();
    }

    public static long currentId(Context context) {
        return context.getSharedPreferences("deckbid_session", Context.MODE_PRIVATE).getLong("account_id", -1);
    }

    public static void logout(Context context) {
        context.getSharedPreferences("deckbid_session", Context.MODE_PRIVATE).edit().remove("account_id").commit();
    }

    public User current() {
        try (Cursor cursor = getReadableDatabase().query("accounts", null, "id = ?", new String[]{String.valueOf(currentId(context))}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            User user = new User();
            user.id = cursor.getLong(0);
            user.username = cursor.getString(cursor.getColumnIndexOrThrow("username"));
            user.birthday = cursor.getString(cursor.getColumnIndexOrThrow("birthday"));
            user.email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
            user.photo = cursor.getString(cursor.getColumnIndexOrThrow("photo"));
            return user;
        }
    }

    public boolean savePhoto(long id, String path) {
        ContentValues values = new ContentValues();
        values.put("photo", path);
        return getWritableDatabase().update("accounts", values, "id = ?", new String[]{String.valueOf(id)}) == 1;
    }

    public static String preferencesName(Context context, String base) {
        long id = currentId(context);
        return id <= 1 ? base : base + "_account_" + id;
    }

    public static String username(Context context) {
        try (AccountStore store = new AccountStore(context)) {
            User user = store.current();
            return user == null ? "Collector" : user.username;
        }
    }

    public static class User {
        public long id;
        public String username, birthday, email, photo;
    }
}
