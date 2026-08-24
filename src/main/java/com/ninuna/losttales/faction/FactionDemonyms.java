package com.ninuna.losttales.faction;

import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import java.util.Locale;
import net.minecraft.util.StatCollector;

/**
 * What the people of a faction are called, as opposed to what the
 * faction itself is called: a Lothlórien character is a
 * <em>Galadhrim</em> Miner, not a Lothlórien Miner. LOTR exposes no such
 * field — its faction strings are the realm's name
 * ({@code lotr.faction.LOTHLORIEN.name}, "Lothlórien") and a plural
 * collective ({@code .entity}, "Elves of Lothlórien"), neither of which
 * reads as the adjective a title needs — so the exceptions are a small
 * localizable table of our own, keyed by the faction's code name:
 * {@code losttales.faction.demonym.LOTHLORIEN=Galadhrim}. It follows
 * LOTR's own NPC naming, which is where players have already met these
 * words ("Galadhrim Warrior", "Dúnedain Blacksmith", "Dalish Baker").
 *
 * <p>A faction with no entry keeps its display name, which is right
 * wherever the realm's name is also the adjective (Gondor, Mordor,
 * Dorwinion), so only the exceptions are listed and a faction added
 * later needs nothing.</p>
 */
public final class FactionDemonyms {
    private static final String KEY_PREFIX = "losttales.faction.demonym.";

    private FactionDemonyms() {}

    /**
     * The name for the people of a faction: the configured demonym, or
     * {@code factionDisplayName} when the faction has none. Never null.
     *
     * @param factionId          a stable faction id ({@code lotr:gondor})
     * @param factionDisplayName the faction's own display name, already
     *                           stripped of formatting codes
     */
    public static String of(String factionId, String factionDisplayName) {
        String fallback = factionDisplayName == null
                ? "" : factionDisplayName.trim();
        String key = keyFor(factionId);
        if (key.length() == 0) {
            return fallback;
        }
        String demonym = StatCollector.translateToLocal(key);
        // An absent key translates to itself; a blank entry is no entry.
        if (demonym == null || demonym.length() == 0
                || demonym.equals(key)) {
            return fallback;
        }
        demonym = demonym.trim();
        return demonym.length() == 0 ? fallback : demonym;
    }

    /**
     * The language key a faction id would use, or empty when the id is
     * not one Lost Tales recognises. The code name is the part after
     * {@code lotr:}, upper-cased, as LOTR keys its own faction strings.
     */
    static String keyFor(String factionId) {
        String normalized = LotrCharacterAdapter.normalizeFactionId(factionId);
        if (normalized.length() == 0) {
            return "";
        }
        String codeName = normalized.substring(
                LotrCharacterAdapter.ID_PREFIX.length());
        return KEY_PREFIX + codeName.toUpperCase(Locale.ROOT);
    }
}
