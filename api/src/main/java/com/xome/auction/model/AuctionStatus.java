package com.xome.auction.model;

import java.time.Instant;

/**
 * The auction as a watcher is allowed to see it, plus — when the caller names
 * themselves — the part that is about them.
 *
 * <p>What is missing here is the point of the class: no maximums, not the
 * leader's and not anybody else's, and not the reserve amount, only whether the
 * reserve has been met. Everything a watcher can see is in this record, so there
 * is one place to check when someone asks "can they see that?".
 *
 * <p>{@code leadingBidder} and {@code leaderId} are the same value under two
 * names: the web client reads leadingBidder, the rest of our tooling talks in
 * leaderId. Cheap to serve both, and the alternative is one of the two callers
 * being wrong.
 *
 * @param auctionId          which auction
 * @param currentPrice       what it would sell for right now
 * @param leaderAmount       what the leader is committed to, null when nobody
 *                           has bid. The same number as currentPrice whenever
 *                           there is a leader — and deliberately <b>not</b> the
 *                           leader's maximum, which is exactly the number
 *                           proxy bidding exists to keep hidden. Before any bid
 *                           the two differ in the way that matters: the price is
 *                           the seller's asking price and nobody is committed to
 *                           anything.
 * @param leadingBidder      who is winning
 * @param leaderId           same as leadingBidder
 * @param reserveMet         whether the price has reached the reserve; the
 *                           reserve amount itself never leaves the server
 * @param closesAt           when bidding closes
 * @param acceptingBidsUntil closesAt plus the late-arrival buffer
 * @param status             OPEN / CLOSING / SOLD / UNSOLD
 * @param active             whether a bid sent right now would be considered
 * @param startingPrice      what it opened at
 * @param increment          the bid step
 * @param bidderCount        how many distinct bidders have taken part
 * @param you                the calling bidder's own position, null when the
 *                           caller did not name themselves
 */
public record AuctionStatus(
        String auctionId,
        long currentPrice,
        Long leaderAmount,
        String leadingBidder,
        String leaderId,
        boolean reserveMet,
        Instant closesAt,
        Instant acceptingBidsUntil,
        AuctionPhase status,
        boolean active,
        long startingPrice,
        long increment,
        int bidderCount,
        BidderView you) {
}
