package com.ninuna.losttales.client.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.input.LostTalesInputIconAtlas.Band;
import com.ninuna.losttales.client.input.LostTalesInputIconAtlas.Cell;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

/**
 * Every key hint is a blank key with its name written on it (MAP-17): the
 * stand-in's cut of the painted key frame matches the texture, and the key
 * is sized to its name.
 */
public final class LostTalesInputIconAtlasTest {
    private static BufferedImage image;

    @BeforeClass
    public static void readTheTexture() throws Exception {
        InputStream input = LostTalesInputIconAtlasTest.class.getResourceAsStream(
                "/assets/losttales/textures/gui/keyboard_keys.png");
        assertNotNull(input);
        try {
            image = ImageIO.read(input);
        } finally {
            input.close();
        }
        assertNotNull(image);
    }

    @Test
    public void bundledTextureMatchesDeclaredAtlasSize() {
        assertEquals(LostTalesInputIconAtlas.TEXTURE_WIDTH, image.getWidth());
        assertEquals(LostTalesInputIconAtlas.TEXTURE_HEIGHT, image.getHeight());
    }

    /** The face holds the font's capitals, and the bands fill each pose's rows from its top down. */
    @Test
    public void everyPoseIsCutIntoBandsThatFillTheKey() {
        assertEquals(LostTalesUiInk.CAP_HEIGHT, LostTalesInputIconAtlas.FACE_ROWS);
        for (int pose = 0; pose < LostTalesInputIconAtlas.KEYBOARD_FRAME_COUNT; pose++) {
            Band[] bands = LostTalesInputIconAtlas.bands(pose);
            int row = pose;
            for (Band band : bands) {
                assertEquals("pose " + pose, row, band.top);
                row += band.rows;
                assertTrue(band.sourceRow >= pose);
                assertTrue(band.sourceRow + band.sourceRows
                        <= LostTalesInputIconAtlas.SPRITE_HEIGHT);
            }
            assertEquals(LostTalesInputIconAtlas.SPRITE_HEIGHT, row);
            assertEquals(LostTalesInputIconAtlas.faceTop(pose), bands[1].top);
            assertEquals(LostTalesInputIconAtlas.FACE_ROWS, bands[1].rows);
        }
        // A pose past the last is read as the last.
        assertEquals(LostTalesInputIconAtlas.faceTop(2),
                LostTalesInputIconAtlas.faceTop(9));
    }

    /**
     * The painted face row each pose stretches is open between the key's
     * sides, and its sides, like every other band's whole width, are ink.
     */
    @Test
    public void theCutMatchesThePaintedFrames() {
        int width = LostTalesInputIconAtlas.KEY_CELL_WIDTH;
        int inset = LostTalesInputIconAtlas.FACE_INSET;
        for (int pose = 0; pose < LostTalesInputIconAtlas.KEYBOARD_FRAME_COUNT; pose++) {
            int u = LostTalesInputIconAtlas.cellU(pose);
            assertTrue(u + width <= LostTalesInputIconAtlas.TEXTURE_WIDTH);
            Band[] bands = LostTalesInputIconAtlas.bands(pose);
            int faceRow = LostTalesInputIconAtlas.KEY_CELL_V + bands[1].sourceRow;
            for (int column = 0; column < width; column++) {
                boolean open = column >= inset && column < width - inset;
                assertEquals("pose " + pose + " column " + column, !open,
                        opaque(u + column, faceRow));
            }
            for (int index = 0; index < bands.length; index++) {
                if (index == 1) {
                    continue;
                }
                for (int row = 0; row < bands[index].sourceRows; row++) {
                    int v = LostTalesInputIconAtlas.KEY_CELL_V
                            + bands[index].sourceRow + row;
                    assertTrue("pose " + pose + " row " + v,
                            opaque(u + LostTalesInputIconAtlas.KEY_MIDDLE_COLUMN, v));
                }
            }
        }
    }

    /** The face is filled with the frame's own face colour, a palette entry. */
    @Test
    public void theFaceTexelIsTheFramesMauve() {
        int argb = image.getRGB(LostTalesInputIconAtlas.FACE_TEXEL_U,
                LostTalesInputIconAtlas.FACE_TEXEL_V);
        assertEquals(0xFF, argb >>> 24);
        assertEquals(LostTalesColors.MAUVE & 0xFFFFFF, argb & 0xFFFFFF);
    }

    /** The arrows keep their painted glyphs, which carry no words; no other key has one. */
    @Test
    public void onlyTheArrowsHaveAGlyph() {
        int[] arrows = {Keyboard.KEY_UP, Keyboard.KEY_RIGHT,
                Keyboard.KEY_DOWN, Keyboard.KEY_LEFT};
        for (int index = 0; index < arrows.length; index++) {
            Cell glyph = LostTalesInputIconAtlas.arrowGlyph(arrows[index]);
            assertNotNull(glyph);
            assertEquals((26 + index) * 8, glyph.u);
            assertEquals(LostTalesInputIconAtlas.GLYPH_V, glyph.v);
            assertTrue(glyph.u + glyph.width <= LostTalesInputIconAtlas.TEXTURE_WIDTH);
            assertTrue(glyph.height <= LostTalesInputIconAtlas.FACE_ROWS);
            assertTrue(opaque(glyph.u + glyph.width / 2, glyph.v + glyph.height / 2));
            assertEquals(LostTalesInputIconAtlas.MIN_KEY_WIDTH,
                    LostTalesInputIconAtlas.glyphKeyWidth(glyph));
        }
        assertNull(LostTalesInputIconAtlas.arrowGlyph(Keyboard.KEY_A));
        assertNull(LostTalesInputIconAtlas.arrowGlyph(Keyboard.KEY_F1));
        assertNull(LostTalesInputIconAtlas.arrowGlyph(Keyboard.KEY_LSHIFT));
    }

    @Test
    public void theMouseWheelIsInsideTheTexture() {
        Cell wheel = LostTalesInputIconAtlas.mouseWheel();
        assertEquals(LostTalesInputIconAtlas.SPRITE_HEIGHT, wheel.height);
        assertTrue(wheel.v + wheel.height <= LostTalesInputIconAtlas.TEXTURE_HEIGHT);
        assertTrue(opaque(wheel.u + wheel.width / 2, wheel.v + wheel.height / 2));
    }

    /**
     * A key is as wide as its name and a clear column each side on its
     * face, never narrower than a letter key; the name stands centred,
     * the odd column to the right.
     */
    @Test
    public void aKeyIsSizedToItsName() {
        // A, six columns with its shadow: a column clear each side.
        assertEquals(14, LostTalesInputIconAtlas.keyWidth(6));
        assertEquals(4, LostTalesInputIconAtlas.labelLeft(14, 6));
        // I, four columns: the narrowest key; on its seven-column face the
        // odd spare column goes to the right.
        assertEquals(13, LostTalesInputIconAtlas.keyWidth(4));
        assertEquals(4, LostTalesInputIconAtlas.labelLeft(13, 4));
        // Nothing to write still draws a key.
        assertEquals(13, LostTalesInputIconAtlas.keyWidth(0));
        assertEquals(13, LostTalesInputIconAtlas.keyWidth(-5));
        // A word: L Shift.
        assertEquals(44, LostTalesInputIconAtlas.keyWidth(36));
        assertEquals(4, LostTalesInputIconAtlas.labelLeft(44, 36));
        // Five columns on a seven-column face: one clear each side.
        assertEquals(4, LostTalesInputIconAtlas.labelLeft(13, 5));
    }

    private static boolean opaque(int x, int y) {
        return (image.getRGB(x, y) >>> 24) > 0;
    }
}
