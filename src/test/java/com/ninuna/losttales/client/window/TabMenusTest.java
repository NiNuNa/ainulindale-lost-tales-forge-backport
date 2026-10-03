package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ConversationPage;
import com.ninuna.losttales.client.chat.TwoWindowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A page's options hold everything its tool strip holds, in the strip's
 * order and parted where the strip is: the panel's row, the page's own
 * options, then the cog's row, Split View, Full Window, the member list's
 * row, the search and the help. Every conversation's cog opens the one
 * Chat Settings; a page with none still shows the cog's row, greyed.
 */
public final class TabMenusTest {
    /** A page with no choices of its own, no panel and no settings. */
    private static final WindowPage BARE = new WindowPage() {
        @Override
        public String id() {
            return "bare";
        }

        @Override
        public String title() {
            return "Bare";
        }

        @Override
        public int tone() {
            return 0;
        }

        @Override
        public void drawIcon(Minecraft minecraft, float x, float y,
                             int alpha, TabMark mark) {}
    };

    @Before
    public void reset() {
        TwoWindowLayout.reset();
    }

    @After
    public void cleanUp() {
        TwoWindowLayout.reset();
    }

    @Test
    public void everyConversationOpensTheOneChatSettings() {
        assertEquals(Settings.Place.CHAT,
                ConversationPage.of(ChatChannel.GLOBAL).settingsPlace());
        assertEquals(Settings.Place.CHAT,
                ConversationPage.of(ChatChannel.CLIENT_CONSOLE).settingsPlace());
    }

    /**
     * A conversation's options read as its strip does from the left: the
     * timestamp area's row, a hairline, its own options in their groups, a
     * hairline, then the cog, split view, full window, the member list, the
     * search and the help.
     */
    @Test
    public void aConversationsOptionsReadAsItsStrip() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        List<String> ids = ids(TabMenus.stripRows(global));
        assertEquals("strip:panel", ids.get(0));
        assertEquals("-", ids.get(1));
        int lastHairline = ids.lastIndexOf("-");
        assertTrue("its own options stand between the two", lastHairline > 2);
        assertEquals(Arrays.asList("settings:CHAT", "split_view",
                "strip:full_window", "strip:members", "strip:search",
                "strip:help"), ids.subList(lastHairline + 1, ids.size()));
    }

    /**
     * A page with no panel, no options and no settings still holds the
     * strip's right end, with no hairline before it, the cog's row greyed
     * and saying why, as the cog does.
     */
    @Test
    public void aBarePageHoldsTheStripsRightEnd() {
        assertNull(BARE.settingsPlace());
        List<MenuWindow.Entry> rows = TabMenus.stripRows(BARE);
        assertEquals(Arrays.asList("settings", "split_view",
                "strip:full_window", "strip:search", "strip:help"), ids(rows));
        assertFalse(rows.get(0).isTakeable());
        assertEquals("gui.losttales.window.cog.nothing", rows.get(0).unavailable);
    }

    @Test
    public void theOptionsAreNamedAfterThePage() {
        assertEquals("gui.losttales.window.page.options",
                BARE.optionsTitle());
    }

    /** Each row's id, a hairline as {@code -}. */
    private static List<String> ids(List<MenuWindow.Entry> rows) {
        List<String> ids = new ArrayList<String>();
        for (MenuWindow.Entry row : rows) {
            ids.add(row.separator ? "-" : row.id);
        }
        return ids;
    }
}
