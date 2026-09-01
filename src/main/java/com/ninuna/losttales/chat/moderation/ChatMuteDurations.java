package com.ninuna.losttales.chat.moderation;

import java.util.Locale;

/**
 * The mute commands' duration grammar and the wording notices use for
 * what is left of one. A duration is a count and a unit — {@code 30s},
 * {@code 15m}, {@code 2h}, {@code 7d} — and nothing else reads as one,
 * so a reason beginning with an ordinary word never parses as time.
 */
public final class ChatMuteDurations {

    /** Nothing parsed: the token is not a duration. */
    public static final long NOT_A_DURATION = -1L;
    /** Longest expressible mute; anything above reads as permanent. */
    public static final long MAX_MILLIS = 365L * 24L * 60L * 60L * 1000L;

    private ChatMuteDurations() {}

    /**
     * The token as milliseconds, or {@link #NOT_A_DURATION} when it is
     * not one. A count of zero is no duration, and one past a year is
     * capped at {@link #MAX_MILLIS} — mute permanently instead.
     */
    public static long parse(String token) {
        String trimmed = token == null ? ""
                : token.trim().toLowerCase(Locale.ROOT);
        if (trimmed.length() < 2) {
            return NOT_A_DURATION;
        }
        char unit = trimmed.charAt(trimmed.length() - 1);
        long perUnit;
        if (unit == 's') {
            perUnit = 1000L;
        } else if (unit == 'm') {
            perUnit = 60L * 1000L;
        } else if (unit == 'h') {
            perUnit = 60L * 60L * 1000L;
        } else if (unit == 'd') {
            perUnit = 24L * 60L * 60L * 1000L;
        } else {
            return NOT_A_DURATION;
        }
        String digits = trimmed.substring(0, trimmed.length() - 1);
        if (digits.length() > 9) {
            return MAX_MILLIS;
        }
        long count = 0L;
        for (int index = 0; index < digits.length(); index++) {
            char digit = digits.charAt(index);
            if (digit < '0' || digit > '9') {
                return NOT_A_DURATION;
            }
            count = count * 10L + (digit - '0');
        }
        if (count == 0L) {
            return NOT_A_DURATION;
        }
        long millis = count * perUnit;
        return millis > MAX_MILLIS ? MAX_MILLIS : millis;
    }

    /**
     * How long remains, in the largest unit and the one below it —
     * {@code 2d 5h}, {@code 3h 12m}, {@code 45m 30s}, {@code 20s}.
     * Anything under a second reads as {@code 1s}: a mute about to end
     * is still a mute.
     */
    public static String formatRemaining(long remainingMillis) {
        long seconds = (remainingMillis + 999L) / 1000L;
        if (seconds < 1L) {
            seconds = 1L;
        }
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secondsLeft = seconds % 60L;
        if (days > 0L) {
            return hours > 0L ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0L) {
            return minutes > 0L ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0L) {
            return secondsLeft > 0L
                    ? minutes + "m " + secondsLeft + "s" : minutes + "m";
        }
        return secondsLeft + "s";
    }
}
