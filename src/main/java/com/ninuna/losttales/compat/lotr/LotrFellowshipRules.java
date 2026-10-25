package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRConfig;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.UUID;

/**
 * LOTR's own rules for fellowships, which ours keep: whether players may
 * make them, how many members one may hold, and how many one character may
 * lead. Each answers LOTR's setting. Where LOTR cannot be asked, players
 * may make fellowships and a fellowship holds our most members, so a fault
 * here never locks players out; a character may then lead one fellowship,
 * the least LOTR lets any character lead.
 */
public final class LotrFellowshipRules {
    /** The fellowships every character may lead, whatever its achievements. */
    public static final int LEAST_LEAD_LIMIT = 1;
    /** Middle-earth achievements that let a character lead one fellowship more. */
    private static final int ACHIEVEMENTS_PER_LEAD = 20;
    /** The id LOTR's data is made under to read a saved state: no account's. */
    private static final UUID SAVED_STATE_READER =
            UUID.fromString("5b8e0f43-2c1a-4d6e-9f70-3a1c2b4d5e6f");

    private LotrFellowshipRules() {}

    /** Whether players may make fellowships: LOTR's *Enable Fellowship creation*. */
    public static boolean creationEnabled() {
        try {
            return LOTRConfig.enableFellowshipCreation;
        } catch (LinkageError error) {
            LostTalesLog.warning("LOTR's fellowship creation setting could not be read: %s",
                    error.toString());
            return true;
        }
    }

    /**
     * How many members make a fellowship full: LOTR's *Fellowship maximum
     * size*, held within our own bounds; none set is our most.
     */
    public static int memberLimit() {
        try {
            int size = LOTRConfig.fellowshipMaxSize;
            return size < 0 ? Fellowship.MAX_MEMBERS : Fellowship.clampMemberLimit(size);
        } catch (LinkageError error) {
            LostTalesLog.warning("LOTR's fellowship size setting could not be read: %s",
                    error.toString());
            return Fellowship.MAX_MEMBERS;
        }
    }

    /**
     * LOTR's rule: one fellowship, and one more for every 20 achievements
     * earned in Middle-earth. A count below none counts as none.
     */
    public static int leadLimitFor(int middleEarthAchievements) {
        return LEAST_LEAD_LIMIT + Math.max(0, middleEarthAchievements) / ACHIEVEMENTS_PER_LEAD;
    }

    /**
     * How many fellowships the character a player plays may lead, as LOTR
     * counts it ({@link #leadLimitFor}) from the player's live data.
     */
    public static int leadLimit(EntityPlayer player) {
        if (player == null) {
            return LEAST_LEAD_LIMIT;
        }
        try {
            return leadLimitFor(LOTRLevelData.getData(player)
                    .getEarnedAchievements(LOTRDimension.MIDDLE_EARTH).size());
        } catch (RuntimeException exception) {
            LostTalesLog.warning("LOTR's achievements could not be counted for %s: %s",
                    player.getCommandSenderName(), exception.toString());
            return LEAST_LEAD_LIMIT;
        } catch (LinkageError error) {
            LostTalesLog.warning("LOTR's achievements could not be counted: %s",
                    error.toString());
            return LEAST_LEAD_LIMIT;
        }
    }

    /**
     * How many fellowships a character nobody plays may lead, counted as
     * LOTR would count it were the character played: from the LOTR
     * progression saved with it ({@link LotrProgressionStateAdapter}'s
     * keys), read into LOTR player data of its own. One where none is
     * saved or LOTR cannot read it.
     */
    public static int leadLimit(NBTTagCompound savedProgression) {
        if (savedProgression == null) {
            return LEAST_LEAD_LIMIT;
        }
        try {
            LOTRPlayerData saved = new LOTRPlayerData(SAVED_STATE_READER);
            NBTTagCompound full = new NBTTagCompound();
            saved.save(full);
            for (Object key : savedProgression.func_150296_c()) {
                String name = (String) key;
                full.setTag(name, savedProgression.getTag(name).copy());
            }
            saved.load(full);
            return leadLimitFor(earnedInMiddleEarth(saved));
        } catch (RuntimeException exception) {
            LostTalesLog.warning("A saved character's LOTR achievements could not be counted: %s",
                    exception.toString());
            return LEAST_LEAD_LIMIT;
        } catch (LinkageError error) {
            LostTalesLog.warning("A saved character's LOTR achievements could not be counted: %s",
                    error.toString());
            return LEAST_LEAD_LIMIT;
        }
    }

    /**
     * The achievements LOTR's {@code getEarnedAchievements} counts for
     * Middle-earth, for data no player holds: those of Middle-earth whose
     * enemy and ally factions the saved alignments meet, as LOTR's
     * {@code canPlayerEarn} asks of a played character.
     */
    private static int earnedInMiddleEarth(LOTRPlayerData saved) {
        int earned = 0;
        for (LOTRAchievement achievement : saved.getAchievements()) {
            if (achievement.getDimension() == LOTRDimension.MIDDLE_EARTH
                    && meets(saved, achievement.enemyFactions, true)
                    && meets(saved, achievement.allyFactions, false)) {
                earned++;
            }
        }
        return earned;
    }

    /**
     * Whether an achievement asks no faction of this kind, or the saved
     * alignment with one of those it names is an enemy's (none or below) or
     * an ally's (none or above).
     */
    private static boolean meets(LOTRPlayerData saved, List<LOTRFaction> factions,
                                 boolean enemies) {
        if (factions == null || factions.isEmpty()) {
            return true;
        }
        for (LOTRFaction faction : factions) {
            float alignment = saved.getAlignment(faction);
            if (enemies ? alignment <= 0.0F : alignment >= 0.0F) {
                return true;
            }
        }
        return false;
    }
}
