package com.xome.auction.model;

/**
 * The precise reason behind a {@link BidDecision}.
 *
 * <p>Stable machine-readable codes: the human message alongside them is free to
 * change wording, this is what clients branch on and what ops greps the audit
 * log for.
 */
public enum ReasonCode {

    /** First bid on the auction — leads at the starting price, nobody pushing. */
    ACCEPTED_FIRST_BID,

    /** Beat the standing maximum and took the lead. */
    ACCEPTED_TOOK_LEAD,

    /** Already leading; raised own maximum. Price is untouched. */
    ACCEPTED_MAX_RAISED,

    /** Valid bid, but the leader's hidden maximum is higher. Price moved up. */
    OUTBID_BY_EXISTING_MAX,

    /** Exactly matched the leader's maximum. Ties go to whoever bid first. */
    OUTBID_TIE_LEADER_RETAINED,

    /** Maximum was under the current price. */
    REJECTED_BELOW_CURRENT_PRICE,

    /** A maximum may only ever be raised. */
    REJECTED_MAX_NOT_INCREASED,

    /** Zero or negative maximum. */
    REJECTED_INVALID_AMOUNT,

    /** Arrived after the close time plus the late-arrival buffer. */
    REJECTED_AUCTION_CLOSED,

    /** No such auction. */
    REJECTED_UNKNOWN_AUCTION,

    /** Request id reused for different bid content — see AuctionService. */
    REJECTED_REQUEST_ID_CONFLICT,

    /** bidderId or requestId missing from the request. */
    REJECTED_MISSING_FIELD
}
