package com.example.mobdeve_deckbid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ActiveAuctions {
    static final int PREVIEW_LIMIT = 3;
    private static final Comparator<AuctionItem> BY_BID = Comparator
            .comparingLong((AuctionItem item) -> item.currentBid).reversed()
            .thenComparingLong(item -> item.endsAt)
            .thenComparing(item -> item.id);

    final List<AuctionItem> selling = new ArrayList<>();
    final List<AuctionItem> bidding = new ArrayList<>();
    long nextChange = Long.MAX_VALUE;

    ActiveAuctions(List<AuctionItem> auctions, long now) {
        for (AuctionItem item : auctions) {
            boolean owned = item.isOwned();
            if (owned && item.isPending(now)) nextChange = Math.min(nextChange, item.startsAt);
            if (!item.isActive(now)) continue;
            if (owned) {
                selling.add(item);
            } else if (item.hasBid()) {
                bidding.add(item);
            } else {
                continue;
            }
            nextChange = Math.min(nextChange, item.endsAt);
        }
        selling.sort(BY_BID);
        bidding.sort(BY_BID);
    }

    List<AuctionItem> items(boolean showBidding) {
        return showBidding ? bidding : selling;
    }

    List<AuctionItem> preview(boolean showBidding) {
        List<AuctionItem> items = items(showBidding);
        return items.subList(0, Math.min(PREVIEW_LIMIT, items.size()));
    }

    boolean canExpand() {
        return selling.size() >= PREVIEW_LIMIT || bidding.size() >= PREVIEW_LIMIT;
    }

    static String emptyMessage(boolean showBidding) {
        return showBidding ? "You have no bids on live auctions." : "You have no live listings.";
    }
}
