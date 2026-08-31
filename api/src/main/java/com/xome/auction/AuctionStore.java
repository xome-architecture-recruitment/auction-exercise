package com.xome.auction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * In-memory storage. No database needed for this exercise.
 *
 * TODO: revisit — this is a plain ArrayList.
 */
@Component
public class AuctionStore {

    public static final long STARTING_PRICE = 250_000L;
    public static final long INCREMENT      = 5_000L;

    private final Instant closesAt = Instant.now().plusSeconds(3600);
    private final List<Bid> bids = new ArrayList<>();

    public Instant closesAt()   { return closesAt; }
    public List<Bid> bids()     { return bids; }
    public void addBid(Bid b)   { bids.add(b); }
}
