package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Arrays;
import java.util.Collections;
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
 * A small window sticks to the screen's margins and to the windows beside
 * it within a chat window's reach and no further, answers its resize band
 * as a chat window does, and is remembered in the layout file as a share
 * of the screen, with the size the player gave it if they gave it one.
 */
public final class ChatSmallWindowsTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final double MARGIN = ChatWindowPlacement.EDGE_MARGIN;
    private static final double GAP = ChatWindowPlacement.WINDOW_GAP;

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
    public void aMovedWindowSticksToTheScreensMarginWithinReach() {
        LostTalesUiHitBox moved = ChatSmallWindowSnap.moved(
                box(MARGIN + 4, 100, 80, 60), none(), SCREEN_WIDTH,
                SCREEN_HEIGHT);
        assertEquals(MARGIN, moved.left, 1.0E-9D);
        assertEquals("the other axis stays where it was put", 100.0D,
                moved.top, 1.0E-9D);

        LostTalesUiHitBox free = ChatSmallWindowSnap.moved(
                box(MARGIN + 20, 100, 80, 60), none(), SCREEN_WIDTH,
                SCREEN_HEIGHT);
        assertEquals("out of reach it stays", MARGIN + 20, free.left, 1.0E-9D);
    }

    @Test
    public void aMovedWindowSticksBesideAnotherWithTheGapWindowsKeep() {
        List<LostTalesUiHitBox> others = Collections.singletonList(
                box(100, 100, 50, 50));
        LostTalesUiHitBox beside = ChatSmallWindowSnap.moved(
                box(100 + 50 + GAP + 3, 110, 80, 40), others, SCREEN_WIDTH,
                SCREEN_HEIGHT);
        assertEquals(100 + 50 + GAP, beside.left, 1.0E-9D);

        LostTalesUiHitBox inLine = ChatSmallWindowSnap.moved(
                box(102, 100 + 50 + GAP + 2, 80, 40), others, SCREEN_WIDTH,
                SCREEN_HEIGHT);
        assertEquals("just under it, its left edge in line", 100.0D,
                inLine.left, 1.0E-9D);
        assertEquals("and its top the gap under it", 100 + 50 + GAP,
                inLine.top, 1.0E-9D);

        LostTalesUiHitBox far = ChatSmallWindowSnap.moved(
                box(300, 220, 80, 40), others, SCREEN_WIDTH, SCREEN_HEIGHT);
        assertEquals("a window far away pulls nothing", 300.0D, far.left,
                1.0E-9D);
    }

    @Test
    public void aResizeSticksOnlyTheEdgeItCarries() {
        LostTalesUiHitBox stretched = ChatSmallWindowSnap.resized(
                box(100, 100, SCREEN_WIDTH - MARGIN - 100 - 3, 60),
                ChatWindowGestures.ResizeEdge.RIGHT, none(), SCREEN_WIDTH,
                SCREEN_HEIGHT);
        assertEquals(100.0D, stretched.left, 1.0E-9D);
        assertEquals(SCREEN_WIDTH - MARGIN, stretched.left + stretched.width,
                1.0E-9D);

        LostTalesUiHitBox kept = ChatSmallWindowSnap.resized(
                box(MARGIN + 3, 100, 80, 60),
                ChatWindowGestures.ResizeEdge.RIGHT, none(), SCREEN_WIDTH,
                SCREEN_HEIGHT);
        assertEquals("the edge across stays put", MARGIN + 3, kept.left,
                1.0E-9D);
    }

    @Test
    public void theResizeBandLiesJustOutsideTheBox() {
        ChatSmallWindow window = new ChatSmallWindow(ChatSmallWindowKind.EMOJI, "",
                new StubContent(), 100, 60, 120, 90);
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
    public void aPlaceIsAShareOfTheScreenAndComesBackWhereItWasLeft() {
        ChatSmallWindowPlacements.Placement placed =
                ChatSmallWindowPlacements.placementOf(250.0D, 40.0D, 120, 90,
                        true, SCREEN_WIDTH, SCREEN_HEIGHT);
        assertEquals(250.0D, placed.left(SCREEN_WIDTH, 120), 1.0E-9D);
        assertEquals(40.0D, placed.top(SCREEN_HEIGHT, 90), 1.0E-9D);
        assertTrue("a wider screen keeps it in the same part of it",
                placed.left(SCREEN_WIDTH * 2, 120) > 250.0D);
        ChatSmallWindowPlacements.Placement corner =
                ChatSmallWindowPlacements.placementOf(-50.0D, 9999.0D, 120, 90,
                        true, SCREEN_WIDTH, SCREEN_HEIGHT);
        assertEquals(0.0D, corner.xPercent, 1.0E-9D);
        assertEquals(100.0D, corner.yPercent, 1.0E-9D);
        ChatSmallWindowPlacements.Placement moved =
                ChatSmallWindowPlacements.placementOf(250.0D, 40.0D, 120, 90,
                        false, SCREEN_WIDTH, SCREEN_HEIGHT);
        assertFalse("a window only moved keeps no size", moved.isSized());
    }

    @Test
    public void aKindResizedOpensWhereAndAsLargeAsItWasLeft() {
        ChatSmallWindowPlacements.remember(ChatSmallWindowKind.EMOJI, 250.0D,
                40.0D, 200, 150, true, SCREEN_WIDTH, SCREEN_HEIGHT);
        ChatSmallWindows windows = new ChatSmallWindows();
        windows.bind(SCREEN_WIDTH, SCREEN_HEIGHT);
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.EMOJI, "",
                new StubContent(), box(10, 10, 100, 60));
        assertTrue(window.sized);
        assertEquals(200, window.width);
        assertEquals(150, window.height);
        assertEquals(250.0D, window.left, 1.0E-9D);
        assertEquals(40.0D, window.top, 1.0E-9D);
    }

    @Test
    public void aKindOnlyMovedOpensAtItsContentsOwnSize() {
        ChatSmallWindowPlacements.remember(ChatSmallWindowKind.TAB, 250.0D,
                40.0D, 200, 150, false, SCREEN_WIDTH, SCREEN_HEIGHT);
        ChatSmallWindows windows = new ChatSmallWindows();
        windows.bind(SCREEN_WIDTH, SCREEN_HEIGHT);
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.TAB, "",
                new StubContent(), box(10, 10, 100, 60));
        assertFalse(window.sized);
        assertEquals(100, window.width);
        assertEquals(ChatSmallWindow.STRIP_HEIGHT + 60, window.height);
    }

    @Test
    public void aWindowTurnedToSomethingElseTakesItsContentsSizeUnlessGivenOne() {
        ChatSmallWindows windows = new ChatSmallWindows();
        windows.bind(SCREEN_WIDTH, SCREEN_HEIGHT);
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.TAB, "",
                new StubContent(), box(100, 100, 180, 120));
        assertEquals("it opens round the box it is handed", 180, window.width);
        windows.refit(window);
        assertEquals(100, window.width);
        assertEquals(ChatSmallWindow.STRIP_HEIGHT + 60, window.height);
        assertEquals("its top left stays", 100.0D, window.left, 1.0E-9D);
        window.sized = true;
        window.width = 180;
        windows.refit(window);
        assertEquals("a size the player gave stays", 180, window.width);
    }

    @Test
    public void aWindowWaitingUndrawnIsNotInFront() {
        ChatSmallWindows windows = new ChatSmallWindows();
        windows.bind(SCREEN_WIDTH, SCREEN_HEIGHT);
        ChatSmallWindow window = windows.open(ChatSmallWindowKind.EMOJI, "",
                new StubContent(), box(100, 100, 100, 60));
        assertSame(window, windows.focused());
        window.hidden = true;
        assertNull(windows.focused());
        assertFalse("Escape passes it by", windows.closeFocused());
        assertTrue(window.isOpen());
    }

    @Test
    public void theLayoutFileKeepsEachKindsPlace() {
        ChatWindowLayoutStore.load(Arrays.asList(
                "window w1 locked=false x=0.00 y=0.00 active=global tabs=global",
                "small emoji x=100.00 y=62.50 w=120 h=160",
                "small tab x=12.00 y=40.00",
                "small quests x=10.00 y=20.00 w=140",
                "small nonsense x=1.00 y=2.00 w=3 h=4"));
        ChatSmallWindowPlacements.Placement emoji =
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.EMOJI);
        assertEquals(100.0D, emoji.xPercent, 1.0E-9D);
        assertEquals(62.5D, emoji.yPercent, 1.0E-9D);
        assertEquals(120, emoji.width);
        assertEquals(160, emoji.height);
        ChatSmallWindowPlacements.Placement tab =
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.TAB);
        assertEquals(12.0D, tab.xPercent, 1.0E-9D);
        assertFalse("a place alone keeps no size", tab.isSized());
        assertNull("a size alone is skipped",
                ChatSmallWindowPlacements.of(ChatSmallWindowKind.QUESTS));
        List<String> described = ChatWindowLayoutStore.describe();
        assertTrue(described.contains(
                "small emoji x=100.00 y=62.50 w=120 h=160"));
        assertTrue("a place alone is written without a size",
                described.contains("small tab x=12.00 y=40.00"));
    }

    private static LostTalesUiHitBox box(double left, double top,
                                         double width, double height) {
        return new LostTalesUiHitBox(left, top, width, height);
    }

    private static List<LostTalesUiHitBox> none() {
        return Collections.<LostTalesUiHitBox>emptyList();
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
