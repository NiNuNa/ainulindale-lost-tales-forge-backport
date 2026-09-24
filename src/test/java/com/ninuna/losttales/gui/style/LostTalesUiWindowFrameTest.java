package com.ninuna.losttales.gui.style;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A window's frame is a lit framed button's frame, faded: its own cells
 * carry the lit button's ink texel for texel, every corner drawn from them
 * texel by texel on the frame's ramps, and every edge from the texel a
 * framed button stretches along that edge.
 */
public final class LostTalesUiWindowFrameTest {

    /**
     * The window frame's cells hold the lit framed button's ink exactly:
     * the same texels opaque, in the same colours. Only what they preview
     * behind the ink may differ.
     */
    @Test
    public void theWindowFrameWearsTheLitFramedButtonsInk() throws Exception {
        BufferedImage sheet = readSheet();
        assertSameInk(sheet, LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT,
                LostTalesUiSheet.FRAME_LIT_TOP_LEFT);
        assertSameInk(sheet, LostTalesUiSheet.WINDOW_FRAME_TOP_RIGHT,
                LostTalesUiSheet.FRAME_LIT_TOP_RIGHT);
        assertSameInk(sheet, LostTalesUiSheet.WINDOW_FRAME_BOTTOM_LEFT,
                LostTalesUiSheet.FRAME_LIT_BOTTOM_LEFT);
        assertSameInk(sheet, LostTalesUiSheet.WINDOW_FRAME_BOTTOM_RIGHT,
                LostTalesUiSheet.FRAME_LIT_BOTTOM_RIGHT);
    }

    /**
     * Each edge comes from the corner cell a framed button stretches it
     * from, and from the one texel of that cell's innermost column or row
     * the stretch shows: the ink on the edges' line.
     */
    @Test
    public void everyEdgeIsStretchedAsAFramedButtonsIs() throws Exception {
        assertEquals(LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT,
                LostTalesUiWindowFrame.edgeCorner(false, false));
        assertEquals(LostTalesUiSheet.WINDOW_FRAME_BOTTOM_LEFT,
                LostTalesUiWindowFrame.edgeCorner(false, true));
        assertEquals(LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT,
                LostTalesUiWindowFrame.edgeCorner(true, false));
        assertEquals(LostTalesUiSheet.WINDOW_FRAME_TOP_RIGHT,
                LostTalesUiWindowFrame.edgeCorner(true, true));
        BufferedImage sheet = readSheet();
        int innermost = LostTalesUiFramedButton.CORNER - 1;
        for (int side = 0; side < 2; side++) {
            for (int far = 0; far < 2; far++) {
                LostTalesUiSheet corner = LostTalesUiWindowFrame.edgeCorner(
                        side == 1, far == 1);
                int line = LostTalesUiWindowFrame.cornerTexel(far == 1,
                        LostTalesUiWindowFrame.EDGE_LINE);
                for (int across = 0; across < LostTalesUiFramedButton.CORNER;
                        across++) {
                    int u = side == 1 ? across : innermost;
                    int v = side == 1 ? innermost : across;
                    int alpha = sheet.getRGB(corner.getTextureU() + u,
                            corner.getTextureV() + v) >>> 24;
                    assertEquals(corner + " texel " + u + "," + v,
                            across == line, alpha == 0xFF);
                }
            }
        }
    }

    /**
     * Every corner cell of the window frame holds exactly the ink the
     * frame draws from it: an arm along each edge's line, from the box's
     * corner to the cell's inner side, and the rounding pixel in the box's
     * corner, all opaque; nothing else in the cell is.
     */
    @Test
    public void everyCornerHoldsTwoArmsAndARoundingPixel() throws Exception {
        BufferedImage sheet = readSheet();
        assertInk(sheet, LostTalesUiSheet.WINDOW_FRAME_TOP_LEFT, false, false);
        assertInk(sheet, LostTalesUiSheet.WINDOW_FRAME_TOP_RIGHT, true, false);
        assertInk(sheet, LostTalesUiSheet.WINDOW_FRAME_BOTTOM_LEFT, false, true);
        assertInk(sheet, LostTalesUiSheet.WINDOW_FRAME_BOTTOM_RIGHT, true, true);
    }

    /**
     * Each arm spans the cell from the box's corner inward, four texels,
     * along the edges' line a texel in from the cell's outer side.
     */
    @Test
    public void anArmRunsFromTheBoxCornerToTheCellsInnerSide() {
        assertEquals(4, LostTalesUiWindowFrame.CORNER_ARM);
        assertEquals(1, LostTalesUiWindowFrame.EDGE_LINE);
        assertEquals(2, LostTalesUiWindowFrame.cornerTexel(false, 2));
        assertEquals(3, LostTalesUiWindowFrame.cornerTexel(true, 2));
        assertEquals(4, LostTalesUiWindowFrame.cornerTexel(true, 1));
    }

    /**
     * The rounding pixel takes the mean of the two arm pixels beside it,
     * so a lit corner's stays full and a faded corner's all but gone.
     */
    @Test
    public void theRoundingPixelTakesTheMeanOfTheArmsBesideIt() {
        assertEquals(255, LostTalesUiWindowFrame.bendAlpha(255, 255,
                255, 255));
        assertEquals(251, LostTalesUiWindowFrame.bendAlpha(250, 230,
                255, 251));
        assertEquals(3, LostTalesUiWindowFrame.bendAlpha(0, 40, 0, 8));
        assertEquals(0, LostTalesUiWindowFrame.bendAlpha(0, 0, 0, 0));
    }

    /**
     * A horizontal edge fades linearly from its full end to nothing at the
     * other, so the arm beside a faded corner is all but gone.
     */
    @Test
    public void aHorizontalEdgeFadesFromItsFullEndToNothing() {
        assertEquals(255, LostTalesUiWindowFrame.edgeAlphaAt(255,
                100.0F, 0.0F, 100.0F));
        assertEquals(128, LostTalesUiWindowFrame.edgeAlphaAt(255,
                100.0F, 0.0F, 50.0F));
        assertEquals(1, LostTalesUiWindowFrame.edgeAlphaAt(255,
                100.0F, 0.0F, 0.5F));
        assertEquals(0, LostTalesUiWindowFrame.edgeAlphaAt(255,
                0.0F, 100.0F, 100.0F));
        assertEquals(0, LostTalesUiWindowFrame.edgeAlphaAt(255,
                0.0F, 0.0F, 0.0F));
    }

    private static void assertInk(BufferedImage sheet, LostTalesUiSheet corner,
                                  boolean right, boolean bottom) {
        int cell = LostTalesUiFramedButton.CORNER;
        int ring = LostTalesUiWindowFrame.WIDTH;
        int line = LostTalesUiWindowFrame.EDGE_LINE;
        boolean[][] ink = new boolean[cell][cell];
        for (int depth = ring; depth < cell; depth++) {
            ink[LostTalesUiWindowFrame.cornerTexel(bottom, depth)]
                    [LostTalesUiWindowFrame.cornerTexel(right, line)] = true;
            ink[LostTalesUiWindowFrame.cornerTexel(bottom, line)]
                    [LostTalesUiWindowFrame.cornerTexel(right, depth)] = true;
        }
        ink[LostTalesUiWindowFrame.cornerTexel(bottom, ring)]
                [LostTalesUiWindowFrame.cornerTexel(right, ring)] = true;
        for (int v = 0; v < cell; v++) {
            for (int u = 0; u < cell; u++) {
                int alpha = sheet.getRGB(corner.getTextureU() + u,
                        corner.getTextureV() + v) >>> 24;
                assertEquals(corner + " texel " + u + "," + v, ink[v][u],
                        alpha == 0xFF);
            }
        }
    }

    private static void assertSameInk(BufferedImage sheet, LostTalesUiSheet cell,
                                      LostTalesUiSheet lit) {
        for (int v = 0; v < cell.getHeight(); v++) {
            for (int u = 0; u < cell.getWidth(); u++) {
                int own = sheet.getRGB(cell.getTextureU() + u,
                        cell.getTextureV() + v);
                int button = sheet.getRGB(lit.getTextureU() + u,
                        lit.getTextureV() + v);
                boolean ink = own >>> 24 == 0xFF;
                assertEquals(cell + " texel " + u + "," + v, button >>> 24 == 0xFF,
                        ink);
                if (ink) {
                    assertEquals(cell + " texel " + u + "," + v, button, own);
                }
            }
        }
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = LostTalesUiWindowFrameTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertNotNull("The UI sheet is missing", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertNotNull("The UI sheet is not a readable PNG", sheet);
            return sheet;
        } finally {
            stream.close();
        }
    }
}
