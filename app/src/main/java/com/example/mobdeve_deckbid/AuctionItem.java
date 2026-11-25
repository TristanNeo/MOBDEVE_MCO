package com.example.mobdeve_deckbid;

import java.util.ArrayList;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuctionItem {
    public String id, name, game, condition, seller, ownerUid, binderId, set, photo, catalogId, notes;
    public long startingBid, currentBid, increment, startsAt, endsAt, autoBidMax;
    public boolean saved;
    public final ArrayList<Bid> history = new ArrayList<>();

    public static class Bid {
        public String bidder, uid;
        public long amount, time;
        public Bid(String bidder, long amount, long time) {
            this.bidder = bidder;
            this.uid = "";
            this.amount = amount;
            this.time = time;
        }
    }

    public boolean isOwned() { return ownerUid != null && ownerUid.equals(AccountStore.uid()); }
    public boolean isLeading() { return !history.isEmpty() && isMine(history.get(0)); }
    public boolean hasBid() {
        for (Bid bid : history) if (isMine(bid)) return true;
        return false;
    }
    public boolean isPending(long now) { return now < startsAt; }
    public boolean isActive(long now) { return now >= startsAt && now < endsAt; }
    public long minimumBid() { return history.isEmpty() ? startingBid : currentBid + increment; }
    public static boolean isMine(Bid bid) {
        String uid = AccountStore.uid();
        return uid != null && uid.equals(bid.uid);
    }
    public static String cardKey(String game, String name, String set, String condition) {
        String text = game.trim().toLowerCase(Locale.ROOT) + "\u001f" + name.trim().toLowerCase(Locale.ROOT)
                + "\u001f" + set.trim().toLowerCase(Locale.ROOT) + "\u001f" + condition.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
