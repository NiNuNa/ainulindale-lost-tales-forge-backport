package com.ninuna.losttales.client.chat;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks the padlock's grid to the bundled sheet. Its frames are a table
 * rather than named cells, so this is what a re-export that moves,
 * resizes or empties one of them fails against.
 */
public final class ChatLockAnimationTest {
    /** The colourway blocks, by the field each one's column is held in. */
    private static final String[] BLOCKS = {
            "RESTING_U", "OPEN_HOVER_U", "SHUT_HOVER_U"};
    /** The padlock's body: the corner every frame is anchored on. */
    private static final int BODY_WIDTH = 5;
    private static final int BODY_HEIGHT = 4;

    @Test
    public void everyFrameIsATightCellInsideItsBlock() throws Exception {
        BufferedImage sheet = readSheet();
        int[][] frames = frames();
        assertEquals("The padlock is drawn as twelve frames",
                12, frames.length);
        for (String block : BLOCKS) {
            int blockU = constant(block);
            for (int index = 0; index < frames.length; index++) {
                int[] frame = frames[index];
                String name = block + " frame " + (index + 1);
                int u = blockU + frame[0];
                int v = frame[1];
                int width = frame[2];
                int height = frame[3];
                assertTrue(name + " is bigger than the swing's box",
                        width <= ChatLockAnimation.WIDTH
                                && height <= ChatLockAnimation.HEIGHT);
                assertTrue(name + " runs off the sheet",
                        u + width <= sheet.getWidth()
                                && v + height <= sheet.getHeight());
                assertTrue(name + " is cut off on the right",
                        columnHasArtwork(sheet, u + width - 1, v, height));
                assertTrue(name + " is cut off on the left",
                        columnHasArtwork(sheet, u, v, height));
                assertTrue(name + " is cut off at the top",
                        rowHasArtwork(sheet, v, u, width));
                assertTrue(name + " is cut off at the bottom",
                        rowHasArtwork(sheet, v + height - 1, u, width));
            }
        }
    }

    /**
     * Every frame is drawn from its bottom-left corner, so the body has
     * to be identical in all twelve: that is what keeps it still while
     * the shackle swings over it.
     */
    @Test
    public void everyFrameCarriesTheSameBody() throws Exception {
        BufferedImage sheet = readSheet();
        int[][] frames = frames();
        for (String block : BLOCKS) {
            int blockU = constant(block);
            int[] first = null;
            for (int index = 0; index < frames.length; index++) {
                int[] frame = frames[index];
                int[] body = body(sheet, blockU + frame[0],
                        frame[1] + frame[3]);
                if (first == null) {
                    first = body;
                } else {
                    for (int pixel = 0; pixel < body.length; pixel++) {
                        assertEquals(block + " frame " + (index + 1)
                                        + " has a body of its own",
                                first[pixel], body[pixel]);
                    }
                }
            }
        }
    }

    /** The poses a control at rest shows: shut when locked, open when not. */
    @Test
    public void theEndFramesAreTheOpenAndShutPoses() throws Exception {
        int[][] frames = frames();
        int open = frames[0][2];
        int shut = frames[frames.length - 1][2];
        assertTrue("The open padlock's shackle reaches past its body",
                open > shut);
        assertEquals("The shut padlock is the width the badge reserves",
                shut, ChatLockAnimation.SHUT_WIDTH);
        assertEquals("The swing's box is the open padlock's width",
                open, ChatLockAnimation.WIDTH);
    }

    /** The padlock's body, read up from a frame's bottom-left corner. */
    private static int[] body(BufferedImage sheet, int u, int bottom) {
        int[] pixels = new int[BODY_WIDTH * BODY_HEIGHT];
        for (int y = 0; y < BODY_HEIGHT; y++) {
            for (int x = 0; x < BODY_WIDTH; x++) {
                pixels[y * BODY_WIDTH + x] =
                        sheet.getRGB(u + x, bottom - BODY_HEIGHT + y);
            }
        }
        return pixels;
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = ChatLockAnimationTest.class.getResourceAsStream(
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

    private static boolean rowHasArtwork(BufferedImage sheet, int y,
                                         int u, int width) {
        for (int x = u; x < u + width; x++) {
            if ((sheet.getRGB(x, y) >>> 24) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean columnHasArtwork(BufferedImage sheet, int x,
                                            int v, int height) {
        for (int y = v; y < v + height; y++) {
            if ((sheet.getRGB(x, y) >>> 24) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int[][] frames() throws Exception {
        Field field = ChatLockAnimation.class.getDeclaredField("FRAMES");
        field.setAccessible(true);
        return (int[][])field.get(null);
    }

    private static int constant(String name) throws Exception {
        Field field = ChatLockAnimation.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}
