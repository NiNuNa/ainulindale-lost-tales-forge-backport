package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A small window lives in a room — its chat window, or the bare screen —
 * as a window lives on a screen: held inside it and never larger than it
 * down to its least, moved and resized there without sticking to
 * anything, answering its resize band as a chat window does, and
 * remembered in the layout file from the corner of the room it was left
 * nearest, with the size the player gave it if they gave it one.
 */
public final class ChatSmallWindowsTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final double MARGIN = ChatWindowPlacement.EDGE_MARGIN;

    @Before
    public void reset() {
        ChatWindowLayout.reset();
        ChatSmallWindowPlacements.load(null);
    }

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
        ChatWindowLayout.setChangeListener(null);
        ChatSmallWindowPlacements.load(null);
    }

    @Test
    public void aWindowStaysInsideItsRoomAndShrinksWithIt() {
        ChatSmallWindow window = standing(150, 20, 120, 90);
        window.layOut(box(50, 40, 200, 150));
        assertEquals("pushed back inside", 50 + 200 - 120, window.left, 1.0E-9D);
        assertEquals(120, window.width);
        window.layOut(box(50, 40, 100, 60));
        assertEquals("a smaller room narrows it", 100, window.width);
        assertEquals(60, window.height);
        assertEquals(50.0D, window.left, 1.0E-9D);
        assertFalse(window.overflowsRoom());
        window.layOut(box(50, 40, 200, 150));
        assertEquals("and a larger one gives the size back", 120, window.width);
        window.layOut(box(50, 40, 30, 20));
        assertEquals("never under its least", window.minWidth(), window.width);
        assertTrue("past which the room cuts it", window.overflowsRoom());
    }

    @Test
    public void theResizeBandLiesJustOutsideTheBox() {
        ChatSmallWindow window = standing(100, 60, 120, 90);
        window.layOut(box(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT));
        window.drawnLeft = window.left;
        window.drawnTop = window.top;
        assertEquals(ChatWindowGestures.ResizeEdge.LEFT,
                window.edgeAt(98, 110));
        assertEquals(ChatWindowGestures.ResizeEdge.TOP_LEFT,
                window.edgeAt(97, 58));
        assertEquals(ChatWindowGestures.ResizeEdge.BOTTOM_RIGHT,
                window.edgeAt(221, 151));
        assertNull("inside, the window's own", window.edgeAt(150, 100));
        assertNull("far off, nobody's", window.edgeAt(10, 10));
    }

    @Test
    public void aPlaceIsMeasuredFromTheNearestCorner() {
        ChatSmallWindowPlacements.Placement placed =
                ChatSmallWindowPlacements.placementOf(150.0D, 10.0D, 40, 30,
                        true, 200.0D, 150.0D);
        assertTrue(placed.fromRight);
        assertFalse(placed.fromBottom);
        assertEquals(10.0D, placed.dx, 1.0E-9D);
        assertEquals(10.0D, placed.dy, 1.0E-9D);
        assertEquals("tr", placed.corner());
        assertEquals("the same room gives it back", 150.0D,
                placed.x(200.0D, 40), 1.0E-9D);
        assertEquals("a wider one keeps it as near its corner",
                400.0D - 40 - 10, placed.x(400.0D, 40), 1.0E-9D);
        assertEquals(10.0D, placed.y(300.0D, 30), 1.0E-9D);
        ChatSmallWindowPlacements.Placement moved =
                ChatSmallWindowPlacements.placementOf(10.0D, 100.0D, 40, 30,
                        false, 200.0D, 150.0D);
        assertFalse("a window only moved keeps no size", moved.isSized());
        assertEquals("bl", moved.corner());
    }

    @Test
    public void aKindResizedOpensWhereAndAsLargeAsItWasLeft() {
        ChatSmallWindows windows = screen();
        LostTalesUiHitBox room = windows.roomOf(null);
        ChatSmallWindowPlacements.remember(ChatSmallWindowKind.EMOJI, 250.0D,
                40.0D, 200, 150, true, room.width, room.height);
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.EMOJI, "",
                new StubContent(), null, box(10, 10, 100, 60));
        assertTrue(window.sized);
        assertEquals(200, window.width);
        assertEquals(150, window.height);
        assertEquals(MARGIN + 250.0D, window.left, 1.0E-9D);
        assertEquals(MARGIN + 40.0D, window.top, 1.0E-9D);
    }

    @Test
    public void aKindOnlyMovedOpensAtItsContentsOwnSize() {
        ChatSmallWindows windows = screen();
        LostTalesUiHitBox room = windows.roomOf(null);
        ChatSmallWindowPlacements.remember(ChatSmallWindowKind.TAB, 250.0D,
                40.0D, 200, 150, false, room.width, room.height);
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.TAB, "",
                new StubContent(), null, box(10, 10, 100, 60));
        assertFalse(window.sized);
        assertEquals(100, window.width);
        assertEquals(ChatSmallWindow.STRIP_HEIGHT + 60, window.height);
    }

    @Test
    public void aWindowTurnedToSomethingElseTakesItsContentsSizeUnlessGivenOne() {
        ChatSmallWindows windows = screen();
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.TAB, "",
                new StubContent(), null, box(100, 100, 180, 120));
        assertEquals("it opens round the box it is handed", 180, window.width);
        windows.refit(window);
        assertEquals(100, window.width);
        assertEquals(ChatSmallWindow.STRIP_HEIGHT + 60, window.height);
        assertEquals("its top left stays", 100.0D, window.left, 1.0E-9D);
        window.sized = true;
        window.wantedWidth = 180;
        windows.refit(window);
        assertEquals("a size the player gave stays", 180, window.wantedWidth);
    }

    @Test
    public void aMovedWindowStopsAtItsRoomsEdgeAndSticksToNothing() {
        ChatSmallWindows windows = screen();
        LostTalesUiHitBox room = windows.roomOf(null);
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.TAB, "",
                new StubContent(), null, box(100, 100, 100, 60));
        windows.armMove(window, 150, 90);
        windows.drag(150 + 1000, 90);
        assertEquals("never past the room's edge",
                room.left + room.width - window.width, window.left, 1.0E-9D);
        windows.drag(150 + 3 - (100 - MARGIN), 90);
        assertEquals("three pixels from the edge it stays three pixels off",
                room.left + 3.0D, window.left, 1.0E-9D);
        windows.release();
        assertEquals(3.0D, window.x, 1.0E-9D);
        assertEquals("and the kind is remembered where it was left", "tl",
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.TAB).corner());
    }

    @Test
    public void aWindowWaitingUndrawnIsNotInFront() {
        ChatSmallWindows windows = screen();
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.EMOJI, "",
                new StubContent(), null, box(100, 100, 100, 60));
        assertSame(window, windows.focused());
        window.hidden = true;
        assertNull(windows.focused());
        assertFalse("Escape passes it by", windows.closeFocused());
        assertTrue(window.isOpen());
    }

    @Test
    public void aWindowOpenedForAChatWindowNotDrawnStandsOnTheScreen() {
        ChatSmallWindows windows = screen();
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.EMOJI, "",
                new StubContent(), "w1", box(100, 100, 100, 60));
        assertNull(window.parentId);
        assertTrue(window.belongsTo(null));
    }

    @Test
    public void theLayoutFileKeepsEachKindsPlace() {
        ChatWindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=global tabs=global",
                "small emoji from=br dx=0.00 dy=12.50 w=120 h=160",
                "small tab from=tl dx=12.00 dy=40.00",
                "small quests from=tl dx=10.00 dy=20.00 w=140",
                "small reactions from=xx dx=1.00 dy=2.00",
                "small nonsense from=tl dx=1.00 dy=2.00 w=3 h=4"));
        ChatSmallWindowPlacements.Placement emoji =
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.EMOJI);
        assertTrue(emoji.fromRight);
        assertTrue(emoji.fromBottom);
        assertEquals(12.5D, emoji.dy, 1.0E-9D);
        assertEquals(120, emoji.width);
        assertEquals(160, emoji.height);
        ChatSmallWindowPlacements.Placement tab =
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.TAB);
        assertEquals(12.0D, tab.dx, 1.0E-9D);
        assertFalse("a place alone keeps no size", tab.isSized());
        assertNull("a size alone is skipped",
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.QUESTS));
        assertNull("a corner that is none is skipped",
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.REACTIONS));
        List<String> described = ChatWindowLayoutStore.describe();
        assertTrue(described.contains(
                "small emoji from=br dx=0.00 dy=12.50 w=120 h=160"));
        assertTrue("a place alone is written without a size",
                described.contains("small tab from=tl dx=12.00 dy=40.00"));
    }

    private static ChatSmallWindows screen() {
        ChatSmallWindows windows = new ChatSmallWindows();
        windows.bind(SCREEN_WIDTH, SCREEN_HEIGHT);
        return windows;
    }

    /** A window on the bare screen where the player put it, at the size they gave it. */
    private static ChatSmallWindow standing(double x, double y, int width,
                                            int height) {
        ChatSmallWindow window = new ChatSmallWindow(
                ChatSmallWindowKind.EMOJI, "", new StubContent(), null);
        window.x = x;
        window.y = y;
        window.wantedWidth = width;
        window.wantedHeight = height;
        return window;
    }

    private static LostTalesUiHitBox box(double left, double top,
                                         double width, double height) {
        return new LostTalesUiHitBox(left, top, width, height);
    }

    /** Content with nothing in it, for a window's own geometry. */
    private static final class StubContent extends ChatSmallWindowContent {
        @Override
        LostTalesUiSheet stripIcon() {
            return null;
        }

        @Override
        int naturalWidth() {
            return 100;
        }

        @Override
        int naturalHeight(int width) {
            return 60;
        }

        @Override
        int minWidth() {
            return 40;
        }

        @Override
        int minHeight() {
            return 20;
        }

        @Override
        void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                  double clipY, double pointerX, double pointerY, int alpha,
                  int surfaceAlpha) {
        }
    }
}
