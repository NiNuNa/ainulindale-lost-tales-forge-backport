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
 * A sub-window opens locked: measured from its window's room, riding
 * along as the window moves, carried by nothing and held on the screen.
 * Unlocked, it goes anywhere on the screen and stays there while its
 * window moves, resized by the band just outside it; locked again, where
 * it stands becomes its kind's place in the layout file, measured from
 * the corner of the room it stands nearest, with the size the player
 * gave it if they gave it one. Closed unlocked, it forgets where it was
 * carried and its kind's place too.
 */
public final class SubWindowsTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final double MARGIN = WindowPlacement.EDGE_MARGIN;
    /** Kinds of the tests' own, registered as a system registers its kinds. */
    private static final SubWindowKind PICKER =
            SubWindowKind.register("test_picker", "test");
    private static final SubWindowKind LIST =
            SubWindowKind.register("test_list", "test");
    private static final SubWindowKind CARD =
            SubWindowKind.register("test_card", "test");

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
    public void aLockedWindowRidesWithItsRoomAndIsHeldOnTheScreen() {
        SubWindow window = standing(150, 20, 120, 90);
        LostTalesUiHitBox screen = box(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        window.layOut(box(50, 40, 200, 150), screen);
        assertEquals("past its room's edge as it was put", 200.0D,
                window.left, 1.0E-9D);
        assertEquals(60.0D, window.top, 1.0E-9D);
        window.layOut(box(100, 40, 200, 150), screen);
        assertEquals("it rides along as its window moves", 250.0D,
                window.left, 1.0E-9D);
        window.layOut(box(400, 40, 200, 150), screen);
        assertEquals("never past the screen's edge",
                SCREEN_WIDTH - 120.0D, window.left, 1.0E-9D);
        window.layOut(box(0, 0, 200, 150), box(0, 0, 30, 20));
        assertEquals("never under its least", window.minWidth(), window.width);
    }

    @Test
    public void anUnlockedWindowStaysWhereItIsWhileItsWindowMoves() {
        SubWindows windows = screen();
        SubWindow window = windows.open(PICKER, "", new StubContent(), null,
                box(100, 100, 100, 60));
        assertTrue("every window opens locked", window.isLocked());
        windows.setLocked(window, false);
        assertFalse(window.isLocked());
        double left = window.left;
        window.layOut(box(300, 10, 100, 100), windows.screenRoom());
        assertEquals(left, window.left, 1.0E-9D);
    }

    @Test
    public void theResizeBandLiesJustOutsideAnUnlockedBox() {
        SubWindow window = standing(100, 60, 120, 90);
        window.locked = false;
        window.layOut(box(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                box(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT));
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
        window.locked = true;
        assertNull("a locked window keeps its size", window.edgeAt(98, 110));
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
        SubWindowPlaces.Placement outside =
                SubWindowPlaces.placementOf(-30.0D, 10.0D, 40, 30,
                        false, 200.0D, 150.0D);
        assertEquals("a place outside the room stays outside it", -30.0D,
                outside.x(200.0D, 40), 1.0E-9D);
    }

    @Test
    public void aKindLockedResizedOpensWhereAndAsLargeAsItWasLeft() {
        SubWindows windows = screen();
        LostTalesUiHitBox room = windows.roomOf(null);
        SubWindowPlaces.remember(PICKER, 250.0D,
                40.0D, 200, 150, true, room.width, room.height);
        SubWindow window = windows.open(PICKER, "",
                new StubContent(), null, box(10, 10, 100, 60));
        assertTrue(window.sized);
        assertTrue(window.isLocked());
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
        assertEquals(Math.max(100, window.minWidth()), window.width);
        assertEquals(SubWindow.STRIP_HEIGHT + 60, window.height);
    }

    @Test
    public void aWindowTurnedToSomethingElseTakesItsContentsSizeUnlessGivenOne() {
        SubWindows windows = screen();
        SubWindow window = windows.open(SubWindowKind.TAB, "",
                new StubContent(), null, box(100, 100, 180, 120));
        assertEquals("it opens round the box it is handed", 180, window.width);
        windows.refit(window);
        assertEquals(Math.max(100, window.minWidth()), window.width);
        assertEquals(SubWindow.STRIP_HEIGHT + 60, window.height);
        assertEquals("its top left stays", 100.0D, window.left, 1.0E-9D);
        window.sized = true;
        window.wantedWidth = 180;
        windows.refit(window);
        assertEquals("a size the player gave stays", 180, window.wantedWidth);
    }

    @Test
    public void aLockedWindowIsCarriedByNothingAndAnUnlockedOneGoesAnywhere() {
        SubWindows windows = screen();
        LostTalesUiHitBox screen = windows.screenRoom();
        SubWindow window = windows.open(SubWindowKind.TAB, "",
                new StubContent(), null, box(100, 100, 100, 60));
        double left = window.left;
        windows.armMove(window, 150, 90);
        assertFalse("the padlock holds it", windows.isHolding());
        windows.drag(300, 90);
        assertEquals(left, window.left, 1.0E-9D);
        windows.setLocked(window, false);
        windows.armMove(window, 150, 90);
        windows.drag(150 + 1000, 90);
        assertEquals("never past the screen's edge",
                screen.left + screen.width - window.width, window.left,
                1.0E-9D);
        windows.drag(150 - 30, 90);
        assertEquals(left - 30.0D, window.left, 1.0E-9D);
        windows.release();
        assertNull("nothing is remembered while it is unlocked",
                SubWindowPlaces.of(SubWindowKind.TAB));
        windows.setLocked(window, true);
        assertEquals("locking it makes where it stands its kind's place",
                "tl", SubWindowPlaces.of(SubWindowKind.TAB).corner());
        windows.close(window);
        windows.beginFrame();
        window.shownShare = 0.0F;
        windows.beginFrame();
        SubWindow again = windows.open(SubWindowKind.TAB, "",
                new StubContent(), null, box(100, 100, 100, 60));
        assertEquals("it opens where it was locked", left - 30.0D,
                again.left, 1.0E-9D);
    }

    @Test
    public void aWindowClosedUnlockedOpensAtItsKindsPlaceAgain() {
        SubWindows windows = screen();
        SubWindow window = windows.open(PICKER, "", new StubContent(), null,
                box(100, 100, 100, 60));
        double left = window.left;
        windows.setLocked(window, false);
        windows.armMove(window, 150, 90);
        windows.drag(250, 90);
        windows.release();
        windows.close(window);
        window.shownShare = 0.0F;
        windows.beginFrame();
        SubWindow again = windows.open(PICKER, "", new StubContent(), null,
                box(100, 100, 100, 60));
        assertTrue(again.isLocked());
        assertEquals("where its opener puts it", left, again.left, 1.0E-9D);
    }

    /**
     * A window closed while unlocked forgets where its kind was locked:
     * the kind opens where its opener puts it again, locked, and so does
     * the same window caught while it still fades.
     */
    @Test
    public void aWindowClosedUnlockedForgetsItsKindsPlace() {
        SubWindows windows = screen();
        SubWindow window = windows.open(PICKER, "", new StubContent(), null,
                box(100, 100, 100, 60));
        double left = window.left;
        windows.setLocked(window, false);
        windows.armMove(window, 150, 90);
        windows.drag(250, 90);
        windows.release();
        windows.setLocked(window, true);
        assertTrue(SubWindowPlaces.of(PICKER) != null);
        windows.setLocked(window, false);
        windows.close(window);
        assertNull(SubWindowPlaces.of(PICKER));
        SubWindow revived = windows.open(PICKER, "", new StubContent(), null,
                box(100, 100, 100, 60));
        assertSame(window, revived);
        assertTrue(revived.isLocked());
        assertEquals("where its opener puts it", left, revived.left,
                1.0E-9D);
    }

    @Test
    public void aWindowWaitingUndrawnIsNotInFront() {
        SubWindows windows = screen();
        SubWindow window = windows.open(PICKER, "",
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
        SubWindow window = windows.open(PICKER, "",
                new StubContent(), "w1", box(100, 100, 100, 60));
        assertNull(window.parentId);
        assertTrue(window.belongsTo(null));
    }

    @Test
    public void theLayoutFileKeepsEachKindsPlace() {
        WindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=global tabs=global",
                "sub test_picker from=br dx=0.00 dy=12.50 w=120 h=160",
                "sub tab from=tl dx=-12.00 dy=40.00",
                "sub test_list from=tl dx=10.00 dy=20.00 w=140",
                "sub test_card from=xx dx=1.00 dy=2.00",
                "sub nonsense from=tl dx=1.00 dy=2.00 w=3 h=4"));
        SubWindowPlaces.Placement picker =
                SubWindowPlaces.of(PICKER);
        assertTrue(picker.fromRight);
        assertTrue(picker.fromBottom);
        assertEquals(12.5D, picker.dy, 1.0E-9D);
        assertEquals(120, picker.width);
        assertEquals(160, picker.height);
        SubWindowPlaces.Placement tab =
                SubWindowPlaces.of(SubWindowKind.TAB);
        assertEquals("a place past the room's edge", -12.0D, tab.dx,
                1.0E-9D);
        assertFalse("a place alone keeps no size", tab.isSized());
        assertNull("a size alone is skipped",
                SubWindowPlaces.of(LIST));
        assertNull("a corner that is none is skipped",
                SubWindowPlaces.of(CARD));
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.contains(
                "sub test_picker from=br dx=0.00 dy=12.50 w=120 h=160"));
        assertTrue("a place alone is written without a size",
                described.contains("sub tab from=tl dx=-12.00 dy=40.00"));
    }

    private static SubWindows screen() {
        SubWindows windows = new SubWindows(new StackFade());
        windows.bind(SCREEN_WIDTH, SCREEN_HEIGHT);
        return windows;
    }

    /** A window on the bare screen where the player put it, at the size they gave it. */
    private static SubWindow standing(double x, double y, int width,
                                            int height) {
        SubWindow window = new SubWindow(
                PICKER, "", new StubContent(), null);
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
