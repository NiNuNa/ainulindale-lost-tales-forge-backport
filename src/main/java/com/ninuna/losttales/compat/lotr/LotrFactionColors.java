package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.gui.style.LostTalesColors;
import lotr.common.fac.LOTRFaction;

/**
 * The one answer to "what colour is this faction" for everything the
 * chat draws in a faction's colour: a character's name, an NPC's name
 * over its speech and in its whispers, a travelling trader's notice, the
 * Faction tab. The colour is LOTR's own, {@code LOTRFaction#getFactionColor},
 * read through {@link LotrCharacterAdapter} by faction id or straight
 * off the faction when the entity is at hand, with two things decided
 * here and nowhere else: a faction that paints itself pure black — LOTR's
 * UNALIGNED, the factionless wanderers — reads in the palette's rose
 * beige instead, since black is unreadable on the chat and the muted
 * grey below it is the Server's own; and a faction
 * that cannot be resolved at all keeps the caller's fallback. Unaligned
 * by id is answered without asking LOTR, since the answer is decided
 * here.
 */
public final class LotrFactionColors {

    private LotrFactionColors() {}

    /** The colour of the faction with that id, or {@code fallback}. */
    public static int forFactionId(String factionId, int fallback) {
        if (LotrCharacterAdapter.UNALIGNED_FACTION_ID.equals(
                LotrCharacterAdapter.normalizeFactionId(factionId))) {
            return readable(0, fallback);
        }
        int color = LotrCharacterAdapter.getInstance().getFactionColor(factionId, fallback);
        return readable(color, fallback);
    }

    /**
     * The colour of the faction itself, or {@code fallback} when there is
     * none or LOTR cannot say.
     */
    public static int forFaction(LOTRFaction faction, int fallback) {
        if (faction == null) {
            return fallback & 0xFFFFFF;
        }
        try {
            return readable(faction.getFactionColor() & 0xFFFFFF, fallback);
        } catch (LinkageError incompatible) {
            return fallback & 0xFFFFFF;
        } catch (RuntimeException failed) {
            return fallback & 0xFFFFFF;
        }
    }

    /** Pure black is the one faction colour the chat cannot show; it reads rose beige. */
    private static int readable(int color, int fallback) {
        if ((color & 0xFFFFFF) == 0 && (fallback & 0xFFFFFF) != 0) {
            return LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
        }
        return color & 0xFFFFFF;
    }
}
