package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRank;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Locale;

/**
 * Whether the identity a player is playing has reached a LOTR faction
 * rank: the source a config chat role can be granted by. The faction is
 * named by its LOTR code name ({@code GONDOR}), the rank by its code
 * name ({@code gondor.knight}) or by an alignment number; LOTR's own
 * player data answers, which the switch swaps per character. A rank LOTR
 * does not know grants nothing and is said so once per name.
 */
public final class LotrFactionRankAdapter {

    private static final java.util.Set<String> WARNED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private LotrFactionRankAdapter() {}

    public static boolean hasRank(EntityPlayerMP player, String factionName, String rankName) {
        if (player == null || factionName == null || rankName == null) {
            return false;
        }
        try {
            LOTRFaction faction = LOTRFaction.forName(factionName.trim().toUpperCase(Locale.ROOT));
            if (faction == null) {
                warnOnce("faction " + factionName);
                return false;
            }
            LOTRPlayerData data = LOTRLevelData.getData(player);
            if (data == null) {
                return false;
            }
            float alignment = data.getAlignment(faction);
            String wanted = rankName.trim().toLowerCase(Locale.ROOT);
            try {
                return alignment >= Float.parseFloat(wanted);
            } catch (NumberFormatException notANumber) {
                // A named rank: the player's rank must be at or above it.
            }
            LOTRFactionRank required = rankNamed(faction, wanted);
            if (required == null) {
                warnOnce("rank " + rankName + " of " + factionName);
                return false;
            }
            LOTRFactionRank held = faction.getRank(alignment);
            return held != null && held.compareTo(required) >= 0;
        } catch (LinkageError error) {
            warnOnce("LOTR rank API (" + error + ")");
            return false;
        } catch (RuntimeException exception) {
            warnOnce("LOTR rank lookup (" + exception + ")");
            return false;
        }
    }

    /** The faction's rank with that code name, walking up from the pledge rank. */
    private static LOTRFactionRank rankNamed(LOTRFaction faction, String codeName) {
        LOTRFactionRank rank = faction.getRank(-1.0E9F);
        for (int step = 0; rank != null && step < 64; step++) {
            if (codeName.equals(rank.getCodeName().toLowerCase(Locale.ROOT))
                    || codeName.equals(rank.getCodeFullName().toLowerCase(Locale.ROOT))) {
                return rank;
            }
            LOTRFactionRank above = faction.getRankAbove(rank);
            if (above == null || above == rank) {
                break;
            }
            rank = above;
        }
        return null;
    }

    private static void warnOnce(String what) {
        if (WARNED.add(what)) {
            FMLLog.warning("[%s] A chat role names %s, which LOTR does not know; "
                    + "it grants nothing", LostTalesMetaData.MOD_ID, what);
        }
    }
}
