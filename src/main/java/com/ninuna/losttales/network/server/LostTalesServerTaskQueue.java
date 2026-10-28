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

/**
 * The one queue every client request waits in for the server thread. A
 * network handler queues the request's own data and the sender's id; at
 * the start of each tick the queue runs up to {@link #MAX_TASKS_PER_TICK}
 * of them, each against the player connected under that id at that
 * moment, so none runs against a player who has left, come back or
 * respawned since. Each player holds at most
 * {@link #MAX_QUEUED_PER_PLAYER} places, and that share is the only
 * bound: nobody is refused because others filled theirs (DATA-01).
 */
public final class LostTalesServerTaskQueue {

    private static final int MAX_TASKS_PER_TICK = 128;
    static final int MAX_QUEUED_PER_PLAYER = 64;
    private static final Queue<QueuedPlayerTask> TASKS =
            new ConcurrentLinkedQueue<QueuedPlayerTask>();
    private static final Object LIFECYCLE_LOCK = new Object();
    /** How many places each player holds in the queue; guarded by the lifecycle lock. */
    private static final Map<UUID, Integer> QUEUED_PER_PLAYER = new HashMap<UUID, Integer>();
    private static volatile boolean acceptingTasks;

    public interface PlayerTask {
        void run(EntityPlayerMP player);
    }

    public static boolean enqueue(UUID ownerId, String taskName, PlayerTask task) {
        if (ownerId == null || task == null) {
            return false;
        }
        synchronized (LIFECYCLE_LOCK) {
            Integer held = QUEUED_PER_PLAYER.get(ownerId);
            int mine = held == null ? 0 : held.intValue();
            if (!acceptingTasks || mine >= MAX_QUEUED_PER_PLAYER) {
                return false;
            }
            QUEUED_PER_PLAYER.put(ownerId, Integer.valueOf(mine + 1));
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
            release(queuedTask.ownerId);
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

    public static void startAccepting() {
        synchronized (LIFECYCLE_LOCK) {
            TASKS.clear();
            QUEUED_PER_PLAYER.clear();
            acceptingTasks = true;
        }
    }

    public static void stopAcceptingAndClear() {
        synchronized (LIFECYCLE_LOCK) {
            acceptingTasks = false;
            TASKS.clear();
            QUEUED_PER_PLAYER.clear();
        }
    }

    /** The player gives back the place a task of theirs held. */
    private static void release(UUID ownerId) {
        synchronized (LIFECYCLE_LOCK) {
            Integer held = QUEUED_PER_PLAYER.get(ownerId);
            if (held == null || held.intValue() <= 1) {
                QUEUED_PER_PLAYER.remove(ownerId);
            } else {
                QUEUED_PER_PLAYER.put(ownerId, Integer.valueOf(held.intValue() - 1));
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
