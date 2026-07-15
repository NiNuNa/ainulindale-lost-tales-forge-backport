package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Spreads periodic durable character checkpoints across server ticks. */
public final class CharacterStateCheckpointHandler {

    private static final ArrayDeque<UUID> PENDING = new ArrayDeque<UUID>();
    private static final Set<UUID> QUEUED = new HashSet<UUID>();
    private static long serverTicks;
    private static long nextScheduleAt;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }

        if (serverTicks < Long.MAX_VALUE) {
            serverTicks++;
        } else {
            serverTicks = 1L;
            nextScheduleAt = 0L;
        }
        long interval = Math.max(600L,
                (long) LostTalesConfig.characterStateCheckpointIntervalSeconds
                        * 20L);
        if (nextScheduleAt <= 0L) {
            nextScheduleAt = safeAdd(serverTicks, interval);
        } else if (serverTicks >= nextScheduleAt) {
            enqueueOnlinePlayers(server.getConfigurationManager().playerEntityList);
            nextScheduleAt = safeAdd(serverTicks, interval);
        }

        int budget = Math.max(1, Math.min(4,
                LostTalesConfig.characterStateCheckpointPlayersPerTick));
        while (budget-- > 0 && !PENDING.isEmpty()) {
            UUID ownerId = PENDING.removeFirst();
            QUEUED.remove(ownerId);
            EntityPlayerMP player = findOnlinePlayer(
                    server.getConfigurationManager().playerEntityList, ownerId);
            if (player != null) {
                CharacterSwitchCoordinator.getInstance()
                        .checkpointActiveState(player);
            }
        }
    }

    public static void reset() {
        PENDING.clear();
        QUEUED.clear();
        serverTicks = 0L;
        nextScheduleAt = 0L;
    }

    private static void enqueueOnlinePlayers(List<?> players) {
        if (players == null) {
            return;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayerMP) {
                UUID ownerId = ((EntityPlayerMP) value).getUniqueID();
                if (ownerId != null && QUEUED.add(ownerId)) {
                    PENDING.addLast(ownerId);
                }
            }
        }
    }

    private static EntityPlayerMP findOnlinePlayer(
            List<?> players, UUID ownerId) {
        if (players == null || ownerId == null) {
            return null;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayerMP
                    && ownerId.equals(((EntityPlayerMP) value).getUniqueID())) {
                return (EntityPlayerMP) value;
            }
        }
        return null;
    }

    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }
}
