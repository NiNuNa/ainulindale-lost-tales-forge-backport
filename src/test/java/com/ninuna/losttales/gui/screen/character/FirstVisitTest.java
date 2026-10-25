package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.client.window.PageCategory;
import com.ninuna.losttales.client.window.Views;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * On a first visit the player may reach the chat, the consoles, the
 * settings and the Character Editor's Profile view; the views a character
 * is needed for stay closed.
 */
public final class FirstVisitTest {

    @Test
    public void aFirstVisitKeepsTheViewsACharacterIsNeededForClosed() {
        for (PageCategory category : PageCategory.values()) {
            if (!category.isView()) {
                continue;
            }
            boolean needsCharacter = category == PageCategory.MAP
                    || category == PageCategory.QUEST_JOURNAL
                    || category == PageCategory.FELLOWSHIPS
                    || category == PageCategory.SKILLS;
            assertEquals(category.id(), needsCharacter,
                    FirstVisit.closes(Views.of(category)));
        }
    }

    @Test
    public void noViewIsNothingToClose() {
        assertEquals(false, FirstVisit.closes(null));
    }
}
