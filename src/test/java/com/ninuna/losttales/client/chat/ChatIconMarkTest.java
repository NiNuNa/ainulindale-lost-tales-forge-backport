package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A channel's icon says what waits in it from its corner: the crimson
 * tile counting the pings — every unread line, in a whisper — or the
 * white sphere for anything else unread. The mark stands where a head's
 * status sphere stands, and the icon gives it the mark's shape grown by
 * a pixel, as Nils's mock-ups draw it.
 */
public final class ChatIconMarkTest {
    private static final float ICON = ChatChannelIcons.SIZE;

    @Before
    public void setUp() {
        ClientChatChannelViews.clear();
    }

    @After
    public void tearDown() {
        ClientChatChannelViews.clear();
    }

    @Test
    public void pingsComeBeforeTheSphereAndNothingReadWearsNone() {
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ChatTab selected = ChatTab.of(ChatChannel.OOC);
        assertTrue(ChatIconMark.of(global).isNone());
        ClientChatChannelViews.record(-1, global, selected, false);
        assertSame(ChatIconMark.UNREAD, ChatIconMark.of(global));
        assertSame(LostTalesUiSheet.PRESENCE_SELECTED,
                ChatIconMark.of(global).figure());
        ClientChatChannelViews.record(-2, global, selected, true);
        ClientChatChannelViews.record(-3, global, selected, true);
        assertEquals(2, ChatIconMark.of(global).pingCount());
        assertSame(LostTalesUiSheet.COUNT_2, ChatIconMark.of(global).figure());
    }

    @Test
    public void severalChannelsTogetherAddTheirPings() {
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ChatTab proximity = ChatTab.of(ChatChannel.PROXIMITY);
        ChatTab selected = ChatTab.of(ChatChannel.OOC);
        ClientChatChannelViews.record(-1, proximity, selected, false);
        assertSame(ChatIconMark.UNREAD,
                ChatIconMark.combined(Arrays.asList(global, proximity)));
        ClientChatChannelViews.record(-2, global, selected, true);
        ClientChatChannelViews.record(-3, proximity, selected, true);
        assertEquals(2, ChatIconMark.combined(Arrays.asList(global, proximity))
                .pingCount());
    }

    /** Past nine the tile shows the plus, and keeps its width. */
    @Test
    public void pastNineTheTileShowsThePlus() {
        assertSame(LostTalesUiSheet.COUNT_MORE, ChatIconMark.pings(42).figure());
        assertSame(LostTalesUiSheet.COUNT_MORE, ChatIconMark.pings(10).figure());
        assertSame(LostTalesUiSheet.COUNT_9, ChatIconMark.pings(9).figure());
        assertSame(LostTalesUiSheet.COUNT_1, ChatIconMark.pings(1).figure());
        assertEquals(ChatIconMark.TILE_WIDTH, ChatIconMark.pings(42).width());
        assertEquals(LostTalesUiSheet.PRESENCE_SELECTED.getWidth(),
                ChatIconMark.TILE_WIDTH);
        assertTrue(ChatIconMark.pings(0).isNone());
    }

    /** Both marks hang two pixels past the icon's right edge and one below its bottom. */
    @Test
    public void theMarkStandsWhereAHeadsSphereStands() {
        ChatIconMark tile = ChatIconMark.pings(2);
        assertEquals(7.0F, tile.markX(0.0F, ICON), 0.0F);
        assertEquals(4.0F, tile.markY(0.0F, ICON), 0.0F);
        assertEquals(7.0F, ChatIconMark.UNREAD.markX(0.0F, ICON), 0.0F);
        assertEquals(6.0F, ChatIconMark.UNREAD.markY(0.0F, ICON), 0.0F);
    }

    /**
     * Nils's mock-up of the white sphere on a ten-pixel emoji: the row
     * over the sphere keeps all but its last two pixels, the sphere's
     * first row all but its last three, and the rows beside it all but
     * their last four.
     */
    @Test
    public void theSpheresCutFollowsItsRoundOutline() {
        LostTalesUiCornerCut cut = ChatIconMark.UNREAD.cut(0.0F, 0.0F, ICON);
        for (int row = 0; row < 5; row++) {
            assertFalse("row " + row, cut.cuts(ICON - 1, row));
        }
        assertEquals(8.0F, cut.cutFrom(5), 0.0F);
        assertEquals(7.0F, cut.cutFrom(6), 0.0F);
        assertEquals(6.0F, cut.cutFrom(7), 0.0F);
        assertEquals(6.0F, cut.cutFrom(9), 0.0F);
    }

    /** Nils's mock-up of the "2" tile: the row over it cut from its left edge, the rows beside it a pixel further left. */
    @Test
    public void theTilesCutIsItsBoxGrownByAPixel() {
        LostTalesUiCornerCut cut = ChatIconMark.pings(2).cut(0.0F, 0.0F, ICON);
        for (int row = 0; row < 3; row++) {
            assertFalse("row " + row, cut.cuts(ICON - 1, row));
        }
        assertEquals(7.0F, cut.cutFrom(3), 0.0F);
        assertEquals(6.0F, cut.cutFrom(4), 0.0F);
        assertEquals(6.0F, cut.cutFrom(9), 0.0F);
    }

    /**
     * The outline the tile's cut is built from is the sheet's: the tile's
     * left edge and every figure after it are as tall as the outline, and
     * each starts on its first column in every row, so a tile is one
     * solid rectangle whatever it counts.
     */
    @Test
    public void theTilesOutlineIsTheSheets() throws Exception {
        BufferedImage sheet = readSheet();
        LostTalesUiSheet[] tiles = {LostTalesUiSheet.COUNT_LEFT,
                LostTalesUiSheet.COUNT_1, LostTalesUiSheet.COUNT_2,
                LostTalesUiSheet.COUNT_3, LostTalesUiSheet.COUNT_4,
                LostTalesUiSheet.COUNT_5, LostTalesUiSheet.COUNT_6,
                LostTalesUiSheet.COUNT_7, LostTalesUiSheet.COUNT_8,
                LostTalesUiSheet.COUNT_9, LostTalesUiSheet.COUNT_MORE};
        for (LostTalesUiSheet tile : tiles) {
            assertEquals(tile.toString(), ChatIconMark.TILE_INK_LEFT.length,
                    tile.getHeight());
            for (int row = 0; row < tile.getHeight(); row++) {
                int first = tile.getWidth();
                for (int x = 0; x < tile.getWidth(); x++) {
                    int argb = sheet.getRGB(tile.getTextureU() + x,
                            tile.getTextureV() + row);
                    if ((argb >>> 24) > 0) {
                        first = x;
                        break;
                    }
                }
                assertEquals(tile + " row " + row,
                        ChatIconMark.TILE_INK_LEFT[row], first);
            }
        }
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = ChatIconMarkTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertTrue("chat sheet is missing", stream != null);
        try {
            return ImageIO.read(stream);
        } finally {
            stream.close();
        }
    }
}
