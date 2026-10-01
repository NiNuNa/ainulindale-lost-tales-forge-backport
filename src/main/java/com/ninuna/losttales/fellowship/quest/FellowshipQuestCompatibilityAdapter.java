package com.ninuna.losttales.fellowship.quest;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Isolates quest-system-specific progress rules from general fellowship membership.
 * Implementations must only mutate server-owned quest state and must leave
 * completion and rewards to the backing quest system.
 */
public interface FellowshipQuestCompatibilityAdapter {

    boolean isAvailable();

    void applyKillProgress(EntityPlayerMP participant, Entity victim,
            boolean shared);

    boolean applyTravelProgress(EntityPlayerMP participant, Entity source,
            boolean shared);
}
