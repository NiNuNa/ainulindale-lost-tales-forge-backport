package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ChatTab;
import net.minecraft.client.Minecraft;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A page's options hold its own rows and then the settings of its kind,
 * which its cog opens at once: every conversation the one Chat Settings.
 * A page with neither has nothing behind its three dots.
 */
public final class TabMenusTest {
    /** A page with no choices of its own and no settings. */
    private static final WindowTab BARE = new WindowTab() {
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

    @Test
    public void everyConversationOpensTheOneChatSettings() {
        assertEquals(Settings.Place.CHAT,
                ChatTab.of(ChatChannel.GLOBAL).settingsPlace());
        assertEquals(Settings.Place.CHAT,
                ChatTab.of(ChatChannel.CLIENT_CONSOLE).settingsPlace());
        assertTrue(TabMenus.hasRows(ChatTab.of(ChatChannel.GLOBAL)));
    }

    @Test
    public void aPageWithNeitherChoicesNorSettingsHasNoOptions() {
        assertNull(BARE.settingsPlace());
        assertFalse(TabMenus.hasRows(BARE));
    }

    @Test
    public void theOptionsAreNamedAfterThePage() {
        assertEquals("gui.losttales.window.page.options",
                BARE.optionsTitle());
    }
}
