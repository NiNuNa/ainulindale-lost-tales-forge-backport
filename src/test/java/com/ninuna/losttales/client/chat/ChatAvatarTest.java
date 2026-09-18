package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The avatar is the face at two pixels a texel, stands centred across
 * the speaker's row and the first row of words with the odd pixel up,
 * and is one icon with the sphere past its edge. A mark standing for a
 * head is drawn at the whole display pixels per texel nearest the
 * avatar's square.
 */
public final class ChatAvatarTest {

    @Test
    public void theAvatarIsTheFaceAtTwoPixelsATexel() {
        assertEquals(16, ChatAvatar.SIZE);
        assertEquals(2 * LostTalesChatOverlayRenderer.HEAD_SIZE,
                ChatAvatar.SIZE);
    }

    @Test
    public void theIconIsTheAvatarAndItsSphere() {
        assertEquals(ChatAvatar.SIZE + ChatPresenceMark.OVERHANG_X,
                ChatAvatar.ICON_WIDTH);
    }

    /**
     * The ten-texel emoji in the sixteen-pixel square: three display
     * pixels a texel at GUI scale 2 (fifteen pixels), five at 3 (sixteen
     * and two thirds, a third of a pixel past each side), six at 4
     * (fifteen); at 1 two would reach two pixels past, so it stays at
     * one.
     */
    @Test
    public void aMarkIsAsLargeAsTheAvatarOnTheDisplaysGrid() {
        assertEquals(15.0F, ChatAvatar.markSize(2.0F), 1.0E-4F);
        assertEquals(50.0F / 3.0F, ChatAvatar.markSize(3.0F), 1.0E-4F);
        assertEquals(15.0F, ChatAvatar.markSize(4.0F), 1.0E-4F);
        assertEquals(10.0F, ChatAvatar.markSize(1.0F), 1.0E-4F);
    }

    /**
     * Centred across the two rows: a speaker's row of sixteen over a row
     * of words of twelve leaves twelve, six above and six below; an odd
     * remainder stands the avatar half a pixel up.
     */
    @Test
    public void theAvatarIsCentredAcrossTheNameAndTheFirstWords() {
        assertEquals(6, ChatAvatar.top(0, 16, 12));
        assertEquals(106, ChatAvatar.top(100, 16, 12));
        // Fifteen over twelve leaves eleven: five above, six below.
        assertEquals(5, ChatAvatar.top(0, 15, 12));
        // Eighteen over twelve at GUI scale 2, twenty-four at 1.
        assertEquals(7, ChatAvatar.top(0, 18, 12));
        assertEquals(10, ChatAvatar.top(0, 24, 12));
    }
}
