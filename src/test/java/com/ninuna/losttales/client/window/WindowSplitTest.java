package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatLayout;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Split view: two pages of a window shown together, the second right
 * after the first in the row, each side keeping a quarter of the room at
 * least. A split goes where both its pages go, ends when one leaves, and
 * a locked window holds it, though its divider still moves.
 */
public final class WindowSplitTest {
    private static final String PAGE = "split_page";
    private static final String OTHER_PAGE = "split_other_page";

    private final Tab a = new Tab("a");
    private final Tab b = new Tab("b");
    private final Tab c = new Tab("c");
    private final Tab d = new Tab("d");

    @BeforeClass
    public static void registerPages() {
        WindowPages.Factory empty = new WindowPages.Factory() {
            @Override
            public PageContent create() {
                return new EmptyPage();
            }
        };
        if (WindowPages.byId(PAGE) == null) {
            WindowPages.register(PAGE, "gui.test.split.page",
                    new ItemStack(Items.book), null, empty);
        }
        if (WindowPages.byId(OTHER_PAGE) == null) {
            WindowPages.register(OTHER_PAGE, "gui.test.split.other",
                    new ItemStack(Items.map), null, empty);
        }
    }

    @Before
    public void reset() {
        ChatLayout.reset();
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
    }

    @After
    public void cleanUp() {
        ChatLayout.reset();
    }

    /* ---- The geometry ---- */

    @Test
    public void theRoomIsSharedWithTheDividerBetween() {
        WindowSplit split = new WindowSplit(this.a, this.b, false, 0.5D);
        assertArrayEquals(new double[] {0.0D, 10.0D, 50.0D, 90.0D},
                split.box(true, 0.0D, 10.0D, 103.0D, 90.0D), 0.0D);
        assertArrayEquals(new double[] {53.0D, 10.0D, 103.0D, 90.0D},
                split.box(false, 0.0D, 10.0D, 103.0D, 90.0D), 0.0D);
        WindowSplit stacked = split.turned(true);
        assertArrayEquals(new double[] {0.0D, 0.0D, 80.0D, 48.0D},
                stacked.box(true, 0.0D, 0.0D, 80.0D, 99.0D), 0.0D);
        assertArrayEquals(new double[] {0.0D, 51.0D, 80.0D, 99.0D},
                stacked.box(false, 0.0D, 0.0D, 80.0D, 99.0D), 0.0D);
    }

    @Test
    public void eachSideKeepsAQuarterAtLeast() {
        assertEquals(0.25D, WindowSplit.clampShare(0.1D), 0.0D);
        assertEquals(0.75D, WindowSplit.clampShare(0.9D), 0.0D);
        assertEquals(0.5D, WindowSplit.clampShare(Double.NaN), 0.0D);
        assertEquals(0.25D, WindowSplit.shareAt(0.0D, 0.0D, 103.0D), 0.0D);
        assertEquals(0.6D, WindowSplit.shareAt(60.0D, 0.0D, 103.0D), 0.0D);
        assertEquals("swapping keeps each side's room",
                0.7D, new WindowSplit(this.a, this.b, false, 0.3D).swapped().share(), 1.0E-9D);
    }

    /* ---- Letting a carried tab go over a window's content ---- */

    @Test
    public void aTabSplitsOnTheNearestEdgeWithinAQuarter() {
        LostTalesUiHitBox room = new LostTalesUiHitBox(0.0D, 0.0D, 100.0D, 80.0D);
        assertEquals(SplitDrop.LEFT, SplitDrop.edgeAt(room, 10.0D, 40.0D));
        assertEquals(SplitDrop.RIGHT, SplitDrop.edgeAt(room, 95.0D, 30.0D));
        assertEquals(SplitDrop.TOP, SplitDrop.edgeAt(room, 50.0D, 5.0D));
        assertEquals(SplitDrop.BOTTOM, SplitDrop.edgeAt(room, 50.0D, 78.0D));
        assertEquals("the middle splits nothing", -1, SplitDrop.edgeAt(room, 50.0D, 40.0D));
        assertEquals("off the content splits nothing", -1, SplitDrop.edgeAt(room, 120.0D, 40.0D));
        assertArrayEquals(new double[] {0.0D, 0.0D, 48.0D, 80.0D},
                SplitDrop.half(room, SplitDrop.LEFT), 0.0D);
    }

    @Test
    public void aConversationGoesOnlySideBySide() {
        PageTab page = WindowPages.tab(PAGE);
        PageTab other = WindowPages.tab(OTHER_PAGE);
        assertTrue(SplitDrop.takes(page, other, SplitDrop.TOP));
        assertTrue(SplitDrop.takes(this.a, page, SplitDrop.LEFT));
        assertTrue(SplitDrop.takes(page, this.a, SplitDrop.RIGHT));
        assertTrue("two conversations side by side", SplitDrop.takes(this.a, this.b, SplitDrop.RIGHT));
        assertFalse("never over or under a page", SplitDrop.takes(this.a, page, SplitDrop.BOTTOM));
        assertFalse("never over or under each other", SplitDrop.takes(this.a, this.b, SplitDrop.TOP));
        assertFalse("never itself", SplitDrop.takes(page, page, SplitDrop.LEFT));
    }

    @Test
    public void twoConversationsShowOnlySideBySide() {
        WindowSplit across = new WindowSplit(this.a, this.b, false, 0.5D);
        assertTrue(WindowDrawing.shows(across, this.a));
        assertFalse(WindowDrawing.shows(across.turned(true), this.a));
        PageTab page = WindowPages.tab(PAGE);
        PageTab other = WindowPages.tab(OTHER_PAGE);
        assertTrue("two pages either way",
                WindowDrawing.shows(new WindowSplit(page, other, true, 0.5D), page));
    }

    /* ---- The layout ---- */

    @Test
    public void aSplitBringsTheSecondPageBesideTheFirstAndToTheFront() {
        Window window = Unlocking.of(WindowLayout.addWindow(row(this.a, this.c, this.b), this.a));
        assertTrue(WindowLayout.split(this.a, this.b));
        assertEquals(row(this.a, this.b, this.c), window.getTabs());
        assertSame(this.b, window.getActiveTab());
        assertSame(this.a, window.shownSplit().other(this.b));
        assertFalse("a page stands in one split at most", WindowLayout.split(this.a, this.c));
    }

    @Test
    public void aPageFromAnotherWindowComesOverForTheSplit() {
        Window window = Unlocking.of(WindowLayout.addWindow(row(this.a), this.a));
        Window other = Unlocking.of(WindowLayout.addWindow(row(this.c, this.b), this.c));
        assertTrue(WindowLayout.split(this.a, this.b));
        assertEquals(row(this.a, this.b), window.getTabs());
        assertEquals(row(this.c), other.getTabs());
    }

    @Test
    public void closingOnePageLeavesTheOtherAsAPlainTab() {
        Window window = Unlocking.of(WindowLayout.addWindow(row(this.a, this.b, this.c), this.a));
        WindowLayout.split(this.a, this.b);
        assertTrue(WindowLayout.close(this.b));
        assertNull(window.splitOf(this.a));
        assertEquals(row(this.a, this.c), window.getTabs());
    }

    @Test
    public void bothPagesTornOffCarryTheSplit() {
        Unlocking.of(WindowLayout.addWindow(row(this.a, this.b, this.c), this.a));
        WindowLayout.split(this.a, this.b);
        Window torn = Tearing.off(Arrays.<WindowTab>asList(this.a, this.b), 20.0D, 20.0D);
        assertNotNull(torn);
        assertNotNull(torn.splitOf(this.a));
        Window rest = WindowLayout.windowOf(this.c);
        assertTrue(rest.getSplits().isEmpty());
    }

    @Test
    public void onePageMovedAwayEndsTheSplit() {
        Window window = Unlocking.of(WindowLayout.addWindow(row(this.a, this.b), this.a));
        Window other = Unlocking.of(WindowLayout.addWindow(row(this.d), this.d));
        WindowLayout.split(this.a, this.b);
        assertTrue(WindowLayout.moveTab(this.b, other.getId(), 1));
        assertTrue(window.getSplits().isEmpty());
        assertTrue(other.getSplits().isEmpty());
    }

    @Test
    public void aLockedWindowHoldsItsSplitButItsDividerMoves() {
        Window window = Unlocking.of(WindowLayout.addWindow(row(this.a, this.b, this.c), this.a));
        WindowLayout.split(this.a, this.b);
        WindowLayout.setLocked(window.getId(), true);
        assertFalse(WindowLayout.separate(this.a));
        assertFalse(WindowLayout.swapSides(this.a));
        assertFalse(WindowLayout.turnSplit(this.a, true));
        assertTrue(WindowLayout.shareSplit(this.a, 0.6D, true));
        assertEquals(0.6D, window.splitOf(this.a).share(), 0.0D);
    }

    @Test
    public void swappingChangesTheSidesAndTheirPlaceInTheRow() {
        Window window = Unlocking.of(WindowLayout.addWindow(row(this.a, this.b), this.a));
        WindowLayout.split(this.a, this.b);
        assertTrue(WindowLayout.swapSides(this.a));
        assertEquals(row(this.b, this.a), window.getTabs());
        assertSame(this.b, window.splitOf(this.a).first());
        assertTrue(WindowLayout.separate(this.a));
        assertTrue(window.getSplits().isEmpty());
        assertEquals("the pages stay side by side", row(this.b, this.a), window.getTabs());
    }

    @Test
    public void ctrlTabStepsOverASplitAsOne() {
        PageTab page = WindowPages.tab(PAGE);
        PageTab other = WindowPages.tab(OTHER_PAGE);
        Window window = Unlocking.of(WindowLayout.addWindow(row(page, this.a, other), page));
        assertTrue(WindowLayout.split(page, other));
        List<WindowTab> order = TabWalk.everyTab(Collections.singletonList(window));
        assertEquals("the split stops at its side in front", row(other, this.a), order);
        assertSame(this.a, TabWalk.step(order, other, 1));
        assertSame(other, TabWalk.step(order, this.a, 1));
    }

    private static List<WindowTab> row(WindowTab... tabs) {
        return new ArrayList<WindowTab>(Arrays.asList(tabs));
    }

    private static final class EmptyPage extends PageContent {
        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }

    /** A tab that is always shown and is no page, as a conversation is. */
    private static final class Tab extends WindowTab {
        private final String name;

        Tab(String name) {
            this.name = name;
        }

        @Override
        public String id() {
            return "test:" + this.name;
        }

        @Override
        public String title() {
            return this.name;
        }

        @Override
        public int tone() {
            return 0;
        }

        @Override
        public void drawIcon(Minecraft minecraft, float x, float y, int alpha,
                             TabMark mark) {
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isKeptInLayout() {
            return false;
        }
    }
}
