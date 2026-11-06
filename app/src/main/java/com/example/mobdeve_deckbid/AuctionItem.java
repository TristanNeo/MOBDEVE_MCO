package com.example.mobdeve_deckbid;

import java.util.ArrayList;

public class AuctionItem {
    public String id, name, game, condition, seller;
    public int image;
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

    public boolean isLeading() {
        return !history.isEmpty() && isMine(history.get(0));
    }

    public boolean hasBid() {
        for (Bid bid : history) if (isMine(bid)) return true;
        return false;
    }

    public boolean isActive(long now) {
        return now >= startsAt && now < endsAt;
    }

    public long minimumBid() {
        return history.isEmpty() ? startingBid : currentBid + increment;
    }

    public static boolean isMine(Bid bid) {
        String uid = AccountStore.uid();
        return uid != null && uid.equals(bid.uid);
    }
}
