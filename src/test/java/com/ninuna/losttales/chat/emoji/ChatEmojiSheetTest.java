package com.ninuna.losttales.chat.emoji;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks the registry to the bundled sprite sheet the same way the compass
 * test locks its atlas: a re-export with different dimensions, or a registry
 * cell outside the sheet or pointing at empty texels, fails the build instead
 * of shipping misaddressed chat sprites.
 */
public final class ChatEmojiSheetTest {

    @Test
    public void sheetMetadataMatchesBundledSprite() throws Exception {
        InputStream stream = ChatEmojiSheetTest.class.getResourceAsStream(
                "/assets/losttales/" + ChatEmoji.TEXTURE_PATH);
        assertNotNull("Emote sprite sheet is missing", stream);
        try {
            BufferedImage sheet = ImageIO.read(stream);
            assertNotNull("Emote sheet is not a readable PNG", sheet);
            assertEquals(ChatEmoji.SHEET_WIDTH, sheet.getWidth());
            assertEquals(ChatEmoji.SHEET_HEIGHT, sheet.getHeight());
            for (ChatEmoji emoji : ChatEmoji.values()) {
                assertTrue(emoji + " cell exceeds sheet width",
                        emoji.getTextureU() + ChatEmoji.SPRITE_SIZE
                                <= sheet.getWidth());
                assertTrue(emoji + " cell exceeds sheet height",
                        emoji.getTextureV() + ChatEmoji.SPRITE_SIZE
                                <= sheet.getHeight());
                assertTrue(emoji + " cell holds no artwork",
                        cellHasOpaquePixels(sheet, emoji));
            }
        } finally {
            stream.close();
        }
    }

    @Test
    public void shortcodesAreUniqueLowercaseAndBounded() {
        Set<String> names = new HashSet<String>();
        Set<String> cells = new HashSet<String>();
        for (ChatEmoji emoji : ChatEmoji.values()) {
            assertTrue(emoji + " name must be non-empty",
                    emoji.getName().length() > 0);
            assertTrue(emoji + " name is not scan-safe",
                    emoji.getName().matches("[a-z0-9_]+"));
            assertTrue(emoji + " name exceeds the scan bound",
                    emoji.getName().length() <= ChatEmoji.longestName());
            assertTrue(emoji + " duplicates a shortcode",
                    names.add(emoji.getName()));
            assertTrue(emoji + " duplicates a sprite cell",
                    cells.add(emoji.getTextureU() + "x"
                            + emoji.getTextureV()));
            assertEquals(":" + emoji.getName() + ":",
                    emoji.getShortcode());
        }
    }

    private static boolean cellHasOpaquePixels(
            BufferedImage sheet, ChatEmoji emoji) {
        for (int y = 0; y < ChatEmoji.SPRITE_SIZE; y++) {
            for (int x = 0; x < ChatEmoji.SPRITE_SIZE; x++) {
                int argb = sheet.getRGB(emoji.getTextureU() + x,
                        emoji.getTextureV() + y);
                if ((argb >>> 24) > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
