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
    public void applyKillProgress(EntityPlayerMP participant, Entity victim,
            boolean shared) {
        LostTalesQuestManager.handleEntityKilled(
                participant, victim, shared);
    }

    @Override
    public boolean applyTravelProgress(EntityPlayerMP participant,
            Entity source, boolean shared) {
        return LostTalesQuestManager.handleTravelProgress(
                participant, source, shared);
    }
}
