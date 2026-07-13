package com.ninuna.losttales.party.quest;

import com.ninuna.losttales.quest.LostTalesQuestManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/** Conservative adapter for Lost Tales' own quest definitions. */
public final class LostTalesPartyQuestCompatibilityAdapter implements PartyQuestCompatibilityAdapter {

    @Override
    public String getId() {
        return "losttales";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void applySharedKillProgress(EntityPlayerMP participant, Entity victim) {
        LostTalesQuestManager.handleEntityKilled(participant, victim);
    }
}
