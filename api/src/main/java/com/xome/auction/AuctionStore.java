package com.xome.auction;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.xome.auction.model.AuctionState;

/**
 * In-memory storage. No database for this exercise.
 *
 * <p>Keyed by auction id even though there is one seeded auction, because the
 * alternative — one global price and one global leader — is the kind of thing
 * that is free to avoid now and expensive to unpick later. Nothing here is
 * durable: a restart loses the auction, which is fine for the exercise and
 * would obviously not be for a property sale.
 */
@Component
public class AuctionStore {

    public static final String DEFAULT_AUCTION_ID = "meridian-way";

    public static final long STARTING_PRICE = 250_000L;
    public static final long INCREMENT      = 5_000L;

    /**
     * The seller's reserve. Confidential — it is held here and never leaves the
     * server; watchers only ever learn whether it has been met.
     *
     * <p>Set above the brief's worked example on purpose: with a reserve of
     * 320,000 the example runs exactly as written (250,000 / 285,000 / 305,000)
     * and still ends with the reserve unmet, which is the more interesting
     * demo. See the reserve jump in {@link AuctionService} for why a reserve at
     * or below 310,000 would have changed those numbers.
     */
    public static final long RESERVE = 320_000L;

    /** How long after the close time a bid can still arrive and count. */
    public static final Duration LATE_BID_BUFFER = Duration.ofMinutes(1);

    private static final Duration AUCTION_LENGTH = Duration.ofHours(1);

    private final Map<String, AuctionState> auctions = new ConcurrentHashMap<>();

    public AuctionStore(Clock clock) {
        AuctionState seeded = new AuctionState(
                DEFAULT_AUCTION_ID,
                STARTING_PRICE,
                INCREMENT,
                RESERVE,
                clock.instant().plus(AUCTION_LENGTH),
                LATE_BID_BUFFER);
        auctions.put(seeded.auctionId(), seeded);
    }

    /** Null when there is no such auction — callers turn that into a rejection. */
    public AuctionState find(String auctionId) {
        return auctions.get(auctionId == null || auctionId.isBlank()
                ? DEFAULT_AUCTION_ID
                : auctionId);
    }

    public AuctionState defaultAuction() {
        return auctions.get(DEFAULT_AUCTION_ID);
    }
}
