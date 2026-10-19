package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Who a LOTR NPC is to a character, as quests read it (QUEST-35): one of
 * LOTR's NPCs at all, and one of a faction the character stands against,
 * whose alignment with it is below zero, as LOTR's own NPCs decide whom
 * to attack.
 */
public final class LotrNpcRelations {
    private LotrNpcRelations() {}

    /** Whether the creature is one of LOTR's NPCs. */
    public static boolean isNpc(Entity entity) {
        try {
            return entity instanceof LOTREntityNPC;
        } catch (LinkageError incompatible) {
            return false;
        }
    }

    /**
     * Whether the creature is a LOTR NPC of a faction the player's
     * character stands against now: alignment with it below zero, LOTR's
     * own hostile creatures among them.
     */
    public static boolean isEnemyOf(Entity entity, EntityPlayer player) {
        if (player == null || !isNpc(entity)) {
            return false;
        }
        try {
            LOTRFaction faction = ((LOTREntityNPC)entity).getFaction();
            LOTRPlayerData data = LOTRLevelData.getData(player);
            return faction != null && data != null
                    && data.getAlignment(faction) < 0.0F;
        } catch (RuntimeException unreadable) {
            return false;
        } catch (LinkageError incompatible) {
            return false;
        }
    }
}
