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
                        if (Boolean.TRUE.equals(doc.getBoolean("demo"))) continue;
                        addedCards.add(read(doc));
                    }
                    changed.run();
                });
    }

    public static Card read(com.google.firebase.firestore.DocumentSnapshot doc) {
        Card card = new Card(doc.getId(), text(doc, "name"), text(doc, "set"), text(doc, "game"), text(doc, "condition"));
        card.uri = text(doc, "photo");
        card.sourceAuctionId = text(doc, "sourceAuctionId");
        card.catalogId = AuctionItem.cardKey(card.game, card.name, card.set, card.condition);
        card.auctionId = text(doc, "auctionId");
        return card;
    }

    private static String text(com.google.firebase.firestore.DocumentSnapshot doc, String key) {
        String value = doc.getString(key);
        return value == null ? "" : value;
    }
    public static final String[] CONDITIONS = {"Ungraded", "PSA 9", "PSA 10"};
    public static final String[] GAMES = {"Pokémon", "One Piece", "Yu-Gi-Oh!", "Magic: The Gathering"};

    public static class Card {
        public String id, name, set, game, condition, uri;
        public String catalogId = "", sourceAuctionId = "", auctionId = "";

        public Card(String id, String name, String set, String game, String condition) {
            this.id = id;
            this.name = name;
            this.set = set;
            this.game = game;
            this.condition = condition;
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
            photo = card.uri.startsWith("content:") || card.uri.startsWith("file:")
                    ? ProfileImages.encode(ProfileImages.load(context, Uri.parse(card.uri))) : card.uri;
        } catch (Exception e) { return com.google.android.gms.tasks.Tasks.forException(e); }
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", card.name);
        data.put("set", card.set);
        data.put("game", card.game);
        data.put("condition", card.condition);
        data.put("photo", photo);
        data.put("catalogId", AuctionItem.cardKey(card.game, card.name, card.set, card.condition));
        data.put("sourceAuctionId", card.sourceAuctionId);
        data.put("auctionId", "");
        return com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("binder").document(card.id).set(data);
    }

    public static String money(double amount) {
        return String.format(Locale.US, "₱%,.0f", amount);
    }

    public static String status(Context context, Card card) {
        AuctionItem listing = AuctionStore.find(context, card.auctionId);
        if (listing == null) return "";
        long now = System.currentTimeMillis();
        if (listing.isPending(now)) return "Scheduled · " + AuctionStore.timeLeft(listing);
        if (listing.isActive(now)) return "Being auctioned · " + AuctionStore.timeLeft(listing);
        return listing.history.isEmpty() ? "Auction ended · no bids" : "Sold";
    }

    public static void image(Context context, ImageView view, Card card) {
        view.setImageResource(R.drawable.deckbid_card_placeholder);
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
