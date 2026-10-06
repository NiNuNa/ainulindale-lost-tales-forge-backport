package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The order tabs come forward in, as a browser's do: the tab keys walk
 * every tab, pages and conversations alike, window by window, and
 * closing the tab in front brings the one to its right forward, else the
 * one to its left.
 */
public final class TabOrderTest {
    private final Tab a = new Tab("a", true);
    private final Tab b = new Tab("b", true);
    private final Tab c = new Tab("c", true);
    private final Tab d = new Tab("d", true);
    private final Tab hidden = new Tab("hidden", false);

    @Before
    public void reset() {
        ChatLayout.reset();
        WindowLayout.load(Collections.<WindowLayout.WindowSpec>emptyList());
    }

    @After
    public void cleanUp() {
        ChatLayout.reset();
    }

    private static List<WindowPage> row(WindowPage... tabs) {
        return new ArrayList<WindowPage>(Arrays.asList(tabs));
    }

    /* ---- W5: the tab keys ---- */

    /**
     * A bar's tab button and Tab walk the one window's row, pages and
     * conversations alike, round from one end to the other.
     */
    @Test
    public void theBarWalksItsWindowsRowRound() {
        Window window = WindowLayout.addWindow(row(this.a, this.hidden,
                this.b, this.c), this.b);
        List<WindowPage> row = WindowFrame.visibleTabs(window);
        assertSame(this.c, TabWalk.step(row, this.b, 1));
        assertSame(this.a, TabWalk.step(row, this.c, 1));
        assertSame("a tab that cannot be shown is passed over", this.a,
                TabWalk.step(row, this.b, -1));
    }

    @Test
    public void ctrlTabWalksEveryWindowsTabsInOrderAndComesRound() {
        Window first = WindowLayout.addWindow(row(this.a, this.b), this.a);
        Window second = WindowLayout.addWindow(row(this.c, this.hidden,
                this.d), this.c);
        List<WindowPage> order = TabWalk.everyTab(Arrays.asList(first, second));
        assertEquals("a tab that cannot be shown is passed over",
                row(this.a, this.b, this.c, this.d), order);
        assertSame(this.c, TabWalk.step(order, this.b, 1));
        assertSame("round from the last to the first", this.a,
                TabWalk.step(order, this.d, 1));
        assertSame("and back", this.d, TabWalk.step(order, this.a, -1));
        assertSame("from nowhere, the first going on", this.a,
                TabWalk.step(order, null, 1));
        assertSame("and the last going back", this.d,
                TabWalk.step(order, null, -1));
        assertNull(TabWalk.step(Collections.<WindowPage>emptyList(),
                this.a, 1));
    }

    @Test
    public void aDigitPicksTheRowsTabByPlaceAndNineTheLast() {
        List<WindowPage> row = row(this.a, this.b, this.c);
        assertSame(this.a, TabWalk.ordinal(row, 1));
        assertSame(this.c, TabWalk.ordinal(row, 3));
        assertNull("past the row", TabWalk.ordinal(row, 4));
        assertSame("nine is always the last", this.c, TabWalk.ordinal(row, 9));
        assertNull(TabWalk.ordinal(row, 0));
        assertNull(TabWalk.ordinal(Collections.<WindowPage>emptyList(), 9));
    }

    /* ---- W6: which tab comes forward ---- */

    @Test
    public void theTabToTheRightComesForwardElseTheOneToTheLeft() {
        // b left a row of a, c, d: c stood to its right.
        assertSame(this.c, WindowLayout.successor(row(this.a, this.c,
                this.d), 1));
        // d, the last, left a row of a, b, c: none to its right.
        assertSame(this.c, WindowLayout.successor(row(this.a, this.b,
                this.c), 3));
        assertNull(WindowLayout.successor(Collections.<WindowPage>emptyList(),
                0));
    }

    @Test
    public void aTabThatCannotBeShownIsPassedOver() {
        assertSame(this.c, WindowLayout.successor(row(this.a, this.hidden,
                this.c), 1));
        assertSame(this.a, WindowLayout.successor(row(this.a, this.hidden),
                1));
    }

    @Test
    public void closingTheFrontTabBringsItsNeighbourForward() {
        Window window = Unlocking.of(WindowLayout.addWindow(row(this.a,
                this.b, this.c, this.d), this.b));
        WindowLayout.close(this.b);
        assertSame("the one to its right", this.c, window.getActiveTab());
        WindowLayout.setActiveTab(this.d);
        WindowLayout.close(this.d);
        assertSame("the last closed: the one to its left", this.c,
                window.getActiveTab());
        WindowLayout.close(this.a);
        assertSame("a tab behind closes leaving the front alone", this.c,
                window.getActiveTab());
    }

    @Test
    public void takingTheFrontTabAwayBringsItsNeighbourForward() {
        final Window window = WindowLayout.addWindow(row(this.a, this.b,
                this.c), this.b);
        WindowLayout.removeTabs(new WindowLayout.TabFilter() {
            @Override
            public boolean matches(WindowPage tab) {
                return tab == TabOrderTest.this.a
                        || tab == TabOrderTest.this.b;
            }
        });
        assertSame(this.c, window.getActiveTab());
        Window other = Unlocking.of(WindowLayout.addWindow(row(this.d,
                this.hidden), this.d));
        assertNotNull(Tearing.off(this.d, 20.0D, 20.0D));
        assertSame("a tab taken off to a window of its own leaves its neighbour",
                this.hidden, other.getActiveTab());
    }

    /** A tab of its own, shown or not. */
    private static final class Tab extends WindowPage {
        private final String name;
        private final boolean available;

        Tab(String name, boolean available) {
            this.name = name;
            this.available = available;
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
            return this.available;
        }

        @Override
        public boolean isKeptInLayout() {
            return false;
        }

        @Override
        public PageCategory category() {
            return PageCategory.CHANNELS;
        }
    }
}
