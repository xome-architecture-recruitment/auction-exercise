package com.xome.auction.model;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * An incoming bid: the most this bidder is willing to pay.
 *
 * <p>{@code amount} is accepted as an alias for {@code maxAmount} because the
 * existing web client posts {@code amount}. The field is named maxAmount here
 * because that is what it is — the number is a ceiling, not a bid, and calling
 * it "amount" is how people end up writing code that treats it as the price.
 *
 * <p>There is deliberately no receivedAt on the request. The time that decides
 * whether a bid made the close is server time; a mobile client's clock is not
 * something to hang an auction outcome on.
 *
 * @param auctionId which auction; defaults to the single seeded auction when absent
 * @param bidderId  who is bidding — no auth in this exercise, we trust the id
 * @param maxAmount the bidder's confidential maximum, in whole dollars
 * @param requestId client-generated, stable across retries of the same bid
 */
public record BidRequest(
        String auctionId,
        String bidderId,
        @JsonAlias({ "amount", "max", "maximum" }) Long maxAmount,
        String requestId) {
}
