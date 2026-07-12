package com.ninuna.losttales.network.server;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded Forge 1.7.10 logical-server task queue for client requests.
 *
 * Network handlers enqueue only immutable request data and the sender UUID.
 * The currently connected EntityPlayerMP is resolved on the server tick so a
 * queued request never executes against a stale entity object after reconnect,
 * respawn, or logout.
 */
public final class LostTalesServerTaskQueue {

    private static final int MAX_QUEUED_TASKS = 1024;
    private static final int MAX_TASKS_PER_TICK = 128;
    private static final Queue<QueuedPlayerTask> TASKS =
            new ConcurrentLinkedQueue<QueuedPlayerTask>();
    private static final AtomicInteger QUEUED_TASK_COUNT = new AtomicInteger();
    private static final Object LIFECYCLE_LOCK = new Object();
    private static volatile boolean acceptingTasks;

    public interface PlayerTask {
        void run(EntityPlayerMP player);
    }

    public static boolean enqueue(UUID ownerId, String taskName, PlayerTask task) {
        if (ownerId == null || task == null) {
            return false;
        }
        synchronized (LIFECYCLE_LOCK) {
            if (!acceptingTasks || QUEUED_TASK_COUNT.get() >= MAX_QUEUED_TASKS) {
                return false;
            }
            QUEUED_TASK_COUNT.incrementAndGet();
            TASKS.add(new QueuedPlayerTask(ownerId, taskName, task));
            return true;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.START) {
            return;
        }

        if (TASKS.isEmpty()) {
            return;
        }

        Map<UUID, EntityPlayerMP> connectedPlayers = snapshotConnectedPlayers();
        int processed = 0;
        QueuedPlayerTask queuedTask;
        while (processed < MAX_TASKS_PER_TICK && (queuedTask = TASKS.poll()) != null) {
            decrementQueuedTaskCount();
            if (!queuedTask.cancelled) {
                EntityPlayerMP player = connectedPlayers.get(queuedTask.ownerId);
                if (player != null && player.worldObj != null && !player.worldObj.isRemote) {
                    try {
                        queuedTask.task.run(player);
                    } catch (Throwable throwable) {
                        FMLLog.warning("[%s] Server request task %s failed for player %s: %s",
                                LostTalesMetaData.MOD_ID,
                                queuedTask.taskName,
                                queuedTask.ownerId,
                                throwable.toString());
                    }
                }
            }
            processed++;
        }
    }

    public static void cancelPlayer(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        for (QueuedPlayerTask queuedTask : TASKS) {
            if (ownerId.equals(queuedTask.ownerId)) {
                queuedTask.cancelled = true;
            }
        }
    }

    public static int getQueuedTaskCount() {
        return QUEUED_TASK_COUNT.get();
    }

    public static void startAccepting() {
        synchronized (LIFECYCLE_LOCK) {
            TASKS.clear();
            QUEUED_TASK_COUNT.set(0);
            acceptingTasks = true;
        }
    }

    public static void stopAcceptingAndClear() {
        synchronized (LIFECYCLE_LOCK) {
            acceptingTasks = false;
            TASKS.clear();
            QUEUED_TASK_COUNT.set(0);
        }
    }

    public static void clear() {
        synchronized (LIFECYCLE_LOCK) {
            TASKS.clear();
            QUEUED_TASK_COUNT.set(0);
        }
    }

    private static void decrementQueuedTaskCount() {
        while (true) {
            int current = QUEUED_TASK_COUNT.get();
            if (current <= 0 || QUEUED_TASK_COUNT.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    private static Map<UUID, EntityPlayerMP> snapshotConnectedPlayers() {
        HashMap<UUID, EntityPlayerMP> result = new HashMap<UUID, EntityPlayerMP>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return result;
        }

        List players = server.getConfigurationManager().playerEntityList;
        if (players == null) {
            return result;
        }
        for (Object value : players) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) value;
                if (player.getUniqueID() != null) {
                    result.put(player.getUniqueID(), player);
                }
            }
        }
        return result;
    }

    private static final class QueuedPlayerTask {
        private final UUID ownerId;
        private final String taskName;
        private final PlayerTask task;
        private volatile boolean cancelled;

        private QueuedPlayerTask(UUID ownerId, String taskName, PlayerTask task) {
            this.ownerId = ownerId;
            this.taskName = taskName == null ? "unnamed" : taskName;
            this.task = task;
        }
    }
}
