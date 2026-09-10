package randomTeamFC27.service.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counter per key. A window starts on a key's first request and resets once
 * {@code windowSeconds} has elapsed since that first request.
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(long windowStartEpochSeconds, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryConsume(String key, int maxRequests, long windowSeconds) {
        long now = clock.instant().getEpochSecond();
        boolean[] allowed = new boolean[1];

        windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartEpochSeconds() >= windowSeconds) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (existing.count() < maxRequests) {
                allowed[0] = true;
                return new Window(existing.windowStartEpochSeconds(), existing.count() + 1);
            }
            allowed[0] = false;
            return existing;
        });

        return allowed[0];
    }
}
