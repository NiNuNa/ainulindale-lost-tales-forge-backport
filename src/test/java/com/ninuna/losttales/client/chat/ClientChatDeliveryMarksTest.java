package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatDeliveryMark;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The marks the server sends about this player's own Discord posts,
 * found from the line the renderer is drawing.
 */
public final class ClientChatDeliveryMarksTest {

    @Before
    public void setUp() {
        ClientChatDeliveryMarks.clear();
        ClientChatMessageIds.clear();
    }

    @After
    public void tearDown() {
        ClientChatDeliveryMarks.clear();
        ClientChatMessageIds.clear();
    }

    @Test
    public void aMarkIsFoundFromTheLineItsMessageIsDrawnOn() {
        ClientChatMessageIds.remember(7, 1234L);
        ClientChatMessageIds.remember(8, 5678L);
        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.RETRYING,
                ChatDeliveryMark.Reason.WAITING);
        assertEquals(ChatDeliveryMark.State.RETRYING,
                ClientChatDeliveryMarks.stateOf(7));
        assertEquals(ChatDeliveryMark.Reason.WAITING,
                ClientChatDeliveryMarks.reasonOf(7));
        // A line with no mark, and one that names no message.
        assertEquals(ChatDeliveryMark.State.NONE, ClientChatDeliveryMarks.stateOf(8));
        assertEquals(ChatDeliveryMark.Reason.NONE, ClientChatDeliveryMarks.reasonOf(8));
        assertEquals(ChatDeliveryMark.State.NONE, ClientChatDeliveryMarks.stateOf(99));
    }

    @Test
    public void aMarkThatArrivesFirstIsFoundOnceItsLineIsDrawn() {
        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.FAILED,
                ChatDeliveryMark.Reason.REFUSED);
        assertEquals(ChatDeliveryMark.State.NONE, ClientChatDeliveryMarks.stateOf(7));
        ClientChatMessageIds.remember(7, 1234L);
        assertEquals(ChatDeliveryMark.State.FAILED, ClientChatDeliveryMarks.stateOf(7));
        assertEquals(ChatDeliveryMark.Reason.REFUSED, ClientChatDeliveryMarks.reasonOf(7));
    }

    @Test
    public void aClockNeverReplacesACrimsonMarkAndNoneLiftsEither() {
        ClientChatMessageIds.remember(7, 1234L);
        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.RETRYING,
                ChatDeliveryMark.Reason.LIMITED);
        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.FAILED,
                ChatDeliveryMark.Reason.GAVE_UP);
        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.RETRYING,
                ChatDeliveryMark.Reason.WAITING);
        assertEquals(ChatDeliveryMark.State.FAILED, ClientChatDeliveryMarks.stateOf(7));
        assertEquals(ChatDeliveryMark.Reason.GAVE_UP, ClientChatDeliveryMarks.reasonOf(7));
        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.NONE,
                ChatDeliveryMark.Reason.NONE);
        assertEquals(ChatDeliveryMark.State.NONE, ClientChatDeliveryMarks.stateOf(7));

        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.RETRYING,
                ChatDeliveryMark.Reason.WAITING);
        ClientChatDeliveryMarks.apply(1234L, ChatDeliveryMark.State.NONE,
                ChatDeliveryMark.Reason.NONE);
        assertEquals(ChatDeliveryMark.State.NONE, ClientChatDeliveryMarks.stateOf(7));
        assertEquals(0, ClientChatDeliveryMarks.size());
    }

    @Test
    public void marksAreBoundedTheOldestFallingOut() {
        int count = ClientChatDeliveryMarks.MAX_MARKS + 10;
        for (int index = 0; index < count; index++) {
            ClientChatDeliveryMarks.apply(1000L + index, ChatDeliveryMark.State.FAILED,
                    ChatDeliveryMark.Reason.STOPPED);
        }
        assertEquals(ClientChatDeliveryMarks.MAX_MARKS, ClientChatDeliveryMarks.size());
        ClientChatMessageIds.remember(1, 1000L);
        assertEquals(ChatDeliveryMark.State.NONE, ClientChatDeliveryMarks.stateOf(1));
        ClientChatMessageIds.remember(2, 1000L + count - 1);
        assertEquals(ChatDeliveryMark.State.FAILED, ClientChatDeliveryMarks.stateOf(2));

        ClientChatDeliveryMarks.clear();
        assertEquals(0, ClientChatDeliveryMarks.size());
        assertEquals(ChatDeliveryMark.State.NONE, ClientChatDeliveryMarks.stateOf(2));
    }

    @Test
    public void anIdTheServerCannotHaveSentIsIgnored() {
        ClientChatDeliveryMarks.apply(0L, ChatDeliveryMark.State.FAILED,
                ChatDeliveryMark.Reason.REFUSED);
        ClientChatDeliveryMarks.apply(-5L, ChatDeliveryMark.State.FAILED,
                ChatDeliveryMark.Reason.REFUSED);
        ClientChatDeliveryMarks.apply(5L, null, ChatDeliveryMark.Reason.REFUSED);
        assertEquals(0, ClientChatDeliveryMarks.size());
    }
}
