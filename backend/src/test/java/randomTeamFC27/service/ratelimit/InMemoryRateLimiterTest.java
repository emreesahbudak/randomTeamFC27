package randomTeamFC27.service.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimiterTest {

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToMaxRequestsWithinWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        assertTrue(limiter.tryConsume("k", 3, 60));
        assertTrue(limiter.tryConsume("k", 3, 60));
        assertTrue(limiter.tryConsume("k", 3, 60));
        assertFalse(limiter.tryConsume("k", 3, 60));
    }

    @Test
    void resetsAfterWindowElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        assertTrue(limiter.tryConsume("k", 1, 60));
        assertFalse(limiter.tryConsume("k", 1, 60));

        clock.advance(61);

        assertTrue(limiter.tryConsume("k", 1, 60));
    }

    @Test
    void tracksDifferentKeysIndependently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        assertTrue(limiter.tryConsume("a", 1, 60));
        assertTrue(limiter.tryConsume("b", 1, 60));
        assertFalse(limiter.tryConsume("a", 1, 60));
    }
}
