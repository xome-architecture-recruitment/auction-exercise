package com.xome.auction;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.xome.auction.model.AuctionEvent;
import com.xome.auction.model.AuctionPhase;
import com.xome.auction.model.AuctionState;
import com.xome.auction.model.AuctionStatus;
import com.xome.auction.model.BidDecision;
import com.xome.auction.model.BidRequest;
import com.xome.auction.model.BidderView;
import com.xome.auction.model.Outcome;
import com.xome.auction.model.ReasonCode;

/**
 * Proxy bidding.
 *
 * <p>The rule the whole thing turns on: a bidder submits the most they are
 * willing to pay, and the price only ever rises far enough to beat the
 * next-highest maximum by one increment, capped at the leader's maximum, so
 * nobody is ever charged more than they agreed to.
 *
 * <p>Every decision runs under the auction's lock. Bidding is a
 * read-then-write over the price, the leader and the leader's maximum
 * together; without the lock two bids arriving in the same millisecond can
 * both read the old leader, and the second can overwrite the first's price. A
 * few hundred bidders in the closing minute is nothing for a lock this short:
 * the work inside it is a handful of comparisons on in-memory data.
 */
@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    private final AuctionStore store;
    private final AuctionAudit auditLog;
    private final Clock clock;

    public AuctionService(AuctionStore store, AuctionAudit auditLog, Clock clock) {
        this.store = store;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Bidding
    // ------------------------------------------------------------------

    /**
     * Decide one bid.
     *
     * <p>Returns a decision for everything the caller could plausibly have got
     * wrong rather than throwing: a rejected bid is a normal business outcome
     * with a reason the bidder is owed, not an error.
     */
    public BidDecision placeBid(BidRequest request) {
        Instant now = clock.instant();

        if (isBlank(request.bidderId()) || isBlank(request.requestId())) {
            // No audit entry: with no bidder and no request id there is nothing
            // to file it against. Logged as a client error instead.
            log.warn("bid rejected before processing: missing bidderId or requestId, auction={}",
                    request.auctionId());
            return rejection(request, ReasonCode.REJECTED_MISSING_FIELD,
                    "Rejected. bidderId and requestId are both required.", 0, null);
        }

        AuctionState auction = store.find(request.auctionId());
        if (auction == null) {
            log.warn("bid rejected: unknown auction {} (bidder={}, requestId={})",
                    request.auctionId(), request.bidderId(), request.requestId());
            return rejection(request, ReasonCode.REJECTED_UNKNOWN_AUCTION,
                    "Rejected. Unknown auction.", 0, null);
        }

        log.info("bid received: auction={} bidder={} max={} requestId={} at={}",
                auction.auctionId(), request.bidderId(), request.maxAmount(),
                request.requestId(), now);

        auction.lock().lock();
        try {
            recordCloseIfSettled(auction, now);

            // The retry check comes first, ahead of every other rule. A bid
            // accepted while the auction was open must replay as accepted even
            // if the retry lands after the close: the client is asking "what
            // happened to my bid?", not submitting a new one.
            String fingerprint = fingerprint(auction, request);
            AuctionState.ProcessedRequest seen = auction.processed(request.requestId());
            if (seen != null) {
                if (!seen.fingerprint().equals(fingerprint)) {
                    // Same id, different bid. Not a retry but a client bug or a
                    // collision. Replaying the old decision here would tell them
                    // a bid went through that never did.
                    BidDecision conflict = rejection(request,
                            ReasonCode.REJECTED_REQUEST_ID_CONFLICT,
                            "Rejected. This request id was already used for a different bid.",
                            auction.currentPrice(), auction.leaderId());
                    record(auction, request, conflict, now, false);
                    return conflict;
                }
                record(auction, request, seen.decision(), now, true);
                return seen.decision();
            }

            BidDecision decision = decide(auction, request, now);
            auction.remember(request.requestId(), fingerprint, decision);
            record(auction, request, decision, now, false);
            return decision;
        } finally {
            auction.lock().unlock();
        }
    }

    /** The rules, in the order they are worth applying. Caller holds the lock. */
    private BidDecision decide(AuctionState auction, BidRequest request, Instant now) {
        long priceBefore = auction.currentPrice();
        String leaderBefore = auction.leaderId();
        String bidderId = request.bidderId();

        if (!auction.acceptingBidsAt(now)) {
            return rejection(request, ReasonCode.REJECTED_AUCTION_CLOSED,
                    "Rejected. The auction has closed.", priceBefore, leaderBefore);
        }

        Long max = request.maxAmount();
        if (max == null || max <= 0) {
            return rejection(request, ReasonCode.REJECTED_INVALID_AMOUNT,
                    "Rejected. Your maximum must be a positive amount.",
                    priceBefore, leaderBefore);
        }

        // A maximum may be raised but never lowered or withdrawn. Once it is in,
        // the auction has already acted on it: other bidders have been pushed up
        // by it, and letting someone walk it back would rewrite a price other
        // people have already bid against. Equal counts as not raised.
        // Resubmitting the same number changes nothing, and a genuine retry is
        // caught above by requestId, so reaching here with an equal maximum
        // means a new request that would do nothing.
        Long standing = auction.maxFor(bidderId);
        if (standing != null && max <= standing) {
            return rejection(request, ReasonCode.REJECTED_MAX_NOT_INCREASED,
                    "Rejected. A maximum can only be raised, and yours is already "
                            + money(standing) + ".",
                    priceBefore, leaderBefore);
        }

        // A maximum below the current price cannot win anything, and letting it
        // in would move the price for no reason.
        if (max < priceBefore) {
            return rejection(request, ReasonCode.REJECTED_BELOW_CURRENT_PRICE,
                    "Rejected. Your maximum is below the current price of "
                            + money(priceBefore) + ".",
                    priceBefore, leaderBefore);
        }

        auction.recordMax(bidderId, max);

        Outcome outcome;
        ReasonCode reason;

        if (leaderBefore == null) {
            // Nobody to push against: the first bidder leads at the starting
            // price, however high their maximum is.
            auction.setLeader(bidderId, max);
            outcome = Outcome.LEADING;
            reason = ReasonCode.ACCEPTED_FIRST_BID;

        } else if (bidderId.equals(leaderBefore)) {
            // The leader raising their own ceiling. Nothing to beat, so the
            // price does not move: raising your maximum must never bid you up
            // against yourself.
            auction.setLeader(bidderId, max);
            outcome = Outcome.LEADING;
            reason = ReasonCode.ACCEPTED_MAX_RAISED;

        } else {
            long leaderMax = auction.leaderMax();

            if (max > leaderMax) {
                // Takes the lead. The price is one increment above the maximum
                // just beaten, or this bidder's own maximum if that is lower,
                // which is the case where a full increment would overshoot what
                // they agreed to pay.
                auction.setLeader(bidderId, max);
                raisePriceTo(auction, Math.min(max, leaderMax + auction.increment()));
                outcome = Outcome.LEADING;
                reason = ReasonCode.ACCEPTED_TOOK_LEAD;

            } else if (max == leaderMax) {
                // A dead heat. Whoever got their maximum in first keeps the
                // lead: the challenger has nothing left to bid with, since one
                // increment above the tie is more than they agreed to pay. The
                // price rises to the tied amount, which both of them are
                // demonstrably willing to pay.
                raisePriceTo(auction, leaderMax);
                outcome = Outcome.OUTBID;
                reason = ReasonCode.OUTBID_TIE_LEADER_RETAINED;

            } else {
                // Loses, but not for nothing: the price rises to one increment
                // above this bid, capped at the leader's maximum so the leader
                // is never pushed past what they agreed to.
                raisePriceTo(auction, Math.min(leaderMax, max + auction.increment()));
                outcome = Outcome.OUTBID;
                reason = ReasonCode.OUTBID_BY_EXISTING_MAX;
            }
        }

        applyReserveJump(auction);

        return new BidDecision(outcome, reason,
                message(reason, auction),
                bidderId, request.requestId(),
                priceBefore, auction.currentPrice(),
                leaderBefore, auction.leaderId());
    }

    /** The price only ever goes up. */
    private void raisePriceTo(AuctionState auction, long candidate) {
        auction.setPrice(Math.max(auction.currentPrice(), candidate));
    }

    /**
     * When the leader's maximum covers the reserve, the price goes straight to
     * the reserve.
     *
     * <p>Without this a lone bidder willing to pay well over the reserve still
     * leaves the property unsold, because the price never had anything to climb
     * against, which is plainly the wrong outcome for everyone involved. The
     * jump keeps the invariant that matters: the price never exceeds the
     * leader's maximum, because the reserve is under it whenever this fires. It
     * is also what makes "reserve met" mean the price actually reached the
     * reserve, so the flag a watcher sees and the number the seller contracted
     * on cannot drift apart.
     *
     * <p>The cost is that it reveals something about the reserve. A bidder who
     * watches the price jump to an unexplained number has found it. Every
     * platform running hidden reserves has that leak; the alternative, holding
     * the price down while flagging the reserve as met, leaks the same fact and
     * adds a displayed price that means nothing.
     */
    private void applyReserveJump(AuctionState auction) {
        Long leaderMax = auction.leaderMax();
        if (leaderMax != null && leaderMax >= auction.reserve()
                && auction.currentPrice() < auction.reserve()) {
            auction.setPrice(auction.reserve());
        }
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    /** The auction as an anonymous watcher may see it. */
    public AuctionStatus status(String auctionId) {
        return status(auctionId, null);
    }

    /**
     * The auction, optionally through one bidder's eyes. Null when there is no
     * such auction.
     *
     * <p>A named bidder gets the same watcher view plus a {@code you} block —
     * whether they are in front and what their own maximum is. Nothing else
     * changes: naming yourself reveals your own position, never anyone else's.
     */
    public AuctionStatus status(String auctionId, String bidderId) {
        AuctionState auction = store.find(auctionId);
        if (auction == null) {
            return null;
        }
        Instant now = clock.instant();
        auction.lock().lock();
        try {
            recordCloseIfSettled(auction, now);
            return snapshot(auction, now, bidderId);
        } finally {
            auction.lock().unlock();
        }
    }

    /** Caller holds the lock. */
    private AuctionStatus snapshot(AuctionState auction, Instant now, String bidderId) {
        String leaderId = auction.leaderId();

        // Null rather than the price when nobody has bid: "the leader is
        // committed to $250,000" would be a lie about a leader who does not
        // exist, and a client showing it has no way to tell the difference.
        Long leaderAmount = leaderId == null ? null : auction.currentPrice();

        BidderView you = isBlank(bidderId) ? null
                : BidderView.of(bidderId, leaderId, auction.maxFor(bidderId));

        return new AuctionStatus(
                auction.auctionId(),
                auction.currentPrice(),
                leaderAmount,
                leaderId,
                leaderId,
                auction.reserveMet(),
                auction.closeTime(),
                auction.acceptingBidsUntil(),
                auction.phaseAt(now),
                auction.acceptingBidsAt(now),
                auction.startingPrice(),
                auction.increment(),
                auction.bidderCount(),
                you);
    }

    /**
     * Close the auction the first time anyone looks at it after the buffer has
     * run out.
     *
     * <p>Deciding this lazily rather than on a timer means there is no scheduler
     * to fall behind and no window where a bid and the close race each other:
     * the phase is a function of the clock and the state, evaluated inside the
     * same lock as everything else. The only thing that needs doing exactly once
     * is writing the close to the audit log.
     */
    private void recordCloseIfSettled(AuctionState auction, Instant now) {
        AuctionPhase phase = auction.phaseAt(now);
        if (phase != AuctionPhase.SOLD && phase != AuctionPhase.UNSOLD) {
            return;
        }
        if (!auction.markCloseRecorded()) {
            return;
        }
        AuctionEvent event = new AuctionEvent(
                auction.nextSequence(), auction.auctionId(), now,
                "auction-close", auction.leaderId(), null, null, null,
                auction.currentPrice(), auction.currentPrice(),
                auction.leaderId(), auction.leaderId(), auction.leaderMax(),
                phase, false);
        auction.append(event);
        auditLog.close(event, auction.reserveMet());

        if (phase == AuctionPhase.SOLD) {
            log.info("auction {} closed SOLD to {} at {}",
                    auction.auctionId(), auction.leaderId(), money(auction.currentPrice()));
        } else {
            // Bids but no sale is the case people query later, so say which of
            // the two kinds of unsold it was.
            log.info("auction {} closed UNSOLD ({}) at {}", auction.auctionId(),
                    auction.leaderId() == null ? "no bids" : "reserve not met",
                    money(auction.currentPrice()));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Caller holds the lock. Writes the history entry and the audit line.
     *
     * <p>A replayed retry logs the price and leader as they are now, on both
     * sides, rather than the numbers from the original decision. The verdict
     * being replayed is the original one, but nothing moved at this instant,
     * and a log line claiming a price change that already happened minutes ago
     * is exactly the sort of thing that makes a reconstruction wrong.
     */
    private void record(AuctionState auction, BidRequest request, BidDecision decision,
                        Instant now, boolean duplicate) {
        long priceBefore = duplicate ? auction.currentPrice() : decision.priceBefore();
        long priceAfter = duplicate ? auction.currentPrice() : decision.priceAfter();
        String leaderBefore = duplicate ? auction.leaderId() : decision.leaderBefore();
        String leaderAfter = duplicate ? auction.leaderId() : decision.leaderAfter();

        AuctionEvent event = new AuctionEvent(
                auction.nextSequence(), auction.auctionId(), now,
                request.requestId(), request.bidderId(), request.maxAmount(),
                decision.outcome(), decision.reasonCode(),
                priceBefore, priceAfter, leaderBefore, leaderAfter,
                auction.leaderMax(), auction.phaseAt(now), duplicate);
        auction.append(event);
        auditLog.bid(event, auction.reserveMet());
    }

    /**
     * Identifies the bid, not the request. Two requests carrying the same id are
     * the same bid only if they say the same thing.
     */
    private String fingerprint(AuctionState auction, BidRequest request) {
        return auction.auctionId() + "|" + request.bidderId() + "|" + request.maxAmount();
    }

    private BidDecision rejection(BidRequest request, ReasonCode reason, String message,
                                  long price, String leader) {
        return new BidDecision(Outcome.REJECTED, reason, message,
                request.bidderId(), request.requestId(),
                price, price, leader, leader);
    }

    /** Caller holds the lock. Nothing here reveals another bidder's maximum. */
    private String message(ReasonCode reason, AuctionState auction) {
        String price = money(auction.currentPrice());
        return switch (reason) {
            case ACCEPTED_FIRST_BID, ACCEPTED_TOOK_LEAD ->
                    "Accepted. You are the leading bidder at " + price + ".";
            case ACCEPTED_MAX_RAISED ->
                    "Accepted. Your maximum was raised and you are still the leading bidder at "
                            + price + ".";
            case OUTBID_BY_EXISTING_MAX ->
                    "Outbid. Another bidder's maximum is higher than yours. The price is now "
                            + price + ".";
            case OUTBID_TIE_LEADER_RETAINED ->
                    "Outbid. Another bidder has the same maximum and set it first. The price is now "
                            + price + ".";
            default -> "Rejected.";
        };
    }

    private static String money(long amount) {
        return String.format("$%,d", amount);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
