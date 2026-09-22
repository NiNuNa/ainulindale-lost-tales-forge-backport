package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.accessory.player.AccessoryInventorySyncManager;
import com.ninuna.losttales.accessory.player.AccessoryRecoveryService;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.character.switching.CharacterLifecycleStateTracker;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.compat.lotr.hired.LotrHiredUnitCustody;
import com.ninuna.losttales.character.lore.transfer.LoreCharacterTransferCoordinator;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.chat.server.ChatPresenceService;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
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
        AccessoryInventorySyncManager.clearPlayer(player.getUniqueID());
        CharacterAppearanceSyncManager.broadcastRemoval(player.getUniqueID());
        // The remaining players' role rosters follow the leave.
        com.ninuna.losttales.chat.server.ChatIdentitySelection.forget(player.getUniqueID());
        ChatPresenceService.forget(player.getUniqueID());
        com.ninuna.losttales.chat.server.ChatMemberWatches.forget(
                player.getUniqueID());
        com.ninuna.losttales.compat.discord.LostTalesDiscordBridge.getInstance()
                .forgetPlayer(player.getUniqueID());
        LostTalesChatService.sendAccessToAll(player);
        CharacterSwitchCoordinator.getInstance().clearRuntimeState(player.getUniqueID());
    }

    /**
     * The character's name as the player's display name on the server:
     * what the game writes into a join, a leave, an achievement and any
     * other line it builds from the display name, for everyone at once
     * and as it happens. The client answers the same for the names it
     * draws. The game caches the answer, so a change of character
     * refreshes it where the appearance is broadcast.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void useCharacterName(PlayerEvent.NameFormat event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP)) {
            return;
        }
        PlayableIdentityResolver.Resolution identity =
                PlayableIdentityResolver.resolve((EntityPlayerMP) event.entityPlayer);
        RoleplayCharacter character = identity.isAvailable()
                ? identity.getCharacter() : null;
        if (character != null) {
            event.displayname = PlayableIdentity.formatDisplayName(
                    event.displayname, event.username,
                    PlayableIdentity.displayName(character, event.username));
        }
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
        EntityPlayer player = event == null ? null : event.player;
        if (player instanceof EntityPlayerMP
                && CharacterLifecycleStateTracker.isOwnedDimensionTransition(
                        (EntityPlayerMP) player)) {
            return;
        }
        initializePlayer(player, LifecycleAction.DIMENSION_CHANGE);
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
        // Finish any ownership/roster/state transfer before exposing a roster
        // snapshot or applying the active character to the live player.
        LoreCharacterTransferCoordinator.getInstance()
                .recoverForPlayer(serverPlayer);
        CharacterOperationResult result = CharacterService.getInstance()
                .ensureRoster(serverPlayer);
        if (!result.isSuccessful()) {
            FMLLog.warning("[%s] Unable to ensure character roster for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    player.getUniqueID(),
                    result.getErrorId().getId());
            AccessoryRecoveryService.recover(serverPlayer);
            AccessoryInventorySyncManager.send(serverPlayer);
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
                && lifecycleResult != CharacterErrorId.SWITCH_DEATH_PENDING) {
            FMLLog.warning("[%s] Character lifecycle recovery for player %s returned %s",
                    LostTalesMetaData.MOD_ID,
                    player.getUniqueID(),
                    lifecycleResult.getId());
        }

        // Made after the journal has been settled, so an interrupted
        // switch is reconciled against the roster as it was rather than
        // against one that has just gained an identity. On a world that
        // already knows this account it does nothing.
        if (action == LifecycleAction.LOGIN) {
            CharacterOperationResult defaultCharacter = CharacterService
                    .getInstance().ensureDefaultCharacter(serverPlayer);
            if (!defaultCharacter.isSuccessful()) {
                FMLLog.warning("[%s] Could not make the default character for %s: %s",
                        LostTalesMetaData.MOD_ID, player.getUniqueID(),
                        defaultCharacter.getErrorId().getId());
            }
        }

        boolean switchingReady = lifecycleResult == CharacterErrorId.NONE
                || lifecycleResult == CharacterErrorId.SWITCH_DEATH_PENDING;
        if (switchingReady) {
            CharacterLifecycleStateTracker.markReady(serverPlayer);
        } else if (action == LifecycleAction.LOGIN) {
            // Joining with switching unavailable is a state only an operator
            // can clear, so the player is told once, on the join itself. A
            // respawn or a dimension change says nothing: those are the
            // transitions the tracker is expected to be busy during.
            serverPlayer.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.character.switching_unavailable"));
        }
        CharacterSyncManager.sendRoster(
                serverPlayer,
                CharacterSyncManager.UNSOLICITED_REQUEST_ID,
                result.getRoster());
        CharacterAppearanceSyncManager.sendFullSnapshot(serverPlayer);
        if (action == LifecycleAction.LOGIN) {
            // Everyone's role roster follows the join, the joiner's own
            // access included; then what was said and done while they
            // were away, in the order it happened.
            LostTalesChatService.sendAccessToAll(null);
            LostTalesChatService.sendLoginReplay(serverPlayer);
            ChatPresenceService.sendAll(serverPlayer);
        } else {
            LostTalesChatService.sendAccess(serverPlayer);
        }
        CharacterRaceGameplayHandler.apply(serverPlayer);
        if (action == LifecycleAction.LOGIN && result.getRoster() != null) {
            // Units that changed hands while the owner was away are settled
            // against the identity the roster says is being played.
            LotrHiredUnitCustody.settle(serverPlayer,
                    PlayableIdentity.fromRoster(result.getRoster()),
                    result.getRoster());
        }
        CharacterAppearanceSyncManager.broadcastPlayer(serverPlayer, result.getRoster());
        AccessoryRecoveryService.recover(serverPlayer);
        AccessoryInventorySyncManager.send(serverPlayer);
        AccessoryEffectService.refresh(serverPlayer);
    }

    private enum LifecycleAction {
        LOGIN,
        RESPAWN,
        DIMENSION_CHANGE
    }
}
