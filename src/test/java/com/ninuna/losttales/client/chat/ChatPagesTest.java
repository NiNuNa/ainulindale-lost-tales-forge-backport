package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageTab;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowTab;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A page is a tab no conversation stands behind: it has no channel, one
 * tab per page, an id of its own the layout file keeps, and a window of
 * its own that opens at a page's size and comes back where it last stood,
 * however many windows stand.
 */
public final class ChatPagesTest {
    private static final String PAGE = "test_page";
    /** A page like the map's: it fills the screen the first time, has no tool strip, and can be out of reach. */
    private static final String FILLING = "test_filling_page";
    private static boolean fillingAvailable = true;
    /** The filling page's key, M's key code. */
    private static final int FILLING_KEY = 50;
    /** The tone the test page gives itself. */
    private static final int TONE = 0x3E3B66;

    @BeforeClass
    public static void registerPage() {
        if (WindowPages.byId(PAGE) == null) {
            WindowPages.register(PAGE, "gui.test.page",
                    new ItemStack(Items.book), new WindowPages.Factory() {
                        @Override
                        public PageContent create() {
                            return new EmptyPage();
                        }
                    });
        }
        if (WindowPages.byId(FILLING) == null) {
            WindowPages.register(FILLING, "gui.test.filling",
                    new ItemStack(Items.map), Window.ScreenFill.FULL,
                    new KeyBinding("key.test.filling", FILLING_KEY,
                            "key.categories.test"),
                    new WindowPages.Factory() {
                        @Override
                        public PageContent create() {
                            return new FillingPage();
                        }
                    });
        }
    }

    @Before
    public void reset() {
        fillingAvailable = true;
        ChatLayout.reset();
        ClientChatChannelState.clear();
    }

    @After
    public void cleanUp() {
        ChatLayout.reset();
        WindowLayout.setChangeListener(null);
        ClientChatChannelState.clear();
    }

    @Test
    public void aPageIsATabOfItsOwnAndNoConversation() {
        PageTab page = WindowPages.tab(PAGE);
        assertNotNull(page);
        assertSame("one tab per page", page, WindowPages.tab(PAGE));
        assertEquals("page:" + PAGE, page.id());
        assertEquals(page, WindowTab.fromId(page.id()));
        assertNull("a page is no conversation", ChatTab.from(page));
        assertNull("no channel goes by a page's id", ChatTab.fromId(page.id()));
        assertFalse(page.equals(ChatTab.of(ChatChannel.GLOBAL)));
        assertFalse(ChatTab.of(ChatChannel.GLOBAL).equals(page));
        assertNull("an unregistered page is no tab", WindowPages.tab("nobody"));
        assertNull(WindowTab.fromId("page:nobody"));
    }

    @Test
    public void aPageOpensInAWindowOfItsOwnAtAPagesSize() {
        PageTab page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        assertNotNull(window);
        assertEquals(Arrays.asList(page), window.getTabs());
        assertEquals(page, window.getActiveTab());
        assertEquals(WindowLayout.PAGE_HEIGHT, window.getOwnHeight(), 1.0E-9D);
        assertEquals(WindowLayout.PAGE_WIDTH, window.getOwnWidth());
        assertSame("shown again, the same window comes forward", window,
                WindowLayout.showPage(page));
    }

    @Test
    public void aClosedPageComesBackWhereItsWindowStood() {
        PageTab page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        WindowLayout.setPosition(window.getId(), 20.0D, 30.0D, false);
        WindowLayout.close(page);
        assertNull(WindowLayout.windowOf(page));
        Window again = WindowLayout.showPage(page);
        assertEquals(20.0D, again.getOffsetX(), 1.0E-9D);
        assertEquals(30.0D, again.getOffsetY(), 1.0E-9D);
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(), described.contains("place page:" + PAGE
                + " x=20.00 y=30.00 height=292.00 width=366"));
    }

    @Test
    public void aConversationPickedComesInFrontOfAPageInItsWindow() {
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        Window window = WindowLayout.windowOf(global);
        ClientChatChannelState.select(global);
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.openTab(page, window.getId());
        WindowLayout.showPage(page);
        assertTrue(WindowLayout.showsPage(window));

        ChatTabActions actions = new ChatTabActions(new ChatInputBar(),
                new ChatInputCompletion(null), new ChatComposer());
        actions.bind(null, new GuiTextField(null, 0, 0, 100, 12));
        actions.selectChannel(global);

        assertEquals("the pick beats the page", global,
                window.getActiveTab());
        assertEquals(global, ClientChatChannelState.getSelected());
        assertFalse(WindowLayout.showsPage(window));
    }

    @Test
    public void aConversationCoveredByAPageStaysTheLastUsed() {
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ChatTab friend = ChatTab.whisper("friend");
        WindowLayout.openInNewWindow(friend);
        Window window = WindowLayout.windowOf(global);
        ClientChatChannelState.select(global);
        PageTab page = WindowPages.tab(PAGE);
        WindowLayout.openTab(page, window.getId());
        WindowLayout.showPage(page);

        ChatTabActions actions = new ChatTabActions(new ChatInputBar(),
                new ChatInputCompletion(null), new ChatComposer());
        actions.bind(null, new GuiTextField(null, 0, 0, 100, 12));
        actions.syncSelection();

        assertEquals("the input is lent to the other window", friend,
                ClientChatChannelState.getSelected());
        assertEquals("the chat key brings the covered one back", global,
                ClientChatChannelState.lastUsed());
        ClientChatChannelState.markUsed();
        assertEquals("typed in, the lent one is the last used", friend,
                ClientChatChannelState.lastUsed());
    }

    @Test
    public void beforeAnyPickTheChatKeyBringsTheTopWindowsConversation() {
        ChatTab friend = ChatTab.whisper("friend");
        WindowLayout.openInNewWindow(friend);
        ClientChatChannelState.clear();
        assertEquals("the top window's, as the layout left it", friend,
                ClientChatChannelState.lastUsed());
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ClientChatChannelState.select(global);
        assertEquals("then the one picked", global,
                ClientChatChannelState.lastUsed());
    }

    @Test
    public void aPagesTabWearsTheToneThePageGivesItself() {
        assertEquals(TONE, WindowPages.tab(PAGE).tone());
    }

    @Test
    public void aPageOpensAWindowHoweverManyStand() {
        for (int index = 0; index < 12; index++) {
            WindowLayout.openInNewWindow(ChatTab.whisper("friend" + index));
        }
        assertNotNull(WindowLayout.showPage(WindowPages.tab(PAGE)));
    }

    @Test
    public void aFillingPageFillsTheScreenTheFirstTimeAndThenWhereItWasLeft() {
        PageTab page = WindowPages.tab(FILLING);
        Window window = WindowLayout.showPage(page);
        assertEquals("the first time, the whole screen",
                Window.ScreenFill.FULL, window.getFill());
        assertEquals("the page's size to go back to", WindowLayout.PAGE_HEIGHT,
                window.getOwnHeight(), 1.0E-9D);
        WindowLayout.setFill(window.getId(), Window.ScreenFill.NONE, false);
        WindowLayout.setPosition(window.getId(), 10.0D, 20.0D, false);
        WindowLayout.close(page);
        Window again = WindowLayout.showPage(page);
        assertEquals("where it was left: its own box",
                Window.ScreenFill.NONE, again.getFill());
        assertEquals(10.0D, again.getOffsetX(), 1.0E-9D);
        WindowLayout.setFill(again.getId(), Window.ScreenFill.FULL, false);
        WindowLayout.close(page);
        assertEquals(Window.ScreenFill.FULL,
                WindowLayout.showPage(page).getFill());
        WindowLayout.close(page);
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(), described.contains("place page:"
                + FILLING + " x=10.00 y=20.00 height=292.00 width=366 fill=full"));
    }

    @Test
    public void aPageOutOfReachWaitsUnseen() {
        PageTab page = WindowPages.tab(FILLING);
        Window window = WindowLayout.showPage(page);
        assertTrue(WindowFrame.visibleTabs(window).contains(page));
        fillingAvailable = false;
        assertFalse(page.isAvailable());
        assertFalse("it waits in its window unseen",
                WindowFrame.visibleTabs(window).contains(page));
    }

    @Test
    public void aPageKeyNamesItsPageAndNoKeyNamesNone() {
        assertSame(WindowPages.tab(FILLING),
                WindowPages.tabForKey(FILLING_KEY));
        assertNull("a key no page has", WindowPages.tabForKey(51));
        assertNull("no key at all", WindowPages.tabForKey(0));
    }

    /** A page like the map's: a key, and out of reach at times. */
    private static final class FillingPage extends PageContent {
        @Override
        public boolean isAvailable() {
            return fillingAvailable;
        }

        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }

    /** A page with nothing on it but its tone. */
    private static final class EmptyPage extends PageContent {
        @Override
        public int tone() {
            return TONE;
        }

        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }
}
