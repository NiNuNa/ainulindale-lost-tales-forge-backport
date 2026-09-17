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
 * Which glyphs of the sheet can show a turn, read from the artwork
 * rather than decided by taste.
 *
 * <p>A glyph unchanged by a quarter turn shows nothing however far it is
 * turned, since a quarter is the only angle a sprite can be rotated
 * through without losing its grid. The cog and the {@code +} are both
 * unchanged by one, which is why neither turns however inviting the
 * subject sounds; the magnifier is not, and turns.</p>
 *
 * <p>Redraw one of them lopsided and its case here fails, which is the
 * signal that a turn has become available to it.</p>
 */
public final class LostTalesUiGlyphTurnTest {

    @Test
    public void theCogIsUnchangedByAQuarterTurnSoItCannotShowOne()
            throws Exception {
        assertTrue("the cog cannot show a turn",
                unchangedByAQuarterTurn(LostTalesUiSheet.COG));
        assertEquals("so it is not given one", 0.0F,
                LostTalesUiButtonMotion.Character.LIFT.getTurnDegrees(), 0.0F);
    }

    @Test
    public void thePlusIsUnchangedByAQuarterTurnToo() throws Exception {
        assertTrue(unchangedByAQuarterTurn(LostTalesUiSheet.PLUS));
    }

    @Test
    public void theMagnifierAndTheSendArrowAreLopsidedEnoughToTurn()
            throws Exception {
        assertFalse("the magnifier hangs off its handle",
                unchangedByAQuarterTurn(LostTalesUiSheet.SEARCH));
        assertFalse("and the send arrow points somewhere",
                unchangedByAQuarterTurn(LostTalesUiSheet.SEND));
        assertTrue("both are given a turn",
                LostTalesUiButtonMotion.Character.TURN.getTurnDegrees() > 0.0F);
    }

    /**
     * Whether a square cell looks the same turned a quarter: every texel
     * matching the one a quarter turn would bring to its place, opaque
     * against opaque and clear against clear.
     */
    private static boolean unchangedByAQuarterTurn(LostTalesUiSheet cell)
            throws Exception {
        assertEquals(cell + " is not square, so a quarter turn moves it "
                        + "out of its own box", cell.getWidth(),
                cell.getHeight());
        BufferedImage sheet = readSheet();
        int size = cell.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean here = opaqueAt(sheet, cell.getTextureU() + x,
                        cell.getTextureV() + y);
                // The texel a quarter turn puts here.
                boolean turned = opaqueAt(sheet,
                        cell.getTextureU() + y,
                        cell.getTextureV() + (size - 1 - x));
                if (here != turned) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean opaqueAt(BufferedImage sheet, int x, int y) {
        return (sheet.getRGB(x, y) >>> 24) > 16;
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = LostTalesUiGlyphTurnTest.class
                .getResourceAsStream("/assets/losttales/"
                        + LostTalesUiSheet.TEXTURE_PATH);
        assertNotNull("the sheet is on the test classpath", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertNotNull("the sheet decodes", sheet);
            return sheet;
        } finally {
            stream.close();
        }
    }
}
