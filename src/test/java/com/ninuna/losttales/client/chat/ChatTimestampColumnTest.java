package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiInk;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The timestamp area holds the avatar with at least seven clear pixels
 * either side, sphere included, and the words stand five pixels past its
 * separator. Where the widest time, as it is drawn, needs more, the area
 * widens to hold it and centres the avatar in it. The feed has no area.
 */
public final class ChatTimestampColumnTest {

    /** {@code 12:59 PM}: 41 pixels of ink, leaning a pixel either way and shadowed. */
    private static final int WIDEST = 41 + 2 * ChatTimestampColumn.ITALIC_LEAN
            + LostTalesUiInk.SHADOW_OFFSET;

    @Test
    public void theAvatarStandsSevenPixelsFromEitherSide() {
        ChatTimestampColumn column = ChatTimestampColumn.forTimeWidth(0.0F);
        assertTrue(column.enabled);
        assertEquals(32, column.width);
        // The frame edge stands outside the window, so the gap starts at
        // the window's own edge.
        assertEquals(ChatTimestampColumn.AVATAR_GAP, column.avatarX(), 0.0F);
        assertEquals(7.0F, column.avatarX(), 0.0F);
        // From the sphere's last pixel to the separator.
        assertEquals(7.0F, column.separatorX()
                - (column.avatarX() + ChatAvatar.ICON_WIDTH), 0.0F);
        // And from the separator to the words.
        assertEquals(5.0F, column.messageX()
                - (column.separatorX() + ChatTimestampColumn.SEPARATOR_WIDTH),
                0.0F);
    }

    /**
     * An area driven out of its window slides past the window's left edge,
     * avatar, times and separator with it, and the words follow it until
     * they stand the words' own gap from the window's edge; half way out,
     * everything stands half way.
     */
    @Test
    public void aDrivenOutAreaTakesItsWordsToTheWindowsEdge() {
        ChatTimestampColumn whole = ChatTimestampColumn.forTimeWidth(0.0F);
        ChatTimestampColumn out = whole.drivenTo(0.0F);
        assertFalse(out.shows());
        assertEquals(ChatTimestampColumn.WORDS_GAP, out.messageX(), 0.0F);
        assertEquals(-ChatTimestampColumn.SEPARATOR_WIDTH, out.separatorX(),
                0.0F);
        ChatTimestampColumn half = whole.drivenTo(0.5F);
        assertTrue(half.shows());
        float travel = (whole.width + ChatTimestampColumn.SEPARATOR_WIDTH) / 2.0F;
        assertEquals(whole.messageX() - travel, half.messageX(), 1.0E-4F);
        assertEquals(whole.avatarX() - travel, half.avatarX(), 1.0E-4F);
        assertEquals(whole.separatorX() - travel, half.separatorX(), 1.0E-4F);
    }

    /**
     * The widest time as drawn, with two clear pixels and a display pixel
     * either side: the avatar's own area holds it at GUI scale 2, and the
     * area widens to thirty-four at 3, thirty-eight at 4, forty at 5 and
     * fifty at 1, where there is no smaller text.
     */
    @Test
    public void theAreaHoldsTheWidestTimeAtEveryScale() {
        assertEquals(32, width(0.5F, 2.0F));
        assertEquals(34, width(2.0F / 3.0F, 3.0F));
        assertEquals(38, width(0.75F, 4.0F));
        assertEquals(40, width(0.8F, 5.0F));
        assertEquals(50, width(1.0F, 1.0F));
    }

    /** Where the area widens, the avatar stands in its middle, the odd pixel after it. */
    @Test
    public void aWiderAreaCentresTheAvatar() {
        ChatTimestampColumn scaleThree = ChatTimestampColumn.forTimeWidth(
                ChatTimestampColumn.timeRoom(WIDEST, 2.0F / 3.0F, 3.0F));
        assertEquals(8.0F, scaleThree.avatarX(), 0.0F);
        assertEquals(8.0F, scaleThree.separatorX()
                - (scaleThree.avatarX() + ChatAvatar.ICON_WIDTH), 0.0F);
        ChatTimestampColumn scaleFour = ChatTimestampColumn.forTimeWidth(
                ChatTimestampColumn.timeRoom(WIDEST, 0.75F, 4.0F));
        assertEquals(10.0F, scaleFour.avatarX(), 0.0F);
        assertEquals(10.0F, scaleFour.separatorX()
                - (scaleFour.avatarX() + ChatAvatar.ICON_WIDTH), 0.0F);
        assertEquals(scaleFour.width + ChatTimestampColumn.SEPARATOR_WIDTH
                + ChatTimestampColumn.WORDS_GAP, scaleFour.messageX(), 0.0F);
    }

    /** A time is centred in the area by its ink. */
    @Test
    public void aTimeIsCentredByItsInk() {
        ChatTimestampColumn column = ChatTimestampColumn.forTimeWidth(0.0F);
        assertEquals(7.0F, column.timeX(18.0F), 0.0F);
    }

    /** The feed has no area: its lines keep their own edge gap and nothing else. */
    @Test
    public void theFeedIsOnlyItsEdgeGap() {
        ChatTimestampColumn column = ChatTimestampColumn.feed();
        assertFalse(column.enabled);
        assertEquals(ChatTimestampColumn.EDGE_GAP, column.messageX(), 0.0F);
    }

    /**
     * A feed line keeps two clear pixels past a mention's bar at whichever
     * edge it stands against: its rows stand in the band less the edge gap
     * at both ends.
     */
    @Test
    public void aFeedLineKeepsTwoClearPixelsPastTheMentionBar() {
        assertEquals(2.0F, ChatTimestampColumn.EDGE_GAP
                - LostTalesChatOverlayRenderer.MENTION_BAR_WIDTH, 0.0F);
        float band = 106.0F;
        float room = ChatTimestampColumn.feedRowRoom(band);
        // The rows start the edge gap in, and a row against the right edge
        // ends as far from it.
        assertEquals(ChatTimestampColumn.EDGE_GAP,
                band - ChatTimestampColumn.EDGE_GAP - room, 0.0F);
        // A row of 40 stands against the right edge by its ink.
        assertEquals(room - 40.0F,
                ChatFeedAlignment.RIGHT.rowShift(room, 0.0F, 40.0F), 0.0F);
    }

    private static int width(float smallScale, float displayPixelsPerPixel) {
        return ChatTimestampColumn.forTimeWidth(ChatTimestampColumn.timeRoom(
                WIDEST, smallScale, displayPixelsPerPixel)).width;
    }
}
