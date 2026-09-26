package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A filter matches anywhere in a name, whatever the case; a section
 * with no rows is left out; and a menu's control is a switch for the
 * thing it was pressed for — a window's search by the window, the empty
 * screen's by null.
 */
public final class WindowMenusTest {
    @Test
    public void aFilterMatchesAnywhereInTheNameWhateverTheCase() {
        assertTrue(WindowMenus.matchesFilter("Global", ""));
        assertTrue(WindowMenus.matchesFilter("Global", "lob"));
        assertTrue(WindowMenus.matchesFilter("Global", "GLO"));
        assertFalse(WindowMenus.matchesFilter("Global", "party"));
    }

    @Test
    public void aSectionWithNoRowsIsLeftOut() {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        WindowMenus.addSection(entries, "Pages",
                Collections.<MenuWindow.Entry>emptyList());
        assertTrue(entries.isEmpty());
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        rows.add(new MenuWindow.Entry("page:journal", "Quest Journal"));
        WindowMenus.addSection(entries, "Pages", rows);
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).header);
    }

    /**
     * A menu open as the screen closed comes back with the next screen,
     * and its rows answer to that screen's menus, not to the closed one's.
     */
    @Test
    public void aMenuThatComesBackAnswersToTheNewScreensMenus() {
        SubWindowKind kind = SubWindowKind.register("test_menu", "test");
        final List<String> taken = new ArrayList<String>();
        WindowMenus.Source source = new WindowMenus.Source() {
            @Override
            public void rebuild(MenuWindow menu) {
                menu.setRows(Collections.singletonList(
                        new MenuWindow.Entry("row", "Row")));
            }

            @Override
            public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                               SubWindow window, boolean back) {
                taken.add(entry.id);
                return true;
            }
        };
        WindowMenus closed = new WindowMenus(bound());
        closed.register(kind, source);
        MenuWindow menu = closed.menu(kind);
        WindowMenus next = new WindowMenus(bound());
        next.register(kind, source);
        assertTrue(next.restore(new SubWindowPlaces.Reopening(kind, "", menu,
                null, 20.0D, 20.0D, 80, 40, true)));
        assertTrue(next.isOpen(kind));
        WindowHover row = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        row.menuEntry = menu.entries().get(0);
        menu.pressed(row, 0.0D, 0.0D, 0);
        assertEquals(Collections.singletonList("row"), taken);
    }

    private static SubWindows bound() {
        SubWindows windows = new SubWindows();
        windows.bind(480, 270);
        return windows;
    }

    @Test
    public void aMenuIsAboutTheSameThingByWhatItEquals() {
        assertTrue(WindowMenus.sameAbout("w1", "w1"));
        assertFalse(WindowMenus.sameAbout("w1", "w2"));
        assertTrue(WindowMenus.sameAbout(null, null));
        assertFalse(WindowMenus.sameAbout("w1", null));
        assertFalse(WindowMenus.sameAbout(null, "w1"));
    }
}
