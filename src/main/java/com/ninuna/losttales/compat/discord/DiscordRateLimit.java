package com.ninuna.losttales.compat.discord;

import java.util.Locale;

/**
 * What the headers of one Discord reply say of the rate limit it was
 * answered under: how many requests its bucket has left, how long until
 * the bucket resets, which bucket the route is in, and whether the limit
 * that answered is the bot's global one. Immutable. Read from the header
 * text without ever throwing: a header that is there and cannot be read
 * makes the whole limit {@link #NONE}, so a garbled reply never holds
 * anything back.
 */
final class DiscordRateLimit {
    /** Discord said nothing, or nothing that could be read. */
    static final DiscordRateLimit NONE =
            new DiscordRateLimit(-1, 0L, "", false, "");
    /** The longest bucket hash kept; Discord's are far shorter. */
    static final int MAX_BUCKET_LENGTH = 64;
    /** The longest reset kept: the lanes' own longest pause. */
    static final long MAX_RESET_MILLIS = DiscordOutboundLanes.MAX_RETRY_MILLIS;
    /** A header longer than this is no number Discord sends. */
    private static final int MAX_NUMBER_LENGTH = 32;
    /** Past this many seconds a reset is clamped whatever follows. */
    private static final long SATURATED_SECONDS = 1000000L;
    /** The longest scope kept; Discord's are user, global and shared. */
    private static final int MAX_SCOPE_LENGTH = 16;

    /** Requests left in the bucket; -1 when Discord did not say. */
    final int remaining;
    /** Until the bucket resets, in milliseconds rounded up; 0 when not said. */
    final long resetAfterMillis;
    /** The hash naming the route's bucket; empty when not said. */
    final String bucket;
    /** Whether the limit that answered is the bot's own, across every route. */
    final boolean global;
    /** Which limit a 429 was, in lower case; empty when not said. */
    final String scope;

    private DiscordRateLimit(int remaining, long resetAfterMillis,
                             String bucket, boolean global, String scope) {
        this.remaining = remaining;
        this.resetAfterMillis = resetAfterMillis;
        this.bucket = bucket;
        this.global = global;
        this.scope = scope;
    }

    /**
     * Reads the X-RateLimit-Remaining, -Reset-After, -Bucket, -Global
     * and -Scope headers, each null when the reply did not carry it. The
     * reset is seconds with a fraction, rounded up to the millisecond and
     * clamped to {@link #MAX_RESET_MILLIS}; a bucket that is not a plain
     * hash of at most {@link #MAX_BUCKET_LENGTH} characters is left
     * empty. The limit is global when the Global header is true or the
     * scope is global.
     */
    static DiscordRateLimit parse(String remaining, String resetAfter,
                                  String bucket, String global, String scope) {
        int left = -1;
        String remainingText = trimmed(remaining);
        if (remainingText.length() > 0) {
            left = parseCount(remainingText);
            if (left < 0) {
                return NONE;
            }
        }
        long reset = 0L;
        String resetText = trimmed(resetAfter);
        if (resetText.length() > 0) {
            reset = parseSeconds(resetText);
            if (reset < 0L) {
                return NONE;
            }
        }
        String hash = cleanBucket(bucket);
        String scopeName = cleanScope(scope);
        boolean isGlobal = "true".equalsIgnoreCase(trimmed(global))
                || "global".equals(scopeName);
        if (left < 0 && reset == 0L && hash.length() == 0 && !isGlobal
                && scopeName.length() == 0) {
            return NONE;
        }
        return new DiscordRateLimit(left, reset, hash, isGlobal, scopeName);
    }

    /** Whether the bucket has nothing left until it resets. */
    boolean exhausted() {
        return this.remaining == 0;
    }

    private static String trimmed(String text) {
        return text == null ? "" : text.trim();
    }

    /** A count of whole requests; -1 for anything else. */
    private static int parseCount(String text) {
        if (text.length() > MAX_NUMBER_LENGTH) {
            return -1;
        }
        long count = 0L;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c < '0' || c > '9') {
                return -1;
            }
            count = Math.min(Integer.MAX_VALUE, count * 10L + (c - '0'));
        }
        return (int)count;
    }

    /**
     * Seconds with an optional fraction, as Discord writes them, in
     * milliseconds rounded up; -1 for anything else, a sign, an exponent
     * and NaN among them.
     */
    private static long parseSeconds(String text) {
        if (text.length() > MAX_NUMBER_LENGTH) {
            return -1L;
        }
        long seconds = 0L;
        long millis = 0L;
        int fractionDigits = 0;
        boolean wholeDigits = false;
        boolean point = false;
        boolean beyondMillis = false;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '.' && !point) {
                point = true;
                continue;
            }
            if (c < '0' || c > '9') {
                return -1L;
            }
            int digit = c - '0';
            if (!point) {
                wholeDigits = true;
                seconds = Math.min(SATURATED_SECONDS, seconds * 10L + digit);
            } else if (fractionDigits < 3) {
                millis = millis * 10L + digit;
                fractionDigits++;
            } else if (digit != 0) {
                // Anything past the millisecond rounds it up.
                beyondMillis = true;
            }
        }
        if (!wholeDigits) {
            return -1L;
        }
        for (; fractionDigits < 3; fractionDigits++) {
            millis *= 10L;
        }
        return Math.min(MAX_RESET_MILLIS,
                seconds * 1000L + millis + (beyondMillis ? 1L : 0L));
    }

    /** A plain hash of letters, digits, '_' and '-'; empty for anything else. */
    private static String cleanBucket(String bucket) {
        String text = trimmed(bucket);
        if (text.length() > MAX_BUCKET_LENGTH) {
            return "";
        }
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            boolean plain = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!plain) {
                return "";
            }
        }
        return text;
    }

    /** A scope of plain letters, lower-cased; empty for anything else. */
    private static String cleanScope(String scope) {
        String text = trimmed(scope).toLowerCase(Locale.ROOT);
        if (text.length() > MAX_SCOPE_LENGTH) {
            return "";
        }
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c < 'a' || c > 'z') {
                return "";
            }
        }
        return text;
    }
}
