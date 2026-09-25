package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatLayout;
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
public final class SubWindowsTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final double MARGIN = WindowPlacement.EDGE_MARGIN;

    @Before
    public void reset() {
        ChatLayout.reset();
        WindowFrame.clearFrames();
        SubWindowPlaces.load(null);
    }

    @After
    public void cleanUp() {
        ChatLayout.reset();
        WindowLayout.setChangeListener(null);
        SubWindowPlaces.load(null);
    }

    @Test
    public void aWindowStaysInsideItsRoomAndShrinksWithIt() {
        SubWindow window = standing(150, 20, 120, 90);
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
        SubWindow window = standing(100, 60, 120, 90);
        window.layOut(box(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT));
        window.drawnLeft = window.left;
        window.drawnTop = window.top;
        assertEquals(WindowGestures.ResizeEdge.LEFT,
                window.edgeAt(98, 110));
        assertEquals(WindowGestures.ResizeEdge.TOP_LEFT,
                window.edgeAt(97, 58));
        assertEquals(WindowGestures.ResizeEdge.BOTTOM_RIGHT,
                window.edgeAt(221, 151));
        assertNull("inside, the window's own", window.edgeAt(150, 100));
        assertNull("far off, nobody's", window.edgeAt(10, 10));
    }

    @Test
    public void aPlaceIsMeasuredFromTheNearestCorner() {
        SubWindowPlaces.Placement placed =
                SubWindowPlaces.placementOf(150.0D, 10.0D, 40, 30,
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
        SubWindowPlaces.Placement moved =
                SubWindowPlaces.placementOf(10.0D, 100.0D, 40, 30,
                        false, 200.0D, 150.0D);
        assertFalse("a window only moved keeps no size", moved.isSized());
        assertEquals("bl", moved.corner());
    }

    @Test
    public void aKindResizedOpensWhereAndAsLargeAsItWasLeft() {
        SubWindows windows = screen();
        LostTalesUiHitBox room = windows.roomOf(null);
        SubWindowPlaces.remember(SubWindowKind.EMOJI, 250.0D,
                40.0D, 200, 150, true, room.width, room.height);
        SubWindow window = windows.open(SubWindowKind.EMOJI, "",
                new StubContent(), null, box(10, 10, 100, 60));
        assertTrue(window.sized);
        assertEquals(200, window.width);
        assertEquals(150, window.height);
        assertEquals(MARGIN + 250.0D, window.left, 1.0E-9D);
        assertEquals(MARGIN + 40.0D, window.top, 1.0E-9D);
    }

    @Test
    public void aKindOnlyMovedOpensAtItsContentsOwnSize() {
        SubWindows windows = screen();
        LostTalesUiHitBox room = windows.roomOf(null);
        SubWindowPlaces.remember(SubWindowKind.TAB, 250.0D,
                40.0D, 200, 150, false, room.width, room.height);
        SubWindow window = windows.open(SubWindowKind.TAB, "",
                new StubContent(), null, box(10, 10, 100, 60));
        assertFalse(window.sized);
        assertEquals(100, window.width);
        assertEquals(SubWindow.STRIP_HEIGHT + 60, window.height);
    }

    @Test
    public void aWindowTurnedToSomethingElseTakesItsContentsSizeUnlessGivenOne() {
        SubWindows windows = screen();
        SubWindow window = windows.open(SubWindowKind.TAB, "",
                new StubContent(), null, box(100, 100, 180, 120));
        assertEquals("it opens round the box it is handed", 180, window.width);
        windows.refit(window);
        assertEquals(100, window.width);
        assertEquals(SubWindow.STRIP_HEIGHT + 60, window.height);
        assertEquals("its top left stays", 100.0D, window.left, 1.0E-9D);
        window.sized = true;
        window.wantedWidth = 180;
        windows.refit(window);
        assertEquals("a size the player gave stays", 180, window.wantedWidth);
    }

    @Test
    public void aMovedWindowStopsAtItsRoomsEdgeAndSticksToNothing() {
        SubWindows windows = screen();
        LostTalesUiHitBox room = windows.roomOf(null);
        SubWindow window = windows.open(SubWindowKind.TAB, "",
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
                SubWindowPlaces.of(SubWindowKind.TAB).corner());
    }

    @Test
    public void aWindowWaitingUndrawnIsNotInFront() {
        SubWindows windows = screen();
        SubWindow window = windows.open(SubWindowKind.EMOJI, "",
                new StubContent(), null, box(100, 100, 100, 60));
        assertSame(window, windows.focused());
        window.hidden = true;
        assertNull(windows.focused());
        assertFalse("Escape passes it by", windows.closeFocused());
        assertTrue(window.isOpen());
    }

    @Test
    public void aWindowOpenedForAChatWindowNotDrawnStandsOnTheScreen() {
        SubWindows windows = screen();
        SubWindow window = windows.open(SubWindowKind.EMOJI, "",
                new StubContent(), "w1", box(100, 100, 100, 60));
        assertNull(window.parentId);
        assertTrue(window.belongsTo(null));
    }

    @Test
    public void theLayoutFileKeepsEachKindsPlace() {
        WindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=global tabs=global",
                "small emoji from=br dx=0.00 dy=12.50 w=120 h=160",
                "small tab from=tl dx=12.00 dy=40.00",
                "small quests from=tl dx=10.00 dy=20.00 w=140",
                "small reactions from=xx dx=1.00 dy=2.00",
                "small nonsense from=tl dx=1.00 dy=2.00 w=3 h=4"));
        SubWindowPlaces.Placement emoji =
                SubWindowPlaces.of(SubWindowKind.EMOJI);
        assertTrue(emoji.fromRight);
        assertTrue(emoji.fromBottom);
        assertEquals(12.5D, emoji.dy, 1.0E-9D);
        assertEquals(120, emoji.width);
        assertEquals(160, emoji.height);
        SubWindowPlaces.Placement tab =
                SubWindowPlaces.of(SubWindowKind.TAB);
        assertEquals(12.0D, tab.dx, 1.0E-9D);
        assertFalse("a place alone keeps no size", tab.isSized());
        assertNull("a size alone is skipped",
                SubWindowPlaces.of(SubWindowKind.QUESTS));
        assertNull("a corner that is none is skipped",
                SubWindowPlaces.of(SubWindowKind.REACTIONS));
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.contains(
                "small emoji from=br dx=0.00 dy=12.50 w=120 h=160"));
        assertTrue("a place alone is written without a size",
                described.contains("small tab from=tl dx=12.00 dy=40.00"));
    }

    private static SubWindows screen() {
        SubWindows windows = new SubWindows();
        windows.bind(SCREEN_WIDTH, SCREEN_HEIGHT);
        return windows;
    }

    /** A window on the bare screen where the player put it, at the size they gave it. */
    private static SubWindow standing(double x, double y, int width,
                                            int height) {
        SubWindow window = new SubWindow(
                SubWindowKind.EMOJI, "", new StubContent(), null);
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
    private static final class StubContent extends SubWindowContent {
        @Override
        public LostTalesUiSheet stripIcon() {
            return null;
        }

        @Override
        public int naturalWidth() {
            return 100;
        }

        @Override
        public int naturalHeight(int width) {
            return 60;
        }

        @Override
        public int minWidth() {
            return 40;
        }

        @Override
        public int minHeight() {
            return 20;
        }

        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                  double clipY, double pointerX, double pointerY, int alpha,
                  int surfaceAlpha) {
        }
    }
}
