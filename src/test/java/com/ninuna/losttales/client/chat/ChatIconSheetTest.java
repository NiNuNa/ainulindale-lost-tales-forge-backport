package com.ninuna.losttales.client.chat;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks the control sprites to the bundled sheet: a re-export with other
 * dimensions, a cell outside the sheet, an empty cell, or two cells
 * overlapping fails the build instead of shipping misaddressed controls.
 */
public final class ChatIconSheetTest {

    @Test
    public void sheetMetadataMatchesBundledSprite() throws Exception {
        InputStream stream = ChatIconSheetTest.class.getResourceAsStream(
                "/assets/losttales/" + ChatIconSheet.TEXTURE_PATH);
        assertNotNull("Chat icon sheet is missing", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertNotNull("Chat icon sheet is not a readable PNG", sheet);
            assertEquals(ChatIconSheet.SHEET_WIDTH, sheet.getWidth());
            assertEquals(ChatIconSheet.SHEET_HEIGHT, sheet.getHeight());
            ChatIconSheet[] icons = ChatIconSheet.values();
            for (ChatIconSheet icon : icons) {
                assertTrue(icon + " cell exceeds sheet width",
                        icon.getTextureU() + icon.getWidth()
                                <= sheet.getWidth());
                assertTrue(icon + " cell exceeds sheet height",
                        icon.getTextureV() + icon.getHeight()
                                <= sheet.getHeight());
                assertTrue(icon + " cell holds no artwork",
                        cellHasOpaquePixels(sheet, icon));
                assertTrue(icon + " cell is cut off on the right",
                        columnHasOpaquePixels(sheet, icon,
                                icon.getWidth() - 1));
                assertTrue(icon + " cell is cut off at the bottom",
                        rowHasOpaquePixels(sheet, icon,
                                icon.getHeight() - 1));
                for (ChatIconSheet other : icons) {
                    if (other != icon) {
                        assertFalse(icon + " overlaps " + other,
                                overlaps(icon, other));
                    }
                }
            }
        } finally {
            stream.close();
        }
    }

    /**
     * A cell holds the whole of its artwork: no texel just outside it
     * touches one inside it, sideways or at a corner. A re-export that
     * grows a sprite past its cell — a speech bubble's tail below it —
     * fails here instead of shipping cropped.
     */
    @Test
    public void everyCellHoldsItsWholeArtwork() throws Exception {
        BufferedImage sheet = readSheet();
        for (ChatIconSheet icon : ChatIconSheet.values()) {
            int u = icon.getTextureU();
            int v = icon.getTextureV();
            for (int y = v; y < v + icon.getHeight(); y++) {
                for (int x = u; x < u + icon.getWidth(); x++) {
                    if (!opaqueAt(sheet, x, y)) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            boolean inside = nx >= u
                                    && nx < u + icon.getWidth()
                                    && ny >= v
                                    && ny < v + icon.getHeight();
                            assertFalse(icon + " is cut off: its artwork "
                                            + "goes on at " + nx + "," + ny,
                                    !inside && opaqueAt(sheet, nx, ny));
                        }
                    }
                }
            }
        }
    }

    private static boolean opaqueAt(BufferedImage sheet, int x, int y) {
        return x >= 0 && y >= 0 && x < sheet.getWidth()
                && y < sheet.getHeight()
                && (sheet.getRGB(x, y) >>> 24) > 0;
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = ChatIconSheetTest.class.getResourceAsStream(
                "/assets/losttales/" + ChatIconSheet.TEXTURE_PATH);
        assertNotNull("Chat icon sheet is missing", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertNotNull("Chat icon sheet is not a readable PNG", sheet);
            return sheet;
        } finally {
            stream.close();
        }
    }

    /**
     * The bar paints a tab's surface itself and keeps only the border
     * pieces' <em>ink</em>, cutting everything under
     * {@link ChatChannelTabBar#TAB_INK_THRESHOLD} away at draw time —
     * the artwork's backdrop texels are a preview, not a layer. That
     * only works while every texel is on the right side of the
     * threshold: ink fully opaque, everything else safely below it. A
     * re-export whose backdrop creeps toward opacity would stack a
     * second surface over the painted one and is failed here instead.
     */
    @Test
    public void tabPiecesSeparateInkFromSurfacePreview() throws Exception {
        InputStream stream = ChatIconSheetTest.class.getResourceAsStream(
                "/assets/losttales/" + ChatIconSheet.TEXTURE_PATH);
        assertNotNull("Chat icon sheet is missing", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertInkOrPreview(sheet, ChatIconSheet.TAB_LEFT);
            assertInkOrPreview(sheet, ChatIconSheet.TAB_RIGHT);
            assertInkOrPreview(sheet, ChatIconSheet.TAB_HOVER_LEFT);
            assertInkOrPreview(sheet, ChatIconSheet.TAB_HOVER_RIGHT);
            assertInkOrPreview(sheet, ChatIconSheet.TAB_SELECTED_LEFT);
            assertInkOrPreview(sheet, ChatIconSheet.TAB_SELECTED_RIGHT);
            // A framed button's corners are cut the same way.
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_TOP_LEFT);
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_TOP_RIGHT);
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_BOTTOM_LEFT);
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_BOTTOM_RIGHT);
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_LIT_TOP_LEFT);
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_LIT_TOP_RIGHT);
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_LIT_BOTTOM_LEFT);
            assertInkOrPreview(sheet, ChatIconSheet.FRAME_LIT_BOTTOM_RIGHT);
        } finally {
            stream.close();
        }
    }

    /**
     * Every texel of the piece must be ink at full opacity or a preview
     * below the ink threshold; anything between would neither be cut
     * away nor drawn whole.
     */
    /** The most a surface-preview texel may reach: safely below the ink threshold. */
    private static final float PREVIEW_ALPHA_CEILING = 0.75F;

    private static void assertInkOrPreview(BufferedImage sheet,
                                           ChatIconSheet piece) {
        int ceiling = (int)Math.floor(
                ChatChannelTabBar.TAB_INK_THRESHOLD * 255.0F);
        // A preview texel keeps a margin below the threshold, so a
        // re-export nudging it up is caught before it is drawn as ink.
        int previewCeiling = (int)Math.floor(PREVIEW_ALPHA_CEILING * 255.0F);
        boolean sawInk = false;
        for (int y = 0; y < piece.getHeight(); y++) {
            for (int x = 0; x < piece.getWidth(); x++) {
                int alpha = sheet.getRGB(piece.getTextureU() + x,
                        piece.getTextureV() + y) >>> 24;
                if (alpha >= ceiling) {
                    // Ink, or a joint texel authored a little translucent
                    // where the rule attaches to a selected piece's foot.
                    sawInk = true;
                    continue;
                }
                assertTrue(piece + " texel " + x + "," + y + " at alpha "
                        + alpha + " sits between preview and ink",
                        alpha <= previewCeiling);
            }
        }
        assertTrue(piece + " carries no ink at all", sawInk);
    }

    @Test
    public void hoverStatesMatchTheirRestingSprite() {
        assertSameSize(ChatIconSheet.EMOJI, ChatIconSheet.EMOJI_HOVER);
        assertSameSize(ChatIconSheet.PLUS, ChatIconSheet.PLUS_HOVER);
        assertSameSize(ChatIconSheet.COG, ChatIconSheet.COG_HOVER);
        assertSameSize(ChatIconSheet.CLOSE, ChatIconSheet.CLOSE_HOVER);
        assertSameSize(ChatIconSheet.ITEM, ChatIconSheet.ITEM_HOVER);
        assertSameSize(ChatIconSheet.GRIP, ChatIconSheet.GRIP_HOVER);
        assertSameSize(ChatIconSheet.HEART, ChatIconSheet.HEART_FAVORITE);
        assertSameSize(ChatIconSheet.TOGGLE_1, ChatIconSheet.TOGGLE_1_HOVER);
        assertSameSize(ChatIconSheet.TOGGLE_2, ChatIconSheet.TOGGLE_2_HOVER);
        assertSameSize(ChatIconSheet.TOGGLE_3, ChatIconSheet.TOGGLE_3_HOVER);
        assertSameSize(ChatIconSheet.TOGGLE_4, ChatIconSheet.TOGGLE_4_HOVER);
        assertSameSize(ChatIconSheet.TOGGLE_5, ChatIconSheet.TOGGLE_5_HOVER);
        // The chevron's end frames mirror each other, so the flip stays
        // centred on the control from either side.
        assertSameSize(ChatIconSheet.TOGGLE_1, ChatIconSheet.TOGGLE_5);
        assertSameSize(ChatIconSheet.TOGGLE_2, ChatIconSheet.TOGGLE_4);
        assertSameSize(ChatIconSheet.SEND, ChatIconSheet.SEND_HOVER);
        assertSameSize(ChatIconSheet.FULLSCREEN,
                ChatIconSheet.FULLSCREEN_HOVER);
        assertSameSize(ChatIconSheet.FULLSCREEN_EXIT,
                ChatIconSheet.FULLSCREEN_EXIT_HOVER);
        // The fullscreen control crosses between its two glyphs on one
        // spot, so they are one size.
        assertSameSize(ChatIconSheet.FULLSCREEN,
                ChatIconSheet.FULLSCREEN_EXIT);
        // The send button takes the square the other bar buttons take.
        assertSameSize(ChatIconSheet.EMOJI, ChatIconSheet.SEND);
        // So do the map-marker and quest buttons, in both states.
        assertSameSize(ChatIconSheet.EMOJI, ChatIconSheet.MAP_MARKER);
        assertSameSize(ChatIconSheet.MAP_MARKER,
                ChatIconSheet.MAP_MARKER_HOVER);
        assertSameSize(ChatIconSheet.EMOJI, ChatIconSheet.QUEST);
        assertSameSize(ChatIconSheet.QUEST, ChatIconSheet.QUEST_HOVER);
        // A framed button's corners are one square in both colourways.
        assertSameSize(ChatIconSheet.FRAME_TOP_LEFT,
                ChatIconSheet.FRAME_TOP_RIGHT);
        assertSameSize(ChatIconSheet.FRAME_TOP_LEFT,
                ChatIconSheet.FRAME_BOTTOM_LEFT);
        assertSameSize(ChatIconSheet.FRAME_TOP_LEFT,
                ChatIconSheet.FRAME_BOTTOM_RIGHT);
        assertSameSize(ChatIconSheet.FRAME_TOP_LEFT,
                ChatIconSheet.FRAME_LIT_TOP_LEFT);
        assertSameSize(ChatIconSheet.FRAME_LIT_TOP_LEFT,
                ChatIconSheet.FRAME_LIT_TOP_RIGHT);
        assertSameSize(ChatIconSheet.FRAME_LIT_TOP_LEFT,
                ChatIconSheet.FRAME_LIT_BOTTOM_LEFT);
        assertSameSize(ChatIconSheet.FRAME_LIT_TOP_LEFT,
                ChatIconSheet.FRAME_LIT_BOTTOM_RIGHT);
        // The tab controls share one square.
        assertEquals(ChatIconSheet.CLOSE.getWidth(),
                ChatIconSheet.COG.getWidth());
        assertEquals(ChatIconSheet.CLOSE.getWidth(),
                ChatIconSheet.FULLSCREEN.getWidth());
    }

    /**
     * A tab is built from a left and a right border piece with its
     * interior filling the span between them, so the two must be the
     * same size within a state, and the pieces of every state the same
     * width — the layout reserves one border width per end. The
     * selected pair is taller by the lift the row already makes room
     * for.
     */
    @Test
    public void tabBordersPairUpAcrossStates() {
        assertSameSize(ChatIconSheet.TAB_LEFT, ChatIconSheet.TAB_RIGHT);
        assertSameSize(ChatIconSheet.TAB_HOVER_LEFT,
                ChatIconSheet.TAB_HOVER_RIGHT);
        assertSameSize(ChatIconSheet.TAB_SELECTED_LEFT,
                ChatIconSheet.TAB_SELECTED_RIGHT);
        assertSameSize(ChatIconSheet.TAB_LEFT, ChatIconSheet.TAB_HOVER_LEFT);
        // The selected pieces are their feet wider than the resting
        // ones: the feet reach past the tab on the rule's row.
        assertEquals(ChatIconSheet.TAB_LEFT.getWidth()
                        + ChatChannelTabBar.SELECTED_FOOT,
                ChatIconSheet.TAB_SELECTED_LEFT.getWidth());
        assertTrue(ChatChannelTabBar.SELECTED_FOOT > 0);
        // The selected pieces are taller by the lift and one row more:
        // the selected tab rises the lift above a resting one and
        // stands one row lower, on the rule.
        assertEquals(ChatChannelTabBar.LIFT + 1,
                ChatIconSheet.TAB_SELECTED_LEFT.getHeight()
                        - ChatIconSheet.TAB_LEFT.getHeight());
        // A tab draws its pieces whole and stands on the window's top
        // rule, so the row is one row taller than the artwork; a
        // re-export at another height moves the row with it.
        assertEquals("A tab is its pieces whole, plus the rule they stand on",
                ChatChannelTabBar.HEIGHT,
                ChatIconSheet.TAB_LEFT.getHeight() + 1);
    }

    private static void assertSameSize(ChatIconSheet a, ChatIconSheet b) {
        assertEquals(a + " and " + b + " differ in width",
                a.getWidth(), b.getWidth());
        assertEquals(a + " and " + b + " differ in height",
                a.getHeight(), b.getHeight());
    }

    private static boolean overlaps(ChatIconSheet a, ChatIconSheet b) {
        return a.getTextureU() < b.getTextureU() + b.getWidth()
                && b.getTextureU() < a.getTextureU() + a.getWidth()
                && a.getTextureV() < b.getTextureV() + b.getHeight()
                && b.getTextureV() < a.getTextureV() + a.getHeight();
    }

    private static boolean cellHasOpaquePixels(BufferedImage sheet,
                                               ChatIconSheet icon) {
        for (int y = 0; y < icon.getHeight(); y++) {
            if (rowHasOpaquePixels(sheet, icon, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rowHasOpaquePixels(BufferedImage sheet,
                                              ChatIconSheet icon, int y) {
        for (int x = 0; x < icon.getWidth(); x++) {
            if (isOpaque(sheet, icon, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean columnHasOpaquePixels(BufferedImage sheet,
                                                 ChatIconSheet icon, int x) {
        for (int y = 0; y < icon.getHeight(); y++) {
            if (isOpaque(sheet, icon, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpaque(BufferedImage sheet, ChatIconSheet icon,
                                    int x, int y) {
        int argb = sheet.getRGB(icon.getTextureU() + x,
                icon.getTextureV() + y);
        return (argb >>> 24) > 0;
    }
}
