package com.xome.auction;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auction")
@CrossOrigin(origins = "http://localhost:3000") // pre-configured so you don't fight CORS
public class AuctionController {

    private static final Logger log = LoggerFactory.getLogger(AuctionController.class);

    private final AuctionStore store;

    public AuctionController(AuctionStore store) {
        this.store = store;
    }

    /** Health check — already working. */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    /**
     * TODO: accept a bidder's maximum. Accept or reject it, and return
     * something the UI can act on.
     */
    @PostMapping("/bids")
    public ResponseEntity<?> placeBid(@RequestBody BidRequest request) {
        log.info("received bid request");
        return ResponseEntity.status(501).body(Map.of("error", "not implemented"));
    }

    /**
     * TODO: return the current state of the auction, as a watcher is allowed
     * to see it.
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.status(501).body(Map.of("error", "not implemented"));
    }
}
