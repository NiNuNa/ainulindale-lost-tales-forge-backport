package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/** Ensures a UUID-owned roster exists at normal server player lifecycle points. */
public final class CharacterPlayerEventHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        ensureRoster(event.player);
    }


    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event != null && event.player != null) {
            CharacterServerTaskQueue.cancelPlayer(event.player.getUniqueID());
            CharacterNetworkSecurity.clearPlayer(event.player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        ensureRoster(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        ensureRoster(event.player);
    }

    private void ensureRoster(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)
                || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        CharacterOperationResult result = CharacterService.getInstance()
                .ensureRoster((EntityPlayerMP) player);
        if (!result.isSuccessful()) {
            FMLLog.warning("[%s] Unable to ensure character roster for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    player.getUniqueID(),
                    result.getErrorId().getId());
            return;
        }
        CharacterSyncManager.sendRoster(
                (EntityPlayerMP) player,
                CharacterSyncManager.UNSOLICITED_REQUEST_ID,
                result.getRoster());
    }
}
