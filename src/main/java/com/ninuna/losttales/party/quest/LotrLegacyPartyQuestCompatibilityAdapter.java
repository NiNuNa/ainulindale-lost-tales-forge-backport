package com.ninuna.losttales.party.quest;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Explicit fail-closed boundary for LOTR Legacy quests.
 *
 * The recovered development dependency does not expose a verified, supported
 * quest progress API in this project. Reflection or copied LOTR internals are
 * intentionally not introduced. This adapter therefore reports unavailable
 * and normal LOTR/Fellowship quest behavior remains untouched.
 */
public final class LotrLegacyPartyQuestCompatibilityAdapter implements PartyQuestCompatibilityAdapter {

    @Override
    public String getId() {
        return "lotr_legacy";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void applySharedKillProgress(EntityPlayerMP participant, Entity victim) {
        // Unsupported objective types and external quest systems fall back to
        // their original individual behavior.
    }
}
