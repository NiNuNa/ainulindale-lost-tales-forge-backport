package com.ninuna.losttales.progression.count;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Your own tamed animal is no foe and tames for you: 1.7.10 keeps its owner as a UUID. */
public final class CountEventsOwnerTest {
    private static final UUID PLAYER = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");

    @Test
    public void theOwnerIsReadAsTheUuidTheAnimalKeeps() {
        assertTrue(CountEvents.isOwner(PLAYER.toString(), PLAYER));
        assertTrue(CountEvents.isOwner(PLAYER.toString().toUpperCase(), PLAYER));
    }

    @Test
    public void aNameOrAnotherUuidIsNoOwner() {
        assertFalse("1.7.10 keeps no owner name", CountEvents.isOwner("Steve", PLAYER));
        assertFalse(CountEvents.isOwner(UUID.randomUUID().toString(), PLAYER));
        assertFalse(CountEvents.isOwner("", PLAYER));
        assertFalse(CountEvents.isOwner(null, PLAYER));
        assertFalse(CountEvents.isOwner(PLAYER.toString(), null));
    }
}
