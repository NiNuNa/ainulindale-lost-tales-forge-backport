package com.ninuna.losttales.client.window;

import com.ninuna.losttales.util.EnglishWords;
import java.util.ArrayList;
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

    /**
     * Every area and shortcut the window system and the New Page list
     * has its words in the English lang file, so no help shows a key in
     * place of its words.
     */
    @Test
    public void theWindowsAndTheNewPagesKeysHaveEnglishWords() {
        List<PageKeys.Area> areas = new ArrayList<PageKeys.Area>(
                PageKeys.windowAreas());
        areas.addAll(new NewPage().keyAreas());
        List<String> missing = new ArrayList<String>();
        for (PageKeys.Area area : areas) {
            if (!EnglishWords.INSTANCE.has(area.labelKey)) {
                missing.add(area.labelKey);
            }
            for (PageKeys.Shortcut shortcut : area.shortcuts) {
                if (!EnglishWords.INSTANCE.has(shortcut.labelKey)) {
                    missing.add(shortcut.labelKey);
                }
            }
        }
        assertEquals(new ArrayList<String>(), missing);
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
