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
     * A window filling the screen stands between the margins — without a
     * Minecraft instance the margin is zero, so the whole 1000 by 600
     * screen — with its lines laid out to that width. The window beside
     * it stays where it was, and letting go gives it back the very box
     * it had, since filling the screen changed nothing the window keeps.
     */
    @Test
    public void aWindowFillingTheScreenTakesItAllAndGivesItBack() {
        ChatWindowLayout.reset();
        ChatWindow console = ChatWindowLayout.firstWindow();
        ChatWindow conversation = ChatWindowLayout.windows().get(1);
        ChatWindowPlacement.Box before = ChatWindowPlacement.windowBounds(
                conversation, null, 1000, 600);
        ChatWindowPlacement.Box consoleBefore =
                ChatWindowPlacement.windowBounds(console, null, 1000, 600);
        assertTrue(ChatWindowLayout.setFill(conversation.getId(),
                ChatWindow.ScreenFill.FULL, false));
        ChatWindowPlacement.Box full = ChatWindowPlacement.windowBounds(
                conversation, null, 1000, 600);
        assertEquals(0.0D, full.x, 0.0001D);
        assertEquals(0.0D, full.y, 0.0001D);
        assertEquals(1000, full.width);
        assertEquals(600.0D, full.bottom(), 0.0001D);
        assertEquals(ChatWindowPlacement.chatWidthForBox(1000.0D, null),
                ChatWindowPlacement.drawnChatWidth(conversation, null, 1000));
        ChatWindowPlacement.Box consoleDuring =
                ChatWindowPlacement.windowBounds(console, null, 1000, 600);
        assertEquals(consoleBefore.x, consoleDuring.x, 0.0001D);
        assertEquals(consoleBefore.y, consoleDuring.y, 0.0001D);
        assertTrue(ChatWindowLayout.setFill(conversation.getId(),
                ChatWindow.ScreenFill.NONE, false));
        ChatWindowPlacement.Box after = ChatWindowPlacement.windowBounds(
                conversation, null, 1000, 600);
        assertEquals(before.x, after.x, 0.0001D);
        assertEquals(before.y, after.y, 0.0001D);
        assertEquals(before.width, after.width);
        assertEquals(before.height, after.height, 0.0001D);
    }

    /**
     * A window filling a half or a quarter of the screen takes exactly
     * that part: a side's half from the screen's edge to its middle, a
     * corner's quarter between the middle and two edges, on an odd
     * screen the far half taking the odd pixel.
     */
    @Test
    public void aWindowFillsTheHalfOrQuarterItIsSentTo() {
        ChatWindowLayout.reset();
        ChatWindow window = ChatWindowLayout.firstWindow();
        assertTrue(ChatWindowLayout.setFill(window.getId(),
                ChatWindow.ScreenFill.LEFT, false));
        ChatWindowPlacement.Box left = ChatWindowPlacement.windowBounds(
                window, null, 1001, 601);
        assertEquals(0.0D, left.x, 0.0001D);
        assertEquals(0.0D, left.y, 0.0001D);
        assertEquals(ChatWindowPlacement.boxWidthForChatWidth(
                ChatWindowPlacement.chatWidthForBox(500.0D, null), null),
                left.width, 0.0001D);
        assertEquals(601.0D, left.bottom(), 0.0001D);
        assertTrue(ChatWindowLayout.setFill(window.getId(),
                ChatWindow.ScreenFill.RIGHT, false));
        ChatWindowPlacement.Box right = ChatWindowPlacement.windowBounds(
                window, null, 1001, 601);
        assertEquals(500.0D, right.x, 0.0001D);
        assertEquals(ChatWindowPlacement.boxWidthForChatWidth(
                ChatWindowPlacement.chatWidthForBox(501.0D, null), null),
                right.width, 0.0001D);
        assertTrue(ChatWindowLayout.setFill(window.getId(),
                ChatWindow.ScreenFill.BOTTOM_RIGHT, false));
        ChatWindowPlacement.Box quarter = ChatWindowPlacement.windowBounds(
                window, null, 1001, 601);
        assertEquals(500.0D, quarter.x, 0.0001D);
        assertEquals(300.0D, quarter.y, 0.0001D);
        assertEquals(601.0D, quarter.bottom(), 0.0001D);
        assertTrue(ChatWindowLayout.setFill(window.getId(),
                ChatWindow.ScreenFill.TOP_LEFT, false));
        quarter = ChatWindowPlacement.windowBounds(window, null, 1001, 601);
        assertEquals(0.0D, quarter.x, 0.0001D);
        assertEquals(0.0D, quarter.y, 0.0001D);
        assertEquals(300.0D, quarter.bottom(), 0.0001D);
        assertEquals(ChatWindowPlacement.chatWidthForBox(500.0D, null),
                ChatWindowPlacement.drawnChatWidth(window, null, 1001));
    }

    /** Half way through the glide, every edge has come half its way. */
    @Test
    public void theGlideMovesEveryEdgeAShareOfTheWay() {
        ChatWindowPlacement.Box from = new ChatWindowPlacement.Box(100.0D,
                300.0D, 200, 100.0D, 35, 40.0D);
        ChatWindowPlacement.Box to = new ChatWindowPlacement.Box(0.0D, 0.0D,
                1000, 600.0D, 35, 540.0D);
        ChatWindowPlacement.Box half = ChatWindowPlacement.between(from, to,
                600, 0.5D, null);
        assertEquals(50.0D, half.x, 0.0001D);
        assertEquals((from.baseline() + to.baseline()) / 2.0D,
                half.baseline(), 0.0001D);
        assertEquals(290.0D, half.room, 0.0001D);
        assertEquals(600, half.width);
    }

    /**
     * Without a Minecraft instance a window is 160 wide, a line 12 tall,
     * the tab row 24 with its 25-row tool strip under it and the bar 25,
     * the rules on their inner rows, 2px of head-room under the tool
     * strip, and one trailing line below the baseline — so a one-line
     * window is 49 + 2 + 12 + (12 + 25) = 100 tall with 12 + 25 = 37 of
     * it below the baseline. The screen margin is zero. A window may hang
     * off either side as long as forty pixels of it stay on screen, and
     * below as long as its strip does; it never rises above the top.
     * Other windows never hold it.
     */
    @Test
    public void theScreenHoldsOnlyAStripsWorthOfAWindow() {
        ChatWindowLayout.reset();
        ChatWindow dragged = ChatWindowLayout.firstWindow();
        // Above the top edge: the strip stops on it (baseline 100 - 37 =
        // 63); fifty pixels past the left edge is allowed, since 110 of
        // the 160 stay on screen.
        ChatWindowPlacement.Anchor anchor = ChatWindowPlacement.constrainWindow(
                dragged, null, -50.0D, -50.0D, 1000, 600);
        assertEquals(-50.0D, anchor.x, 0.0001D);
        assertEquals(63.0D, anchor.baseline, 0.0001D);
        // Far past the left edge: held where forty pixels remain (40 -
        // 160 = -120).
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, -500.0D,
                300.0D, 1000, 600);
        assertEquals(-120.0D, anchor.x, 0.0001D);
        // Far past the bottom-right: forty pixels remain on the right
        // (x 1000 - 40 = 960), and the strip stays on screen below (the
        // box top at 600 - 24 = 576, so the baseline at 576 + 100 - 37 =
        // 639).
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, 2000.0D,
                2000.0D, 1000, 600);
        assertEquals(960.0D, anchor.x, 0.0001D);
        assertEquals(639.0D, anchor.baseline, 0.0001D);
        // A stored overhang is a share of the window: -25 percent stands
        // a quarter of the window off the left edge on any screen.
        ChatWindowLayout.setPosition(dragged.getId(), -25.0D, 0.0D, false);
        assertEquals(-40.0D, ChatWindowPlacement.windowBounds(dragged, null,
                1000, 600).x, 0.0001D);
        assertEquals(-40.0D, ChatWindowPlacement.windowBounds(dragged, null,
                500, 600).x, 0.0001D);
        // Past what the screen holds, the stored value is kept and the
        // box held: -90 percent asks for 144 off, and gets 120.
        ChatWindowLayout.setPosition(dragged.getId(), -90.0D, 0.0D, false);
        assertEquals(-90.0D, dragged.getOffsetX(), 0.0D);
        assertEquals(-120.0D, ChatWindowPlacement.windowBounds(dragged, null,
                1000, 600).x, 0.0001D);
        // Anywhere inside is fine, another window there or not: the
        // console window may be dropped right onto the conversation one.
        ChatWindowPlacement.Box other = ChatWindowPlacement.windowBounds(
                ChatWindowLayout.windows().get(1), null, 1000, 600);
        anchor = ChatWindowPlacement.constrainWindow(dragged, null, other.x,
                other.baseline(), 1000, 600);
        assertEquals(other.x, anchor.x, 0.0001D);
        assertEquals(other.baseline(), anchor.baseline, 0.0001D);
        // A window about to be created is placed at its smallest: a
        // baseline the one-line box fits above (63 or more) is kept.
        anchor = ChatWindowPlacement.constrainWindow(null, null, 210.0D,
                80.0D, 1000, 600);
        assertEquals(210.0D, anchor.x, 0.0001D);
        assertEquals(80.0D, anchor.baseline, 0.0001D);
    }

    /**
     * Without a Minecraft instance lines are 12 tall, the row and its
     * tool strip 49 with 2px of head-room under them, and 12 + 25 = 37
     * hang below the baseline (the trailing line and the bar); a
     * one-line window is 100 tall, so its travel on a 600px screen is
     * 600 - 100 = 500. The console window sits at 10% (baseline 0.10 *
     * 500 + 100 - 37 = 113, bottom 113 + 37 = 150), the conversation
     * window at 22% (baseline 0.22 * 500 + 63 = 173) with eight lines
     * (room 8 * 12 = 96, box 49 + 2 + 96 + 37 = 184 tall), so its top
     * (173 - (184 - 37) = 26) runs over the console window.
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
            assertEquals(113.0D, consoleBox.baseline(), 0.0001D);
            assertEquals(150.0D, consoleBox.bottom(), 0.0001D);
            assertEquals(96.0D, belowBox.room, 0.0001D);
            assertEquals(173.0D, belowBox.baseline(), 0.0001D);
            assertEquals(26.0D, belowBox.y, 0.0001D);
            assertTrue(belowBox.y < consoleBox.bottom());
            // Linked above the growing window, it moves up with it until
            // it meets the top margin; the growing window keeps growing.
            // The console window would stand at 173 - 96 - 2 - 49 - 37
            // = -11 and is held where its top meets the edge: baseline
            // 12 + 2 + 49 = 63.
            ChatWindowLayout.link(console.getId(), below.getId(), true);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(63.0D, consoleBox.baseline(), 0.0001D);
            assertEquals(0.0D, consoleBox.y, 0.0001D);
            assertEquals(96.0D, belowBox.room, 0.0001D);
            assertEquals(173.0D, belowBox.baseline(), 0.0001D);
            // Stored anchors are untouched.
            assertEquals(10.0D, console.getOffsetY(), 0.0D);
            assertEquals(22.0D, below.getOffsetY(), 0.0D);
            // With one line the linked window simply sits directly above
            // (the growing window's top at 173 - 63 = 110, the linked
            // one's baseline at 110 - 37 = 73).
            frame.lines = lines.subList(0, 1);
            consoleBox = ChatWindowPlacement.windowBounds(console, null,
                    1000, 600);
            belowBox = ChatWindowPlacement.windowBounds(below, null, 1000,
                    600);
            assertEquals(12.0D, belowBox.room, 0.0001D);
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
    public void percentAndPositionRoundTripInsideAndPastTheMargins() {
        // 1000px screen, 200px element, no margin: travel is 800.
        assertEquals(0.0D, ChatWindowPlacement.position(0.0D, 1000, 200),
                0.0001D);
        assertEquals(800.0D, ChatWindowPlacement.position(100.0D, 1000, 200),
                0.0001D);
        assertEquals(400.0D, ChatWindowPlacement.position(50.0D, 1000, 200),
                0.0001D);
        assertEquals(50.0D, ChatWindowPlacement.percent(400.0D, 1000, 200),
                0.0001D);
        // Past either margin the percent counts the element's own size:
        // twenty pixels off the left of a 200px element is -10.
        assertEquals(-10.0D, ChatWindowPlacement.percent(-20.0D, 1000, 200),
                0.0001D);
        assertEquals(-20.0D, ChatWindowPlacement.position(-10.0D, 1000, 200),
                0.0001D);
        assertEquals(125.0D, ChatWindowPlacement.percent(850.0D, 1000, 200),
                0.0001D);
        assertEquals(850.0D, ChatWindowPlacement.position(125.0D, 1000, 200),
                0.0001D);
        // Bounded to a whole element past either end.
        assertEquals(200.0D, ChatWindowPlacement.percent(5000.0D, 1000, 200),
                0.0001D);
        assertEquals(-100.0D, ChatWindowPlacement.percent(-5000.0D, 1000, 200),
                0.0001D);
        // An element larger than the screen has no travel: it stands on
        // the margin, and hangs past it by its own share past 100.
        assertEquals(0.0D, ChatWindowPlacement.percent(0.0D, 100, 200),
                0.0001D);
        assertEquals(0.0D, ChatWindowPlacement.position(100.0D, 100, 200),
                0.0001D);
        assertEquals(150.0D, ChatWindowPlacement.percent(100.0D, 100, 200),
                0.0001D);
    }

    /**
     * A window is exactly as tall as it was dragged: the message room is
     * a pixel count, not a whole number of lines: nothing rounds it, so
     * the box is exactly as tall as asked. Without a Minecraft instance
     * a line is 12 tall, the row and its tool strip 49 with 2px of
     * head-room under them and 12 + 25 = 37 hang below the baseline, so
     * a box of n lines is 49 + 2 + 12n + 37 = 88 + 12n tall.
     */
    @Test
    public void heightIsContinuousBetweenWholeLines() {
        ChatWindowLayout.reset();
        // Twelve lines: 12 * 12 = 144 of room.
        assertEquals(49 + 2 + 144 + 37,
                ChatWindowPlacement.heightForLines(12.0D, null), 0.0001D);
        // 12.37 lines: 12.37 * 12 = 148.44 of room.
        assertEquals(49 + 2 + 148.44D + 37,
                ChatWindowPlacement.heightForLines(12.37D, null), 0.0001D);
        // Every height between two whole lines is reachable, and asking
        // for one gives it back unchanged, exactly: twelve lines are
        // 88 + 144 = 232 tall, thirteen 88 + 156 = 244.
        for (int height = 232; height <= 244; height++) {
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
            assertEquals(148.44D, box.room, 0.0001D);
            assertEquals(49 + 2 + 148.44D + 37, box.height, 0.0001D);
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
        // Below, only the strip is held on screen: the box top may go
        // down to 300 - 24 = 276, a baseline of 276 + 60 - 24 = 312.
        assertEquals(312.0D, ChatWindowPlacement.keepOnScreen(400.0D, 60, 24,
                300), 0.0001D);
        // A box taller than the screen keeps its top on the edge.
        assertEquals(376.0D, ChatWindowPlacement.keepOnScreen(10.0D, 400, 24,
                300), 0.0001D);
    }
}
