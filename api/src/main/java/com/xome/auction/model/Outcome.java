package com.xome.auction.model;

/**
 * What a bid did, from the bidder's point of view.
 *
 * <p>Deliberately three values rather than a boolean. A bid can be perfectly
 * valid, be recorded, move the price — and still not put you in front, because
 * someone else's proxy maximum is higher. That is a different thing from a bid
 * we refused to act on at all, and support needs to be able to tell them apart
 * when a bidder rings up to complain.
 */
public enum Outcome {

    /** The bid was accepted and this bidder is now the leader. */
    LEADING,

    /** The bid was valid and recorded, but an existing maximum still leads. */
    OUTBID,

    /** The bid broke a rule. Nothing was recorded and no state changed. */
    REJECTED
}
