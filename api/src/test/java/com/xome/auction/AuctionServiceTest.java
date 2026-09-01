package com.xome.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xome.auction.model.AuctionEvent;
import com.xome.auction.model.AuctionStatus;
import com.xome.auction.model.BidDecision;
import com.xome.auction.model.BidRequest;
import com.xome.auction.model.Outcome;
import com.xome.auction.model.ReasonCode;

/**
 * The proxy bidding rules, tested against the service directly.
 *
 * <p>No Spring context: these are the price calculation and the rules around
 * it, and running them in-process keeps the feedback loop short enough to
 * actually use while changing the algorithm. The clock is fixed so nothing here
 * depends on how long the suite takes to run.
 */
class AuctionServiceTest {

    /** Well inside the auction window, so nothing here is near the close. */
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private AuctionStore store;
    private AuctionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        store = new AuctionStore(clock);
        service = new AuctionService(store, new AuctionAudit(), clock);
    }

    /**
     * The case that separates proxy bidding from a plain highest-bid auction: a
     * bid can lose and still move the price. Bob never leads, but Alice's
     * standing maximum absorbs his bid and the price settles one increment above
     * it. This is the brief's worked example.
     */
    @Test
    void losingBidRaisesThePriceWithoutChangingTheLeader() {
        bid("alice", 300_000, "r1");

        BidDecision bobs = bid("bob", 280_000, "r2");

        assertThat(bobs.outcome()).isEqualTo(Outcome.OUTBID);
        assertThat(bobs.reasonCode()).isEqualTo(ReasonCode.OUTBID_BY_EXISTING_MAX);
        assertThat(bobs.accepted()).isFalse();
        assertThat(bobs.movedPrice()).isTrue();

        assertThat(status().currentPrice()).isEqualTo(285_000);
        assertThat(status().leadingBidder()).isEqualTo("alice");

        // What Bob is told must not give away what Alice is willing to pay.
        assertThat(bobs.message()).doesNotContain("300,000");
    }

    /**
     * The price is one increment above the maximum just beaten — unless that
     * overshoots what the new leader agreed to pay, in which case it stops at
     * their maximum. Bob beats Alice's 300,000 but only has 302,000, so he leads
     * at 302,000 rather than the 305,000 a blind increment would have charged
     * him. The invariant is that the price never exceeds the leader's maximum.
     */
    @Test
    void takingTheLeadIsCappedByTheNewLeadersOwnMaximum() {
        bid("alice", 300_000, "r1");

        BidDecision bobs = bid("bob", 302_000, "r2");

        assertThat(bobs.outcome()).isEqualTo(Outcome.LEADING);
        assertThat(bobs.reasonCode()).isEqualTo(ReasonCode.ACCEPTED_TOOK_LEAD);

        assertThat(status().currentPrice())
                .as("capped at Bob's own maximum, not Alice's max plus an increment")
                .isEqualTo(302_000)
                .isLessThan(305_000);
        assertThat(status().leadingBidder()).isEqualTo("bob");
    }

    /**
     * Bids arrive from a mobile app that retries on timeout, so the same bid can
     * land twice. The retry must not move the price a second time and must come
     * back with the same verdict — a bidder who sees "outbid" and then "accepted"
     * for one bid has been lied to once.
     */
    @Test
    void aRetriedBidIsDecidedOnceAndThenReplayed() {
        bid("alice", 300_000, "r1");
        BidDecision first = bid("bob", 280_000, "r2");
        long priceAfterFirst = status().currentPrice();

        BidDecision retry = bid("bob", 280_000, "r2");

        assertThat(retry).isEqualTo(first);
        assertThat(status().currentPrice()).isEqualTo(priceAfterFirst);
        assertThat(status().bidderCount()).isEqualTo(2);

        // The audit trail still shows both arrivals — a retry that vanished from
        // the log would leave ops unable to explain a duplicate charge report —
        // with the replay marked as such and moving nothing.
        List<AuctionEvent> events = store.defaultAuction().events();
        assertThat(events).hasSize(3);
        AuctionEvent replay = events.get(2);
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.requestId()).isEqualTo("r2");
        assertThat(replay.priceBefore()).isEqualTo(replay.priceAfter());
    }

    /**
     * A maximum can be raised but never lowered: other bidders have already been
     * pushed up by it, and walking it back would rewrite a price they bid
     * against. Raising is allowed and must not move the price — the leader must
     * never be bid up against themselves.
     */
    @Test
    void theLeadersMaximumCanOnlyGoUp() {
        bid("alice", 300_000, "r1");
        bid("bob", 280_000, "r2");     // price 285,000, alice still leads

        BidDecision lowered = bid("alice", 260_000, "r3");

        assertThat(lowered.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(lowered.reasonCode()).isEqualTo(ReasonCode.REJECTED_MAX_NOT_INCREASED);
        assertThat(status().currentPrice()).isEqualTo(285_000);
        assertThat(status().leadingBidder()).isEqualTo("alice");

        // Resubmitting the same number is not a change either.
        assertThat(bid("alice", 300_000, "r4").reasonCode())
                .isEqualTo(ReasonCode.REJECTED_MAX_NOT_INCREASED);

        // Raising is the one permitted edit, and it leaves the price alone.
        BidDecision raised = bid("alice", 310_000, "r5");

        assertThat(raised.outcome()).isEqualTo(Outcome.LEADING);
        assertThat(raised.reasonCode()).isEqualTo(ReasonCode.ACCEPTED_MAX_RAISED);
        assertThat(status().currentPrice()).isEqualTo(285_000);
        assertThat(status().leadingBidder()).isEqualTo("alice");
    }

    // ------------------------------------------------------------------

    private BidDecision bid(String bidderId, long maxAmount, String requestId) {
        return service.placeBid(new BidRequest(null, bidderId, maxAmount, requestId));
    }

    private AuctionStatus status() {
        return service.status(null);
    }
}
