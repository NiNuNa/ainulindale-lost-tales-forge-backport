package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** A join line names the player it announces from those arriving, once. */
public final class ChatArrivalsTest {

    @After
    public void cleanUp() {
        ChatArrivals.clear();
    }

    @Test
    public void anArrivalIsTakenOnceByItsAccountName() {
        ChatNamedPlayer nils = ChatNamedPlayer.account(UUID.randomUUID(), "Nils");
        ChatArrivals.note(nils);
        assertNull(ChatArrivals.take("Sam"));
        assertEquals(nils, ChatArrivals.take("nils"));
        assertNull(ChatArrivals.take("Nils"));
        assertNull(ChatArrivals.take(null));
    }

    @Test
    public void clearingForgetsEveryArrival() {
        ChatArrivals.note(ChatNamedPlayer.account(UUID.randomUUID(), "Nils"));
        ChatArrivals.clear();
        assertNull(ChatArrivals.take("Nils"));
    }
}
