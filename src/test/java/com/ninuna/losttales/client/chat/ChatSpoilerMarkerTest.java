package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A reveal names a spoiler of a message, not a built component: two
 * builds of the same message — the window's line and the feed's copy —
 * must answer alike, and everything that is not a spoiler run must be
 * left exactly as it was.
 */
public final class ChatSpoilerMarkerTest {

    @Before
    public void setUp() {
        ChatSpoilerMarker.clear();
    }

    @After
    public void tearDown() {
        ChatSpoilerMarker.clear();
    }

    private static ChatComponentText plain(String text) {
        return new ChatComponentText(text);
    }

    private static ChatComponentText spoiler(String text) {
        ChatComponentText component = new ChatComponentText(text);
        component.getChatStyle().setObfuscated(Boolean.TRUE);
        return component;
    }

    private static List<IChatComponent> body() {
        List<IChatComponent> parts = new ArrayList<IChatComponent>();
        parts.add(plain("before "));
        parts.add(spoiler("first"));
        parts.add(spoiler(" secret"));
        parts.add(plain(" between "));
        parts.add(spoiler("second"));
        return parts;
    }

    @Test
    public void unbrokenRunsShareOneSpoilerAndRevealTogether() {
        List<IChatComponent> parts = body();
        ChatSpoilerMarker.mark(parts, 0, 5000L);
        assertFalse(ChatSpoilerMarker.isMarker(parts.get(0)));
        assertTrue(ChatSpoilerMarker.isMarker(parts.get(1)));
        assertTrue(ChatSpoilerMarker.isMarker(parts.get(2)));
        assertTrue(ChatSpoilerMarker.isMarker(parts.get(4)));
        assertFalse(ChatSpoilerMarker.isRevealed(parts.get(1)));
        ChatSpoilerMarker.reveal(parts.get(2));
        // The whole first spoiler is revealed; the second is its own.
        assertTrue(ChatSpoilerMarker.isRevealed(parts.get(1)));
        assertTrue(ChatSpoilerMarker.isRevealed(parts.get(2)));
        assertFalse(ChatSpoilerMarker.isRevealed(parts.get(4)));
    }

    @Test
    public void everyBuildOfTheMessageAnswersAlike() {
        List<IChatComponent> window = body();
        List<IChatComponent> feed = body();
        ChatSpoilerMarker.mark(window, 0, 5000L);
        ChatSpoilerMarker.mark(feed, 0, 5000L);
        ChatSpoilerMarker.reveal(window.get(4));
        assertTrue(ChatSpoilerMarker.isRevealed(feed.get(4)));
        assertFalse(ChatSpoilerMarker.isRevealed(feed.get(1)));
        // Another message's spoilers are their own.
        List<IChatComponent> other = body();
        ChatSpoilerMarker.mark(other, 0, 6000L);
        assertFalse(ChatSpoilerMarker.isRevealed(other.get(4)));
    }

    @Test
    public void onlyTheBodyRangeAndOnlyServerIdsAreMarked() {
        List<IChatComponent> parts = body();
        // Marked from index 3: the leading spoiler run is a quote's and
        // stays untouched.
        ChatSpoilerMarker.mark(parts, 3, 5000L);
        assertFalse(ChatSpoilerMarker.isMarker(parts.get(1)));
        assertTrue(ChatSpoilerMarker.isMarker(parts.get(4)));
        // A message the server never named is not marked at all.
        List<IChatComponent> local = body();
        ChatSpoilerMarker.mark(local, 0, 0L);
        ChatSpoilerMarker.mark(local, 0, -3L);
        assertFalse(ChatSpoilerMarker.isMarker(local.get(1)));
    }

    @Test
    public void aRunWithItsOwnClickEventKeepsIt() {
        ChatComponentText mention = spoiler("@Aldric");
        mention.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, "losttales-chat-mention:"
                        + "aabbcc:Aldric"));
        List<IChatComponent> parts = new ArrayList<IChatComponent>();
        parts.add(spoiler("see "));
        parts.add(mention);
        ChatSpoilerMarker.mark(parts, 0, 5000L);
        assertTrue(ChatSpoilerMarker.isMarker(parts.get(0)));
        assertFalse(ChatSpoilerMarker.isMarker(parts.get(1)));
        assertEquals("losttales-chat-mention:aabbcc:Aldric",
                mention.getChatStyle().getChatClickEvent().getValue());
    }

    @Test
    public void clearingForgetsEveryReveal() {
        List<IChatComponent> parts = body();
        ChatSpoilerMarker.mark(parts, 0, 5000L);
        ChatSpoilerMarker.reveal(parts.get(1));
        assertTrue(ChatSpoilerMarker.isRevealed(parts.get(1)));
        ChatSpoilerMarker.clear();
        assertFalse(ChatSpoilerMarker.isRevealed(parts.get(1)));
    }
}
