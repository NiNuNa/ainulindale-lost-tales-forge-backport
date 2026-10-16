package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A join line names the player it announces from those arriving, once. */
public final class ChatArrivalsTest {

    @After
    public void cleanUp() {
        ChatArrivals.clear();
    }

    @Test
    public void anArrivalIsTakenOnceByItsAccountName() {
        ChatNamedPlayer nils = ChatNamedPlayer.account(UUID.randomUUID(), "Nils");
        ChatArrivals.note(nils, false);
        assertNull(ChatArrivals.take("Sam"));
        assertEquals(nils, ChatArrivals.take("nils"));
        assertNull(ChatArrivals.take("Nils"));
        assertNull(ChatArrivals.take(null));
    }

    /** One who comes Invisible is taken by that answer; one who comes visibly is left. */
    @Test
    public void anInvisibleArrivalIsAnsweredOnce() {
        ChatArrivals.note(ChatNamedPlayer.account(UUID.randomUUID(), "Nils"), true);
        ChatNamedPlayer sam = ChatNamedPlayer.account(UUID.randomUUID(), "Sam");
        ChatArrivals.note(sam, false);
        assertTrue(ChatArrivals.arrivesInvisible("NILS"));
        assertNull("its join line names nobody", ChatArrivals.take("Nils"));
        assertFalse(ChatArrivals.arrivesInvisible("Sam"));
        assertEquals(sam, ChatArrivals.take("Sam"));
        assertFalse(ChatArrivals.arrivesInvisible(null));
    }

    @Test
    public void clearingForgetsEveryArrival() {
        ChatArrivals.note(ChatNamedPlayer.account(UUID.randomUUID(), "Nils"), false);
        ChatArrivals.clear();
        assertNull(ChatArrivals.take("Nils"));
    }
}
