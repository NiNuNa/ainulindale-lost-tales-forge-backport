package com.ninuna.losttales.gui.style;

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
public final class LostTalesUiSheetTest {

    @Test
    public void sheetMetadataMatchesBundledSprite() throws Exception {
        InputStream stream = LostTalesUiSheetTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertNotNull("Chat icon sheet is missing", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertNotNull("Chat icon sheet is not a readable PNG", sheet);
            assertEquals(LostTalesUiSheet.SHEET_WIDTH, sheet.getWidth());
            assertEquals(LostTalesUiSheet.SHEET_HEIGHT, sheet.getHeight());
            LostTalesUiSheet[] icons = LostTalesUiSheet.values();
            for (LostTalesUiSheet icon : icons) {
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
                for (LostTalesUiSheet other : icons) {
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
        for (LostTalesUiSheet icon : LostTalesUiSheet.values()) {
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
        InputStream stream = LostTalesUiSheetTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
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
     * {@link LostTalesUiSheet#INK_THRESHOLD} away at draw time —
     * the artwork's backdrop texels are a preview, not a layer. That
     * only works while every texel is on the right side of the
     * threshold: ink fully opaque, everything else safely below it. A
     * re-export whose backdrop creeps toward opacity would stack a
     * second surface over the painted one and is failed here instead.
     */
    @Test
    public void tabPiecesSeparateInkFromSurfacePreview() throws Exception {
        InputStream stream = LostTalesUiSheetTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertNotNull("Chat icon sheet is missing", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_RIGHT);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_HOVER_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_HOVER_RIGHT);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_SELECTED_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_SELECTED_RIGHT);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_LIFTED_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.TAB_LIFTED_RIGHT);
            // A framed button's corners are cut the same way.
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_TOP_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_TOP_RIGHT);
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_BOTTOM_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_BOTTOM_RIGHT);
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_LIT_TOP_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_LIT_TOP_RIGHT);
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_LIT_BOTTOM_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.FRAME_LIT_BOTTOM_RIGHT);
            // So are a chat window frame's.
            assertInkOrPreview(sheet, LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.WINDOW_FRAME_TOP_RIGHT);
            assertInkOrPreview(sheet, LostTalesUiSheet.WINDOW_FRAME_BOTTOM_LEFT);
            assertInkOrPreview(sheet, LostTalesUiSheet.WINDOW_FRAME_BOTTOM_RIGHT);
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
                                           LostTalesUiSheet piece) {
        int ceiling = (int)Math.floor(
                LostTalesUiSheet.INK_THRESHOLD * 255.0F);
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
        assertSameSize(LostTalesUiSheet.EMOJI, LostTalesUiSheet.EMOJI_HOVER);
        assertSameSize(LostTalesUiSheet.PLUS, LostTalesUiSheet.PLUS_HOVER);
        assertSameSize(LostTalesUiSheet.COG, LostTalesUiSheet.COG_HOVER);
        assertSameSize(LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER);
        assertSameSize(LostTalesUiSheet.ITEM, LostTalesUiSheet.ITEM_HOVER);
        assertSameSize(LostTalesUiSheet.GRIP, LostTalesUiSheet.GRIP_HOVER);
        assertSameSize(LostTalesUiSheet.HEART, LostTalesUiSheet.HEART_FAVORITE);
        assertSameSize(LostTalesUiSheet.TOGGLE_1, LostTalesUiSheet.TOGGLE_1_HOVER);
        assertSameSize(LostTalesUiSheet.TOGGLE_2, LostTalesUiSheet.TOGGLE_2_HOVER);
        assertSameSize(LostTalesUiSheet.TOGGLE_3, LostTalesUiSheet.TOGGLE_3_HOVER);
        assertSameSize(LostTalesUiSheet.TOGGLE_4, LostTalesUiSheet.TOGGLE_4_HOVER);
        assertSameSize(LostTalesUiSheet.TOGGLE_5, LostTalesUiSheet.TOGGLE_5_HOVER);
        // The chevron's end frames mirror each other, so the flip stays
        // centred on the control from either side.
        assertSameSize(LostTalesUiSheet.TOGGLE_1, LostTalesUiSheet.TOGGLE_5);
        assertSameSize(LostTalesUiSheet.TOGGLE_2, LostTalesUiSheet.TOGGLE_4);
        // The vertical chevron's three colourways are one run, frame by
        // frame, and its end frames mirror each other too.
        LostTalesUiSheet[][] chevrons = {
                {LostTalesUiSheet.CHEVRON_1, LostTalesUiSheet.CHEVRON_1_HOVER,
                        LostTalesUiSheet.CHEVRON_1_MUTED},
                {LostTalesUiSheet.CHEVRON_2, LostTalesUiSheet.CHEVRON_2_HOVER,
                        LostTalesUiSheet.CHEVRON_2_MUTED},
                {LostTalesUiSheet.CHEVRON_3, LostTalesUiSheet.CHEVRON_3_HOVER,
                        LostTalesUiSheet.CHEVRON_3_MUTED},
                {LostTalesUiSheet.CHEVRON_4, LostTalesUiSheet.CHEVRON_4_HOVER,
                        LostTalesUiSheet.CHEVRON_4_MUTED},
                {LostTalesUiSheet.CHEVRON_5, LostTalesUiSheet.CHEVRON_5_HOVER,
                        LostTalesUiSheet.CHEVRON_5_MUTED}};
        for (LostTalesUiSheet[] frame : chevrons) {
            assertSameSize(frame[0], frame[1]);
            assertSameSize(frame[0], frame[2]);
        }
        assertSameSize(LostTalesUiSheet.CHEVRON_1, LostTalesUiSheet.CHEVRON_5);
        assertSameSize(LostTalesUiSheet.CHEVRON_2, LostTalesUiSheet.CHEVRON_4);
        assertSameSize(LostTalesUiSheet.SEARCH, LostTalesUiSheet.SEARCH_HOVER);
        assertSameSize(LostTalesUiSheet.MEMBERS, LostTalesUiSheet.MEMBERS_HOVER);
        assertSameSize(LostTalesUiSheet.COPY, LostTalesUiSheet.COPY_HOVER);
        assertSameSize(LostTalesUiSheet.REPLY, LostTalesUiSheet.REPLY_HOVER);
        assertSameSize(LostTalesUiSheet.FORWARD, LostTalesUiSheet.FORWARD_HOVER);
        assertSameSize(LostTalesUiSheet.MORE, LostTalesUiSheet.MORE_HOVER);
        assertSameSize(LostTalesUiSheet.AREA, LostTalesUiSheet.AREA_HOVER);
        assertSameSize(LostTalesUiSheet.SPEECH_BUBBLE,
                LostTalesUiSheet.SPEECH_BUBBLE_HOVER);
        assertSameSize(LostTalesUiSheet.SEND, LostTalesUiSheet.SEND_HOVER);
        assertSameSize(LostTalesUiSheet.FULLSCREEN,
                LostTalesUiSheet.FULLSCREEN_HOVER);
        assertSameSize(LostTalesUiSheet.FULLSCREEN_EXIT,
                LostTalesUiSheet.FULLSCREEN_EXIT_HOVER);
        // The fullscreen control crosses between its two glyphs on one
        // spot, so they are one size.
        assertSameSize(LostTalesUiSheet.FULLSCREEN,
                LostTalesUiSheet.FULLSCREEN_EXIT);
        // The send button takes the square the other bar buttons take.
        assertSameSize(LostTalesUiSheet.EMOJI, LostTalesUiSheet.SEND);
        // So do the map-marker and quest buttons, in both states.
        assertSameSize(LostTalesUiSheet.EMOJI, LostTalesUiSheet.MAP_MARKER);
        assertSameSize(LostTalesUiSheet.MAP_MARKER,
                LostTalesUiSheet.MAP_MARKER_HOVER);
        assertSameSize(LostTalesUiSheet.EMOJI, LostTalesUiSheet.QUEST);
        assertSameSize(LostTalesUiSheet.QUEST, LostTalesUiSheet.QUEST_HOVER);
        // A framed button's corners are one square in both colourways.
        assertSameSize(LostTalesUiSheet.FRAME_TOP_LEFT,
                LostTalesUiSheet.FRAME_TOP_RIGHT);
        assertSameSize(LostTalesUiSheet.FRAME_TOP_LEFT,
                LostTalesUiSheet.FRAME_BOTTOM_LEFT);
        assertSameSize(LostTalesUiSheet.FRAME_TOP_LEFT,
                LostTalesUiSheet.FRAME_BOTTOM_RIGHT);
        assertSameSize(LostTalesUiSheet.FRAME_TOP_LEFT,
                LostTalesUiSheet.FRAME_LIT_TOP_LEFT);
        assertSameSize(LostTalesUiSheet.FRAME_LIT_TOP_LEFT,
                LostTalesUiSheet.FRAME_LIT_TOP_RIGHT);
        assertSameSize(LostTalesUiSheet.FRAME_LIT_TOP_LEFT,
                LostTalesUiSheet.FRAME_LIT_BOTTOM_LEFT);
        assertSameSize(LostTalesUiSheet.FRAME_LIT_TOP_LEFT,
                LostTalesUiSheet.FRAME_LIT_BOTTOM_RIGHT);
        assertSameSize(LostTalesUiSheet.FRAME_LIT_TOP_LEFT,
                LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT);
        assertSameSize(LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT,
                LostTalesUiSheet.WINDOW_FRAME_TOP_RIGHT);
        assertSameSize(LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT,
                LostTalesUiSheet.WINDOW_FRAME_BOTTOM_LEFT);
        assertSameSize(LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT,
                LostTalesUiSheet.WINDOW_FRAME_BOTTOM_RIGHT);
        // The tab controls share one square.
        assertEquals(LostTalesUiSheet.CLOSE.getWidth(),
                LostTalesUiSheet.COG.getWidth());
        assertEquals(LostTalesUiSheet.CLOSE.getWidth(),
                LostTalesUiSheet.FULLSCREEN.getWidth());
    }

    /**
     * A tab is built from a left and a right border piece with its
     * interior filling the span between them, so the two must be the
     * same size within a state, and the pieces of every state the same
     * width — the layout reserves one border width per end. The
     * selected pair is a row taller and a foot wider.
     */
    private static void assertSameSize(LostTalesUiSheet a, LostTalesUiSheet b) {
        assertEquals(a + " and " + b + " differ in width",
                a.getWidth(), b.getWidth());
        assertEquals(a + " and " + b + " differ in height",
                a.getHeight(), b.getHeight());
    }

    private static boolean overlaps(LostTalesUiSheet a, LostTalesUiSheet b) {
        return a.getTextureU() < b.getTextureU() + b.getWidth()
                && b.getTextureU() < a.getTextureU() + a.getWidth()
                && a.getTextureV() < b.getTextureV() + b.getHeight()
                && b.getTextureV() < a.getTextureV() + a.getHeight();
    }

    private static boolean cellHasOpaquePixels(BufferedImage sheet,
                                               LostTalesUiSheet icon) {
        for (int y = 0; y < icon.getHeight(); y++) {
            if (rowHasOpaquePixels(sheet, icon, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rowHasOpaquePixels(BufferedImage sheet,
                                              LostTalesUiSheet icon, int y) {
        for (int x = 0; x < icon.getWidth(); x++) {
            if (isOpaque(sheet, icon, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean columnHasOpaquePixels(BufferedImage sheet,
                                                 LostTalesUiSheet icon, int x) {
        for (int y = 0; y < icon.getHeight(); y++) {
            if (isOpaque(sheet, icon, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpaque(BufferedImage sheet, LostTalesUiSheet icon,
                                    int x, int y) {
        int argb = sheet.getRGB(icon.getTextureU() + x,
                icon.getTextureV() + y);
        return (argb >>> 24) > 0;
    }

    /**
     * A crossing control's resting ink, laid once with its hollows and
     * once alone, stands at the control's own opacity: laid at nothing
     * first, the ink goes at the whole of it; laid whole first, no second
     * pass.
     */
    @Test
    public void theRestingInkStandsAtTheControlsOpacity() {
        assertEquals(255, LostTalesUiSheet.inkOver(255, 100));
        assertEquals(128, LostTalesUiSheet.inkOver(128, 0));
        assertEquals(0, LostTalesUiSheet.inkOver(200, 200));
        assertEquals(0, LostTalesUiSheet.inkOver(255, 255));
        int[] alphas = {40, 128, 200, 255};
        for (int alpha : alphas) {
            for (int under = 0; under < alpha; under += 7) {
                int ink = LostTalesUiSheet.inkOver(alpha, under);
                float together = 1.0F - (1.0F - under / 255.0F)
                        * (1.0F - ink / 255.0F);
                assertEquals(alpha + " over " + under, alpha / 255.0F,
                        together, 1.0F / 255.0F);
            }
        }
    }
}
