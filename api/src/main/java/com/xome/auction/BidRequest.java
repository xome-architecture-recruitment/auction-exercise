package com.xome.auction;

/**
 * Incoming bid. Shape it however your design needs — this is a starting point,
 * not a constraint.
 */
public record BidRequest(String bidderId, long amount, String requestId) {
}
