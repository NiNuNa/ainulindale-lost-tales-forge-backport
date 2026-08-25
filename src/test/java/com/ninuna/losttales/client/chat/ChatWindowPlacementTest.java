package com.ninuna.losttales.client.chat;

import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ChatWindowPlacementTest {

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
    }

    /**
     * Without a Minecraft instance a window is 160 wide, a line 11 tall,
     * the strips 17 each with the rules on their inner rows, and one
     * trailing line below the baseline — so a one-line window is 56 tall
     * with 28 of it below the baseline.
     * Only the screen margins hold a window; other windows never do.
     */
    @Test
    public void onlyTheScreenEdgesHoldAWindow() {
        ChatWindowLayout.reset();
        ChatWindow dragged = ChatWindowLayout.firstWindow();
        // Past the top-left margins: the corner (baseline 4 + 28 = 32).
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                dragged, null, -50.0D, -50.0D, 1000, 600);
        assertEquals(4.0D, anchor.x, 0.0001D);
        assertEquals(32.0D, anchor.baseline, 0.0001D);
        // Past the bottom-right margins: the far corner.
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, 2000.0D,
                2000.0D, 1000, 600);
        assertEquals(836.0D, anchor.x, 0.0001D);
        assertEquals(568.0D, anchor.baseline, 0.0001D);
        // Anywhere inside is fine, another window there or not: the
        // console window may be dropped right onto the conversation one.
        ChatWindowPlacement.Box other = ChatWindowPlacement.windowBounds(
                ChatWindowLayout.windows().get(1), null, 1000, 600);
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, other.x,
                other.baseline(), 1000, 600);
        assertEquals(other.x, anchor.x, 0.0001D);
        assertEquals(other.baseline(), anchor.baseline, 0.0001D);
        // A window about to be created is placed at its smallest.
        anchor = ChatWindowPlacement.constrainWindow(null, null, 210.0D,
                40.0D, 1000, 600);
        assertEquals(210.0D, anchor.x, 0.0001D);
        assertEquals(40.0D, anchor.baseline, 0.0001D);
    }

    /**
     * Without a Minecraft instance lines are 11 tall, the row 17, and
     * 28 hang below the baseline (the trailing line and the bar); a
     * one-line window's travel on a 600px screen is 536. The console
     * window sits at 10% (baseline 85.6, bottom 113.6), the conversation
     * window at 22% (baseline 149.92) with eight lines, so its top
     * (44.92) runs over the console window.
     */
    @Test
    public void aGrowingWindowOverlapsItsNeighbourAndMovesALinkedOne() {
        ChatWindowLayout.reset();
        ChatWindow console = ChatWindowLayout.firstWindow();
        ChatWindow below = ChatWindowLayout.windows().get(1);
        ChatWindowLayout.setPosition(console.getId(), 0.0D, 10.0D, false);
        ChatWindowLayout.setPosition(below.getId(), 0.0D, 22.0D, false);
        ChatWindowFrame frame = ChatWindowFrame.of(below);
        List<net.minecraft.client.gui.ChatLine> lines =
                new java.util.ArrayList<net.minecraft.client.gui.ChatLine>();
        for (int index = 0; index < 8; index++) {
            lines.add(new net.minecraft.client.gui.ChatLine(0,
                    new net.minecraft.util.ChatComponentText("x"), index));
        }
        frame.lines = lines;
        try {
            // Unlinked, the console window is no border: it stays put
            // and the growing window shows every line, over it.
            ChatWindowPlacement.Box consoleBox =
                    ChatWindowPlacement.windowBounds(console, null, 1000, 600);
            ChatWindowPlacement.Box belowBox =
                    ChatWindowPlacement.windowBounds(below, null, 1000, 600);
            assertEquals(85.6D, consoleBox.baseline(), 0.0001D);
            assertEquals(8 * 11, belowBox.room);
            assertEquals(149.92D, belowBox.baseline(), 0.0001D);
            assertEquals(44.92D, belowBox.y, 0.0001D);
            assertTrue(belowBox.y < consoleBox.bottom());
            // Linked above the growing window, it moves up with it until
            // it meets the top margin; the growing window keeps growing.
            ChatWindowLayout.link(console.getId(), below.getId(), true);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(32.0D, consoleBox.baseline(), 0.0001D);
            assertEquals(4.0D, consoleBox.y, 0.0001D);
            assertEquals(8 * 11, belowBox.room);
            assertEquals(149.92D, belowBox.baseline(), 0.0001D);
            // Stored anchors are untouched.
            assertEquals(10.0D, console.getOffsetY(), 0.0D);
            assertEquals(22.0D, below.getOffsetY(), 0.0D);
            // With one line the linked window simply sits a margin above.
            frame.lines = lines.subList(0, 1);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(11, belowBox.room);
            assertEquals(belowBox.y - 4.0D, consoleBox.bottom(), 0.0001D);
        } finally {
            ChatWindowFrame.clear();
        }
    }

    @Test
    public void aLinkedWindowKeepsItsGapToItsTarget() {
        ChatWindowLayout.reset();
        ChatWindow console = ChatWindowLayout.firstWindow();
        ChatWindow below = ChatWindowLayout.windows().get(1);
        // The console window sits above the conversation window and is
        // linked to it; wherever the conversation window is, the console
        // window ends a margin above its top.
        ChatWindowLayout.setPosition(below.getId(), 0.0D, 50.0D, false);
        ChatWindowLayout.link(console.getId(), below.getId(), true);
        ChatWindowPlacement.Box belowBox = ChatWindowPlacement.windowBounds(
                below, null, 1000, 600);
        ChatWindowPlacement.Box consoleBox = ChatWindowPlacement.windowBounds(
                console, null, 1000, 600);
        assertEquals(belowBox.y - 4.0D, consoleBox.bottom(), 0.0001D);
        ChatWindowLayout.setPosition(below.getId(), 0.0D, 80.0D, false);
        belowBox = ChatWindowPlacement.windowBounds(below, null, 1000, 600);
        consoleBox = ChatWindowPlacement.windowBounds(console, null, 1000,
                600);
        assertEquals(belowBox.y - 4.0D, consoleBox.bottom(), 0.0001D);
        // Linked below instead: its top follows the target's bottom.
        ChatWindowLayout.link(console.getId(), below.getId(), false);
        consoleBox = ChatWindowPlacement.windowBounds(console, null, 1000,
                600);
        assertEquals(belowBox.bottom() + 4.0D, consoleBox.y, 0.0001D);
    }

    @Test
    public void percentAndPositionRoundTripInsideTheMargins() {
        // 1000px screen, 200px element: travel is 1000 - 200 - 8.
        assertEquals(4.0D, ChatWindowPlacement.position(0.0D, 1000, 200),
                0.0001D);
        assertEquals(796.0D, ChatWindowPlacement.position(100.0D, 1000, 200),
                0.0001D);
        assertEquals(400.0D, ChatWindowPlacement.position(50.0D, 1000, 200),
                0.0001D);
        assertEquals(50.0D, ChatWindowPlacement.percent(400.0D, 1000, 200),
                0.0001D);
        assertEquals(0.0D, ChatWindowPlacement.percent(-20.0D, 1000, 200),
                0.0001D);
        assertEquals(100.0D, ChatWindowPlacement.percent(5000.0D, 1000, 200),
                0.0001D);
        // An element larger than the screen has no travel.
        assertEquals(0.0D, ChatWindowPlacement.percent(4.0D, 100, 200),
                0.0001D);
        assertEquals(4.0D, ChatWindowPlacement.position(100.0D, 100, 200),
                0.0001D);
    }

    /**
     * A window is exactly as tall as it was dragged: the message room is
     * a pixel count, not a whole number of lines. Without a Minecraft
     * instance a line is 11 tall, the row 17 and 28 hang below the
     * baseline, so a box of n lines is 45 + round(11n) tall and every
     * pixel of drag between two whole lines lands on a box of its own.
     */
    @Test
    public void heightIsContinuousBetweenWholeLines() {
        ChatWindowLayout.reset();
        assertEquals(17 + 132 + 28,
                ChatWindowPlacement.heightForLines(12.0D, null));
        assertEquals(17 + 136 + 28,
                ChatWindowPlacement.heightForLines(12.37D, null));
        // Every height between two whole lines is reachable, and asking
        // for one gives it back unchanged.
        for (int height = 177; height <= 188; height++) {
            double lines = ChatWindowPlacement.linesForHeight(height, null);
            assertEquals(height,
                    ChatWindowPlacement.heightForLines(lines, null));
        }
        // The box a resized window is drawn in carries that room, and
        // the draw shows one more line than the whole ones to clip.
        ChatWindow window = ChatWindowLayout.firstWindow();
        ChatWindowLayout.setWindowLines(window.getId(), 12.37D, true);
        ChatWindowFrame frame = ChatWindowFrame.of(window);
        List<net.minecraft.client.gui.ChatLine> lines =
                new java.util.ArrayList<net.minecraft.client.gui.ChatLine>();
        for (int index = 0; index < 40; index++) {
            lines.add(new net.minecraft.client.gui.ChatLine(0,
                    new net.minecraft.util.ChatComponentText("x"), index));
        }
        frame.lines = lines;
        try {
            ChatWindowPlacement.Box box = ChatWindowPlacement.windowBounds(
                    window, null, 1000, 600);
            assertEquals(136, box.room);
            assertEquals(17 + 136 + 28, box.height);
            assertEquals(13, LostTalesChatOverlayRenderer.linesForRoom(
                    box.room, 1.0F));
        } finally {
            ChatWindowFrame.clear();
        }
    }

    /**
     * Messages fill a share of the window and no more, so a wide window
     * and a narrow one keep the same margin at the right; the share is
     * of the window, not of the chat scale, so it looks the same at
     * every scale.
     */
    @Test
    public void messagesFillTheirShareOfTheWindowAndNoMore() {
        assertEquals(304, ChatWindowPlacement.wrapWidth(320, 1.0F));
        assertEquals(0.95D, ChatWindowPlacement.TEXT_WIDTH_SHARE, 0.0D);
        // Half the chat scale is twice the chat units, same share.
        assertEquals(608, ChatWindowPlacement.wrapWidth(320, 0.5F));
        // Never past nothing, whatever the width.
        assertTrue(ChatWindowPlacement.wrapWidth(1, 1.0F) >= 1);
    }

    @Test
    public void aWindowGrowingPastTheTopIsPushedDownNotOff() {
        // A 60px-tall box (24 of it below the baseline) anchored near the
        // top: the baseline moves down until the top sits on the margin.
        assertEquals(40.0D, ChatWindowPlacement.keepOnScreen(20.0D, 60, 24,
                300), 0.0001D);
        // Plenty of room: untouched.
        assertEquals(150.0D, ChatWindowPlacement.keepOnScreen(150.0D, 60, 24,
                300), 0.0001D);
        // Never past the bottom margin either.
        assertEquals(272.0D, ChatWindowPlacement.keepOnScreen(400.0D, 60, 24,
                300), 0.0001D);
        // A box taller than the screen keeps its top on the margin.
        assertEquals(380.0D, ChatWindowPlacement.keepOnScreen(10.0D, 400, 24,
                300), 0.0001D);
    }
}
