package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.LOTRConfig;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import net.minecraft.entity.player.EntityPlayer;

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
     * How many fellowships the character a player plays may lead, as LOTR
     * counts it: one, and one more for every 20 achievements earned in
     * Middle-earth. Achievements are each character's own.
     */
    public static int leadLimit(EntityPlayer player) {
        if (player == null) {
            return LEAST_LEAD_LIMIT;
        }
        try {
            int achievements = LOTRLevelData.getData(player)
                    .getEarnedAchievements(LOTRDimension.MIDDLE_EARTH).size();
            return LEAST_LEAD_LIMIT + achievements / ACHIEVEMENTS_PER_LEAD;
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
}
