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

    @Test
    public void hoverStatesMatchTheirRestingSprite() {
        assertSameSize(ChatIconSheet.EMOJI, ChatIconSheet.EMOJI_HOVER);
        assertSameSize(ChatIconSheet.PLUS, ChatIconSheet.PLUS_HOVER);
        assertSameSize(ChatIconSheet.COG, ChatIconSheet.COG_HOVER);
        assertSameSize(ChatIconSheet.CLOSE, ChatIconSheet.CLOSE_HOVER);
        assertSameSize(ChatIconSheet.UNLOCKED, ChatIconSheet.UNLOCKED_HOVER);
        assertSameSize(ChatIconSheet.LOCKED, ChatIconSheet.LOCKED_HOVER);
        assertSameSize(ChatIconSheet.GRIP, ChatIconSheet.GRIP_HOVER);
        // The tab controls share one square; the lock's body is the
        // width of the locked sprite.
        assertEquals(ChatIconSheet.CLOSE.getWidth(),
                ChatIconSheet.COG.getWidth());
        assertEquals(ChatIconSheet.LOCKED.getHeight(),
                ChatIconSheet.UNLOCKED.getHeight());
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
