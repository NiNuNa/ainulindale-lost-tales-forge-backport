package com.ninuna.losttales.party.quest;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Isolates quest-system-specific progress rules from general party membership.
 * Implementations must only mutate server-owned quest state and must leave
 * completion and rewards to the backing quest system.
 */
public interface PartyQuestCompatibilityAdapter {

    String getId();

    boolean isAvailable();

    void applySharedKillProgress(EntityPlayerMP participant, Entity victim);
}
