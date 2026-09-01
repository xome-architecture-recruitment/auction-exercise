package com.xome.auction;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AuctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuctionApplication.class, args);
    }

    /**
     * Time comes from a bean rather than Instant.now() so that closing, the
     * late-arrival buffer and everything after them can be tested without
     * sleeping. Anything that decides an auction outcome reads this.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
