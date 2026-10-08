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
 * options, then the cog's row, Split View, Borderless, the member list's
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

        @Override
        public PageCategory category() {
            return PageCategory.SETTINGS;
        }
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
     * hairline, then the cog, split view, borderless, the member list, the
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
        assertEquals("the inbox last of its own options, beside its settings",
                "inbox", ids.get(lastHairline - 1));
        assertEquals(Arrays.asList("settings:CHAT", "split_view",
                "tab:duplicate", "strip:borderless", "strip:members", "strip:search",
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
                "tab:duplicate", "strip:borderless", "strip:search",
                "strip:help"), ids(rows));
        assertFalse(rows.get(0).isTakeable());
        assertEquals("gui.losttales.window.cog.nothing", rows.get(0).unavailable);
    }

    @Test
    public void theOptionsAreNamedAfterThePage() {
        assertEquals("gui.losttales.window.page.options",
                BARE.optionsTitle());
    }

    /**
     * What can be opened stands under its categories' names, in their
     * order: the channels, then the consoles and the whispers under their
     * own names, then the other categories.
     */
    @Test
    public void pagesStandUnderTheirCategories() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage console = ConversationPage.of(ChatChannel.CLIENT_CONSOLE);
        ConversationPage whisper = ConversationPage.whisper("Steve", "");
        List<MenuWindow.Entry> rows = Arrays.asList(
                new MenuWindow.Entry(BARE.id(), "Bare", false, -1, BARE),
                new MenuWindow.Entry(whisper.id(), "Steve", false, -1, whisper),
                new MenuWindow.Entry(console.id(), "Console", false, -1, console),
                new MenuWindow.Entry(global.id(), "Global", false, -1, global));
        List<MenuWindow.Entry> listed = TabMenus.byCategory(rows);
        assertEquals(Arrays.asList("", global.id(), "", console.id(), "",
                whisper.id(), "", BARE.id()), ids(listed));
        assertEquals(PageCategory.CHANNELS.title(), listed.get(0).label);
        assertEquals(PageCategory.CONSOLES.title(), listed.get(2).label);
        assertEquals(PageCategory.SETTINGS.title(), listed.get(6).label);
        assertFalse(listed.get(0).isTakeable());
    }

    /**
     * A category's menu offers what its pages may take all at once: a
     * conversation's Mark All as Read and its two settings, once each; a
     * word is marked only while every page reads it.
     */
    @Test
    public void aCategorysMenuOffersWhatEveryPageMayTake() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        List<WindowPage> pages = Arrays.<WindowPage>asList(global, ooc, BARE);
        List<String> offered = new ArrayList<String>();
        for (PageOption option : TabMenus.everyPageOptions(pages)) {
            offered.add(option.id);
            assertTrue(option.everyPageLabel().length() > 0);
        }
        assertEquals(Arrays.asList("mark_read", "feed", "notify"), offered);
        assertTrue(TabMenus.everyPageReads(pages, "notify", "notify:mentions"));
        com.ninuna.losttales.client.chat.ChatLayout.setNotification(ooc,
                com.ninuna.losttales.client.chat.ChatLineChoice.NOTHING);
        assertFalse(TabMenus.everyPageReads(pages, "notify", "notify:mentions"));
        assertFalse(TabMenus.everyPageReads(pages, "notify", "notify:nothing"));
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
