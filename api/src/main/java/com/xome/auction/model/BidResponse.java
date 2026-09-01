package com.xome.auction.model;

/**
 * What POST /api/auction/bids returns.
 *
 * <p>{@code accepted} and {@code reason} are what the existing web client
 * reads; {@code outcome} and {@code reasonCode} are for anything that needs to
 * branch on the decision rather than render it. The status snapshot is always
 * the live one, so a client that only ever posts bids still has a current view
 * without a second call.
 */
public record BidResponse(
        boolean accepted,
        String reason,
        Outcome outcome,
        ReasonCode reasonCode,
        String bidderId,
        String requestId,
        AuctionStatus status) {

    public static BidResponse of(BidDecision decision, AuctionStatus status) {
        return new BidResponse(
                decision.accepted(),
                decision.message(),
                decision.outcome(),
                decision.reasonCode(),
                decision.bidderId(),
                decision.requestId(),
                status);
    }
}
