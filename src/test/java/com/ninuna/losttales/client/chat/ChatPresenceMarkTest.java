package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;
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
 * The sphere a head wears for its identity's presence sits in a corner
 * the head gives up: the cut is the sphere's shape grown by a pixel, so a
 * clear pixel runs between the two along every side the sphere has ink
 * on, and the corner its round artwork does not reach keeps its pixel.
 * It stands two pixels past the head's right edge and one below its
 * bottom, and the head and the sphere are laid out as one icon, so what
 * follows keeps its clear space from the sphere.
 */
public final class ChatPresenceMarkTest {

    /** A head as the chat, the player list and the name list draw one. */
    private static final int HEAD = 8;

    @Test
    public void theSphereStandsPastTheHeadsRightEdgeAndItsBottom() {
        assertEquals(5, ChatPresenceMark.SIZE);
        assertEquals(3, ChatPresenceMark.INSET_X);
        // A pixel higher than it is far in: the sphere is not on the
        // corner's diagonal.
        assertEquals(4, ChatPresenceMark.INSET_Y);
        int left = HEAD - ChatPresenceMark.INSET_X;
        int top = HEAD - ChatPresenceMark.INSET_Y;
        assertEquals(HEAD + 2, left + ChatPresenceMark.SIZE);
        assertEquals(HEAD + 1, top + ChatPresenceMark.SIZE);
        assertEquals(2, ChatPresenceMark.OVERHANG_X);
        assertEquals(1, ChatPresenceMark.OVERHANG_Y);
    }

    /**
     * The head gives up the sphere's <em>shape</em> grown by a pixel on
     * every side, not its box: read from the sheet's own artwork, every
     * head pixel within one of a sphere's inked texels is cut, and no
     * other. The two steps {@link ChatPresenceMark#beginHeadCut} cuts in
     * are exactly that, and the corner they meet at keeps its pixel
     * because the sphere's own corner there is clear.
     */
    @Test
    public void theCutIsTheSpheresShapeGrownByAPixel() throws Exception {
        assertEquals(1, ChatPresenceMark.CUT_MARGIN);
        BufferedImage sheet = readSheet();
        int sphereX = HEAD - ChatPresenceMark.INSET_X;
        int sphereY = HEAD - ChatPresenceMark.INSET_Y;
        // The two steps the head is cut in.
        int lowerX = sphereX - ChatPresenceMark.CUT_MARGIN;
        int lowerY = sphereY;
        int upperX = sphereX;
        int upperY = sphereY - ChatPresenceMark.CUT_MARGIN;
        LostTalesUiSheet[] spheres = {
                LostTalesUiSheet.PRESENCE_ONLINE,
                LostTalesUiSheet.PRESENCE_AWAY,
                LostTalesUiSheet.PRESENCE_BUSY,
                LostTalesUiSheet.PRESENCE_OFFLINE,
                LostTalesUiSheet.PRESENCE_SELECTED,
        };
        for (LostTalesUiSheet sphere : spheres) {
            for (int y = 0; y < HEAD; y++) {
                for (int x = 0; x < HEAD; x++) {
                    boolean nearInk = false;
                    for (int dy = -1; dy <= 1 && !nearInk; dy++) {
                        for (int dx = -1; dx <= 1 && !nearInk; dx++) {
                            int sx = x + dx - sphereX;
                            int sy = y + dy - sphereY;
                            nearInk = sx >= 0 && sy >= 0
                                    && sx < ChatPresenceMark.SIZE
                                    && sy < ChatPresenceMark.SIZE
                                    && inked(sheet, sphere, sx, sy);
                        }
                    }
                    boolean cut = (x >= lowerX && y >= lowerY)
                            || (x >= upperX && y >= upperY);
                    assertEquals(sphere + " at " + x + "," + y, nearInk,
                            cut);
                }
            }
        }
        // The pixel the steps meet at stays: the one the sphere's round
        // corner points at, diagonally off it.
        int cornerX = lowerX;
        int cornerY = upperY;
        assertEquals(4, cornerX);
        assertEquals(3, cornerY);
        assertFalse((cornerX >= lowerX && cornerY >= lowerY)
                || (cornerX >= upperX && cornerY >= upperY));
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

    /** The head and its sphere are one icon, and are laid out as one. */
    @Test
    public void theIconIsTheHeadAndTheSphereTogether() {
        assertEquals(ChatInlineIcons.HEAD_SLOT_WIDTH
                        + ChatPresenceMark.OVERHANG_X,
                ChatInlineIcons.PRESENCE_HEAD_SLOT_WIDTH);
        assertTrue(ChatInlineIcons.PRESENCE_HEAD_SLOT_WIDTH
                > ChatInlineIcons.HEAD_SLOT_WIDTH);
    }

    /** A player's own head on their own line wears one; nothing else does. */
    @Test
    public void onlyAPlayersOwnHeadWearsASphere() {
        UUID player = UUID.randomUUID();
        assertTrue(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                player, true, null, "", "", 0, 0))));
        assertTrue(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                player, false, UUID.randomUUID(), "skin", "", 0, 0))));
        // An NPC has no account at all.
        assertFalse(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encodeNpc(
                player, "skin", "", 0, 0))));
        // Nor have the server, the client or the bridge.
        assertFalse(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                LostTalesChatMessagePacket.SERVER_SENDER_ID, true, null, "",
                "", 0, 0))));
        assertFalse(ChatPresenceMark.wears(ownHead(ChatHeadMarker.encode(
                LostTalesChatMessagePacket.CLIENT_SENDER_ID, true, null, "",
                "", 0, 0))));
        // A reply's quote wears the quoted head without one: a quote does
        // not say which of the sender's characters spoke.
        assertFalse(ChatPresenceMark.wears(
                ChatHeadMarker.Data.head(player, true, false, "")));
        assertFalse(ChatPresenceMark.wears(
                ChatHeadMarker.Data.head(player, false, false, "skin")));
        assertFalse(ChatPresenceMark.wears(null));
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
    public void everyPresenceHasItsOwnSphere() {
        assertEquals(LostTalesUiSheet.PRESENCE_ONLINE,
                ChatPresenceMark.sphereOf(ChatPresence.ONLINE));
        assertEquals(LostTalesUiSheet.PRESENCE_AWAY,
                ChatPresenceMark.sphereOf(ChatPresence.AWAY));
        assertEquals(LostTalesUiSheet.PRESENCE_BUSY,
                ChatPresenceMark.sphereOf(ChatPresence.DO_NOT_DISTURB));
        // Offline, Invisible, which nobody else is shown, and a presence
        // nobody said all wear the muted one.
        assertEquals(LostTalesUiSheet.PRESENCE_OFFLINE,
                ChatPresenceMark.sphereOf(ChatPresence.OFFLINE));
        assertEquals(LostTalesUiSheet.PRESENCE_OFFLINE,
                ChatPresenceMark.sphereOf(ChatPresence.INVISIBLE));
        assertEquals(LostTalesUiSheet.PRESENCE_OFFLINE,
                ChatPresenceMark.sphereOf(null));
        assertNotSame(LostTalesUiSheet.PRESENCE_SELECTED,
                ChatPresenceMark.sphereOf(ChatPresence.ONLINE));
    }

    @Test
    public void theSpheresAreTheSheetsOwnSize() {
        LostTalesUiSheet[] spheres = {
                LostTalesUiSheet.PRESENCE_ONLINE,
                LostTalesUiSheet.PRESENCE_AWAY,
                LostTalesUiSheet.PRESENCE_BUSY,
                LostTalesUiSheet.PRESENCE_OFFLINE,
                LostTalesUiSheet.PRESENCE_SELECTED,
        };
        for (LostTalesUiSheet sphere : spheres) {
            assertEquals(sphere.name(), ChatPresenceMark.SIZE,
                    sphere.getWidth());
            assertEquals(sphere.name(), ChatPresenceMark.SIZE,
                    sphere.getHeight());
        }
    }
}
