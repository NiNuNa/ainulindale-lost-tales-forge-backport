package com.ninuna.losttales.compat.discord;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Discord's replies have said of its rate limits, so a lane waits
 * for a bucket to reset instead of sending into a 429. Discord names the
 * bucket a route is in by a hash, and counts each bucket apart for every
 * webhook or channel a request names — the major key, here the lane —
 * so one state is kept per bucket and lane: a bucket a reply said has
 * nothing left holds that lane's requests on every route in the bucket
 * until its reset, and a later reply with requests left releases it. A
 * route whose bucket Discord has not named is kept under the route
 * itself. The bot's global limit, which Discord announces on a 429, is
 * held apart. Worker thread only; nothing here is synchronized. Bounded
 * to {@link #MAX_STATES}, the one least recently used falling out first.
 */
final class DiscordRateBuckets {
    /** A webhook post: a line or a notice. */
    static final String ROUTE_WEBHOOK_POST = "webhook-post";
    /** A webhook rewriting its own post. */
    static final String ROUTE_WEBHOOK_EDIT = "webhook-edit";
    /** A webhook taking its own post back. */
    static final String ROUTE_WEBHOOK_DELETE = "webhook-delete";
    /** The bot's own reaction, put on or taken off. */
    static final String ROUTE_REACTION = "reaction";
    /** Bucket and lane pairs remembered at most. */
    static final int MAX_STATES = 256;

    /** The bucket Discord named for each route; the routes are the constants above. */
    private final Map<String, String> bucketByRoute = new HashMap<String, String>();
    /** When each spent bucket resets, by bucket (or route) and lane, least recently used first. */
    private final LinkedHashMap<String, Long> resets =
            new LinkedHashMap<String, Long>(16, 0.75F, true);
    /** When the bot's global limit lets it send again; 0 when it has not been hit. */
    private long globalResetMillis;

    /**
     * Learns from the reply to a request on {@code route} for
     * {@code major}: the route's bucket when the reply names it, and
     * whether that bucket is spent. A reply that says nothing changes
     * nothing.
     */
    void observe(String route, String major, DiscordRateLimit limit,
                 long nowMillis) {
        if (route == null || major == null || limit == null
                || limit == DiscordRateLimit.NONE) {
            return;
        }
        if (limit.bucket.length() > 0) {
            this.bucketByRoute.put(route, limit.bucket);
        }
        String key = key(route, major);
        if (limit.exhausted() && limit.resetAfterMillis > 0L) {
            this.resets.put(key, Long.valueOf(nowMillis + limit.resetAfterMillis));
            while (this.resets.size() > MAX_STATES) {
                Iterator<String> eldest = this.resets.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
        } else if (limit.remaining > 0) {
            this.resets.remove(key);
        }
    }

    /**
     * When a request on {@code route} for {@code major} may go: the
     * reset of its bucket while that bucket is spent, else 0.
     */
    long holdUntil(String route, String major, long nowMillis) {
        if (route == null || major == null || this.resets.isEmpty()) {
            return 0L;
        }
        String key = key(route, major);
        Long reset = this.resets.get(key);
        if (reset == null) {
            return 0L;
        }
        if (reset.longValue() <= nowMillis) {
            this.resets.remove(key);
            return 0L;
        }
        return reset.longValue();
    }

    /** Holds every request of the bot until {@code untilMillis}: its global limit was hit. */
    void holdGlobal(long untilMillis) {
        this.globalResetMillis = Math.max(this.globalResetMillis, untilMillis);
    }

    /** When a request of the bot may go under its global limit, or 0 for now. */
    long globalHoldUntil(long nowMillis) {
        return this.globalResetMillis > nowMillis ? this.globalResetMillis : 0L;
    }

    /** Test hook: bucket and lane pairs remembered. */
    int size() {
        return this.resets.size();
    }

    /** One bucket of one lane: by the route's hash when Discord named it, else by the route. */
    private String key(String route, String major) {
        String bucket = this.bucketByRoute.get(route);
        return (bucket == null ? "route " + route : "bucket " + bucket)
                + '\n' + major;
    }
}
