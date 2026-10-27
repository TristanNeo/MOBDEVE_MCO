package com.example.mobdeve_deckbid;

import java.util.ArrayList;

public class AuctionItem {
    public String id, name, game, condition, seller;
    public int image;
    public long startingBid, currentBid, increment, startsAt, endsAt, autoBidMax;
    public boolean saved;
    public final ArrayList<Bid> history = new ArrayList<>();

    public static class Bid {
        public String bidder;
        public long amount, time;

        public Bid(String bidder, long amount, long time) {
            this.bidder = bidder;
            this.amount = amount;
            this.time = time;
        }
    }

    public boolean isLeading() {
        return !history.isEmpty() && history.get(0).bidder.equals("You");
    }

    public boolean hasBid() {
        for (Bid bid : history) if (bid.bidder.equals("You")) return true;
        return false;
    }

    public boolean isActive(long now) {
        return now >= startsAt && now < endsAt;
    }

    public long minimumBid() {
        return history.isEmpty() ? startingBid : currentBid + increment;
    }

    public void placeBid(long amount, String bidder, long now) {
        if (!isActive(now)) throw new IllegalArgumentException("This auction has ended or has not started.");
        if (amount < minimumBid()) throw new IllegalArgumentException("Your bid is below the minimum.");
        if (amount > 100000000L) throw new IllegalArgumentException("Enter a bid up to ₱100,000,000.");
        if (bidder.equals("You") && amount > autoBidMax) autoBidMax = 0;
        currentBid = amount;
        history.add(0, new Bid(bidder, amount, now));
        if (!bidder.equals("You") && autoBidMax >= currentBid + increment) {
            currentBid += increment;
            history.add(0, new Bid("You", currentBid, now));
        }
        if (endsAt - now <= 300000L) endsAt += 300000L;
    }

    public void setAutoBid(long maximum, long now) {
        if (!isActive(now)) throw new IllegalArgumentException("This auction is no longer active.");
        long minimum = isLeading() ? currentBid : minimumBid();
        if (maximum < minimum || maximum > 100000000L) throw new IllegalArgumentException("Choose a maximum between the minimum bid and ₱100,000,000.");
        autoBidMax = maximum;
        if (!isLeading()) placeBid(minimumBid(), "You", now);
    }
}
