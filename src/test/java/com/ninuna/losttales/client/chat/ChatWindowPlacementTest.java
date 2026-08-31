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
     * the strips 22 each with the rules on their inner rows, 2px of
     * head-room under the top rule, and one trailing line below the
     * baseline — so a one-line window is 68 tall with 33 of it below
     * the baseline. The screen margin is zero: a window may be dragged
     * flush against the border; other windows never hold it.
     */
    @Test
    public void onlyTheScreenEdgesHoldAWindow() {
        ChatWindowLayout.reset();
        ChatWindow dragged = ChatWindowLayout.firstWindow();
        // Past the top-left edge: the corner (baseline 68 - 33 = 35).
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                dragged, null, -50.0D, -50.0D, 1000, 600);
        assertEquals(0.0D, anchor.x, 0.0001D);
        assertEquals(35.0D, anchor.baseline, 0.0001D);
        // Past the bottom-right margins: the far corner.
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, 2000.0D,
                2000.0D, 1000, 600);
        assertEquals(840.0D, anchor.x, 0.0001D);
        assertEquals(567.0D, anchor.baseline, 0.0001D);
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
     * Without a Minecraft instance lines are 11 tall, the row 22 with
     * 2px of head-room under it, and 33 hang below the baseline (the
     * trailing line and the bar); a one-line window's travel on a 600px
     * screen is 532. The console window sits at 10% (baseline 88.2,
     * bottom 121.2), the conversation window at 22% (baseline 152.04)
     * with eight lines, so its top (40.04) runs over the console
     * window.
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
            assertEquals(88.2D, consoleBox.baseline(), 0.0001D);
            assertEquals(88.0D, belowBox.room, 0.0001D);
            assertEquals(152.04D, belowBox.baseline(), 0.0001D);
            assertEquals(40.04D, belowBox.y, 0.0001D);
            assertTrue(belowBox.y < consoleBox.bottom());
            // Linked above the growing window, it moves up with it until
            // it meets the top margin; the growing window keeps growing.
            ChatWindowLayout.link(console.getId(), below.getId(), true);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(35.0D, consoleBox.baseline(), 0.0001D);
            assertEquals(0.0D, consoleBox.y, 0.0001D);
            assertEquals(88.0D, belowBox.room, 0.0001D);
            assertEquals(152.04D, belowBox.baseline(), 0.0001D);
            // Stored anchors are untouched.
            assertEquals(10.0D, console.getOffsetY(), 0.0D);
            assertEquals(22.0D, below.getOffsetY(), 0.0D);
            // With one line the linked window simply sits directly above.
            frame.lines = lines.subList(0, 1);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(11.0D, belowBox.room, 0.0001D);
            assertEquals(belowBox.y, consoleBox.bottom(), 0.0001D);
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
        // window ends directly on its top.
        ChatWindowLayout.setPosition(below.getId(), 0.0D, 50.0D, false);
        ChatWindowLayout.link(console.getId(), below.getId(), true);
        ChatWindowPlacement.Box belowBox = ChatWindowPlacement.windowBounds(
                below, null, 1000, 600);
        ChatWindowPlacement.Box consoleBox = ChatWindowPlacement.windowBounds(
                console, null, 1000, 600);
        assertEquals(belowBox.y, consoleBox.bottom(), 0.0001D);
        ChatWindowLayout.setPosition(below.getId(), 0.0D, 80.0D, false);
        belowBox = ChatWindowPlacement.windowBounds(below, null, 1000, 600);
        consoleBox = ChatWindowPlacement.windowBounds(console, null, 1000,
                600);
        assertEquals(belowBox.y, consoleBox.bottom(), 0.0001D);
        // Linked below instead: its top follows the target's bottom.
        ChatWindowLayout.link(console.getId(), below.getId(), false);
        consoleBox = ChatWindowPlacement.windowBounds(console, null, 1000,
                600);
        assertEquals(belowBox.bottom(), consoleBox.y, 0.0001D);
    }

    @Test
    public void percentAndPositionRoundTripInsideTheMargins() {
        // 1000px screen, 200px element, no margin: travel is 800.
        assertEquals(0.0D, ChatWindowPlacement.position(0.0D, 1000, 200),
                0.0001D);
        assertEquals(800.0D, ChatWindowPlacement.position(100.0D, 1000, 200),
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
        assertEquals(0.0D, ChatWindowPlacement.position(100.0D, 100, 200),
                0.0001D);
    }

    /**
     * A window is exactly as tall as it was dragged: the message room is
     * a pixel count, not a whole number of lines: nothing rounds it, so
     * the box is exactly as tall as asked. Without a Minecraft instance
     * a line is 11 tall, the row 22 with 2px of head-room under it and
     * 33 hang below the baseline, so a box of n lines is 57 + 11n
     * tall.
     */
    @Test
    public void heightIsContinuousBetweenWholeLines() {
        ChatWindowLayout.reset();
        assertEquals(22 + 2 + 132 + 33,
                ChatWindowPlacement.heightForLines(12.0D, null), 0.0001D);
        assertEquals(22 + 2 + 136.07D + 33,
                ChatWindowPlacement.heightForLines(12.37D, null), 0.0001D);
        // Every height between two whole lines is reachable, and asking
        // for one gives it back unchanged, exactly.
        for (int height = 183; height <= 194; height++) {
            double lines = ChatWindowPlacement.linesForHeight(height, null);
            assertEquals(height,
                    ChatWindowPlacement.heightForLines(lines, null),
                    0.0001D);
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
            assertEquals(136.07D, box.room, 0.0001D);
            assertEquals(22 + 2 + 136.07D + 33, box.height, 0.0001D);
            assertEquals(13, LostTalesChatOverlayRenderer.linesForRoom(
                    (float)box.room, 1.0F));
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
        // top: the baseline moves down until the top sits on the edge.
        assertEquals(36.0D, ChatWindowPlacement.keepOnScreen(20.0D, 60, 24,
                300), 0.0001D);
        // Plenty of room: untouched.
        assertEquals(150.0D, ChatWindowPlacement.keepOnScreen(150.0D, 60, 24,
                300), 0.0001D);
        // Never past the bottom edge either.
        assertEquals(276.0D, ChatWindowPlacement.keepOnScreen(400.0D, 60, 24,
                300), 0.0001D);
        // A box taller than the screen keeps its top on the edge.
        assertEquals(376.0D, ChatWindowPlacement.keepOnScreen(10.0D, 400, 24,
                300), 0.0001D);
    }
}
