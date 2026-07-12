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
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Ensures a UUID-owned roster exists at normal server player lifecycle points. */
public final class CharacterPlayerEventHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        ensureRoster(event.player);
    }


    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event != null && event.player != null) {
            CharacterAppearanceSyncManager.broadcastRemoval(event.player.getUniqueID());
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


    /**
     * WorldSavedData is UUID-owned, so a cloned EntityPlayer does not need an
     * NBT copy. Re-resolving here applies that same authoritative record to the
     * replacement entity before normal respawn synchronization completes.
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event != null) {
            ensureRoster(event.entityPlayer);
        }
    }

    /** Send the target's current public appearance when tracking begins. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP)
                || !(event.target instanceof EntityPlayerMP)) {
            return;
        }
        CharacterAppearanceSyncManager.sendPlayer(
                (EntityPlayerMP) event.entityPlayer,
                (EntityPlayerMP) event.target);
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
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        CharacterSyncManager.sendRoster(
                serverPlayer,
                CharacterSyncManager.UNSOLICITED_REQUEST_ID,
                result.getRoster());
        CharacterAppearanceSyncManager.sendFullSnapshot(serverPlayer);
        CharacterRaceGameplayHandler.apply(serverPlayer);
        CharacterAppearanceSyncManager.broadcastPlayer(serverPlayer, result.getRoster());
    }
}
