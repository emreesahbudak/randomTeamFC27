package randomTeamFC27.service.ratelimit;

/**
 * Fixed-window request limiter keyed by an arbitrary string (e.g. an OTP destination).
 * Swappable behind this interface — the in-memory implementation is fine for a single
 * instance; a distributed deployment would back this with Redis instead.
 */
public interface RateLimiter {

    /**
     * @return true if the call for {@code key} is allowed under the given window/limit,
     * false if the caller has exceeded {@code maxRequests} within {@code windowSeconds}.
     */
    boolean tryConsume(String key, int maxRequests, long windowSeconds);
}
