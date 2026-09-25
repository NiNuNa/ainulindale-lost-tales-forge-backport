package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.client.window.WindowHover;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The chat's answer about what of it is under the pointer decides the
 * pointer's pose as the window's own answer does: the hand exactly where
 * a press does something, the arrow over what only shows.
 */
public final class ChatHoverTest {

    @Test
    public void theHandIsWhereAPressActs() {
        for (ChatHover.Kind kind : new ChatHover.Kind[] {
                ChatHover.Kind.MENU_ENTRY,
                ChatHover.Kind.EMPTY_PLUS, ChatHover.Kind.SUGGESTION,
                ChatHover.Kind.PICKER_CELL, ChatHover.Kind.PICKER_LABEL,
                ChatHover.Kind.OTHER_BAR,
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
                ChatHover.Kind.MENU, ChatHover.Kind.SUGGESTIONS,
                ChatHover.Kind.PICKER}) {
            assertEquals(kind.name(), LostTalesMapCursor.Pose.ARROW,
                    new ChatHover(kind).pose());
        }
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
    }

    @Test
    public void aMemberListsEdgeResizesSideways() {
        assertEquals(LostTalesMapCursor.Pose.RESIZE_HORIZONTAL,
                new ChatHover(ChatHover.Kind.MEMBER_LIST_EDGE).pose());
    }

    @Test
    public void theChatsAnswerIsContentToTheScreen() {
        ChatHover line = new ChatHover(ChatHover.Kind.LINE);
        assertTrue(line.is(WindowHover.Kind.CONTENT));
        assertTrue(ChatHover.is(line, ChatHover.Kind.LINE));
        assertFalse(ChatHover.is(WindowHover.NONE, ChatHover.Kind.LINE));
        assertFalse(line.isOnRowOf(null));
    }
}
