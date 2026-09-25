package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatFrame;
import com.ninuna.losttales.client.chat.ChatLayout;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WindowPlacementTest {

    @After
    public void cleanUp() {
        ChatLayout.reset();
    }

    /**
     * A window filling the screen stands between the margins with room
     * for its frame — without a Minecraft instance the margin is zero,
     * so the 1000 by 600 screen less the frame's two pixels on every side —
     * with its lines laid out to that width. The window beside
     * it stays where it was, and letting go gives it back the very box
     * it had, since filling the screen changed nothing the window keeps.
     */
    @Test
    public void aWindowFillingTheScreenTakesItAllAndGivesItBack() {
        ChatLayout.reset();
        Window console = WindowLayout.firstWindow();
        Window conversation = WindowLayout.windows().get(1);
        WindowPlacement.Box before = WindowPlacement.windowBounds(
                conversation, null, 1000, 600);
        WindowPlacement.Box consoleBefore =
                WindowPlacement.windowBounds(console, null, 1000, 600);
        assertTrue(WindowLayout.setFill(conversation.getId(),
                Window.ScreenFill.FULL, false));
        WindowPlacement.Box full = WindowPlacement.windowBounds(
                conversation, null, 1000, 600);
        int frame = WindowPlacement.FRAME_WIDTH;
        assertEquals(frame, full.x, 0.0001D);
        assertEquals(frame, full.y, 0.0001D);
        assertEquals(WindowPlacement.boxWidthForChatWidth(
                WindowPlacement.chatWidthForBox(1000.0D - 2 * frame,
                        null), null), full.width, 0.0001D);
        assertEquals(600.0D - frame, full.bottom(), 0.0001D);
        assertEquals(WindowPlacement.chatWidthForBox(1000.0D - 2 * frame,
                null), WindowPlacement.drawnChatWidth(conversation, null,
                1000));
        WindowPlacement.Box consoleDuring =
                WindowPlacement.windowBounds(console, null, 1000, 600);
        assertEquals(consoleBefore.x, consoleDuring.x, 0.0001D);
        assertEquals(consoleBefore.y, consoleDuring.y, 0.0001D);
        assertTrue(WindowLayout.setFill(conversation.getId(),
                Window.ScreenFill.NONE, false));
        WindowPlacement.Box after = WindowPlacement.windowBounds(
                conversation, null, 1000, 600);
        assertEquals(before.x, after.x, 0.0001D);
        assertEquals(before.y, after.y, 0.0001D);
        assertEquals(before.width, after.width);
        assertEquals(before.height, after.height, 0.0001D);
    }

    /**
     * A window filling a half or a quarter of the screen takes exactly
     * that part less its frame's two pixels on every side: a side's half
     * from the screen's edge to its middle, a corner's quarter between
     * the middle and two edges, on an odd screen the far half taking the
     * odd pixel, so two windows filling neighbouring parts stand four
     * pixels apart with their frames side by side.
     */
    @Test
    public void aWindowFillsTheHalfOrQuarterItIsSentTo() {
        ChatLayout.reset();
        Window window = WindowLayout.firstWindow();
        assertTrue(WindowLayout.setFill(window.getId(),
                Window.ScreenFill.LEFT, false));
        WindowPlacement.Box left = WindowPlacement.windowBounds(
                window, null, 1001, 601);
        int frame = WindowPlacement.FRAME_WIDTH;
        assertEquals(frame, left.x, 0.0001D);
        assertEquals(frame, left.y, 0.0001D);
        assertEquals(WindowPlacement.boxWidthForChatWidth(
                WindowPlacement.chatWidthForBox(500.0D - 2 * frame, null),
                null), left.width, 0.0001D);
        assertEquals(601.0D - frame, left.bottom(), 0.0001D);
        assertTrue(WindowLayout.setFill(window.getId(),
                Window.ScreenFill.RIGHT, false));
        WindowPlacement.Box right = WindowPlacement.windowBounds(
                window, null, 1001, 601);
        assertEquals(500.0D + frame, right.x, 0.0001D);
        assertEquals(WindowPlacement.boxWidthForChatWidth(
                WindowPlacement.chatWidthForBox(501.0D - 2 * frame, null),
                null), right.width, 0.0001D);
        assertTrue(WindowLayout.setFill(window.getId(),
                Window.ScreenFill.BOTTOM_RIGHT, false));
        WindowPlacement.Box quarter = WindowPlacement.windowBounds(
                window, null, 1001, 601);
        assertEquals(500.0D + frame, quarter.x, 0.0001D);
        assertEquals(300.0D + frame, quarter.y, 0.0001D);
        assertEquals(601.0D - frame, quarter.bottom(), 0.0001D);
        assertTrue(WindowLayout.setFill(window.getId(),
                Window.ScreenFill.TOP_LEFT, false));
        quarter = WindowPlacement.windowBounds(window, null, 1001, 601);
        assertEquals(frame, quarter.x, 0.0001D);
        assertEquals(frame, quarter.y, 0.0001D);
        assertEquals(300.0D - frame, quarter.bottom(), 0.0001D);
        assertEquals(WindowPlacement.chatWidthForBox(500.0D - 2 * frame,
                null), WindowPlacement.drawnChatWidth(window, null, 1001));
    }

    /** Half way through the glide, every edge has come half its way. */
    @Test
    public void theGlideMovesEveryEdgeAShareOfTheWay() {
        WindowPlacement.Box from = new WindowPlacement.Box(100.0D,
                300.0D, 200, 100.0D, 35, 40.0D);
        WindowPlacement.Box to = new WindowPlacement.Box(0.0D, 0.0D,
                1000, 600.0D, 35, 540.0D);
        WindowPlacement.Box half = WindowPlacement.between(from, to,
                600, 0.5D, null);
        assertEquals(50.0D, half.x, 0.0001D);
        assertEquals((from.baseline() + to.baseline()) / 2.0D,
                half.baseline(), 0.0001D);
        assertEquals(290.0D, half.room, 0.0001D);
        assertEquals(600, half.width);
    }

    /**
     * Without a Minecraft instance a window is 160 wide, a line 12 tall,
     * the tab row 22 with its 17-row tool strip under it and the bar 23,
     * the rules on their inner rows, 2px of head-room under the tool
     * strip, and one trailing line below the baseline — so a window
     * given one line of its own is 39 + 2 + 12 + (12 + 23) = 88 tall
     * with 12 + 23 = 35 of it below the baseline. The screen margin is
     * zero, and a window keeps two pixels inside the screen for its
     * frame. A window may hang off either side and off the bottom as
     * long as a twentieth of it stays on screen, eight pixels at least;
     * its strip never rises above the top. Other windows never hold it.
     */
    @Test
    public void theScreenHoldsOnlyAStripsWorthOfAWindow() {
        ChatLayout.reset();
        Window dragged = WindowLayout.firstWindow();
        WindowLayout.setWindowHeight(dragged.getId(),
                WindowPlacement.heightForLines(1.0D, null), false);
        // Above the top edge: the strip stops the frame's two pixels
        // below it (baseline 2 + 88 - 35 = 55); fifty pixels past the left
        // edge is allowed, since 110 of the 160 stay on screen.
        WindowPlacement.Anchor anchor = WindowPlacement.constrainWindow(
                dragged, null, -50.0D, -50.0D, 1000, 600);
        assertEquals(-50.0D, anchor.x, 0.0001D);
        assertEquals(55.0D, anchor.baseline, 0.0001D);
        // Far past the left edge: held where eight pixels remain, a
        // twentieth of 160 (8 - 160 = -152).
        anchor = WindowPlacement.constrainWindow(dragged, null, -500.0D,
                300.0D, 1000, 600);
        assertEquals(-152.0D, anchor.x, 0.0001D);
        // Far past the bottom-right: eight pixels remain on the right
        // (x 1000 - 8 = 992) and eight below (the box top at 592, so the
        // baseline at 592 + 88 - 35 = 645).
        anchor = WindowPlacement.constrainWindow(dragged, null, 2000.0D,
                2000.0D, 1000, 600);
        assertEquals(992.0D, anchor.x, 0.0001D);
        assertEquals(645.0D, anchor.baseline, 0.0001D);
        // A stored overhang is a share of the window: -25 percent stands
        // a quarter of the window past the frame's two pixels on any
        // screen.
        WindowLayout.setPosition(dragged.getId(), -25.0D, 0.0D, false);
        assertEquals(-38.0D, WindowPlacement.windowBounds(dragged, null,
                1000, 600).x, 0.0001D);
        assertEquals(-38.0D, WindowPlacement.windowBounds(dragged, null,
                500, 600).x, 0.0001D);
        // Past what the screen holds, the stored value is kept and the
        // box held: -100 percent asks for 2 - 160 = -158, and gets -152.
        WindowLayout.setPosition(dragged.getId(), -100.0D, 0.0D, false);
        assertEquals(-100.0D, dragged.getOffsetX(), 0.0D);
        assertEquals(-152.0D, WindowPlacement.windowBounds(dragged, null,
                1000, 600).x, 0.0001D);
        // Anywhere inside is fine, another window there or not: the
        // console window may be dropped right onto the conversation one.
        WindowPlacement.Box other = WindowPlacement.windowBounds(
                WindowLayout.windows().get(1), null, 1000, 600);
        anchor = WindowPlacement.constrainWindow(dragged, null, other.x,
                other.baseline(), 1000, 600);
        assertEquals(other.x, anchor.x, 0.0001D);
        assertEquals(other.baseline(), anchor.baseline, 0.0001D);
        // A window about to be created is placed at its smallest: a
        // baseline the one-line box fits above (63 or more) is kept.
        anchor = WindowPlacement.constrainWindow(null, null, 210.0D,
                80.0D, 1000, 600);
        assertEquals(210.0D, anchor.x, 0.0001D);
        assertEquals(80.0D, anchor.baseline, 0.0001D);
    }

    /**
     * Without a Minecraft instance lines are 12 tall, the row and its
     * tool strip 39 with 2px of head-room under them, and 12 + 23 = 35
     * hang below the baseline (the trailing line and the bar); a
     * one-line window is 88 tall, and a window keeps two pixels inside
     * the screen for its frame, so its travel on a 592px screen is
     * 592 - 88 - 4 = 500. The console window, one line tall, sits at 10%
     * (baseline 2 + 0.10 * 500 + 88 - 35 = 105, bottom 105 + 35 = 140),
     * the conversation window at 22% (baseline 2 + 0.22 * 500 + 53 = 165)
     * eight lines tall (room 8 * 12 = 96, box 39 + 2 + 96 + 35 = 172
     * tall), so its top (165 - (172 - 35) = 28) runs over the console
     * window.
     */
    @Test
    public void aTallWindowOverlapsItsNeighbourAndMovesALinkedOne() {
        ChatLayout.reset();
        Window console = WindowLayout.firstWindow();
        Window below = WindowLayout.windows().get(1);
        WindowLayout.setPosition(console.getId(), 0.0D, 10.0D, false);
        WindowLayout.setPosition(below.getId(), 0.0D, 22.0D, false);
        WindowLayout.setWindowHeight(console.getId(),
                WindowPlacement.heightForLines(1.0D, null), false);
        WindowLayout.setWindowHeight(below.getId(),
                WindowPlacement.heightForLines(8.0D, null), false);
        // Unlinked, the console window is no border: it stays put and the
        // tall window shows every line, over it.
        WindowPlacement.Box consoleBox =
                WindowPlacement.windowBounds(console, null, 1000, 592);
        WindowPlacement.Box belowBox =
                WindowPlacement.windowBounds(below, null, 1000, 592);
        assertEquals(105.0D, consoleBox.baseline(), 0.0001D);
        assertEquals(140.0D, consoleBox.bottom(), 0.0001D);
        assertEquals(96.0D, belowBox.room, 0.0001D);
        assertEquals(165.0D, belowBox.baseline(), 0.0001D);
        assertEquals(28.0D, belowBox.y, 0.0001D);
        assertTrue(belowBox.y < consoleBox.bottom());
        // Linked above the tall window, it moves up with it until it meets
        // the top margin; the tall window keeps its height. The console
        // window would stand at 165 - 96 - 2 - 39 - 4 - 35 = -11, the
        // window gap between them, and is held where its top is the
        // frame's two pixels below the edge: baseline 2 + 12 + 2 + 39 = 55.
        WindowLayout.link(console.getId(), below.getId(), true);
        consoleBox = WindowPlacement.windowBounds(console, null, 1000,
                592);
        belowBox = WindowPlacement.windowBounds(below, null, 1000, 592);
        assertEquals(55.0D, consoleBox.baseline(), 0.0001D);
        assertEquals(2.0D, consoleBox.y, 0.0001D);
        assertEquals(96.0D, belowBox.room, 0.0001D);
        assertEquals(165.0D, belowBox.baseline(), 0.0001D);
        // Stored anchors are untouched.
        assertEquals(10.0D, console.getOffsetY(), 0.0D);
        assertEquals(22.0D, below.getOffsetY(), 0.0D);
        // One line tall, the window has the linked one simply sit a
        // window gap above it (its top at 165 - 53 = 112, the linked
        // one's baseline at 112 - 4 - 35 = 73).
        WindowLayout.setWindowHeight(below.getId(),
                WindowPlacement.heightForLines(1.0D, null), false);
        consoleBox = WindowPlacement.windowBounds(console, null, 1000,
                592);
        belowBox = WindowPlacement.windowBounds(below, null, 1000, 592);
        assertEquals(12.0D, belowBox.room, 0.0001D);
        assertEquals(73.0D, consoleBox.baseline(), 0.0001D);
        assertEquals(belowBox.y - WindowPlacement.WINDOW_GAP,
                consoleBox.bottom(), 0.0001D);
    }

    /**
     * A window's height is its own, never what its tabs hold: one that
     * follows the game's chat height is as tall as that setting — twenty
     * lines without a Minecraft instance — whether it holds no line or
     * many, so bringing another tab forward never resizes it.
     */
    @Test
    public void aWindowIsAsTallAsItsOwnHeightWhateverItHolds() {
        ChatLayout.reset();
        Window window = WindowLayout.firstWindow();
        assertEquals(0.0D, window.getOwnHeight(), 0.0D);
        ChatFrame frame = ChatFrame.of(window);
        try {
            double empty = WindowPlacement.currentHeight(window, null);
            List<net.minecraft.client.gui.ChatLine> lines =
                    new java.util.ArrayList<net.minecraft.client.gui.ChatLine>();
            for (int index = 0; index < 8; index++) {
                lines.add(new net.minecraft.client.gui.ChatLine(0,
                        new net.minecraft.util.ChatComponentText("x"), index));
            }
            frame.lines = lines;
            assertEquals(WindowPlacement.heightForLines(20.0D, null), empty,
                    0.0D);
            assertEquals(empty, WindowPlacement.currentHeight(window, null),
                    0.0D);
            WindowLayout.setWindowHeight(window.getId(),
                WindowPlacement.heightForLines(3.0D, null), false);
            assertEquals(WindowPlacement.heightForLines(3.0D, null),
                    WindowPlacement.currentHeight(window, null), 0.0D);
            frame.lines = lines.subList(0, 1);
            assertEquals(WindowPlacement.heightForLines(3.0D, null),
                    WindowPlacement.currentHeight(window, null), 0.0D);
        } finally {
            ChatFrame.clear();
        }
    }

    @Test
    public void aLinkedWindowKeepsItsGapToItsTarget() {
        ChatLayout.reset();
        Window console = WindowLayout.firstWindow();
        Window below = WindowLayout.windows().get(1);
        WindowLayout.setWindowHeight(console.getId(),
                WindowPlacement.heightForLines(3.0D, null), false);
        WindowLayout.setWindowHeight(below.getId(),
                WindowPlacement.heightForLines(3.0D, null), false);
        // The console window sits above the conversation window and is
        // linked to it; wherever the conversation window is, the console
        // window ends a window gap above its top, their frames side by
        // side.
        WindowLayout.setPosition(below.getId(), 0.0D, 50.0D, false);
        WindowLayout.link(console.getId(), below.getId(), true);
        WindowPlacement.Box belowBox = WindowPlacement.windowBounds(
                below, null, 1000, 600);
        WindowPlacement.Box consoleBox = WindowPlacement.windowBounds(
                console, null, 1000, 600);
        int gap = WindowPlacement.WINDOW_GAP;
        assertEquals(belowBox.y - gap, consoleBox.bottom(), 0.0001D);
        WindowLayout.setPosition(below.getId(), 0.0D, 80.0D, false);
        belowBox = WindowPlacement.windowBounds(below, null, 1000, 600);
        consoleBox = WindowPlacement.windowBounds(console, null, 1000,
                600);
        assertEquals(belowBox.y - gap, consoleBox.bottom(), 0.0001D);
        // Linked below instead: its top follows the target's bottom, the
        // same gap apart.
        WindowLayout.link(console.getId(), below.getId(), false);
        consoleBox = WindowPlacement.windowBounds(console, null, 1000,
                600);
        assertEquals(belowBox.bottom() + gap, consoleBox.y, 0.0001D);
    }

    @Test
    public void percentAndPositionRoundTripInsideAndPastTheMargins() {
        // 1000px screen, 200px element, no margin: travel is 800.
        assertEquals(0.0D, WindowPlacement.position(0.0D, 1000, 200),
                0.0001D);
        assertEquals(800.0D, WindowPlacement.position(100.0D, 1000, 200),
                0.0001D);
        assertEquals(400.0D, WindowPlacement.position(50.0D, 1000, 200),
                0.0001D);
        assertEquals(50.0D, WindowPlacement.percent(400.0D, 1000, 200),
                0.0001D);
        // Past either margin the percent counts the element's own size:
        // twenty pixels off the left of a 200px element is -10.
        assertEquals(-10.0D, WindowPlacement.percent(-20.0D, 1000, 200),
                0.0001D);
        assertEquals(-20.0D, WindowPlacement.position(-10.0D, 1000, 200),
                0.0001D);
        assertEquals(125.0D, WindowPlacement.percent(850.0D, 1000, 200),
                0.0001D);
        assertEquals(850.0D, WindowPlacement.position(125.0D, 1000, 200),
                0.0001D);
        // Bounded to a whole element past either end.
        assertEquals(200.0D, WindowPlacement.percent(5000.0D, 1000, 200),
                0.0001D);
        assertEquals(-100.0D, WindowPlacement.percent(-5000.0D, 1000, 200),
                0.0001D);
        // An element larger than the screen has no travel: it stands on
        // the margin, and hangs past it by its own share past 100.
        assertEquals(0.0D, WindowPlacement.percent(0.0D, 100, 200),
                0.0001D);
        assertEquals(0.0D, WindowPlacement.position(100.0D, 100, 200),
                0.0001D);
        assertEquals(150.0D, WindowPlacement.percent(100.0D, 100, 200),
                0.0001D);
    }

    /**
     * A window is exactly as tall as it was dragged: the message room is
     * a pixel count, not a whole number of lines: nothing rounds it, so
     * the box is exactly as tall as asked. Without a Minecraft instance
     * a line is 12 tall, the row and its tool strip 39 with 2px of
     * head-room under them and 12 + 23 = 35 hang below the baseline, so
     * a box of n lines is 39 + 2 + 12n + 35 = 76 + 12n tall.
     */
    @Test
    public void heightIsContinuousBetweenWholeLines() {
        ChatLayout.reset();
        // Twelve lines: 12 * 12 = 144 of room.
        assertEquals(39 + 2 + 144 + 35,
                WindowPlacement.heightForLines(12.0D, null), 0.0001D);
        // 12.37 lines: 12.37 * 12 = 148.44 of room.
        assertEquals(39 + 2 + 148.44D + 35,
                WindowPlacement.heightForLines(12.37D, null), 0.0001D);
        // Every height between two whole lines is a room of its own, and
        // the room gives the height back unchanged, exactly: twelve lines
        // are 76 + 144 = 220 tall, thirteen 76 + 156 = 232.
        for (int height = 220; height <= 232; height++) {
            double room = WindowPlacement.roomForHeight(height, null);
            assertEquals(height, WindowPlacement.heightForRoom(room, null),
                    0.0001D);
        }
        // The box a resized window is drawn in carries that room, and
        // the draw shows one more line than the whole ones to clip.
        Window window = WindowLayout.firstWindow();
        WindowLayout.setWindowHeight(window.getId(),
                WindowPlacement.heightForLines(12.37D, null), true);
        ChatFrame frame = ChatFrame.of(window);
        List<net.minecraft.client.gui.ChatLine> lines =
                new java.util.ArrayList<net.minecraft.client.gui.ChatLine>();
        for (int index = 0; index < 40; index++) {
            lines.add(new net.minecraft.client.gui.ChatLine(0,
                    new net.minecraft.util.ChatComponentText("x"), index));
        }
        frame.lines = lines;
        try {
            WindowPlacement.Box box = WindowPlacement.windowBounds(
                    window, null, 1000, 600);
            assertEquals(148.44D, box.room, 0.0001D);
            assertEquals(39 + 2 + 148.44D + 35, box.height, 0.0001D);
        } finally {
            ChatFrame.clear();
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
        assertEquals(304, WindowPlacement.wrapWidth(320, 1.0F));
        assertEquals(0.95D, WindowPlacement.TEXT_WIDTH_SHARE, 0.0D);
        // Half the chat scale is twice the chat units, same share.
        assertEquals(608, WindowPlacement.wrapWidth(320, 0.5F));
        // Never past nothing, whatever the width.
        assertTrue(WindowPlacement.wrapWidth(1, 1.0F) >= 1);
    }

    @Test
    public void aWindowGrowingPastTheTopIsPushedDownNotOff() {
        // A 60px-tall box (24 of it below the baseline) anchored near the
        // top: the baseline moves down until the top sits on the margin,
        // the strip whole on screen.
        assertEquals(38.0D, WindowPlacement.holdBaseline(20.0D, 60, 24,
                300), 0.0001D);
        // Plenty of room: untouched.
        assertEquals(150.0D, WindowPlacement.holdBaseline(150.0D, 60, 24,
                300), 0.0001D);
        // A box taller than the screen keeps its top on the margin.
        assertEquals(378.0D, WindowPlacement.holdBaseline(10.0D, 400, 24,
                300), 0.0001D);
    }

    /**
     * Past the left, right and bottom edges a window may go ninety-five
     * hundredths of the way out: a twentieth of it stays in view, and
     * never less than a stretch the pointer can take hold of.
     */
    @Test
    public void aWindowMayGoNinetyFivePercentPastThreeEdges() {
        // Below: a 400px box keeps 20px in view, its top at 280.
        assertEquals(280.0D + 376.0D, WindowPlacement.holdBaseline(
                2000.0D, 400, 24, 300), 0.0001D);
        // A small box keeps the least hold, 8px: its top at 292.
        assertEquals(292.0D + 36.0D, WindowPlacement.holdBaseline(
                2000.0D, 60, 24, 300), 0.0001D);
        // Past the left edge a 300px box keeps 15px in view.
        assertEquals(-285.0D, WindowPlacement.holdOnScreen(-1000.0D, 300,
                640), 0.0001D);
        // Past the right edge too.
        assertEquals(625.0D, WindowPlacement.holdOnScreen(1000.0D, 300,
                640), 0.0001D);
        // A box that fits stays where it was put.
        assertEquals(100.0D, WindowPlacement.holdOnScreen(100.0D, 300,
                640), 0.0001D);
    }
}
