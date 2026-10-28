package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatReplyReference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A command's answer of the server's own carries the page it was typed
 * in, appended last; a player's line never does.
 */
public final class LostTalesChatMessagePacketPageIdTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    public void aServerLineCarriesItsPageAndAPlayersLineNever() {
        LostTalesChatMessagePacket answer = line(
                LostTalesChatMessagePacket.SERVER_SENDER_ID, "Server")
                .withPageId(" faction|in:gondor ");
        assertEquals("faction|in:gondor", answer.getPageId());
        LostTalesChatMessagePacket read = roundTrip(answer);
        assertFalse(read.isMalformed());
        assertEquals("faction|in:gondor", read.getPageId());
        // Carried through every copy of the line.
        assertEquals("faction|in:gondor",
                answer.withNameColor(0x123456).getPageId());
        // A player's line keeps none, whatever it is handed.
        assertEquals("", line(ALICE, "Aldric").withPageId("global").getPageId());
        // Nor an id that would not be a context, nor one past the bound.
        assertEquals("", answer.withPageId("two\nlines").getPageId());
        StringBuilder wide = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            wide.append('x');
        }
        assertEquals("", answer.withPageId(wide.toString()).getPageId());
        // A payload written without one names none.
        LostTalesChatMessagePacket bare = roundTrip(line(
                LostTalesChatMessagePacket.SERVER_SENDER_ID, "Server"));
        assertFalse(bare.isMalformed());
        assertEquals("", bare.getPageId());
        assertTrue(read.getMessage().equals(bare.getMessage()));
    }

    private static LostTalesChatMessagePacket roundTrip(
            LostTalesChatMessagePacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket read = new LostTalesChatMessagePacket();
        read.fromBytes(buffer);
        return read;
    }

    private static LostTalesChatMessagePacket line(UUID sender, String name) {
        return new LostTalesChatMessagePacket(ChatChannel.GLOBAL, sender, name,
                name, "", 0, 0, "words", 1000000L, "", null, "", "", 0,
                true, 7L, ChatReplyReference.NONE, "");
    }
}
