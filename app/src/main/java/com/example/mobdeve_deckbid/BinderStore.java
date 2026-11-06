package com.example.mobdeve_deckbid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

public class BinderStore {
    private static final ArrayList<Card> addedCards = new ArrayList<>();
    private static String owner;
    public static void reset() { addedCards.clear(); owner = null; }

    private static void checkOwner() {
        String uid = AccountStore.uid();
        if (!java.util.Objects.equals(owner, uid)) {
            addedCards.clear();
            owner = uid;
        }
    }

    public static com.google.firebase.firestore.ListenerRegistration listen(android.app.Activity activity, Runnable changed) {
        checkOwner();
        String uid = owner;
        if (uid == null) return () -> { };
        return com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("binder").addSnapshotListener((snapshot, error) -> {
                    if (!uid.equals(AccountStore.uid()) || activity.isFinishing() || activity.isDestroyed()) return;
                    if (error != null) {
                        android.widget.Toast.makeText(activity, "Could not load Binder: " + error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    checkOwner();
                    addedCards.clear();
                    if (snapshot != null) for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        addedCards.add(read(doc));
                    }
                    changed.run();
                });
    }

    public static Card read(com.google.firebase.firestore.DocumentSnapshot doc) {
        Card card = new Card(doc.getId(), text(doc, "name"), text(doc, "set"), text(doc, "game"), text(doc, "condition"),
                doc.getLong("artwork") == null ? 0 : doc.getLong("artwork").intValue());
        card.uri = text(doc, "photo");
        card.demo = Boolean.TRUE.equals(doc.getBoolean("demo"));
        card.average = doc.getDouble("average") == null ? 0 : doc.getDouble("average");
        card.change = doc.getDouble("change") == null ? 0 : doc.getDouble("change");
        return card;
    }

    public static com.google.android.gms.tasks.Task<Void> initialize() {
        String uid = AccountStore.uid();
        if (uid == null) return com.google.android.gms.tasks.Tasks.forException(new Exception("Please sign in again."));
        com.google.firebase.firestore.DocumentReference user = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid);
        return com.google.firebase.firestore.FirebaseFirestore.getInstance().runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot profile = transaction.get(user);
            if (Boolean.TRUE.equals(profile.getBoolean("binderInitialized"))) return (Void) null;
            String[] sets = {"Scarlet & Violet · 076/165", "OP02-013 · Alternate Art", "LOB-119 · Spell Card", "Mirrodin · Artifact"};
            double[] averages = {1250, 8500, 2300, 650};
            double[] changes = {8.4, 5.2, -3.1, 2.6};
            java.util.ArrayList<com.google.firebase.firestore.DocumentSnapshot> existing = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) existing.add(transaction.get(user.collection("binder").document("demo_" + i)));
            for (int i = 0; i < 4; i++) {
                if (existing.get(i).exists()) continue;
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("name", ARTWORK[i]);
                data.put("set", sets[i]);
                data.put("game", GAMES[i]);
                data.put("condition", "Ungraded");
                data.put("artwork", i);
                data.put("photo", "");
                data.put("demo", true);
                data.put("average", averages[i]);
                data.put("change", changes[i]);
                transaction.set(existing.get(i).getReference(), data);
            }
            transaction.update(user, "binderInitialized", true);
            return (Void) null;
        });
    }

    private static String text(com.google.firebase.firestore.DocumentSnapshot doc, String key) {
        String value = doc.getString(key);
        return value == null ? "" : value;
    }
    public static final String[] CONDITIONS = {"Ungraded", "PSA 9", "PSA 10"};
    public static final String[] GAMES = {"Pokémon", "One Piece", "Yu-Gi-Oh!", "Magic: The Gathering"};
    public static final String[] ARTWORK = {"Snorlax ex", "Portgas D. Ace", "Pot of Greed", "Timesifter"};
    public static final int[] IMAGES = {R.drawable.card_pokemon, R.drawable.card_one_piece, R.drawable.card_yugioh, R.drawable.card_mtg};

    public static class Card {
        public String id, name, set, game, condition, uri;
        public int artwork;
        public boolean demo;
        public double average, change;

        public Card(String id, String name, String set, String game, String condition, int artwork) {
            this.id = id;
            this.name = name;
            this.set = set;
            this.game = game;
            this.condition = condition;
            this.artwork = artwork;
            uri = "";
        }
    }

    public static ArrayList<Card> load(Context context) {
        checkOwner();
        return new ArrayList<>(addedCards);
    }

    public static com.google.android.gms.tasks.Task<Void> add(Context context, Card card) {
        String uid = AccountStore.uid();
        if (uid == null) return com.google.android.gms.tasks.Tasks.forException(new Exception("Please sign in again."));
        final String photo;
        try {
            photo = card.uri.isEmpty() ? "" : ProfileImages.encode(ProfileImages.load(context, Uri.parse(card.uri)));
        } catch (Exception e) { return com.google.android.gms.tasks.Tasks.forException(e); }
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", card.name);
        data.put("set", card.set);
        data.put("game", card.game);
        data.put("condition", card.condition);
        data.put("artwork", card.artwork);
        data.put("photo", photo);
        return com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("binder").document(card.id).set(data);
    }

    public static String money(double amount) {
        return String.format(Locale.US, "₱%,.0f", amount);
    }

    public static String change(Card c) {
        return c.demo ? String.format(Locale.US, "%s %.1f%%", c.change >= 0 ? "▲" : "▼", Math.abs(c.change)) : "—";
    }

    public static int changeColor(Card c) {
        return android.graphics.Color.parseColor(!c.demo ? "#95A1B3" : c.change >= 0 ? "#4CD6A0" : "#F28B82");
    }

    public static void image(Context context, ImageView view, Card card) {
        view.setImageResource(IMAGES[Math.max(0, Math.min(3, card.artwork))]);
        view.setContentDescription(card.name);
        if (card.uri.isEmpty()) return;
        if (!card.uri.startsWith("content:") && !card.uri.startsWith("file:")) {
            Bitmap bitmap = ProfileImages.decode(card.uri);
            if (bitmap != null) view.setImageBitmap(bitmap);
            return;
        }
        try {
            Uri uri = Uri.parse(card.uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, options);
            }
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 800 || options.outHeight / options.inSampleSize > 1000) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
                if (bitmap != null) view.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            android.util.Log.w("BinderStore", "Card photo unavailable", e);
        }
    }
}
