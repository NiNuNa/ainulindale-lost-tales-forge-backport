package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatTranslatedWords;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Server's line in a fellowship's conversation about its marks. */
public final class FellowshipMarkNoticeTest {

    private static final FellowshipMark MARK = new FellowshipMark(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            "Weathertop",
            UUID.fromString("30000000-0000-0000-0000-000000000003"),
            100, 1200.5D, -300.0D, 99L);

    /** Each change is words each game translates: a lang key, who did it, and the mark, a link while it stands. */
    @Test
    public void eachChangeIsWordsEachGameTranslates() {
        for (FellowshipOperationType operation : new FellowshipOperationType[] {
                FellowshipOperationType.PLACE_MARK,
                FellowshipOperationType.MOVE_MARK,
                FellowshipOperationType.REMOVE_MARK}) {
            assertTrue(ChatTranslatedWords.isWords(
                    FellowshipMarkNotice.keyOf(operation)));
        }
        assertNull(FellowshipMarkNotice.keyOf(FellowshipOperationType.RENAME));
        assertArrayEquals(new Object[] {"Aldric", "[m:Weathertop]"},
                FellowshipMarkNotice.argumentsOf(
                        FellowshipOperationType.PLACE_MARK, "Aldric", "Weathertop"));
        assertArrayEquals(new Object[] {"Aldric", "Weathertop"},
                FellowshipMarkNotice.argumentsOf(
                        FellowshipOperationType.REMOVE_MARK, "Aldric", "Weathertop"));
    }

    /** A placed mark's words as an English game writes them from the lang line. */
    private static String placedWords(String markName) {
        return String.format("%s marked %s.", FellowshipMarkNotice.argumentsOf(
                FellowshipOperationType.PLACE_MARK, "Aldric", markName));
    }

    /** The mark's name is a link to its place, which a Server line carries whole over the wire. */
    @Test
    public void thePlacedMarksNameLinksToItsPlace() {
        String text = placedWords(MARK.getName());
        List<ChatShowcase> link = FellowshipMarkNotice.link(text, MARK, "#3A6EA5");

        assertEquals(1, link.size());
        ChatShowcase showcase = link.get(0);
        assertEquals(ChatShareKind.MARKER, showcase.getKind());
        assertEquals(MARK.getMarkerId(), showcase.getMarkerId());
        assertTrue(showcase.getMarkerId().startsWith(FellowshipMark.MARKER_ID_PREFIX));
        assertEquals(100, showcase.getMarkerDimension());
        assertEquals(1200.5D, showcase.getMarkerX(), 0.0D);

        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.FELLOWSHIP, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                "Server", "Server", "", 0xFFFFFF, 0xFFFFFF, text, 5L, "", link,
                "", "", 0, true, 7L, ChatReplyReference.NONE)
                .withScope(MARK.getFellowshipId().toString());
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(1, decoded.getShowcases().size());
        assertTrue(decoded.getNamedPlayers().isEmpty());
    }

    /** No mark name breaks the link or reaches anyone: the chat's own marks are not allowed in one. */
    @Test
    public void aMarkNameHoldsNothingTheChatReads() {
        assertFalse(FellowshipMark.isValidName("@Moderators"));
        assertFalse(FellowshipMark.isValidName("Camp [i:Sword]"));
        assertFalse(FellowshipMark.isValidName("Camp]"));
        assertTrue(FellowshipMark.isValidName("Amon Hen #2"));
        String text = placedWords("Amon Hen #2");
        assertEquals(1, ChatShareTokenParser.parse(text).size());
        assertEquals(1, FellowshipMarkNotice.link(text, MARK, "#3A6EA5").size());
    }
}
