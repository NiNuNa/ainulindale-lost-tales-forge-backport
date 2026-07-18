package com.ninuna.losttales.accessory.effect;

import com.ninuna.losttales.accessory.AccessoryBootstrap;
import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.AccessoryDefinition;
import com.ninuna.losttales.accessory.AccessorySlotType;
import com.ninuna.losttales.accessory.player.AccessoryInventory;
import com.ninuna.losttales.accessory.player.AccessoryInventorySyncManager;
import com.ninuna.losttales.accessory.player.AccessoryPlayerData;
import com.ninuna.losttales.compat.lotr.LotrAccessoryMapHooks;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.AccessoryEffectSyncPacket;
import com.ninuna.losttales.party.server.PartyTrackingSyncManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Derives every active accessory effect from the authoritative server slot. */
public final class AccessoryEffectService {

    private static final int HEARTBEAT_TICKS = 100;
    private static final double TARGET_CLEAR_RADIUS = 128.0D;
    private static final Map<UUID, RuntimeState> STATES =
            new HashMap<UUID, RuntimeState>();

    private AccessoryEffectService() {}

    public static void reconcile(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj == null
                || player.worldObj.isRemote || player.getUniqueID() == null) {
            return;
        }
        EntityPlayerMP serverPlayer = (EntityPlayerMP)player;
        if (serverPlayer.isDead || !serverPlayer.isEntityAlive()
                || serverPlayer.getHealth() <= 0.0F) {
            deactivate(serverPlayer);
            return;
        }
        AccessoryDefinition definition = resolveDefinition(serverPlayer, true);
        String definitionId = hasPublishedEffect(definition)
                ? definition.getId() : "";
        RuntimeState state = getOrCreateState(serverPlayer.getUniqueID());
        boolean changed = !definitionId.equals(state.definitionId);
        if (changed) {
            state.definitionId = definitionId;
            state.nextSequence();
            broadcast(serverPlayer, state);
            refreshTracking(serverPlayer);
        } else if (definition != null && isConcealing(definition)
                && serverPlayer.ticksExisted % 20 == 0) {
            clearTargetingEntities(serverPlayer);
        }
        if (definitionId.length() > 0
                && serverPlayer.ticksExisted % HEARTBEAT_TICKS == 0) {
            broadcast(serverPlayer, state);
        }
    }

    public static void deactivate(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)
                || player.getUniqueID() == null) {
            return;
        }
        RuntimeState state = getOrCreateState(player.getUniqueID());
        if (state.definitionId.length() == 0) {
            return;
        }
        state.definitionId = "";
        state.nextSequence();
        broadcast((EntityPlayerMP)player, state);
        refreshTracking((EntityPlayerMP)player);
    }

    /** Re-sends the current state after a world/respawn tracking reset. */
    public static void refresh(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        reconcile(player);
        RuntimeState state = STATES.get(player.getUniqueID());
        if (state != null && state.sequence > 0L) {
            broadcast((EntityPlayerMP)player, state);
        }
        refreshTracking((EntityPlayerMP)player);
    }

    public static void sendCurrentTo(
            EntityPlayerMP viewer, EntityPlayer target) {
        if (viewer == null || target == null || target.getUniqueID() == null) {
            return;
        }
        if (target instanceof EntityPlayerMP) {
            reconcile(target);
        }
        RuntimeState state = STATES.get(target.getUniqueID());
        if (state != null && state.sequence > 0L
                && state.definitionId.length() > 0) {
            LostTalesNetworkHandler.CHANNEL.sendTo(
                    packet(target, state), viewer);
        }
    }

    public static void sendFullSnapshot(EntityPlayerMP viewer) {
        if (viewer == null) {
            return;
        }
        List<?> players = onlinePlayers();
        if (players == null) {
            return;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayerMP) {
                sendCurrentTo(viewer, (EntityPlayerMP)value);
            }
        }
    }

    public static boolean isConcealed(EntityPlayer player) {
        return player != null && player.worldObj != null
                && !player.worldObj.isRemote
                && !player.isDead && player.isEntityAlive()
                && isConcealing(resolveDefinition(player, false));
    }

    public static boolean isConcealed(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        List<?> players = onlinePlayers();
        if (players == null) {
            return false;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayer
                    && playerId.equals(
                    ((EntityPlayer)value).getUniqueID())) {
                return isConcealed((EntityPlayer)value);
            }
        }
        return false;
    }

    public static void clearAll() {
        STATES.clear();
    }

    private static AccessoryDefinition resolveDefinition(
            EntityPlayer player, boolean removeInvalid) {
        AccessoryPlayerData data = AccessoryPlayerData.get(player);
        AccessoryInventory inventory = data == null ? null : data.getInventory();
        ItemStack stack = inventory == null ? null
                : inventory.getStackInSlot(AccessoryInventory.RING_SLOT);
        if (stack == null) {
            return null;
        }
        AccessoryDefinition definition = stack.stackSize == 1
                ? AccessoryCompatibilityRegistry.getInstance().find(
                AccessorySlotType.RING, stack) : null;
        boolean valid = definition != null
                && definition.canEquip(player, stack);
        if (!valid && removeInvalid) {
            rejectInvalidStack(player, data, inventory);
        }
        return valid ? definition : null;
    }

    private static void rejectInvalidStack(
            EntityPlayer player, AccessoryPlayerData data,
            AccessoryInventory inventory) {
        ItemStack removed = inventory.decrStackSize(
                AccessoryInventory.RING_SLOT, Integer.MAX_VALUE);
        if (removed == null) {
            return;
        }
        ItemStack remaining = removed;
        if (remaining.stackSize > 0
                && remaining.stackSize <= Math.max(
                1, remaining.getMaxStackSize())) {
            player.inventory.addItemStackToInventory(remaining);
        }
        if (remaining.stackSize > 0 && !data.quarantine(remaining)) {
            player.dropPlayerItemWithRandomChoice(remaining, false);
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        if (player.openContainer != null
                && player.openContainer != player.inventoryContainer) {
            player.openContainer.detectAndSendChanges();
        }
        AccessoryInventorySyncManager.send(player);
    }

    private static boolean hasPublishedEffect(AccessoryDefinition definition) {
        return definition != null
                && (definition.getServerEffectId().length() > 0
                || definition.getOwnerVisualEffectId().length() > 0
                || definition.getPublicEffectId().length() > 0);
    }

    private static boolean isConcealing(AccessoryDefinition definition) {
        return definition != null
                && AccessoryBootstrap.CONCEALED_PUBLIC_EFFECT_ID.equals(
                definition.getPublicEffectId());
    }

    private static void clearTargetingEntities(EntityPlayer player) {
        if (player.worldObj == null || player.boundingBox == null) {
            return;
        }
        AxisAlignedBB bounds = player.boundingBox.expand(
                TARGET_CLEAR_RADIUS, TARGET_CLEAR_RADIUS,
                TARGET_CLEAR_RADIUS);
        List<?> entities = player.worldObj.getEntitiesWithinAABB(
                EntityLiving.class, bounds);
        for (Object value : entities) {
            if (!(value instanceof EntityLiving)) {
                continue;
            }
            EntityLiving living = (EntityLiving)value;
            if (living.getAttackTarget() == player) {
                living.setAttackTarget(null);
            }
            if (living.getAITarget() == player) {
                living.setRevengeTarget(null);
            }
        }
    }

    private static RuntimeState getOrCreateState(UUID playerId) {
        RuntimeState state = STATES.get(playerId);
        if (state == null) {
            state = new RuntimeState();
            STATES.put(playerId, state);
        }
        return state;
    }

    private static void broadcast(
            EntityPlayerMP target, RuntimeState state) {
        List<?> players = onlinePlayers();
        if (players == null) {
            return;
        }
        AccessoryEffectSyncPacket packet = packet(target, state);
        for (Object value : players) {
            if (value instanceof EntityPlayerMP) {
                LostTalesNetworkHandler.CHANNEL.sendTo(
                        packet, (EntityPlayerMP)value);
            }
        }
    }

    private static AccessoryEffectSyncPacket packet(
            EntityPlayer target, RuntimeState state) {
        return new AccessoryEffectSyncPacket(
                target.getUniqueID(), target.getEntityId(),
                state.sequence, state.definitionId);
    }

    private static void refreshTracking(EntityPlayerMP player) {
        PartyTrackingSyncManager.refreshAll();
        LotrAccessoryMapHooks.refreshPlayerLocations(player.worldObj);
    }

    private static List<?> onlinePlayers() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null || server.getConfigurationManager() == null
                ? null : server.getConfigurationManager().playerEntityList;
    }

    private static final class RuntimeState {
        private String definitionId = "";
        private long sequence;

        private void nextSequence() {
            this.sequence++;
            if (this.sequence <= 0L) {
                throw new IllegalStateException(
                        "accessory effect sequence exhausted");
            }
        }
    }
}
