package com.ninuna.losttales.accessory.effect;

import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.AccessoryDefinition;
import com.ninuna.losttales.accessory.AccessoryEffect;
import com.ninuna.losttales.accessory.AccessorySlotType;
import com.ninuna.losttales.accessory.player.AccessoryInventory;
import com.ninuna.losttales.accessory.player.AccessoryInventorySyncManager;
import com.ninuna.losttales.accessory.player.AccessoryPlayerData;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.AccessoryEffectSyncPacket;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Derives every active accessory effect from the authoritative server
 * slot. Who wears what is told only to the players tracking the wearer
 * and to the wearer, at most once a second per wearer (ACC-08); a player
 * who starts tracking a wearer is told then. The fellowship trackers and
 * LOTR's map read concealment from here on their own rounds.
 */
public final class AccessoryEffectService {

    private static final int HEARTBEAT_TICKS = 100;
    private static final double TARGET_CLEAR_RADIUS = 128.0D;
    private static final Map<UUID, AccessoryEffectState> STATES =
            new HashMap<UUID, AccessoryEffectState>();

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
        AccessoryEffectState state = getOrCreateState(serverPlayer.getUniqueID());
        state.wear(hasPublishedEffect(definition) ? definition.getId() : "");
        if (state.takeChange(now())) {
            sendToWatchers(serverPlayer, state);
        } else if (definition != null && isConcealing(definition)
                && serverPlayer.ticksExisted % 20 == 0) {
            clearTargetingEntities(serverPlayer);
        }
        if (state.sent().length() > 0
                && serverPlayer.ticksExisted % HEARTBEAT_TICKS == 0) {
            sendToWatchers(serverPlayer, state);
        }
    }

    /** At death or logout: the watchers are told at once that nothing is worn. */
    public static void deactivate(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)
                || player.getUniqueID() == null) {
            return;
        }
        AccessoryEffectState state = getOrCreateState(player.getUniqueID());
        if (state.takeRemoval(now())) {
            sendToWatchers((EntityPlayerMP)player, state);
        }
    }

    /**
     * After a respawn, a change of dimension or a character switch: the
     * player's own game is told what they were last told to wear, under
     * the entity they are now, and any change waits its turn.
     */
    public static void refresh(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        reconcile(player);
        AccessoryEffectState state = STATES.get(player.getUniqueID());
        if (state != null && state.sequence() > 0L) {
            sendTo((EntityPlayerMP)player, player, state);
        }
    }

    /**
     * A player starts tracking {@code target}: they are told what it was
     * last told to wear, nothing included, so a state they kept from
     * before is set right.
     */
    public static void sendCurrentTo(
            EntityPlayerMP viewer, EntityPlayer target) {
        if (viewer == null || target == null || target.getUniqueID() == null) {
            return;
        }
        if (target instanceof EntityPlayerMP) {
            reconcile(target);
        }
        AccessoryEffectState state = STATES.get(target.getUniqueID());
        if (state != null && state.sequence() > 0L) {
            sendTo(viewer, target, state);
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
                ? AccessoryCompatibilityRegistry.server().find(
                AccessorySlotType.RING, stack) : null;
        if (definition == null && removeInvalid) {
            rejectInvalidStack(player, data, inventory);
        }
        return definition;
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
        return definition != null && !definition.getEffects().isEmpty();
    }

    private static boolean isConcealing(AccessoryDefinition definition) {
        return definition != null && definition.has(AccessoryEffect.CONCEALMENT);
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
            // Spiders, endermen and other creatures of the old AI keep their own target.
            if (living instanceof EntityCreature
                    && ((EntityCreature) living).getEntityToAttack() == player) {
                ((EntityCreature) living).setTarget(null);
            }
        }
    }

    private static AccessoryEffectState getOrCreateState(UUID playerId) {
        AccessoryEffectState state = STATES.get(playerId);
        if (state == null) {
            state = new AccessoryEffectState();
            STATES.put(playerId, state);
        }
        return state;
    }

    /** Tells the wearer and every player tracking them what they were last told to wear. */
    private static void sendToWatchers(
            EntityPlayerMP wearer, AccessoryEffectState state) {
        sendTo(wearer, wearer, state);
        if (!(wearer.worldObj instanceof WorldServer)) {
            return;
        }
        List<EntityPlayer> watchers = new ArrayList<EntityPlayer>(
                ((WorldServer)wearer.worldObj).getEntityTracker()
                        .getTrackingPlayers(wearer));
        for (EntityPlayer watcher : watchers) {
            if (watcher instanceof EntityPlayerMP && watcher != wearer) {
                sendTo((EntityPlayerMP)watcher, wearer, state);
            }
        }
    }

    private static void sendTo(EntityPlayerMP viewer, EntityPlayer wearer,
                               AccessoryEffectState state) {
        if (viewer.playerNetServerHandler != null) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet(wearer, state), viewer);
        }
    }

    private static AccessoryEffectSyncPacket packet(
            EntityPlayer target, AccessoryEffectState state) {
        return new AccessoryEffectSyncPacket(
                target.getUniqueID(), target.getEntityId(),
                state.sequence(), state.sent());
    }

    /** The server's tick, the clock the pace of changes is kept by. */
    private static long now() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? 0L : server.getTickCounter();
    }

    private static List<?> onlinePlayers() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null || server.getConfigurationManager() == null
                ? null : server.getConfigurationManager().playerEntityList;
    }
}
