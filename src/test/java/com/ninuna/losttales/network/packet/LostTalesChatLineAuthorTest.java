package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatTypedText;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A line wears the quiet mark of a character spoken as but not played
 * (CHAT-05) on every copy, and keeps what its author typed (CHAT-83) on
 * the author's own copy alone.
 */
public final class LostTalesChatLineAuthorTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEREN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    public void aCharacterNotInTheWorldIsSaidSoOnEveryCopy() {
        LostTalesChatMessagePacket line = characterLine("hail").withNotInWorld(true);
        assertTrue(line.isNotInWorld());
        LostTalesChatMessagePacket shared = roundTrip(line.withoutEcho());
        assertFalse(shared.isMalformed());
        assertTrue(shared.isNotInWorld());
        assertTrue("an edit keeps it", line.withMessage("hail again").isNotInWorld());
    }

    @Test
    public void onlyACharactersLineMayStandNotInTheWorld() {
        LostTalesChatMessagePacket account = new LostTalesChatMessagePacket(
                ChatChannel.OOC, ALICE, "alice", "alice", "", 0, 0, "hi",
                1000L, "", null, "", "", 0, true, 7L, ChatReplyReference.NONE)
                .withNotInWorld(true);
        assertFalse(account.isNotInWorld());
        assertFalse(roundTrip(account).isNotInWorld());
    }

    @Test
    public void whatWasTypedTravelsOnTheAuthorsCopyAlone() {
        LostTalesChatMessagePacket line = characterLine("hello 🙂")
                .withTyped("hello :)");
        assertEquals("hello :)", line.getTyped());
        LostTalesChatMessagePacket own = roundTrip(line);
        assertFalse(own.isMalformed());
        assertEquals("hello :)", own.getTyped());
        assertEquals("every other copy leaves it off", "",
                roundTrip(line.withoutEcho()).getTyped());
        assertEquals("an edit's words are typed anew", "",
                line.withMessage("bye").getTyped());
        assertEquals("the same as the words keeps nothing", "",
                characterLine("hello").withTyped("hello").getTyped());
    }

    @Test
    public void typedWordsAreAMessagesOrAnActions() {
        assertTrue(ChatTypedText.isValid(""));
        assertTrue(ChatTypedText.isValid("/me draws his sword"));
        assertTrue(ChatTypedText.isValid("hello :)"));
        assertFalse(ChatTypedText.isValid(null));
        assertFalse(ChatTypedText.isValid("/me "));
        assertFalse(ChatTypedText.isValid("two\u0000words"));
        assertEquals("", ChatTypedText.kept("two\u0000words", "two words"));
    }

    private static LostTalesChatMessagePacket characterLine(String words) {
        return new LostTalesChatMessagePacket(ChatChannel.GLOBAL, ALICE, "Beren",
                "alice", "", 0, 0, words, 1000L, "skin", null, "", "", 0, false,
                7L, ChatReplyReference.NONE, "", 3L, BEREN);
    }

    private static LostTalesChatMessagePacket roundTrip(LostTalesChatMessagePacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMessagePacket read = new LostTalesChatMessagePacket();
        read.fromBytes(buffer);
        return read;
    }
}
