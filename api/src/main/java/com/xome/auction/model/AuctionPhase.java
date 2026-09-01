package com.xome.auction.model;

/** Where an auction is in its lifecycle. Derived from the clock, never stored. */
public enum AuctionPhase {

    /** Before the close time. Bids accepted normally. */
    OPEN,

    /** Past the close time but inside the late-arrival buffer. Bids still accepted. */
    CLOSING,

    /** Closed with the reserve met. The leader has bought the property. */
    SOLD,

    /** Closed without the reserve being met. Nothing sells, bids or not. */
    UNSOLD
}
