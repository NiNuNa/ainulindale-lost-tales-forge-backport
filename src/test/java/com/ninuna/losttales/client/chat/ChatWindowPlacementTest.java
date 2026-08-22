package com.ninuna.losttales.client.chat;

import java.util.Collections;
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
     * the bar 13 tall with an 11 gap: a one-line window, with 5 of padding above and below its line, is 58
     * tall with 29 of it below the baseline.
     */
    @Test
    public void otherWindowsAreWallsForDraggingAndPlacement() {
        ChatWindowLayout.reset();
        ChatWindow dragged = ChatWindowLayout.firstWindow();
        // The dragged window rests at the top-left (baseline 4 + 29 = 33).
        ChatWindowPlacement.Box wall = new ChatWindowPlacement.Box(
                200.0D, 4.0D, 160, 58, 29);
        List<ChatWindowPlacement.Box> walls = Collections.singletonList(wall);
        // Sliding right along the wall stops a margin short of its edge.
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                dragged, null, 120.0D, 33.0D, 1000, 600, walls);
        assertEquals(36.0D, anchor.x, 0.0001D);
        assertEquals(33.0D, anchor.baseline, 0.0001D);
        // Clear of the wall vertically, the same x is fine.
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, 120.0D,
                300.0D, 1000, 600, walls);
        assertEquals(120.0D, anchor.x, 0.0001D);
        assertEquals(300.0D, anchor.baseline, 0.0001D);
        // Screen margins still apply.
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, -50.0D,
                -50.0D, 1000, 600, walls);
        assertEquals(4.0D, anchor.x, 0.0001D);
        assertEquals(33.0D, anchor.baseline, 0.0001D);
        // A new window dropped onto a wall lands in the nearest clear
        // spot, a margin away: just below it rather than beside it.
        anchor = ChatWindowPlacement.constrainWindow(null, null, 210.0D,
                40.0D, 1000, 600, walls);
        assertEquals(210.0D, anchor.x, 0.0001D);
        assertEquals(95.0D, anchor.baseline, 0.0001D);
        // No walls, no change beyond the margins.
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, 120.0D,
                33.0D, 1000, 600, Collections.<ChatWindowPlacement.Box>emptyList());
        assertEquals(120.0D, anchor.x, 0.0001D);
    }

    /**
     * Without a Minecraft instance lines are 11 tall, the row 13, the
     * bar 29 below the baseline; a one-line window's travel on a 600px
     * screen is 534. The console window sits at 10% (baseline 86.4,
     * bottom 115.4), the conversation window at 22% (baseline 150.48)
     * with eight lines, so its top (44.48) runs into the console window.
     */
    @Test
    public void aGrowingWindowStopsAtABorderAndMovesALinkedWindow() {
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
            // Unlinked, the console window is a border: it stays put and
            // the growing window shows only the one line that fits.
            ChatWindowPlacement.Box consoleBox =
                    ChatWindowPlacement.windowBounds(console, null, 1000, 600);
            ChatWindowPlacement.Box belowBox =
                    ChatWindowPlacement.windowBounds(below, null, 1000, 600);
            assertEquals(86.4D, consoleBox.baseline(), 0.0001D);
            assertEquals(1, belowBox.lines);
            assertEquals(150.48D, belowBox.baseline(), 0.0001D);
            assertTrue(belowBox.y >= consoleBox.bottom() + 4.0D - 0.0001D);
            // Linked above the growing window, it moves up with it until
            // it meets the top margin; then the growing window stops at
            // the six lines that fit below it.
            ChatWindowLayout.link(console.getId(), below.getId(), true);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(33.0D, consoleBox.baseline(), 0.0001D);
            assertEquals(4.0D, consoleBox.y, 0.0001D);
            assertEquals(6, belowBox.lines);
            assertTrue(belowBox.y >= consoleBox.bottom() + 4.0D - 0.0001D);
            assertTrue(belowBox.y < consoleBox.bottom() + 4.0D + 11.0D);
            // Stored anchors are untouched.
            assertEquals(10.0D, console.getOffsetY(), 0.0D);
            assertEquals(22.0D, below.getOffsetY(), 0.0D);
            // With one line the linked window simply sits a margin above.
            frame.lines = lines.subList(0, 1);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(1, belowBox.lines);
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
        // The linked window is not a wall for its target.
        assertTrue(ChatWindowPlacement.wallsExcept(below, null, 1000, 600)
                .isEmpty());
        assertEquals(1, ChatWindowPlacement.wallsExcept(console, null, 1000,
                600).size());
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
