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
    public static void reset() { addedCards.clear(); }
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
        ArrayList<Card> cards = new ArrayList<>();
        String[] sets = {"Scarlet & Violet · 076/165", "OP02-013 · Alternate Art", "LOB-119 · Spell Card", "Mirrodin · Artifact"};
        double[] averages = {1250, 8500, 2300, 650};
        double[] changes = {8.4, 5.2, -3.1, 2.6};
        for (int i = 0; i < 4; i++) {
            Card c = new Card("demo_" + i, ARTWORK[i], sets[i], GAMES[i], "Ungraded", i);
            c.demo = true;
            c.average = averages[i];
            c.change = changes[i];
            cards.add(c);
        }
        cards.addAll(addedCards);
        return cards;
    }

    public static boolean add(Context context, Card card) {
        addedCards.add(card);
        return true;
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
