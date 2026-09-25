package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatNarrator;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;
import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * The mark a head wears for its identity's presence sits in a corner
 * the head gives up: the cut is the mark's shape grown by a pixel up,
 * down, left and right, so a clear pixel runs between the two along
 * every side the mark has ink on, and the pixels off its rounded
 * corner stay the head's.
 * It stands two pixels past the head's right edge and one below its
 * bottom, and the head and the mark are laid out as one icon, so what
 * follows keeps its clear space from the mark.
 */
public final class ChatPresenceMarkTest {

    /** A head as the chat, the player list and the name list draw one. */
    private static final int HEAD = 8;

    @Test
    public void theMarkStandsPastTheHeadsRightEdgeAndItsBottom() {
        assertEquals(5, LostTalesUiCornerMark.SIZE);
        assertEquals(3, LostTalesUiCornerMark.INSET_X);
        // A pixel higher than it is far in: the mark is not on the
        // corner's diagonal.
        assertEquals(4, LostTalesUiCornerMark.INSET_Y);
        int left = HEAD - LostTalesUiCornerMark.INSET_X;
        int top = HEAD - LostTalesUiCornerMark.INSET_Y;
        assertEquals(HEAD + 2, left + LostTalesUiCornerMark.SIZE);
        assertEquals(HEAD + 1, top + LostTalesUiCornerMark.SIZE);
        assertEquals(2, LostTalesUiCornerMark.OVERHANG_X);
        assertEquals(1, LostTalesUiCornerMark.OVERHANG_Y);
    }

    /**
     * The head gives up the mark's <em>shape</em> grown by a pixel up,
     * down, left and right, not its box: read from the sheet's own
     * artwork, every head pixel next to one of a mark's inked texels,
     * or on one, is cut, and no other. The outline the cut is built
     * from is the sheet's own.
     */
    @Test
    public void theCutIsTheMarksShapeGrownByAPixel() throws Exception {
        BufferedImage sheet = readSheet();
        int markX = HEAD - LostTalesUiCornerMark.INSET_X;
        int markY = HEAD - LostTalesUiCornerMark.INSET_Y;
        LostTalesUiCornerCut cut = ChatPresenceMark.cutFor(0.0F, 0.0F, HEAD);
        LostTalesUiSheet[] marks = {
                LostTalesUiSheet.PRESENCE_ONLINE,
                LostTalesUiSheet.PRESENCE_AWAY,
                LostTalesUiSheet.PRESENCE_BUSY,
                LostTalesUiSheet.PRESENCE_OFFLINE,
                LostTalesUiSheet.PRESENCE_SELECTED,
        };
        for (LostTalesUiSheet mark : marks) {
            for (int row = 0; row < LostTalesUiCornerMark.SIZE; row++) {
                assertEquals(mark + " row " + row,
                        LostTalesUiCornerMark.SPHERE_INK_LEFT[row],
                        firstInk(sheet, mark, row));
            }
            for (int y = 0; y < HEAD; y++) {
                for (int x = 0; x < HEAD; x++) {
                    boolean nearInk = false;
                    for (int dy = -1; dy <= 1 && !nearInk; dy++) {
                        for (int dx = -1; dx <= 1 && !nearInk; dx++) {
                            if (Math.abs(dx) + Math.abs(dy) > 1) {
                                continue;
                            }
                            int sx = x + dx - markX;
                            int sy = y + dy - markY;
                            nearInk = sx >= 0 && sy >= 0
                                    && sx < LostTalesUiCornerMark.SIZE
                                    && sy < LostTalesUiCornerMark.SIZE
                                    && inked(sheet, mark, sx, sy);
                        }
                    }
                    assertEquals(mark + " at " + x + "," + y, nearInk,
                            cut.cuts(x, y));
                }
            }
        }
        // The two pixels off the mark's rounded top-left corner stay.
        assertFalse(cut.cuts(markX, markY - 1));
        assertFalse(cut.cuts(markX - 1, markY));
        assertTrue(cut.cuts(markX + 1, markY - 1));
        assertTrue(cut.cuts(markX - 1, markY + 1));
    }

    /** The first inked column of a cell's row; the cell's width for a clear row. */
    private static int firstInk(BufferedImage sheet, LostTalesUiSheet cell,
                                int row) {
        for (int x = 0; x < cell.getWidth(); x++) {
            if (inked(sheet, cell, x, row)) {
                return x;
            }
        }
        return cell.getWidth();
    }

    private static boolean inked(BufferedImage sheet, LostTalesUiSheet cell,
                                 int x, int y) {
        int argb = sheet.getRGB(cell.getTextureU() + x,
                cell.getTextureV() + y);
        return (argb >>> 24) > 0;
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = ChatPresenceMarkTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertTrue("chat sheet is missing", stream != null);
        try {
            return ImageIO.read(stream);
        } finally {
            stream.close();
        }
    }

    /** The head and its mark are one icon, and are laid out as one. */
    @Test
    public void theIconIsTheHeadAndTheMarkTogether() {
        assertEquals(ChatInlineIcons.HEAD_SLOT_WIDTH
                        + LostTalesUiCornerMark.OVERHANG_X,
                ChatInlineIcons.PRESENCE_HEAD_SLOT_WIDTH);
        assertTrue(ChatInlineIcons.PRESENCE_HEAD_SLOT_WIDTH
                > ChatInlineIcons.HEAD_SLOT_WIDTH);
    }

    /**
     * Every voice that can be online wears one on its own line: a
     * player's account and characters, and the server. An NPC, the
     * client, the Narrator and the bridge itself never do, and nor does a
     * reply's quote.
     */
    @Test
    public void everyVoiceThatCanBeOnlineWearsAMark() {
        UUID player = UUID.randomUUID();
        assertTrue(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                player, true, null, "", "", 0, 0))));
        assertTrue(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                player, false, UUID.randomUUID(), "skin", "", 0, 0))));
        assertTrue(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                LostTalesChatMessagePacket.SERVER_SENDER_ID, true, null, "",
                "", 0, 0))));
        assertFalse(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encodeNpc(
                player, "skin", "", 0, 0))));
        assertFalse(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                LostTalesChatMessagePacket.CLIENT_SENDER_ID, true, null, "",
                "", 0, 0))));
        assertFalse(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                LostTalesChatMessagePacket.DISCORD_SENDER_ID, true, null, "",
                "", 0, 0))));
        assertFalse(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                player, false, UUID.randomUUID(), ChatNarrator.SKIN_ID, "", 0, 0))));
        // A quote does not say which of the sender's characters spoke.
        assertFalse(ChatPresenceMark.wears(
                ChatHeadMarker.Data.head(player, true, false, "")));
        assertFalse(ChatPresenceMark.wears(
                ChatHeadMarker.Data.head(player, false, false, "skin")));
        assertFalse(ChatPresenceMark.wears(null));
    }

    /** A Discord member wears their status only while the server follows Discord statuses. */
    @Test
    public void aDiscordMemberWearsAStatusOnlyWhileTheServerFollowsThem() {
        UUID member = LostTalesChatMessagePacket.discordSenderId("80351110224678912");
        try {
            assertFalse(ChatPresenceMark.hasStatus(member, false, false));
            ClientChatPresence.setDiscordStatuses(true);
            assertTrue(ChatPresenceMark.hasStatus(member, false, false));
            // Nobody has said they are online, so they are offline.
            assertEquals(ChatPresence.OFFLINE,
                    ChatPresenceMark.statusOf(member, true, null));
            // The bridge's own id is nobody, statuses or not.
            assertFalse(ChatPresenceMark.hasStatus(
                    LostTalesChatMessagePacket.DISCORD_SENDER_ID, false, false));
        } finally {
            ClientChatPresence.clear();
        }
    }

    /** The server is online while it runs; a character line naming no character is offline. */
    @Test
    public void theServerIsOnlineAndAnUnnamedCharacterIsOffline() {
        assertEquals(ChatPresence.ONLINE, ChatPresenceMark.statusOf(
                LostTalesChatMessagePacket.SERVER_SENDER_ID, true, null));
        assertEquals(ChatPresence.OFFLINE, ChatPresenceMark.statusOf(
                UUID.randomUUID(), false, null));
        assertFalse(ChatPresenceMark.hasStatus(null, false, false));
        assertFalse(ChatPresenceMark.hasStatus(UUID.randomUUID(), true, false));
        assertFalse(ChatPresenceMark.hasStatus(UUID.randomUUID(), false, true));
    }

    /** A line's own head carries the character it was said as. */
    @Test
    public void aLinesOwnHeadNamesItsCharacter() {
        UUID player = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        ChatHeadMarker.Data head = ownHead(ChatHeadMarker.encode(player,
                false, character, "skin", "hello", 0x123456, 0xA94B54));
        assertEquals(character, head.characterId);
        assertFalse(head.quoted);
        assertEquals("skin", head.skinId);
        assertEquals(0xA94B54, head.nameColor);
        // An account line names no character, whatever it was handed.
        assertEquals(null, ownHead(ChatHeadMarker.encode(player, true,
                character, "", "", 0, 0)).characterId);
    }

    private static ChatHeadMarker.Data ownHead(String value) {
        net.minecraft.util.ChatComponentText slot =
                new net.minecraft.util.ChatComponentText("  ");
        slot.setChatStyle(new net.minecraft.util.ChatStyle().setChatClickEvent(
                new net.minecraft.event.ClickEvent(
                        net.minecraft.event.ClickEvent.Action.SUGGEST_COMMAND,
                        value)));
        return ChatHeadMarker.decode(slot);
    }

    @Test
    public void everyPresenceHasItsOwnMark() {
        assertEquals(LostTalesUiSheet.PRESENCE_ONLINE,
                ChatPresenceMark.markOf(ChatPresence.ONLINE));
        assertEquals(LostTalesUiSheet.PRESENCE_AWAY,
                ChatPresenceMark.markOf(ChatPresence.AWAY));
        assertEquals(LostTalesUiSheet.PRESENCE_BUSY,
                ChatPresenceMark.markOf(ChatPresence.DO_NOT_DISTURB));
        // Offline, Invisible, which nobody else is shown, and a presence
        // nobody said all wear the muted one.
        assertEquals(LostTalesUiSheet.PRESENCE_OFFLINE,
                ChatPresenceMark.markOf(ChatPresence.OFFLINE));
        assertEquals(LostTalesUiSheet.PRESENCE_OFFLINE,
                ChatPresenceMark.markOf(ChatPresence.INVISIBLE));
        assertEquals(LostTalesUiSheet.PRESENCE_OFFLINE,
                ChatPresenceMark.markOf(null));
        assertNotSame(LostTalesUiSheet.PRESENCE_SELECTED,
                ChatPresenceMark.markOf(ChatPresence.ONLINE));
    }

    @Test
    public void theMarksAreTheSheetsOwnSize() {
        LostTalesUiSheet[] marks = {
                LostTalesUiSheet.PRESENCE_ONLINE,
                LostTalesUiSheet.PRESENCE_AWAY,
                LostTalesUiSheet.PRESENCE_BUSY,
                LostTalesUiSheet.PRESENCE_OFFLINE,
                LostTalesUiSheet.PRESENCE_SELECTED,
        };
        for (LostTalesUiSheet mark : marks) {
            assertEquals(mark.name(), LostTalesUiCornerMark.SIZE,
                    mark.getWidth());
            assertEquals(mark.name(), LostTalesUiCornerMark.SIZE,
                    mark.getHeight());
        }
    }

    /**
     * Every status but Online wears a shape as well as a colour, so it
     * reads without the colour: each of their marks is hollowed where the
     * Online sphere is solid ink, and no two alike.
     */
    @Test
    public void everyStatusButOnlineWearsAShapeOfItsOwn() throws Exception {
        BufferedImage sheet = readSheet();
        LostTalesUiSheet[] shaped = {
                LostTalesUiSheet.PRESENCE_AWAY,
                LostTalesUiSheet.PRESENCE_BUSY,
                LostTalesUiSheet.PRESENCE_OFFLINE,
        };
        java.util.Set<String> hollows = new java.util.HashSet<String>();
        for (LostTalesUiSheet mark : shaped) {
            StringBuilder hollow = new StringBuilder();
            for (int y = 0; y < LostTalesUiCornerMark.SIZE; y++) {
                for (int x = 0; x < LostTalesUiCornerMark.SIZE; x++) {
                    boolean solid = opaque(sheet, LostTalesUiSheet.PRESENCE_ONLINE,
                            x, y);
                    hollow.append(solid && !opaque(sheet, mark, x, y) ? '#' : '.');
                }
            }
            assertTrue(mark.name(), hollow.indexOf("#") >= 0);
            assertTrue(mark.name(), hollows.add(hollow.toString()));
        }
    }

    private static boolean opaque(BufferedImage sheet, LostTalesUiSheet cell,
                                  int x, int y) {
        return sheet.getRGB(cell.getTextureU() + x, cell.getTextureV() + y)
                >>> 24 == 0xFF;
    }
}
