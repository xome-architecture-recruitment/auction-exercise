package com.xome.auction.model;

/** Where one bidder stands in an auction they are looking at. */
public enum BidderStanding {

    /** This bidder holds the lead. */
    LEADING,

    /** This bidder has bid, and someone else's maximum is in front. */
    OUTBID,

    /** This bidder has never bid on this auction. */
    NOT_BIDDING
}
