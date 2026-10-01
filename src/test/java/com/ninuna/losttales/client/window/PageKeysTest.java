package com.ninuna.losttales.client.window;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The keys a help lists: each area under its name, and a search that
 * keeps a whole area whose name it finds, else the shortcuts whose words
 * or key names it finds. Read without the game's translator, so each
 * word shows as its key.
 */
public final class PageKeysTest {
    private static final List<PageKeys.Area> AREAS = Collections.singletonList(
            PageKeys.area("gui.losttales.page.map",
                    PageKeys.pageKey("map", "location", Keyboard.KEY_R),
                    PageKeys.pageKey("map", "zoom", PageKeys.WHEEL)));

    @Test
    public void everyAreaStandsOverItsShortcuts() {
        List<MenuWindow.Entry> rows = PageKeys.rows(AREAS, "");
        assertEquals(3, rows.size());
        assertTrue(rows.get(0).group);
        assertEquals("gui.losttales.help.keys.map.location", rows.get(1).label);
    }

    @Test
    public void theSearchFindsAShortcutByTheKeyItsIconNames() {
        List<MenuWindow.Entry> rows = PageKeys.rows(AREAS, "r");
        assertEquals(2, rows.size());
        assertEquals("gui.losttales.help.keys.map.location", rows.get(1).label);
        // A search that finds the area's name keeps the whole area.
        assertEquals(3, PageKeys.rows(AREAS, "page.map").size());
        rows = PageKeys.rows(AREAS, "wheel");
        assertEquals(2, rows.size());
        assertEquals("gui.losttales.help.keys.map.zoom", rows.get(1).label);
    }

    @Test
    public void aSearchThatFindsNothingLeavesNoArea() {
        assertTrue(PageKeys.rows(AREAS, "nothing like it").isEmpty());
    }

    @Test
    public void everyPageEndsWithTheHelpKey() {
        boolean found = false;
        for (MenuWindow.Entry row : PageKeys.rows(PageKeys.windowAreas(), "")) {
            found |= "gui.losttales.window.keys.pages.help".equals(row.label);
        }
        assertTrue(found);
    }
}
