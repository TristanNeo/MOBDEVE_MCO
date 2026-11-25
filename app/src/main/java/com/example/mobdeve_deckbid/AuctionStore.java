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
    public static final long MAX_DURATION = 7L * 24 * 60 * 60 * 1000;
    public static void reset() {
        auctions.clear(); preferences.clear(); bidding.clear(); attempted.clear(); owner = null;
    }
    private static void checkOwner() {
        if (!java.util.Objects.equals(owner, AccountStore.uid())) { reset(); owner = AccountStore.uid(); }
    }
    public static final String[] CATEGORIES = {"All games", "Pokémon", "One Piece", "Yu-Gi-Oh!", "Magic: The Gathering"};
    public static final String[] CONDITIONS = {"All conditions", "Ungraded", "PSA 9", "PSA 10"};
    public static final String[] PRICES = {"Any price", "Under ₱1,000", "₱1,000–₱5,000", "Over ₱5,000"};
    public static final String[] SORTS = {"Ending soon", "Lowest bid", "Highest bid"};

    public static ArrayList<AuctionItem> load(Context context) {
        checkOwner();
        ArrayList<AuctionItem> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (AuctionItem a : auctions) if (a.isOwned() || !a.isPending(now)) result.add(a);
        return result;
    }
    public static AuctionItem find(Context context, String id) {
        for (AuctionItem a : load(context)) if (a.id.equals(id)) return a;
        return null;
    }
    public static ArrayList<AuctionItem> matching(Context context, BinderStore.Card card, boolean completed) {
        ArrayList<AuctionItem> result = new ArrayList<>();
        String key = AuctionItem.cardKey(card.game, card.name, card.set, card.condition);
        long now = System.currentTimeMillis();
        for (AuctionItem a : load(context)) {
            if (!key.equals(AuctionItem.cardKey(a.game, a.name, a.set, a.condition))) continue;
            if (completed ? a.endsAt <= now && !a.history.isEmpty() : a.isActive(now)) result.add(a);
        }
        result.sort(completed ? (a, b) -> Long.compare(b.currentBid, a.currentBid)
                : (a, b) -> Long.compare(a.endsAt, b.endsAt));
        return result;
    }
    public static ArrayList<AuctionItem> catalog(Context context, String game, String condition) {
        Map<String, AuctionItem> unique = new java.util.LinkedHashMap<>();
        for (AuctionItem a : load(context)) {
            if (a.isPending(System.currentTimeMillis()) || !a.game.equals(game) || !a.condition.equals(condition)) continue;
            String key = AuctionItem.cardKey(a.game, a.name, a.set, a.condition);
            unique.put(key, a);
        }
        ArrayList<AuctionItem> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }
    public static String average(Context context, BinderStore.Card card) {
        ArrayList<AuctionItem> completed = matching(context, card, true);
        if (completed.isEmpty()) return "No sales yet";
        double total = 0;
        for (AuctionItem a : completed) total += a.currentBid;
        return BinderStore.money(total / completed.size());
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
            if (snapshot != null && !snapshot.getMetadata().isFromCache() && !snapshot.getMetadata().hasPendingWrites()) runAutomaticBids(activity);
        });
        ListenerRegistration personal = db.collection("users").document(uid).collection("auctionSettings")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!uid.equals(AccountStore.uid()) || activity.isFinishing() || activity.isDestroyed()) return;
                    if (error != null) { showError(activity, error); return; }
                    preferences.clear();
                    if (snapshot != null) for (DocumentSnapshot doc : snapshot.getDocuments()) preferences.put(doc.getId(), doc.getData());
                    for (AuctionItem a : auctions) applyPreferences(a);
                    changed.run();
                    if (snapshot != null && !snapshot.getMetadata().isFromCache() && !snapshot.getMetadata().hasPendingWrites()) runAutomaticBids(activity);
                });
        return () -> { shared.remove(); personal.remove(); };
    }

    public static Task<Void> create(Context context, String cardId, long starting, long increment, long start, long duration, String notes) {
        String uid = AccountStore.uid();
        if (uid == null) return Tasks.forException(new Exception("Please sign in again."));
        if (starting < 1 || starting > 100000000 || increment < 1 || increment > 100000000)
            return Tasks.forException(new Exception("Use whole-peso bids and increments between ₱1 and ₱100,000,000."));
        if (duration < 3600000L || duration > MAX_DURATION)
            return Tasks.forException(new Exception("Duration must be from 1 hour to 7 days."));
        if (start < System.currentTimeMillis() - 60000L)
            return Tasks.forException(new Exception("Choose a start time in the future or Start immediately."));
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference binderRef = db.collection("users").document(uid).collection("binder").document(cardId);
        DocumentReference ref = db.collection("auctions").document("listing_" + java.util.UUID.randomUUID());
        return db.runTransaction(transaction -> {
            DocumentSnapshot doc = transaction.get(binderRef);
            DocumentSnapshot profile = transaction.get(db.collection("users").document(uid));
            if (!uid.equals(AccountStore.uid()) || !doc.exists() || Boolean.TRUE.equals(doc.getBoolean("demo")))
                throw new IllegalArgumentException("This card is not available in your Binder.");
            BinderStore.Card card = BinderStore.read(doc);
            if (!card.auctionId.isEmpty()) {
                AuctionItem previous = read(context, transaction.get(db.collection("auctions").document(card.auctionId)));
                if (previous != null && (previous.endsAt > System.currentTimeMillis() || !previous.history.isEmpty()))
                    throw new IllegalArgumentException("This card is already scheduled, being auctioned, or sold.");
            }
            if (start < System.currentTimeMillis() - 60000L) throw new IllegalArgumentException("The selected start time has passed.");
            Map<String, Object> data = new HashMap<>();
            data.put("version", 2);
            data.put("ownerUid", uid);
            data.put("binderId", card.id);
            data.put("catalogId", card.catalogId);
            data.put("name", card.name);
            data.put("set", card.set);
            data.put("game", card.game);
            data.put("condition", card.condition);
            data.put("photo", card.uri);
            data.put("seller", text(profile, "username"));
            data.put("notes", notes.trim());
            data.put("starting", starting);
            data.put("current", starting);
            data.put("increment", increment);
            data.put("start", start);
            data.put("end", Math.addExact(start, duration));
            data.put("history", new ArrayList<>());
            transaction.set(ref, data);
            transaction.update(binderRef, "auctionId", ref.getId());
            return (Void) null;
        });
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
            if (uid.equals(latest.ownerUid)) throw new IllegalArgumentException("You cannot bid on your own auction.");
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
                Map<String, Object> update = new HashMap<>();
                update.put("current", latest.currentBid);
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
            if (a.isOwned() || !a.isActive(System.currentTimeMillis()) || a.isLeading() || a.autoBidMax < a.minimumBid()) continue;
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
        if (!doc.exists() || number(doc.get("version")) != 2 || text(doc, "ownerUid").isEmpty()) return null;
        AuctionItem a = new AuctionItem();
        a.id = doc.getId();
        a.name = text(doc, "name"); a.set = text(doc, "set");
        a.game = text(doc, "game"); a.condition = text(doc, "condition");
        a.seller = text(doc, "seller"); a.ownerUid = text(doc, "ownerUid");
        a.binderId = text(doc, "binderId"); a.photo = text(doc, "photo");
        a.catalogId = text(doc, "catalogId"); a.notes = text(doc, "notes");
        a.startingBid = number(doc.get("starting")); a.increment = number(doc.get("increment"));
        a.currentBid = number(doc.get("current")); a.startsAt = number(doc.get("start")); a.endsAt = number(doc.get("end"));
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
            value.put("bidder", bid.bidder); value.put("uid", bid.uid);
            value.put("amount", bid.amount); value.put("time", bid.time);
            result.add(value);
        }
        return result;
    }
    public static void image(Context context, android.widget.ImageView view, AuctionItem a) {
        BinderStore.Card card = new BinderStore.Card(a.binderId, a.name, a.set, a.game, a.condition);
        card.uri = a.photo;
        BinderStore.image(context, view, card);
        if (a.isPending(System.currentTimeMillis())) {
            android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
            matrix.setSaturation(0);
            view.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
        } else view.clearColorFilter();
    }
    public static String money(long value) { return String.format(Locale.US, "₱%,d", value); }
    public static String timeLeft(AuctionItem a) {
        long now = System.currentTimeMillis();
        boolean pending = a.isPending(now);
        long remaining = (pending ? a.startsAt : a.endsAt) - now;
        if (remaining <= 0) return "Ended";
        long seconds = (remaining + 999) / 1000;
        String label;
        if (seconds < 60) label = seconds + "s";
        else if (seconds < 3600) label = seconds / 60 + "m";
        else if (seconds < 86400) label = seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
        else label = seconds / 86400 + "d " + seconds % 86400 / 3600 + "h";
        return pending ? "Opens in " + label : label + " left";
    }
}
