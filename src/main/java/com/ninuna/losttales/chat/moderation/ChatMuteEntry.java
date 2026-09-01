package com.ninuna.losttales.chat.moderation;

import java.util.UUID;

/**
 * One server mute: the account it silences, for how long, and the words
 * the operator gave for it. Keyed by account rather than character so no
 * character switch or rename slips past it; the names carried are the
 * last known ones, kept for listing and never used to decide anything.
 * Times are wall-clock epoch milliseconds, so a timed mute keeps running
 * while the server is down; {@link #EXPIRES_NEVER} marks a permanent one.
 */
public final class ChatMuteEntry {

    public static final int CURRENT_DATA_VERSION = 1;
    public static final long EXPIRES_NEVER = 0L;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_REASON_LENGTH = 256;

    private final UUID accountId;
    private final String accountName;
    private final String mutedByName;
    private final String reason;
    private final long issuedAtMillis;
    private final long expiresAtMillis;

    public ChatMuteEntry(UUID accountId, String accountName,
                         String mutedByName, String reason,
                         long issuedAtMillis, long expiresAtMillis) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (expiresAtMillis < 0L) {
            throw new IllegalArgumentException(
                    "expiresAtMillis must be EXPIRES_NEVER or positive");
        }
        this.accountId = accountId;
        this.accountName = bounded(accountName, MAX_NAME_LENGTH);
        this.mutedByName = bounded(mutedByName, MAX_NAME_LENGTH);
        this.reason = bounded(reason, MAX_REASON_LENGTH);
        this.issuedAtMillis = issuedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public UUID getAccountId() { return this.accountId; }
    public String getAccountName() { return this.accountName; }
    public String getMutedByName() { return this.mutedByName; }
    public String getReason() { return this.reason; }
    public long getIssuedAtMillis() { return this.issuedAtMillis; }
    public long getExpiresAtMillis() { return this.expiresAtMillis; }

    public boolean isPermanent() {
        return this.expiresAtMillis == EXPIRES_NEVER;
    }

    public boolean isExpired(long nowMillis) {
        return !isPermanent() && nowMillis >= this.expiresAtMillis;
    }

    private static String bounded(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxLength ? trimmed
                : trimmed.substring(0, maxLength);
    }
}
