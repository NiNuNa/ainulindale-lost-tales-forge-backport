package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A bucket Discord said is spent holds its lane's requests on every
 * route in it until the bucket resets, and nothing else.
 */
public final class DiscordRateBucketsTest {
    private static final String LANE_A = "https://discord.com/api/webhooks/1/a";
    private static final String LANE_B = "https://discord.com/api/webhooks/2/b";

    private static DiscordRateLimit spent(String bucket, String resetSeconds) {
        return DiscordRateLimit.parse("0", resetSeconds, bucket, null, null);
    }

    private static DiscordRateLimit left(String bucket, int remaining) {
        return DiscordRateLimit.parse(String.valueOf(remaining), "1", bucket, null, null);
    }

    @Test
    public void aSpentBucketHoldsItsLaneUntilItResets() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A,
                spent("post", "2"), 1000L);
        assertEquals(3000L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_A, 1000L));
        assertEquals(3000L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_A, 2999L));
        assertEquals("free at its reset", 0L, buckets.holdUntil(
                DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A, 3000L));
    }

    @Test
    public void aReplyWithRequestsLeftReleasesTheBucket() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A,
                spent("post", "2"), 1000L);
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A,
                left("post", 3), 1500L);
        assertEquals(0L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_A, 1500L));
    }

    @Test
    public void everyLaneHasBucketsOfItsOwn() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A,
                spent("post", "2"), 0L);
        assertEquals(2000L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_A, 0L));
        assertEquals(0L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_B, 0L));
    }

    @Test
    public void routesInOneBucketShareItsHold() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_DELETE, LANE_A,
                left("message", 4), 0L);
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_EDIT, LANE_A,
                spent("message", "1"), 0L);
        assertEquals(1000L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_DELETE,
                LANE_A, 0L));
        // A route in another bucket, and one never answered, are free.
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A,
                left("post", 4), 0L);
        assertEquals(0L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_A, 0L));
        assertEquals(0L, buckets.holdUntil(DiscordRateBuckets.ROUTE_REACTION,
                LANE_A, 0L));
    }

    @Test
    public void aRouteWhoseBucketIsUnnamedIsHeldByItself() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        buckets.observe(DiscordRateBuckets.ROUTE_REACTION, LANE_A,
                DiscordRateLimit.parse("0", "1", null, null, null), 0L);
        assertEquals(1000L, buckets.holdUntil(DiscordRateBuckets.ROUTE_REACTION,
                LANE_A, 0L));
        assertEquals(0L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_A, 0L));
    }

    @Test
    public void aReplyThatSaysNothingChangesNothing() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A,
                DiscordRateLimit.NONE, 0L);
        buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, LANE_A, null, 0L);
        assertEquals(0, buckets.size());
        assertEquals(0L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                LANE_A, 0L));
    }

    @Test
    public void theGlobalLimitHoldsUntilItsResetAndOnlyGrows() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        assertEquals(0L, buckets.globalHoldUntil(0L));
        buckets.holdGlobal(5000L);
        assertEquals(5000L, buckets.globalHoldUntil(1000L));
        buckets.holdGlobal(2000L);
        assertEquals(5000L, buckets.globalHoldUntil(1000L));
        assertEquals(0L, buckets.globalHoldUntil(5000L));
    }

    @Test
    public void theLeastRecentlyUsedBucketFallsOut() {
        DiscordRateBuckets buckets = new DiscordRateBuckets();
        for (int lane = 0; lane <= DiscordRateBuckets.MAX_STATES; lane++) {
            buckets.observe(DiscordRateBuckets.ROUTE_WEBHOOK_POST, "lane " + lane,
                    spent("post", "10"), 0L);
        }
        assertEquals(DiscordRateBuckets.MAX_STATES, buckets.size());
        assertEquals(0L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                "lane 0", 0L));
        assertEquals(10000L, buckets.holdUntil(DiscordRateBuckets.ROUTE_WEBHOOK_POST,
                "lane " + DiscordRateBuckets.MAX_STATES, 0L));
    }
}
