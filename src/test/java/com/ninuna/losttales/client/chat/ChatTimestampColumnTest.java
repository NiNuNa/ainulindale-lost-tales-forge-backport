package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The timestamp area holds the avatar with five clear pixels either
 * side, sphere included, and the words stand five pixels past its
 * separator. The widest clock fits in it at every GUI scale; only a font
 * with wider digits widens it, centring both. The feed has no area.
 */
public final class ChatTimestampColumnTest {

    @Test
    public void theAvatarStandsFivePixelsFromEitherSide() {
        ChatTimestampColumn column = ChatTimestampColumn.forTimeWidth(0);
        assertTrue(column.enabled);
        assertEquals(28, column.width);
        // The frame edge stands outside the window, so the gap starts at
        // the window's own edge.
        assertEquals(ChatTimestampColumn.AVATAR_GAP, column.avatarX());
        assertEquals(5, column.avatarX());
        // From the sphere's last pixel to the separator.
        assertEquals(5, column.separatorX()
                - (column.avatarX() + ChatAvatar.ICON_WIDTH));
        // And from the separator to the words.
        assertEquals(5, column.messageX()
                - (column.separatorX() + ChatTimestampColumn.SEPARATOR_WIDTH));
    }

    /**
     * {@code 12:59} is twenty-five pixels of ink at the font's own size,
     * which GUI scale 1 draws it at: with a clear pixel either side it
     * still fits the area the avatar sets, so no scale widens it.
     */
    @Test
    public void theWidestClockFitsAtEveryScale() {
        assertEquals(ChatTimestampColumn.AVATAR_WIDTH,
                ChatTimestampColumn.forTimeWidth(25).width);
        assertEquals(1.5F,
                ChatTimestampColumn.forTimeWidth(25).timeX(25.0F), 0.0F);
    }

    /** A font with wider digits widens the area and centres the avatar in it. */
    @Test
    public void aWiderClockWidensTheAreaAndCentresTheAvatar() {
        ChatTimestampColumn column = ChatTimestampColumn.forTimeWidth(31);
        assertEquals(31 + 2 * ChatTimestampColumn.TIME_GAP, column.width);
        assertEquals(7, column.avatarX());
        assertEquals(8, column.separatorX()
                - (column.avatarX() + ChatAvatar.ICON_WIDTH));
        assertEquals((float)ChatTimestampColumn.TIME_GAP,
                column.timeX(31.0F), 0.0F);
    }

    /** The feed has no area: its lines keep their own edge gap and nothing else. */
    @Test
    public void theFeedIsOnlyItsEdgeGap() {
        ChatTimestampColumn column = ChatTimestampColumn.feed();
        assertFalse(column.enabled);
        assertEquals(ChatTimestampColumn.EDGE_GAP, column.messageX());
    }
}
