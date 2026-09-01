package com.xome.auction.model;

/**
 * The part of the status that is about you.
 *
 * <p>Only ever populated for the bidder named on the request, and only ever
 * carrying that bidder's own numbers. Nothing in here is derived from anybody
 * else's maximum: {@code leading} says whether you are in front, not who is in
 * front of you or by how much.
 *
 * <p><b>The gap.</b> There is no authentication in this exercise — a bidder is
 * whoever the request says they are — so anyone can ask for the status as
 * {@code bidderId=alice} and read back Alice's maximum. That is a real leak of
 * the one number the brief says must stay confidential, and it is not fixable
 * inside this class: it needs the caller's identity to be something the server
 * establishes rather than something the client asserts. Until then the honest
 * summary is that maximums are private from other bidders by convention, not by
 * enforcement. If that trade is not acceptable before this ships, drop
 * {@code yourMaximum} and keep the rest — the standing and the flag leak
 * nothing on their own.
 *
 * @param bidderId    the bidder this view was built for
 * @param leading     true when this bidder currently holds the lead
 * @param standing    the same fact with the "never bid" case separated out
 * @param yourMaximum this bidder's own standing maximum, null if they have not bid
 */
public record BidderView(
        String bidderId,
        boolean leading,
        BidderStanding standing,
        Long yourMaximum) {

    public static BidderView of(String bidderId, String leaderId, Long yourMaximum) {
        boolean leading = bidderId.equals(leaderId);
        BidderStanding standing = yourMaximum == null ? BidderStanding.NOT_BIDDING
                : leading ? BidderStanding.LEADING
                : BidderStanding.OUTBID;
        return new BidderView(bidderId, leading, standing, yourMaximum);
    }
}
