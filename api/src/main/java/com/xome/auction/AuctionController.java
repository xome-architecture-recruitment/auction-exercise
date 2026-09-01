package com.xome.auction;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xome.auction.model.AuctionStatus;
import com.xome.auction.model.BidDecision;
import com.xome.auction.model.BidRequest;
import com.xome.auction.model.BidResponse;

/**
 * The two endpoints.
 *
 * <p>On status codes: a rejected bid comes back as 200 with
 * {@code accepted: false} and a reason, not as a 4xx. Being outbid or bidding
 * under the current price is the auction working correctly, not a malformed
 * request, and the client needs the reason and the fresh price in order to show
 * the bidder anything useful. 4xx is kept for requests we could not act on at
 * all: a missing bidderId or requestId, or an auction that does not exist.
 *
 * <p>That is also what the existing web client needs, since it throws away the
 * body on any non-2xx response and shows a generic failure. Worth raising:
 * a client that wants HTTP to carry the verdict would rather have 409 for an
 * outbid, and the two audiences cannot both be right.
 */
@RestController
@RequestMapping("/api/auction")
@CrossOrigin(origins = "http://localhost:3000") // pre-configured so you don't fight CORS
public class AuctionController {

    private static final Logger log = LoggerFactory.getLogger(AuctionController.class);

    private final AuctionService service;

    public AuctionController(AuctionService service) {
        this.service = service;
    }

    /** Health check. */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    /**
     * Accept a bidder's maximum.
     *
     * <p>Safe to retry: the same requestId always produces the same verdict and
     * never moves the price twice.
     */
    @PostMapping("/bids")
    public ResponseEntity<BidResponse> placeBid(@RequestBody BidRequest request) {
        BidDecision decision = service.placeBid(request);

        // Their own view, not the anonymous one: someone who has just bid wants
        // to know whether they are in front, and making them follow up with a
        // GET to find out is a round trip for something we already know.
        AuctionStatus status = service.status(request.auctionId(), request.bidderId());

        HttpStatus httpStatus = switch (decision.reasonCode()) {
            case REJECTED_MISSING_FIELD -> HttpStatus.BAD_REQUEST;
            case REJECTED_UNKNOWN_AUCTION -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.OK;
        };

        return ResponseEntity.status(httpStatus).body(BidResponse.of(decision, status));
    }

    /**
     * The current state of the auction, as a watcher is allowed to see it.
     *
     * <p>Pass {@code bidderId} to get the same thing plus a {@code you} block:
     * whether you are in front and what your own maximum is. Leaving it off is
     * the watcher view, which is what the public page wants.
     *
     * <p>Since there is no authentication, {@code bidderId} is a claim, not a
     * fact — see {@link com.xome.auction.model.BidderView} for what that costs.
     */
    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(required = false) String auctionId,
                                    @RequestParam(required = false) String bidderId) {
        AuctionStatus status = service.status(auctionId, bidderId);
        if (status == null) {
            log.warn("status requested for unknown auction {}", auctionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "unknown auction"));
        }
        return ResponseEntity.ok(status);
    }
}
