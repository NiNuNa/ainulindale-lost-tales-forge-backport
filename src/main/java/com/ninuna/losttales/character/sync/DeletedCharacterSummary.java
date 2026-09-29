package com.ninuna.losttales.character.sync;

import java.util.UUID;

/**
 * One of a player's deleted characters the server still keeps: enough to
 * name it at the foot of the roster, draw its head, and say how long it
 * can still be restored.
 */
public final class DeletedCharacterSummary {

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final UUID characterId;
    private final String name;
    private final String raceId;
    private final String skinId;
    private final int daysLeft;

    public DeletedCharacterSummary(UUID characterId, String name,
                                   String raceId, String skinId,
                                   int daysLeft) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        this.characterId = characterId;
        this.name = name == null ? "" : name;
        this.raceId = raceId == null ? "" : raceId;
        this.skinId = skinId == null ? "" : skinId;
        this.daysLeft = Math.max(0, daysLeft);
    }

    /**
     * Whole days left before the server purges it, counted up: a
     * character with an hour left has one day left.
     */
    public static int daysLeft(long now, long purgeAfter) {
        long left = purgeAfter - now;
        if (left <= 0L) {
            return 0;
        }
        long days = (left + DAY_MILLIS - 1L) / DAY_MILLIS;
        return days > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) days;
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public String getName() {
        return this.name;
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getSkinId() {
        return this.skinId;
    }

    /** Whole days before the server purges it. */
    public int getDaysLeft() {
        return this.daysLeft;
    }
}
