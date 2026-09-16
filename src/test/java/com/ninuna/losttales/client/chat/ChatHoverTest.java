package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one answer about what is under the pointer decides the pointer's
 * pose: the hand exactly where a press does something, the arrow over
 * what only shows, and nothing under the pointer answers while a control
 * above it has it.
 */
public final class ChatHoverTest {

    @Test
    public void theHandIsWhereAPressActs() {
        for (ChatHover.Kind kind : new ChatHover.Kind[] {
                ChatHover.Kind.MENU_ENTRY,
                ChatHover.Kind.EMPTY_PLUS, ChatHover.Kind.SUGGESTION,
                ChatHover.Kind.PICKER_CELL, ChatHover.Kind.PICKER_LABEL,
                ChatHover.Kind.TAB_ROW, ChatHover.Kind.OTHER_BAR,
                ChatHover.Kind.CHARACTER_BUTTON, ChatHover.Kind.INDICATOR,
                ChatHover.Kind.SEND_BUTTON, ChatHover.Kind.TOOLBAR_TOGGLE,
                ChatHover.Kind.PICKER_BUTTON, ChatHover.Kind.JUMP_PILL,
                ChatHover.Kind.REPLY_CHIP, ChatHover.Kind.MESSAGE_TOOLBAR,
                ChatHover.Kind.SCROLLBAR}) {
            assertEquals(kind.name(), LostTalesMapCursor.Pose.HAND,
                    new ChatHover(kind).pose());
        }
    }

    @Test
    public void theArrowIsWhereAPressOnlyLands() {
        for (ChatHover.Kind kind : new ChatHover.Kind[] {
                ChatHover.Kind.NONE, ChatHover.Kind.CARD, ChatHover.Kind.MENU,
                ChatHover.Kind.SUGGESTIONS, ChatHover.Kind.PICKER,
                ChatHover.Kind.OVERLAY}) {
            assertEquals(kind.name(), LostTalesMapCursor.Pose.ARROW,
                    new ChatHover(kind).pose());
        }
        assertEquals(LostTalesMapCursor.Pose.ARROW, ChatHover.NONE.pose());
    }

    @Test
    public void aLineOrAWindowActsOnlyWhenAPressWouldDoSomething() {
        ChatHover line = new ChatHover(ChatHover.Kind.LINE);
        assertEquals(LostTalesMapCursor.Pose.ARROW, line.pose());
        line.acts = true;
        assertEquals(LostTalesMapCursor.Pose.HAND, line.pose());
        ChatHover window = new ChatHover(ChatHover.Kind.WINDOW);
        assertEquals(LostTalesMapCursor.Pose.ARROW, window.pose());
        window.acts = true;
        assertEquals(LostTalesMapCursor.Pose.HAND, window.pose());
        assertEquals("a strip of no window moves nothing",
                LostTalesMapCursor.Pose.ARROW,
                new ChatHover(ChatHover.Kind.STRIP).pose());
    }

    @Test
    public void aControlAskedWithThePointerAwayFindsNothing() {
        double away = ChatHover.AWAY;
        assertFalse(away >= 0.0D);
        assertFalse(away < 0.0D);
        assertFalse(away >= Double.NEGATIVE_INFINITY);
        ChatPointerRegions regions = new ChatPointerRegions();
        regions.addScreen(-1000, -1000, 1000, 1000);
        assertTrue(regions.contains(0.5D, 0.5D));
        assertFalse(regions.contains(away, away));
    }

    @Test
    public void onlyTheRowThePointerIsOnSeesIt() {
        ChatHover tab = new ChatHover(ChatHover.Kind.TAB_ROW);
        assertFalse("a row hover names its window", tab.isOnRowOf(null));
        assertFalse(new ChatHover(ChatHover.Kind.LINE).isOnRowOf(null));
    }
}
