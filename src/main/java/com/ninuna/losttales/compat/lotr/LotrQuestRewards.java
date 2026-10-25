package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.util.Locale;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRMod;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRAlignmentValues;
import lotr.common.fac.LOTRFaction;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;

/**
 * What a Lost Tales quest pays in LOTR's own coin (QUEST-33): alignment
 * with a faction, through the call LOTR's own mini-quests pay it with, and
 * LOTR's coins, counted out as LOTR counts them, which the quest hands
 * over as items so what finds no room waits (QUEST-44). A faction is named
 * as the mod names it everywhere, {@code lotr:gondor}.
 */
public final class LotrQuestRewards {
    private LotrQuestRewards() {}

    /**
     * Whether the id names a faction a player can hold alignment with:
     * one of LOTR's playable factions, never Hostile or Unaligned.
     */
    public static boolean isAlignmentFaction(String factionId) {
        LOTRFaction faction = faction(factionId);
        return faction != null && faction.isPlayableAlignmentFaction();
    }

    /**
     * Adds {@code amount} alignment with the faction, as a finished LOTR
     * mini-quest adds it: LOTR's own bonus, popup and rank notices.
     * Answers whether it was paid.
     */
    public static boolean addAlignment(EntityPlayerMP player, String factionId,
                                       int amount) {
        LOTRFaction faction = faction(factionId);
        if (player == null || faction == null || amount <= 0
                || !faction.isPlayableAlignmentFaction()) {
            return false;
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            if (data == null) {
                return false;
            }
            data.addAlignment(player, LOTRAlignmentValues.createMiniquestBonus(
                    (float)amount), faction, player);
            return true;
        } catch (RuntimeException unpaid) {
            FMLLog.warning("[%s] Could not pay %s alignment with %s to %s: %s",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(amount), factionId,
                    player.getCommandSenderName(), unpaid.toString());
            return false;
        }
    }

    /**
     * The id LOTR registers its coins under, {@code lotr:item.silverCoin};
     * empty where LOTR's coin cannot be read.
     */
    public static String coinItemId() {
        try {
            Object name = Item.itemRegistry.getNameForObject(LOTRMod.silverCoin);
            return name == null ? "" : String.valueOf(name);
        } catch (RuntimeException unreadable) {
            return "";
        } catch (LinkageError incompatible) {
            return "";
        }
    }

    /**
     * LOTR's coins worth {@code value}, counted out as LOTR's own
     * {@code giveCoins} counts them, the largest coin first: how many of
     * each, by the coin's meta. Null where LOTR's coin values cannot be
     * read, or for no value.
     */
    public static int[] coinCounts(int value) {
        int[] values;
        try {
            values = LOTRItemCoin.values;
        } catch (LinkageError incompatible) {
            return null;
        }
        return countOut(values, value);
    }

    /** {@code value} counted out in coins of {@code values}, the largest first; null for none. */
    static int[] countOut(int[] values, int value) {
        if (values == null || values.length == 0 || value <= 0) {
            return null;
        }
        int[] counts = new int[values.length];
        int left = value;
        for (int meta = values.length - 1; meta >= 0; meta--) {
            if (values[meta] <= 0) {
                return null;
            }
            counts[meta] = left / values[meta];
            left -= counts[meta] * values[meta];
        }
        return left == 0 ? counts : null;
    }

    /** Gives the player coins worth {@code value}, in LOTR's own coins; what does not fit drops. */
    public static boolean giveCoins(EntityPlayerMP player, int value) {
        if (player == null || value <= 0) {
            return false;
        }
        try {
            LOTRItemCoin.giveCoins(value, player);
            return true;
        } catch (RuntimeException unpaid) {
            FMLLog.warning("[%s] Could not give %s coins to %s: %s",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(value),
                    player.getCommandSenderName(), unpaid.toString());
            return false;
        }
    }

    /**
     * The faction's name in this side's language, or empty where the id
     * names none; a client words it in its own language.
     */
    public static String factionName(String factionId) {
        LOTRFaction faction = faction(factionId);
        if (faction == null) {
            return "";
        }
        try {
            String name = faction.factionName();
            return name == null ? "" : name;
        } catch (RuntimeException unnamed) {
            return "";
        }
    }

    /** The faction the id names ({@code lotr:gondor}), or null. */
    private static LOTRFaction faction(String factionId) {
        String id = factionId == null ? "" : factionId.trim().toLowerCase(Locale.ROOT);
        if (!id.startsWith(LotrCharacterAdapter.ID_PREFIX)) {
            return null;
        }
        String code = id.substring(LotrCharacterAdapter.ID_PREFIX.length());
        try {
            for (LOTRFaction faction : LOTRFaction.values()) {
                String name = faction == null ? null : faction.codeName();
                if (name != null && name.toLowerCase(Locale.ROOT).equals(code)) {
                    return faction;
                }
            }
        } catch (LinkageError incompatible) {
            return null;
        }
        return null;
    }
}
