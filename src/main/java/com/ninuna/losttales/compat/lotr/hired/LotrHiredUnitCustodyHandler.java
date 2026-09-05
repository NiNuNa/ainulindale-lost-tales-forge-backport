package com.ninuna.losttales.compat.lotr.hired;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * Settles a hired unit the moment it enters the world — spawned, or
 * loaded with its chunk — so a unit that was out of reach during its
 * owner's switch is parked or released as soon as it is back.
 */
public final class LotrHiredUnitCustodyHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null || event.entity == null || event.world == null
                || event.world.isRemote) {
            return;
        }
        LotrHiredUnitCustody.settleJoined(event.entity);
    }
}
