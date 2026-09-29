package com.ninuna.losttales.network.server;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Each player holds a share of the queue, so one cannot crowd out the rest. */
public final class LostTalesServerTaskQueueTest {
    private static final LostTalesServerTaskQueue.PlayerTask NOTHING =
            new LostTalesServerTaskQueue.PlayerTask() {
                @Override
                public void run(EntityPlayerMP player) {}
            };

    @Before
    public void setUp() {
        LostTalesServerTaskQueue.startAccepting();
    }

    @After
    public void tearDown() {
        LostTalesServerTaskQueue.stopAcceptingAndClear();
    }

    @Test
    public void onePlayerFillsOnlyTheirOwnShare() {
        UUID greedy = UUID.randomUUID();
        for (int task = 0; task < LostTalesServerTaskQueue.MAX_QUEUED_PER_PLAYER; task++) {
            assertTrue(LostTalesServerTaskQueue.enqueue(greedy, "request", NOTHING));
        }
        assertFalse(LostTalesServerTaskQueue.enqueue(greedy, "request", NOTHING));
        assertTrue("everybody else still gets in",
                LostTalesServerTaskQueue.enqueue(UUID.randomUUID(), "request", NOTHING));
    }

    @Test
    public void aClearedQueueGivesEveryShareBack() {
        UUID player = UUID.randomUUID();
        for (int task = 0; task < LostTalesServerTaskQueue.MAX_QUEUED_PER_PLAYER; task++) {
            LostTalesServerTaskQueue.enqueue(player, "request", NOTHING);
        }
        // The server stopping and the next one starting.
        LostTalesServerTaskQueue.stopAcceptingAndClear();
        LostTalesServerTaskQueue.startAccepting();
        assertTrue(LostTalesServerTaskQueue.enqueue(player, "request", NOTHING));
    }
}
