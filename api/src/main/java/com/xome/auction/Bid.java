package com.xome.auction;

import java.time.Instant;

public record Bid(String bidderId, long amount, Instant placedAt) {
}
