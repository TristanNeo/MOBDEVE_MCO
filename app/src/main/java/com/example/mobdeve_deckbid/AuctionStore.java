package com.example.mobdeve_deckbid;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

public class AuctionStore {
    public static final String[] CATEGORIES = {"All games", "Pokémon", "One Piece", "Yu-Gi-Oh!", "Magic: The Gathering"};
    public static final String[] CONDITIONS = {"All conditions", "Ungraded", "PSA 9", "PSA 10"};
    public static final String[] PRICES = {"Any price", "Under ₱1,000", "₱1,000–₱5,000", "Over ₱5,000"};
    public static final String[] SORTS = {"Ending soon", "Lowest bid", "Highest bid"};

    public static ArrayList<AuctionItem> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("deckbid_auctions", Context.MODE_PRIVATE);
        long epoch = prefs.getLong("demo_started", 0);
        if (epoch == 0) {
            epoch = System.currentTimeMillis();
            prefs.edit().putLong("demo_started", epoch).apply();
        }
        String[] names = {"Mega Greninja ex", "Charizard ex", "Blaziken", "Furret", "Giratina V", "Portgas D. Ace", "Monkey D. Luffy", "Sabo", "Izo", "Nami", "Dragon Master Magia", "Chaos Emperor Dragon", "Legendary Fire King Ponix", "Red-Eyes Darkness Metal Dragon", "Honest", "Thornscape Battlemage", "Clive, Ifrit's Dominant", "Endurance", "Firion, Wild Rose Warrior", "Bofur, Reliable Guardian"};
        int[] images = {R.drawable.pokemon1, R.drawable.pokemon2, R.drawable.pokemon3, R.drawable.pokemon4, R.drawable.pokemon5, R.drawable.onepiece1, R.drawable.onepiece2, R.drawable.onepiece3, R.drawable.onepiece4, R.drawable.onepiece5, R.drawable.yugioh1, R.drawable.yugioh2, R.drawable.yugioh3, R.drawable.yugioh4, R.drawable.yugioh5, R.drawable.mtg1, R.drawable.mtg2, R.drawable.mtg3, R.drawable.mtg4, R.drawable.mtg5};
        long[] values = {1500, 6800, 900, 600, 12500, 8500, 4200, 2100, 750, 1100, 18000, 3200, 650, 1200, 800, 500, 2400, 1800, 950, 550};
        String[] sellers = {"CardCorner", "DeckCollector", "ManilaTCG", "CardShelf"};
        String[] bidders = {"CardHunter", "DeckFan", "CollectorPH", "CardHunter", "TCGPlayer"};
        ArrayList<AuctionItem> items = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            AuctionItem a = new AuctionItem();
            a.id = "auction_" + i;
            a.name = names[i];
            a.image = images[i];
            a.game = CATEGORIES[i / 5 + 1];
            a.condition = CONDITIONS[i % 3 + 1];
            a.seller = sellers[i % sellers.length];
            a.increment = values[i] >= 5000 ? 100 : 50;
            a.currentBid = values[i];
            a.startingBid = values[i] - a.increment * 5;
            a.startsAt = epoch - 86400000L;
            a.endsAt = epoch + (2L + i * 3L) * 3600000L + (i % 3) * 840000L;
            for (int j = 0; j < 5; j++) a.history.add(new AuctionItem.Bid(bidders[j], a.currentBid - j * a.increment, epoch - (j + 1) * 900000L));
            String stored = prefs.getString(a.id, "");
            if (!stored.isEmpty()) {
                try {
                    JSONObject o = new JSONObject(stored);
                    a.currentBid = o.getLong("current");
                    a.endsAt = o.getLong("end");
                    a.autoBidMax = o.optLong("auto", 0);
                    a.saved = o.optBoolean("saved", false);
                    a.history.clear();
                    JSONArray history = o.getJSONArray("history");
                    for (int j = 0; j < history.length(); j++) {
                        JSONObject b = history.getJSONObject(j);
                        a.history.add(new AuctionItem.Bid(b.getString("bidder"), b.getLong("amount"), b.getLong("time")));
                    }
                } catch (JSONException e) {
                    android.util.Log.e("AuctionStore", "Unable to read auction", e);
                }
            }
            items.add(a);
        }
        return items;
    }

    public static AuctionItem find(Context context, String id) {
        for (AuctionItem a : load(context)) if (a.id.equals(id)) return a;
        return null;
    }

    public static boolean save(Context context, AuctionItem a) {
        try {
            JSONObject o = new JSONObject();
            o.put("current", a.currentBid);
            o.put("end", a.endsAt);
            o.put("auto", a.autoBidMax);
            o.put("saved", a.saved);
            JSONArray history = new JSONArray();
            for (AuctionItem.Bid bid : a.history) {
                JSONObject b = new JSONObject();
                b.put("bidder", bid.bidder);
                b.put("amount", bid.amount);
                b.put("time", bid.time);
                history.put(b);
            }
            o.put("history", history);
            return context.getSharedPreferences("deckbid_auctions", Context.MODE_PRIVATE).edit().putString(a.id, o.toString()).commit();
        } catch (JSONException e) {
            return false;
        }
    }

    public static void image(Context context, android.widget.ImageView view, AuctionItem a) {
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(context.getResources(), a.image, options);
        options.inSampleSize = 1;
        while (options.outHeight / options.inSampleSize > 600 || options.outWidth / options.inSampleSize > 450) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        view.setImageBitmap(android.graphics.BitmapFactory.decodeResource(context.getResources(), a.image, options));
        view.setContentDescription(a.name);
    }

    public static String money(long value) {
        return String.format(Locale.US, "₱%,d", value);
    }

    public static String timeLeft(AuctionItem a) {
        long remaining = a.endsAt - System.currentTimeMillis();
        if (remaining <= 0) return "Ended";
        long seconds = (remaining + 999) / 1000;
        if (seconds < 60) return seconds + "s left";
        long minutes = seconds / 60;
        if (minutes >= 1440) return minutes / 1440 + "d " + minutes % 1440 / 60 + "h left";
        if (minutes >= 60) return minutes / 60 + "h " + minutes % 60 + "m left";
        return minutes + "m left";
    }
}
