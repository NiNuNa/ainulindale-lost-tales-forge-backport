package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Discord's rate-limit headers, read without ever throwing: what cannot
 * be read holds nothing back.
 */
public final class DiscordRateLimitTest {

    @Test
    public void theResetIsReadInMillisecondsRoundedUp() {
        assertEquals(1234L, DiscordRateLimit.parse(null, "1.234", null, null, null)
                .resetAfterMillis);
        assertEquals(1L, DiscordRateLimit.parse(null, "0.0001", null, null, null)
                .resetAfterMillis);
        assertEquals(2000L, DiscordRateLimit.parse(null, "2", null, null, null)
                .resetAfterMillis);
        assertEquals(1500L, DiscordRateLimit.parse(null, " 1.5 ", null, null, null)
                .resetAfterMillis);
    }

    @Test
    public void theCountLeftSaysWhetherTheBucketIsSpent() {
        DiscordRateLimit spent = DiscordRateLimit.parse("0", "1.5", "abc123", null, null);
        assertTrue(spent.exhausted());
        assertEquals(0, spent.remaining);
        assertEquals("abc123", spent.bucket);
        DiscordRateLimit left = DiscordRateLimit.parse("4", "1.5", "abc123", null, null);
        assertFalse(left.exhausted());
        assertEquals(4, left.remaining);
        DiscordRateLimit unsaid = DiscordRateLimit.parse(null, "1", null, null, null);
        assertEquals(-1, unsaid.remaining);
        assertFalse(unsaid.exhausted());
    }

    @Test
    public void whatCannotBeReadIsNone() {
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse(null, null, null, null, null));
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse("", " ", "", "", ""));
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse(null, "abc", null, null, null));
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse(null, "NaN", null, null, null));
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse(null, "-1", null, null, null));
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse("0", "1e3", null, null, null));
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse("-1", "1", null, null, null));
        assertSame(DiscordRateLimit.NONE, DiscordRateLimit.parse("abc", "1", "abc123", null, null));
    }

    @Test
    public void aHugeResetIsClampedToTheLanesLongestPause() {
        assertEquals(DiscordOutboundLanes.MAX_RETRY_MILLIS, DiscordRateLimit.parse(
                "0", "99999999999999999999", null, null, null).resetAfterMillis);
        assertEquals(DiscordOutboundLanes.MAX_RETRY_MILLIS, DiscordRateLimit.parse(
                "0", "3600", null, null, null).resetAfterMillis);
    }

    @Test
    public void aBucketThatIsNotAPlainHashIsLeftEmpty() {
        assertEquals("", DiscordRateLimit.parse("1", "1", "has space", null, null).bucket);
        assertEquals("", DiscordRateLimit.parse("1", "1", "a/b", null, null).bucket);
        StringBuilder tooLong = new StringBuilder();
        for (int index = 0; index <= DiscordRateLimit.MAX_BUCKET_LENGTH; index++) {
            tooLong.append('a');
        }
        assertEquals("", DiscordRateLimit.parse("1", "1", tooLong.toString(), null, null)
                .bucket);
        assertEquals("abc-DEF_123", DiscordRateLimit.parse("1", "1", "abc-DEF_123", null, null)
                .bucket);
    }

    @Test
    public void theGlobalLimitIsReadFromEitherHeader() {
        assertTrue(DiscordRateLimit.parse(null, null, null, "True", null).global);
        assertTrue(DiscordRateLimit.parse(null, "1", null, null, "global").global);
        assertTrue(DiscordRateLimit.parse(null, "1", null, null, "GLOBAL").global);
        DiscordRateLimit user = DiscordRateLimit.parse("0", "1", null, null, "user");
        assertFalse(user.global);
        assertEquals("user", user.scope);
        assertFalse(DiscordRateLimit.parse("0", "1", null, "false", null).global);
    }
}
