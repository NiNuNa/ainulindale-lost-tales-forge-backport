package com.ninuna.losttales.character.lore.ownership;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable world-level ownership state for one lore-character identity. */
public final class LoreCharacterOwnershipRecord {

    public static final int CURRENT_DATA_VERSION = 1;
    public static final int MAX_IDENTIFIER_LENGTH = 160;

    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final String loreCharacterId;
    private final UUID characterId;
    private final UUID ownerId;
    private final long revision;
    private final long createdAt;
    private final long lastClaimedAt;
    private final long lastReleasedAt;

    public LoreCharacterOwnershipRecord(
            String loreCharacterId,
            UUID characterId,
            UUID ownerId,
            long revision,
            long createdAt,
            long lastClaimedAt,
            long lastReleasedAt) {
        String normalizedLoreId = normalizeIdentifier(loreCharacterId);
        if (!isValidIdentifier(normalizedLoreId)) {
            throw new IllegalArgumentException(
                    "loreCharacterId must be a valid namespaced identifier");
        }
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        if (revision <= 0L || createdAt <= 0L || lastClaimedAt <= 0L
                || lastReleasedAt < 0L || createdAt > lastClaimedAt) {
            throw new IllegalArgumentException(
                    "ownership revision and timestamps are invalid");
        }
        if (ownerId != null && lastClaimedAt < lastReleasedAt) {
            throw new IllegalArgumentException(
                    "claimed ownership must be newer than its last release");
        }
        if (ownerId == null
                && (lastReleasedAt == 0L || lastReleasedAt < lastClaimedAt)) {
            throw new IllegalArgumentException(
                    "released ownership requires a current release timestamp");
        }
        this.loreCharacterId = normalizedLoreId;
        this.characterId = characterId;
        this.ownerId = ownerId;
        this.revision = revision;
        this.createdAt = createdAt;
        this.lastClaimedAt = lastClaimedAt;
        this.lastReleasedAt = lastReleasedAt;
    }

    public static LoreCharacterOwnershipRecord firstClaim(
            String loreCharacterId, UUID characterId,
            UUID ownerId, long timestamp) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        long safeTimestamp = Math.max(1L, timestamp);
        return new LoreCharacterOwnershipRecord(
                loreCharacterId, characterId, ownerId,
                1L, safeTimestamp, safeTimestamp, 0L);
    }

    public String getLoreCharacterId() {
        return this.loreCharacterId;
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public boolean isClaimed() {
        return this.ownerId != null;
    }

    public long getRevision() {
        return this.revision;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public long getLastClaimedAt() {
        return this.lastClaimedAt;
    }

    public long getLastReleasedAt() {
        return this.lastReleasedAt;
    }

    public LoreCharacterOwnershipRecord claim(UUID newOwnerId, long timestamp) {
        if (isClaimed()) {
            throw new IllegalStateException("lore character is already claimed");
        }
        if (newOwnerId == null) {
            throw new IllegalArgumentException("newOwnerId must not be null");
        }
        long nextRevision = nextRevision();
        long claimedAt = Math.max(Math.max(1L, timestamp), this.lastReleasedAt);
        return new LoreCharacterOwnershipRecord(
                this.loreCharacterId, this.characterId, newOwnerId,
                nextRevision, this.createdAt, claimedAt, this.lastReleasedAt);
    }

    public LoreCharacterOwnershipRecord release(UUID releasingOwnerId,
                                                long timestamp) {
        if (!isClaimed()) {
            throw new IllegalStateException("lore character is already released");
        }
        if (releasingOwnerId == null || !releasingOwnerId.equals(this.ownerId)) {
            throw new IllegalArgumentException(
                    "only the current owner may release a lore character");
        }
        long nextRevision = nextRevision();
        long releasedAt = Math.max(Math.max(1L, timestamp), this.lastClaimedAt);
        return new LoreCharacterOwnershipRecord(
                this.loreCharacterId, this.characterId, null,
                nextRevision, this.createdAt, this.lastClaimedAt, releasedAt);
    }

    public static String normalizeIdentifier(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidIdentifier(String value) {
        return value != null && value.length() > 0
                && value.length() <= MAX_IDENTIFIER_LENGTH
                && IDENTIFIER.matcher(value).matches();
    }

    private long nextRevision() {
        if (this.revision == Long.MAX_VALUE) {
            throw new IllegalStateException("ownership revision is exhausted");
        }
        return this.revision + 1L;
    }
}
