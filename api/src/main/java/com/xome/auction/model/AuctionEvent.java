package com.xome.auction.model;

import java.time.Instant;

/**
 * One line of the auction's history, append-only.
 *
 * <p>Every bid that reaches the service produces exactly one of these —
 * accepted, outbid, rejected or replayed — plus one for the close itself. The
 * audit log is written from these, and the sequence number gives ops a total
 * order to argue from when two bidders both claim they were first.
 *
 * <p>Unlike {@link BidDecision}, this is internal: it carries the leader's
 * maximum, which must never leave the server through the API but is exactly
 * what you need to explain to a bidder why the price moved when it did.
 */
public record AuctionEvent(
        long sequence,
        String auctionId,
        Instant at,
        String requestId,
        String bidderId,
        Long maxAmount,
        Outcome outcome,
        ReasonCode reasonCode,
        long priceBefore,
        long priceAfter,
        String leaderBefore,
        String leaderAfter,
        Long leaderMaxAfter,
        AuctionPhase phase,
        boolean duplicate) {
}
