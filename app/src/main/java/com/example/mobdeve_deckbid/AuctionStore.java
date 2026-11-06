package com.example.mobdeve_deckbid;

import android.app.Activity;
import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class AuctionStore {
    private static final ArrayList<AuctionItem> auctions = new ArrayList<>();
    private static final Map<String, Map<String, Object>> preferences = new HashMap<>();
    private static final HashSet<String> bidding = new HashSet<>();
    private static final Map<String, String> attempted = new HashMap<>();
    private static String owner;
    public static void reset() {
        auctions.clear(); preferences.clear(); bidding.clear(); attempted.clear(); owner = null;
    }
    private static void checkOwner() {
        if (!java.util.Objects.equals(owner, AccountStore.uid())) {
            reset();
            owner = AccountStore.uid();
        }
    }
    public static final String[] CATEGORIES = {"All games", "Pokémon", "One Piece", "Yu-Gi-Oh!", "Magic: The Gathering"};
    public static final String[] CONDITIONS = {"All conditions", "Ungraded", "PSA 9", "PSA 10"};
    public static final String[] PRICES = {"Any price", "Under ₱1,000", "₱1,000–₱5,000", "Over ₱5,000"};
    public static final String[] SORTS = {"Ending soon", "Lowest bid", "Highest bid"};

    public static ArrayList<AuctionItem> load(Context context) {
        checkOwner();
        return new ArrayList<>(auctions);
    }

    public static AuctionItem find(Context context, String id) {
        for (AuctionItem a : load(context)) if (a.id.equals(id)) return a;
        return null;
    }

    public static Task<Void> initialize(Context context) {
        ArrayList<AuctionItem> samples = samples();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return db.runTransaction(transaction -> {
            ArrayList<DocumentSnapshot> docs = new ArrayList<>();
            for (AuctionItem a : samples) docs.add(transaction.get(db.collection("auctions").document(a.id)));
            for (int i = 0; i < samples.size(); i++) {
                if (!docs.get(i).exists()) transaction.set(docs.get(i).getReference(), data(context, samples.get(i)));
            }
            return (Void) null;
        });
    }

    public static ListenerRegistration listen(Activity activity, Runnable changed) {
        checkOwner();
        final String uid = owner;
        attempted.clear();
        if (uid == null) return () -> { };
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        ListenerRegistration shared = db.collection("auctions").addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (!uid.equals(AccountStore.uid()) || activity.isFinishing() || activity.isDestroyed()) return;
            if (error != null) { showError(activity, error); return; }
            checkOwner();
            auctions.clear();
            if (snapshot != null) for (DocumentSnapshot doc : snapshot.getDocuments()) {
                AuctionItem a = read(activity, doc);
                if (a != null) { applyPreferences(a); auctions.add(a); }
            }
            changed.run();
            if (snapshot != null && !snapshot.getMetadata().isFromCache() && !snapshot.getMetadata().hasPendingWrites())
                runAutomaticBids(activity);
        });
        ListenerRegistration personal = db.collection("users").document(uid).collection("auctionSettings")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!uid.equals(AccountStore.uid()) || activity.isFinishing() || activity.isDestroyed()) return;
                    if (error != null) { showError(activity, error); return; }
                    checkOwner();
                    preferences.clear();
                    if (snapshot != null) for (DocumentSnapshot doc : snapshot.getDocuments()) preferences.put(doc.getId(), doc.getData());
                    for (AuctionItem a : auctions) applyPreferences(a);
                    changed.run();
                    if (snapshot != null && !snapshot.getMetadata().isFromCache() && !snapshot.getMetadata().hasPendingWrites())
                        runAutomaticBids(activity);
                });
        return () -> { shared.remove(); personal.remove(); };
    }

    private static void applyPreferences(AuctionItem a) {
        Map<String, Object> values = preferences.get(a.id);
        a.saved = values != null && Boolean.TRUE.equals(values.get("saved"));
        a.autoBidMax = values == null ? 0 : number(values.get("auto"));
    }

    public static Task<Void> setSaved(String id, boolean saved) {
        String uid = AccountStore.uid();
        if (uid == null) return Tasks.forException(new Exception("Please sign in again."));
        Map<String, Object> data = new HashMap<>();
        data.put("saved", saved);
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection("auctionSettings")
                .document(id).set(data, SetOptions.merge());
    }

    public static Task<Void> clearAutoBid(String id) {
        String uid = AccountStore.uid();
        if (uid == null) return Tasks.forException(new Exception("Please sign in again."));
        Map<String, Object> data = new HashMap<>();
        data.put("auto", 0);
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection("auctionSettings")
                .document(id).set(data, SetOptions.merge());
    }

    public static Task<Void> bid(Context context, String id, long amount, boolean automatic) {
        return transactBid(context, id, amount, automatic, false);
    }

    private static Task<Void> transactBid(Context context, String id, long amount, boolean automatic, boolean respond) {
        String uid = AccountStore.uid();
        if (uid == null) return Tasks.forException(new Exception("Please sign in again."));
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference auctionRef = db.collection("auctions").document(id);
        DocumentReference settingsRef = db.collection("users").document(uid).collection("auctionSettings").document(id);
        return db.runTransaction(transaction -> {
            AuctionItem latest = read(context, transaction.get(auctionRef));
            DocumentSnapshot settings = transaction.get(settingsRef);
            DocumentSnapshot profile = transaction.get(db.collection("users").document(uid));
            String username = profile.getString("username");
            if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("Your account profile is missing. Please sign in again.");
            if (!uid.equals(AccountStore.uid())) throw new IllegalArgumentException("Please sign in again.");
            if (latest == null) throw new IllegalArgumentException("Auction not found.");
            long now = System.currentTimeMillis();
            long maximum = number(settings.get("auto"));
            boolean leading = !latest.history.isEmpty() && uid.equals(latest.history.get(0).uid);
            if (respond && (!latest.isActive(now) || leading || maximum < latest.minimumBid())) return (Void) null;
            if (!latest.isActive(now)) throw new IllegalArgumentException("This auction has ended or has not started.");
            long minimum = automatic && leading ? latest.currentBid : latest.minimumBid();
            long requested = respond ? maximum : amount;
            if (requested < minimum || requested > 100000000L)
                throw new IllegalArgumentException("Minimum: " + money(minimum) + ". Maximum: ₱100,000,000.");
            if (!automatic && leading) throw new IllegalArgumentException("You are already the highest bidder.");
            long placed = automatic ? latest.minimumBid() : requested;
            if (!leading) {
                AuctionItem.Bid newBid = new AuctionItem.Bid(username, placed, now);
                newBid.uid = uid;
                latest.history.add(0, newBid);
                latest.currentBid = placed;
                if (latest.endsAt - now <= 300000L) latest.endsAt += 300000L;
                Map<String, Object> update = new HashMap<>();
                update.put("current", latest.currentBid);
                update.put("end", latest.endsAt);
                update.put("history", history(latest));
                transaction.update(auctionRef, update);
            }
            Map<String, Object> update = new HashMap<>();
            if (automatic && !respond) update.put("auto", requested);
            else if (!automatic && requested > maximum) update.put("auto", 0);
            if (!update.isEmpty()) transaction.set(settingsRef, update, SetOptions.merge());
            return (Void) null;
        });
    }

    private static void runAutomaticBids(Activity activity) {
        for (AuctionItem a : new ArrayList<>(auctions)) {
            if (!a.isActive(System.currentTimeMillis()) || a.isLeading() || a.autoBidMax < a.minimumBid()) continue;
            String version = a.currentBid + ":" + a.history.size() + ":" + a.autoBidMax;
            if (bidding.contains(a.id) || version.equals(attempted.get(a.id))) continue;
            String uid = AccountStore.uid();
            String id = a.id;
            bidding.add(id);
            attempted.put(id, version);
            transactBid(activity, id, 0, true, true).addOnCompleteListener(task -> {
                if (!java.util.Objects.equals(uid, AccountStore.uid())) return;
                bidding.remove(id);
                if (!task.isSuccessful()) showError(activity, task.getException());
                else runAutomaticBids(activity);
            });
        }
    }

    private static void showError(Activity activity, Exception e) {
        if (!activity.isFinishing() && !activity.isDestroyed())
            android.widget.Toast.makeText(activity, "Could not update auctions: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
    }

    private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : 0; }
    private static String text(DocumentSnapshot doc, String field) {
        String value = doc.getString(field);
        return value == null ? "" : value;
    }

    private static AuctionItem read(Context context, DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        AuctionItem a = new AuctionItem();
        a.id = doc.getId();
        a.name = text(doc, "name");
        a.game = text(doc, "game");
        a.condition = text(doc, "condition");
        a.seller = text(doc, "seller");
        a.image = context.getResources().getIdentifier(text(doc, "image"), "drawable", context.getPackageName());
        if (a.image == 0) a.image = R.drawable.card_pokemon;
        a.startingBid = number(doc.get("starting"));
        a.increment = number(doc.get("increment"));
        a.currentBid = number(doc.get("current"));
        a.startsAt = number(doc.get("start"));
        a.endsAt = number(doc.get("end"));
        Object raw = doc.get("history");
        if (raw instanceof List<?>) for (Object entry : (List<?>) raw) {
            if (!(entry instanceof Map<?, ?>)) continue;
            Map<?, ?> bid = (Map<?, ?>) entry;
            AuctionItem.Bid b = new AuctionItem.Bid(String.valueOf(bid.get("bidder")), number(bid.get("amount")), number(bid.get("time")));
            b.uid = bid.get("uid") instanceof String ? (String) bid.get("uid") : "";
            a.history.add(b);
        }
        return a;
    }

    private static List<Map<String, Object>> history(AuctionItem a) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AuctionItem.Bid bid : a.history) {
            Map<String, Object> value = new HashMap<>();
            value.put("bidder", bid.bidder);
            value.put("uid", bid.uid);
            value.put("amount", bid.amount);
            value.put("time", bid.time);
            result.add(value);
        }
        return result;
    }

    private static Map<String, Object> data(Context context, AuctionItem a) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", a.name);
        data.put("game", a.game);
        data.put("condition", a.condition);
        data.put("seller", a.seller);
        data.put("image", context.getResources().getResourceEntryName(a.image));
        data.put("starting", a.startingBid);
        data.put("increment", a.increment);
        data.put("current", a.currentBid);
        data.put("start", a.startsAt);
        data.put("end", a.endsAt);
        data.put("history", history(a));
        return data;
    }

    private static ArrayList<AuctionItem> samples() {
        long seedTime = System.currentTimeMillis();
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
            a.startsAt = seedTime - 86400000L;
            a.endsAt = seedTime + (2L + i * 3L) * 3600000L + (i % 3) * 840000L;
            for (int j = 0; j < 5; j++) a.history.add(new AuctionItem.Bid(bidders[j], a.currentBid - j * a.increment, seedTime - (j + 1) * 900000L));
            items.add(a);
        }
        return items;
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
