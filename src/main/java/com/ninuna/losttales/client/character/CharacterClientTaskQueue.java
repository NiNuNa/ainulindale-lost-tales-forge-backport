package com.ninuna.losttales.client.character;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Drains character S2C work on the Minecraft client thread. */
public final class CharacterClientTaskQueue {

    private static final int MAX_QUEUED_TASKS = 512;
    private static final int MAX_TASKS_PER_TICK = 128;
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();
    private static final AtomicInteger QUEUED_TASK_COUNT = new AtomicInteger();

    public static boolean enqueue(Runnable task) {
        if (task == null) {
            return false;
        }
        while (true) {
            int current = QUEUED_TASK_COUNT.get();
            if (current >= MAX_QUEUED_TASKS) {
                return false;
            }
            if (QUEUED_TASK_COUNT.compareAndSet(current, current + 1)) {
                TASKS.add(task);
                return true;
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.START) {
            return;
        }
        int processed = 0;
        Runnable task;
        while (processed < MAX_TASKS_PER_TICK && (task = TASKS.poll()) != null) {
            QUEUED_TASK_COUNT.decrementAndGet();
            try {
                task.run();
            } catch (Throwable throwable) {
                FMLLog.warning("[%s] Character client task failed: %s",
                        LostTalesMetaData.MOD_ID, throwable.toString());
            }
            processed++;
        }
    }

    public static int getQueuedTaskCount() {
        return QUEUED_TASK_COUNT.get();
    }

    public static void clear() {
        TASKS.clear();
        QUEUED_TASK_COUNT.set(0);
    }
}
