package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Forge 1.7.10 logical-server task queue for character packets.
 *
 * Network handlers enqueue bounded work here. Tasks are drained on the server
 * tick thread before authoritative state is accessed or mutated.
 */
public final class CharacterServerTaskQueue {

    private static final int MAX_QUEUED_TASKS = 1024;
    private static final int MAX_TASKS_PER_TICK = 128;
    private static final Queue<QueuedTask> TASKS = new ConcurrentLinkedQueue<QueuedTask>();
    private static final AtomicInteger QUEUED_TASK_COUNT = new AtomicInteger();

    public static boolean enqueue(Runnable task) {
        return enqueue(null, task);
    }

    public static boolean enqueue(UUID ownerId, Runnable task) {
        if (task == null) {
            return false;
        }
        while (true) {
            int current = QUEUED_TASK_COUNT.get();
            if (current >= MAX_QUEUED_TASKS) {
                return false;
            }
            if (QUEUED_TASK_COUNT.compareAndSet(current, current + 1)) {
                TASKS.add(new QueuedTask(ownerId, task));
                return true;
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.START) {
            return;
        }
        int processed = 0;
        QueuedTask queuedTask;
        while (processed < MAX_TASKS_PER_TICK && (queuedTask = TASKS.poll()) != null) {
            QUEUED_TASK_COUNT.decrementAndGet();
            if (!queuedTask.cancelled) {
                try {
                    queuedTask.task.run();
                } catch (Throwable throwable) {
                    FMLLog.warning("[%s] Character server task failed: %s",
                            LostTalesMetaData.MOD_ID, throwable.toString());
                }
            }
            processed++;
        }
    }

    public static void cancelPlayer(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        for (QueuedTask queuedTask : TASKS) {
            if (ownerId.equals(queuedTask.ownerId)) {
                queuedTask.cancelled = true;
            }
        }
    }

    public static int getQueuedTaskCount() {
        return QUEUED_TASK_COUNT.get();
    }

    public static void clear() {
        TASKS.clear();
        QUEUED_TASK_COUNT.set(0);
    }

    private static final class QueuedTask {
        private final UUID ownerId;
        private final Runnable task;
        private volatile boolean cancelled;

        private QueuedTask(UUID ownerId, Runnable task) {
            this.ownerId = ownerId;
            this.task = task;
        }
    }
}
