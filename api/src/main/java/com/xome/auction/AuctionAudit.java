package com.xome.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xome.auction.model.AuctionEvent;
import com.xome.auction.model.AuctionPhase;

/**
 * The audit trail, written as one line per decision.
 *
 * <p>Written to its own logger, {@code AUCTION_AUDIT}, so it can be routed to
 * its own file and kept for as long as a dispute can take, independently of
 * whatever the application logger is doing.
 *
 * <p>The line carries the price and the leader on both sides of the bid, so a
 * disputed auction can be replayed from the log alone: every price move has an
 * event that caused it, and every event says what the price was before it. The
 * format is flat {@code key=value} on one line — greppable by requestId or by
 * bidder, and a step away from being structured JSON if we ever ship the logs
 * somewhere that wants that.
 *
 * <p>It logs the leader's maximum, which the API never reveals. That is
 * deliberate: "why did the price move when I didn't bid?" cannot be answered
 * without it. The confidentiality rule is about what bidders and watchers can
 * see, not about what operations can reconstruct — which does mean these logs
 * are commercially sensitive and want the access controls to match.
 */
@Component
public class AuctionAudit {

    private static final Logger audit = LoggerFactory.getLogger("AUCTION_AUDIT");

    /** One bid, whatever we decided about it — including replayed retries. */
    public void bid(AuctionEvent e, boolean reserveMet) {
        audit.info("event=BID seq={} auction={} at={} requestId={} bidder={} max={}"
                        + " outcome={} reason={} priceBefore={} priceAfter={}"
                        + " leaderBefore={} leaderAfter={} leaderMax={} reserveMet={}"
                        + " phase={} duplicate={}",
                e.sequence(), e.auctionId(), e.at(), e.requestId(), e.bidderId(), e.maxAmount(),
                e.outcome(), e.reasonCode(), e.priceBefore(), e.priceAfter(),
                e.leaderBefore(), e.leaderAfter(), e.leaderMaxAfter(), reserveMet,
                e.phase(), e.duplicate());
    }

    /** The close. Logged once, the first time anyone observes the auction past its buffer. */
    public void close(AuctionEvent e, boolean reserveMet) {
        audit.info("event=CLOSE seq={} auction={} at={} phase={} finalPrice={} winner={}"
                        + " leaderMax={} reserveMet={}",
                e.sequence(), e.auctionId(), e.at(), e.phase(), e.priceAfter(),
                e.phase() == AuctionPhase.SOLD ? e.leaderAfter() : null,
                e.leaderMaxAfter(), reserveMet);
    }
}
