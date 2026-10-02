package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.TabMark;
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
 * a pixel, as the mock-ups draw it.
 */
public final class ChatIconMarkTest {
    private static final float ICON = TabIcons.SIZE;

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
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage selected = ConversationPage.of(ChatChannel.OOC);
        assertTrue(TabMark.of(global).isNone());
        ClientChatChannelViews.record(-1, global, selected, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertSame(TabMark.UNREAD, TabMark.of(global));
        assertEquals(LostTalesUiSheet.PRESENCE_SELECTED.getWidth(),
                TabMark.of(global).width());
        ClientChatChannelViews.record(-2, global, selected, true, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(-3, global, selected, true, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertEquals(TabMark.pings(2), TabMark.of(global));
    }

    @Test
    public void severalChannelsTogetherAddTheirPings() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage proximity = ConversationPage.of(ChatChannel.PROXIMITY);
        ConversationPage selected = ConversationPage.of(ChatChannel.OOC);
        ClientChatChannelViews.record(-1, proximity, selected, false, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertSame(TabMark.UNREAD,
                TabMark.combined(Arrays.asList(global, proximity)));
        ClientChatChannelViews.record(-2, global, selected, true, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        ClientChatChannelViews.record(-3, proximity, selected, true, ChatMessageIds.NONE, System.currentTimeMillis(), false);
        assertEquals(TabMark.pings(2), TabMark.combined(
                Arrays.asList(global, proximity)));
    }

    /** The tile counts to 99, then reads 99+, as Discord's does; it grows a figure at a time. */
    @Test
    public void theTileCountsToNinetyNineThenShowsThePlus() {
        assertEquals("1", LostTalesUiSheet.countText(1));
        assertEquals("9", LostTalesUiSheet.countText(9));
        assertEquals("10", LostTalesUiSheet.countText(10));
        assertEquals("99", LostTalesUiSheet.countText(99));
        assertEquals("99+", LostTalesUiSheet.countText(100));
        assertEquals("99+", LostTalesUiSheet.countText(4242));
        int edge = LostTalesUiSheet.COUNT_LEFT.getWidth();
        int figure = LostTalesUiSheet.COUNT_1.getWidth();
        assertEquals(edge + figure, TabMark.pings(9).width());
        assertEquals(edge + 2 * figure, TabMark.pings(42).width());
        assertEquals(edge + 3 * figure, TabMark.pings(100).width());
        assertEquals(LostTalesUiSheet.PRESENCE_SELECTED.getWidth(),
                TabMark.pings(5).width());
        assertTrue(TabMark.pings(0).isNone());
    }

    /** Both marks hang two pixels past the icon's right edge and one below its bottom. */
    @Test
    public void theMarkStandsWhereAHeadsSphereStands() {
        TabMark tile = TabMark.pings(2);
        assertEquals(7.0F, tile.markX(0.0F, ICON), 0.0F);
        assertEquals(4.0F, tile.markY(0.0F, ICON), 0.0F);
        assertEquals(7.0F, TabMark.UNREAD.markX(0.0F, ICON), 0.0F);
        assertEquals(6.0F, TabMark.UNREAD.markY(0.0F, ICON), 0.0F);
    }

    /**
     * The mock-up of the white sphere on a ten-pixel emoji: the row
     * over the sphere keeps all but its last two pixels, the sphere's
     * first row all but its last three, and the rows beside it all but
     * their last four.
     */
    @Test
    public void theSpheresCutFollowsItsRoundOutline() {
        LostTalesUiCornerCut cut = TabMark.UNREAD.cut(0.0F, 0.0F, ICON);
        for (int row = 0; row < 5; row++) {
            assertFalse("row " + row, CornerCuts.cuts(cut, ICON - 1, row));
        }
        assertEquals(8.0F, CornerCuts.cutFrom(cut, 5), 0.0F);
        assertEquals(7.0F, CornerCuts.cutFrom(cut, 6), 0.0F);
        assertEquals(6.0F, CornerCuts.cutFrom(cut, 7), 0.0F);
        assertEquals(6.0F, CornerCuts.cutFrom(cut, 9), 0.0F);
    }

    /**
     * The mock-up of the "2" tile, its corners rounded: the row over it
     * cut from its first figure, its top row from its left edge, the
     * rows beside it a pixel further left.
     */
    @Test
    public void theTilesCutIsItsShapeGrownByAPixel() {
        LostTalesUiCornerCut cut = TabMark.pings(2).cut(0.0F, 0.0F, ICON);
        for (int row = 0; row < 3; row++) {
            assertFalse("row " + row, CornerCuts.cuts(cut, ICON - 1, row));
        }
        assertEquals(8.0F, CornerCuts.cutFrom(cut, 3), 0.0F);
        assertEquals(7.0F, CornerCuts.cutFrom(cut, 4), 0.0F);
        assertEquals(6.0F, CornerCuts.cutFrom(cut, 5), 0.0F);
        assertEquals(6.0F, CornerCuts.cutFrom(cut, 9), 0.0F);
    }

    /**
     * The outline the tile's cut is built from is the sheet's: every
     * figure is as tall as the outline and starts on its first column in
     * every row; the left edge is a row shorter at each end, inked in
     * every row it has. So a tile's left side is its edge between two
     * rounded corners, whatever it counts.
     */
    @Test
    public void theTilesOutlineIsTheSheets() throws Exception {
        BufferedImage sheet = readSheet();
        LostTalesUiSheet[] figures = {LostTalesUiSheet.COUNT_0,
                LostTalesUiSheet.COUNT_1,
                LostTalesUiSheet.COUNT_2, LostTalesUiSheet.COUNT_3,
                LostTalesUiSheet.COUNT_4, LostTalesUiSheet.COUNT_5,
                LostTalesUiSheet.COUNT_6, LostTalesUiSheet.COUNT_7,
                LostTalesUiSheet.COUNT_8, LostTalesUiSheet.COUNT_9,
                LostTalesUiSheet.COUNT_MORE};
        int rows = TabMark.TILE_INK_LEFT.length;
        for (LostTalesUiSheet figure : figures) {
            assertEquals(figure.toString(), rows, figure.getHeight());
            for (int row = 0; row < rows; row++) {
                assertEquals(figure + " row " + row, 0,
                        firstInk(sheet, figure, row));
            }
        }
        LostTalesUiSheet edge = LostTalesUiSheet.COUNT_LEFT;
        assertEquals(rows - 2, edge.getHeight());
        for (int row = 0; row < edge.getHeight(); row++) {
            assertEquals("edge row " + row, 0, firstInk(sheet, edge, row));
        }
        for (int row = 0; row < rows; row++) {
            boolean besideEdge = row >= 1 && row < rows - 1;
            assertEquals("outline row " + row, besideEdge ? 0 : edge.getWidth(),
                    TabMark.TILE_INK_LEFT[row]);
        }
    }

    /** The first column of a cell's row that holds ink, or the cell's width for none. */
    private static int firstInk(BufferedImage sheet, LostTalesUiSheet cell,
                                int row) {
        for (int x = 0; x < cell.getWidth(); x++) {
            int argb = sheet.getRGB(cell.getTextureU() + x,
                    cell.getTextureV() + row);
            if ((argb >>> 24) > 0) {
                return x;
            }
        }
        return cell.getWidth();
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = ChatIconMarkTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertTrue("The window sheet is missing", stream != null);
        try {
            return ImageIO.read(stream);
        } finally {
            stream.close();
        }
    }
}
