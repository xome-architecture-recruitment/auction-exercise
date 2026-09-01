package com.xome.auction.model;

/**
 * The verdict on one bid, and the auction facts that verdict was based on.
 *
 * <p>Held onto after the fact: this is what a retry of the same requestId gets
 * replayed back, and what the audit log is written from.
 *
 * @param outcome      leading / outbid / rejected
 * @param reasonCode   the specific reason
 * @param message      human-readable, safe to show a bidder (leaks no maximum
 *                     other than their own, and never the reserve)
 * @param bidderId     who bid
 * @param requestId    the request this decision belongs to
 * @param priceBefore  current price before the bid was applied
 * @param priceAfter   current price after
 * @param leaderBefore leading bidder before, null if there was none
 * @param leaderAfter  leading bidder after
 */
public record BidDecision(
        Outcome outcome,
        ReasonCode reasonCode,
        String message,
        String bidderId,
        String requestId,
        long priceBefore,
        long priceAfter,
        String leaderBefore,
        String leaderAfter) {

    /** True only when this bidder is in front. See {@link Outcome}. */
    public boolean accepted() {
        return outcome == Outcome.LEADING;
    }

    /** True when the bid was valid and recorded, whether or not it took the lead. */
    public boolean recorded() {
        return outcome != Outcome.REJECTED;
    }

    /** True when the bid moved the current price. */
    public boolean movedPrice() {
        return priceAfter != priceBefore;
    }
}
