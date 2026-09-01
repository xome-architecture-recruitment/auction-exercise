package com.xome.auction.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Everything we know about one auction, and the lock that guards it.
 *
 * <p>The state is deliberately mutable and deliberately not thread-safe on its
 * own. A bid is not a single write — it reads the price and the leader's
 * maximum, works out a new price, and writes both back — so field-level
 * synchronisation would buy nothing. Instead the whole decision runs under
 * {@link #lock()}. Contention is one auction wide, which is the right grain:
 * the final minute of a hot property is a few hundred bidders on one item, and
 * each decision is a handful of comparisons on data already in memory.
 *
 * <p>Two things are held here that must never reach a client: {@code reserve},
 * and every bidder's {@code maxAmount}. {@link AuctionStatus} is the only shape
 * that goes out over the wire.
 *
 * <p>The mutators below are public because the service that drives them lives
 * in another package. That is a real loss: they were package-private, and the
 * compiler used to guarantee that nothing outside the auction's own code could
 * move the price. Now "hold {@link #lock()} before calling these" is a
 * convention held up by this comment. If that stops being enough, the fix is
 * not to widen it further but to fold the decision itself into this class, so
 * that the only public mutator is one that takes the lock on your behalf.
 */
public class AuctionState {

    private final ReentrantLock lock = new ReentrantLock();

    private final String auctionId;
    private final long startingPrice;
    private final long increment;
    private final long reserve;
    private final Instant closeTime;
    private final Duration lateBidBuffer;

    private long currentPrice;
    private String leaderId;
    private Long leaderMax;

    /**
     * The standing maximum for every bidder who has ever bid, including bidders
     * who are currently losing. Needed for one rule only — a maximum can be
     * raised but never lowered — and that rule is the reason we keep losers'
     * numbers at all. The price does not depend on them: an underbidder's
     * maximum has already been spent pushing the price up at the moment it
     * arrived.
     */
    private final Map<String, Long> maxByBidder = new HashMap<>();

    /** requestId to the decision it produced, for replaying retries. */
    private final Map<String, ProcessedRequest> processed = new HashMap<>();

    private final List<AuctionEvent> events = new ArrayList<>();
    private long sequence;
    private boolean closeRecorded;

    public AuctionState(String auctionId, long startingPrice, long increment,
                        long reserve, Instant closeTime, Duration lateBidBuffer) {
        this.auctionId = auctionId;
        this.startingPrice = startingPrice;
        this.increment = increment;
        this.reserve = reserve;
        this.closeTime = closeTime;
        this.lateBidBuffer = lateBidBuffer;
        this.currentPrice = startingPrice;
    }

    /** A bid or a status read is decided under this. */
    public ReentrantLock lock() {
        return lock;
    }

    public String auctionId()     { return auctionId; }
    public long startingPrice()   { return startingPrice; }
    public long increment()       { return increment; }
    public long reserve()         { return reserve; }
    public Instant closeTime()    { return closeTime; }
    public long currentPrice()    { return currentPrice; }
    public String leaderId()      { return leaderId; }
    public Long leaderMax()       { return leaderMax; }
    public int bidderCount()      { return maxByBidder.size(); }

    /**
     * The last instant a bid can arrive and still count.
     *
     * <p>Bids come off mobile connections that retry, and a bid handed to the
     * network before the close should not lose because a packet took four
     * seconds. The buffer is a fixed grace window after the close time, not an
     * extension of the auction: nobody is told about it, and it does not move
     * when a late bid lands, so it cannot be used to snipe indefinitely.
     */
    public Instant acceptingBidsUntil() {
        return closeTime.plus(lateBidBuffer);
    }

    /**
     * Reserve met means the price actually reached the reserve — see the jump
     * in {@link AuctionService}. Keeping this equivalence means a watcher who
     * sees "met" and a seller reading the contract never disagree.
     */
    public boolean reserveMet() {
        return currentPrice >= reserve;
    }

    public AuctionPhase phaseAt(Instant now) {
        if (now.isAfter(acceptingBidsUntil())) {
            return leaderId != null && reserveMet() ? AuctionPhase.SOLD : AuctionPhase.UNSOLD;
        }
        if (now.isAfter(closeTime)) {
            return AuctionPhase.CLOSING;
        }
        return AuctionPhase.OPEN;
    }

    public boolean acceptingBidsAt(Instant now) {
        AuctionPhase phase = phaseAt(now);
        return phase == AuctionPhase.OPEN || phase == AuctionPhase.CLOSING;
    }

    public Long maxFor(String bidderId) {
        return maxByBidder.get(bidderId);
    }

    public void recordMax(String bidderId, long maxAmount) {
        maxByBidder.put(bidderId, maxAmount);
    }

    public void setPrice(long price) {
        this.currentPrice = price;
    }

    public void setLeader(String bidderId, long max) {
        this.leaderId = bidderId;
        this.leaderMax = max;
    }

    public ProcessedRequest processed(String requestId) {
        return processed.get(requestId);
    }

    public void remember(String requestId, String fingerprint, BidDecision decision) {
        processed.put(requestId, new ProcessedRequest(fingerprint, decision));
    }

    /** Closing is recorded once, however many times someone reads the status. */
    public boolean markCloseRecorded() {
        if (closeRecorded) {
            return false;
        }
        closeRecorded = true;
        return true;
    }

    public long nextSequence() {
        return ++sequence;
    }

    public void append(AuctionEvent event) {
        events.add(event);
    }

    /** The full history, oldest first. Ops view — not exposed through the API. */
    public List<AuctionEvent> events() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(events));
        } finally {
            lock.unlock();
        }
    }

    /**
     * A request we have already answered.
     *
     * <p>The fingerprint is what makes the retry check honest: matching on
     * requestId alone would let a client reuse an id for a genuinely different
     * bid and silently get the old answer back.
     */
    public record ProcessedRequest(String fingerprint, BidDecision decision) {
    }
}
