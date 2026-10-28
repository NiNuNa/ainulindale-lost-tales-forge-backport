package com.ninuna.losttales.compat.lotr;

import java.util.UUID;

/** How the mod names a LOTR mini-quest: {@code lotr:miniquest/<uuid>}. */
public final class LotrQuestReference {
    public static final String PREFIX = "lotr:miniquest/";

    private LotrQuestReference() {}

    public static String create(UUID questId) {
        return questId == null ? "" : PREFIX + questId.toString();
    }

    public static boolean isLotrQuest(String reference) {
        return reference != null && reference.startsWith(PREFIX)
                && parse(reference) != null;
    }

    public static UUID parse(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(reference.substring(PREFIX.length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
