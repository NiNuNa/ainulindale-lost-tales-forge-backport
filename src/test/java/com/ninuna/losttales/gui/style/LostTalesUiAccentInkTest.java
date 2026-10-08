package com.ninuna.losttales.gui.style;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The rule a lit glyph is repainted by for another accent, held against
 * the sheet itself: a honey glyph repainted green comes out as the sheet's
 * own green glyph of the same shape, and repainted crimson as its red one.
 */
public final class LostTalesUiAccentInkTest {

    @Test
    public void honeyLeavesTheSheetAsPainted() throws Exception {
        int[] painted = sheet();
        int[] repainted = painted.clone();
        LostTalesUiAccentInk.repaintSheet(repainted, LostTalesUiSheet.SHEET_WIDTH,
                "HONEY");
        assertArrayEquals(painted, repainted);
    }

    @Test
    public void fernGreenPaintsLitGlyphsAsTheSheetsGreenOnes() throws Exception {
        int[] repainted = repainted("FERN_GREEN");
        int[] painted = sheet();
        assertSame(repainted, LostTalesUiSheet.PLUS_LIT, painted, LostTalesUiSheet.PLUS_ADD);
        assertSame(repainted, LostTalesUiSheet.BELL_LIT, painted, LostTalesUiSheet.BELL_EVERYTHING);
        assertSame(repainted, LostTalesUiSheet.FEED_LIT, painted, LostTalesUiSheet.FEED_EVERYTHING);
    }

    @Test
    public void crimsonPaintsLitGlyphsAsTheSheetsRedOnes() throws Exception {
        int[] repainted = repainted("CRIMSON");
        int[] painted = sheet();
        assertSame(repainted, LostTalesUiSheet.BELL_LIT, painted, LostTalesUiSheet.BELL_NOTHING);
        assertSame(repainted, LostTalesUiSheet.FEED_LIT, painted, LostTalesUiSheet.FEED_NOTHING);
    }

    @Test
    public void onlyTheLitHoneyGlyphsAreRepainted() throws Exception {
        int[] painted = sheet();
        int[] repainted = repainted("SEAFOAM");
        for (LostTalesUiSheet cell : LostTalesUiSheet.values()) {
            if (!cell.isAccentLit()) {
                assertArrayEquals(cell + " is not lit in honey and stays",
                        pixels(painted, cell), pixels(repainted, cell));
            }
        }
    }

    /**
     * New artwork lit in honey is repainted too, unless its honey means
     * something (Away's crescent) or it is a picture of its own (the emoji
     * and the item a button turns into under the pointer).
     */
    @Test
    public void everyGlyphInkedInHoneyIsAccentLit() throws Exception {
        int[] painted = sheet();
        int honey = LostTalesColors.HONEY;
        for (LostTalesUiSheet cell : LostTalesUiSheet.values()) {
            if (cell == LostTalesUiSheet.PRESENCE_AWAY
                    || cell == LostTalesUiSheet.EMOJI_HOVER
                    || cell == LostTalesUiSheet.ITEM_HOVER) {
                continue;
            }
            boolean inked = false;
            for (int pixel : pixels(painted, cell)) {
                inked |= pixel == honey;
            }
            assertEquals(cell + " is inked in honey", inked, cell.isAccentLit());
        }
    }

    @Test
    public void anAccentAtTheTopOfItsRampTakesIvoryForItsHighlight() {
        assertEquals("IVORY", LostTalesUiAccentInk.highlight("SEAFOAM"));
        assertEquals("MEADOW_GREEN", LostTalesUiAccentInk.highlight("FERN_GREEN"));
        assertEquals("HARBOR_BLUE", LostTalesUiAccentInk.stepsDown("FERN_GREEN", 9));
    }

    private static void assertSame(int[] repainted, LostTalesUiSheet lit,
                                   int[] painted, LostTalesUiSheet own) {
        assertArrayEquals(lit + " repainted is not " + own,
                pixels(painted, own), pixels(repainted, lit));
    }

    private static int[] repainted(String accent) throws Exception {
        int[] pixels = sheet();
        LostTalesUiAccentInk.repaintSheet(pixels, LostTalesUiSheet.SHEET_WIDTH, accent);
        return pixels;
    }

    private static int[] pixels(int[] sheet, LostTalesUiSheet cell) {
        int[] out = new int[cell.getWidth() * cell.getHeight()];
        for (int y = 0; y < cell.getHeight(); y++) {
            for (int x = 0; x < cell.getWidth(); x++) {
                int at = (cell.getTextureV() + y) * LostTalesUiSheet.SHEET_WIDTH
                        + cell.getTextureU() + x;
                // A fully clear texel is clear whatever colour it holds.
                out[y * cell.getWidth() + x] = sheet[at] >>> 24 == 0 ? 0 : sheet[at];
            }
        }
        return out;
    }

    private static int[] sheet() throws Exception {
        InputStream stream = LostTalesUiAccentInkTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertNotNull("The window sheet is missing", stream);
        try {
            BufferedImage image = ImageIO.read(stream);
            assertTrue(image.getWidth() == LostTalesUiSheet.SHEET_WIDTH);
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null,
                    0, image.getWidth());
        } finally {
            stream.close();
        }
    }
}
