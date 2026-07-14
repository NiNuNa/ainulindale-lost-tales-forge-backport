package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Coordinates character persistence with the legacy Forge player lifecycle. */
public final class CharacterPlayerEventHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        initializePlayer(event == null ? null : event.player, LifecycleAction.LOGIN);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        CharacterSwitchCoordinator.getInstance().saveActiveStateOnLogout(player);
        CharacterAppearanceSyncManager.broadcastRemoval(player.getUniqueID());
        CharacterNetworkSecurity.clearPlayer(player.getUniqueID());
        CharacterSwitchCoordinator.getInstance().clearRuntimeState(player.getUniqueID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event != null && !event.isCanceled()
                && event.entityLiving instanceof EntityPlayerMP) {
            CharacterSwitchCoordinator.getInstance().markDeathPending(
                    (EntityPlayerMP) event.entityLiving);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        initializePlayer(event == null ? null : event.player,
                LifecycleAction.DIMENSION_CHANGE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        initializePlayer(event == null ? null : event.player,
                LifecycleAction.RESPAWN);
    }

    /** The later PlayerRespawnEvent owns post-clone state capture. */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event != null && event.entityPlayer instanceof EntityPlayerMP) {
            CharacterService.getInstance().ensureRoster(
                    (EntityPlayerMP) event.entityPlayer);
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

    private void initializePlayer(EntityPlayer player, LifecycleAction action) {
        if (!(player instanceof EntityPlayerMP)
                || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        CharacterOperationResult result = CharacterService.getInstance()
                .ensureRoster(serverPlayer);
        if (!result.isSuccessful()) {
            FMLLog.warning("[%s] Unable to ensure character roster for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    player.getUniqueID(),
                    result.getErrorId().getId());
            return;
        }

        CharacterErrorId lifecycleResult;
        if (action == LifecycleAction.RESPAWN) {
            lifecycleResult = CharacterSwitchCoordinator.getInstance()
                    .handleRespawn(serverPlayer);
        } else if (action == LifecycleAction.DIMENSION_CHANGE) {
            lifecycleResult = CharacterSwitchCoordinator.getInstance()
                    .handleDimensionChange(serverPlayer);
        } else {
            lifecycleResult = CharacterSwitchCoordinator.getInstance()
                    .recover(serverPlayer);
        }
        if (lifecycleResult != CharacterErrorId.NONE
                && lifecycleResult != CharacterErrorId.SWITCH_DEATH_PENDING
                && lifecycleResult != CharacterErrorId.SWITCH_STATE_IMPORT_REQUIRED) {
            FMLLog.warning("[%s] Character lifecycle recovery for player %s returned %s",
                    LostTalesMetaData.MOD_ID,
                    player.getUniqueID(),
                    lifecycleResult.getId());
        }

        boolean switchingReady = lifecycleResult == CharacterErrorId.NONE
                || lifecycleResult == CharacterErrorId.SWITCH_DEATH_PENDING
                || lifecycleResult == CharacterErrorId.SWITCH_STATE_IMPORT_REQUIRED;
        if (switchingReady) {
            CharacterLifecycleStateTracker.markReady(serverPlayer);
        }
        CharacterSyncManager.sendRoster(
                serverPlayer,
                CharacterSyncManager.UNSOLICITED_REQUEST_ID,
                result.getRoster());
        CharacterAppearanceSyncManager.sendFullSnapshot(serverPlayer);
        CharacterRaceGameplayHandler.apply(serverPlayer);
        CharacterAppearanceSyncManager.broadcastPlayer(serverPlayer, result.getRoster());
    }

    private enum LifecycleAction {
        LOGIN,
        RESPAWN,
        DIMENSION_CHANGE
    }
}
