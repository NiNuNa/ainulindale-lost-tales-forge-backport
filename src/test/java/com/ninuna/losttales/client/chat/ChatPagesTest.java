package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.OtherPage;
import com.ninuna.losttales.client.window.PageCategory;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowPage;
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
    /** A page like the map's: it has a key, and can be out of reach. */
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
                    new ItemStack(Items.book), null,
                    PageCategory.QUEST_JOURNAL, new WindowPages.Factory() {
                        @Override
                        public PageContent create() {
                            return new EmptyPage();
                        }
                    });
        }
        if (WindowPages.byId(FILLING) == null) {
            WindowPages.register(FILLING, "gui.test.filling",
                    new ItemStack(Items.map),
                    new KeyBinding("key.test.filling", FILLING_KEY,
                            "key.categories.test"),
                    PageCategory.MAP, new WindowPages.Factory() {
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
        OtherPage page = WindowPages.tab(PAGE);
        assertNotNull(page);
        assertSame("one tab per page", page, WindowPages.tab(PAGE));
        assertEquals("page:" + PAGE, page.id());
        assertEquals(page, WindowPage.fromId(page.id()));
        assertNull("a page is no conversation", ConversationPage.from(page));
        assertNull("no channel goes by a page's id", ConversationPage.fromId(page.id()));
        assertFalse(page.equals(ConversationPage.of(ChatChannel.GLOBAL)));
        assertFalse(ConversationPage.of(ChatChannel.GLOBAL).equals(page));
        assertNull("an unregistered page is no tab", WindowPages.tab("nobody"));
        assertNull(WindowPage.fromId("page:nobody"));
    }

    /**
     * A page whose category has no window opens its category's first
     * window: at the default place, locked, in front of the others, never
     * a step on from the chat's window.
     */
    @Test
    public void aPageOpensItsCategorysFirstWindow() {
        OtherPage page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        assertNotNull(window);
        assertEquals(Arrays.asList(page), window.getTabs());
        assertEquals(page, window.getActiveTab());
        assertTrue(WindowPlacement.atDefaultPlace(window));
        List<Window> stacked = WindowLayout.stacked();
        assertSame(window, stacked.get(stacked.size() - 1));
        assertTrue(window.isLocked());
        assertEquals(Window.ScreenFill.NONE, window.getFill());
        assertSame("shown again, the same window comes forward", window,
                WindowLayout.showPage(page));
    }

    /**
     * A page closed comes back as its category's first window stands,
     * wherever its window was moved: nothing of that window is kept.
     */
    @Test
    public void aClosedPageComesBackInItsCategorysFirstWindow() {
        OtherPage page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        WindowLayout.setLocked(window.getId(), false);
        WindowLayout.setWindowWidth(window.getId(), 320, false);
        WindowLayout.setWindowHeight(window.getId(), 250.0D, false);
        WindowLayout.setPosition(window.getId(), 20.0D, 30.0D, false);
        WindowLayout.close(page);
        assertNull(WindowLayout.windowOf(page));
        Window again = WindowLayout.showPage(page);
        assertTrue(WindowPlacement.atDefaultPlace(again));
        assertTrue(again.isLocked());
        List<String> described = WindowLayoutStore.describe();
        assertFalse(described.toString(), described.toString().contains(
                "place "));
    }

    @Test
    public void aConversationPickedComesInFrontOfAPageInItsWindow() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        Window window = WindowLayout.windowOf(global);
        ClientChatChannelState.select(global);
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.setLocked(window.getId(), false);
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
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage friend = ConversationPage.whisper("friend", "");
        WindowLayout.openInNewWindow(friend, null);
        Window window = WindowLayout.windowOf(global);
        ClientChatChannelState.select(global);
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.setLocked(window.getId(), false);
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
        ConversationPage friend = ConversationPage.whisper("friend", "");
        WindowLayout.openInNewWindow(friend, null);
        ClientChatChannelState.clear();
        assertEquals("the top window's, as the layout left it", friend,
                ClientChatChannelState.lastUsed());
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
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
            WindowLayout.openInNewWindow(ConversationPage.whisper("friend" + index, ""), null);
        }
        assertNotNull(WindowLayout.showPage(WindowPages.tab(PAGE)));
    }

    /**
     * Locking a window by hand makes where it stands its category's first
     * place: the category's next first window opens there, the file keeps
     * it, and Reset Window Layout forgets it, back to the shipped defaults.
     */
    @Test
    public void lockingAWindowKeepsItsPlaceForItsCategory() {
        OtherPage page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        WindowLayout.setLocked(window.getId(), false);
        WindowLayout.setWindowWidth(window.getId(), 320, false);
        WindowLayout.setWindowHeight(window.getId(), 250.0D, false);
        WindowLayout.setPosition(window.getId(), 20.0D, 30.0D, false);
        assertTrue(WindowLayout.setLocked(window.getId(), true));
        WindowLayout.setLocked(window.getId(), false);
        WindowLayout.close(page);
        Window again = WindowLayout.showPage(page);
        assertTrue(again.isLocked());
        assertEquals(20.0D, again.getOffsetX(), 1.0E-9D);
        assertEquals(30.0D, again.getOffsetY(), 1.0E-9D);
        assertEquals(320, again.getOwnWidth());
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(), described.contains(
                "category quest_journal x=20.00 y=30.00 height=250.00 width=320"));
        WindowLayoutStore.load(described);
        assertEquals(described, WindowLayoutStore.describe());
        Window loaded = WindowLayout.windowOf(page);
        WindowLayout.setLocked(loaded.getId(), false);
        assertTrue(WindowLayout.resetWindow(loaded.getId()));
        assertTrue(WindowPlacement.atDefaultPlace(loaded));
        WindowLayout.setLocked(loaded.getId(), false);
        WindowLayout.close(page);
        assertTrue("forgotten: the shipped defaults again",
                WindowPlacement.atDefaultPlace(WindowLayout.showPage(page)));
    }

    /**
     * The map's category opens its first window filling the screen, locked;
     * reset, an unlocked map window fills it again.
     */
    @Test
    public void aMapLikePageOpensFillingTheScreen() {
        OtherPage page = WindowPages.tab(FILLING);
        Window window = WindowLayout.showPage(page);
        assertEquals(Window.ScreenFill.FULL, window.getFill());
        assertTrue(window.isLocked());
        WindowLayout.setLocked(window.getId(), false);
        WindowLayout.setFill(window.getId(), Window.ScreenFill.LEFT, false);
        assertTrue(WindowLayout.resetWindow(window.getId()));
        assertEquals(Window.ScreenFill.FULL, window.getFill());
    }

    @Test
    public void aPageOutOfReachWaitsUnseen() {
        OtherPage page = WindowPages.tab(FILLING);
        Window window = WindowLayout.showPage(page);
        assertTrue(WindowFrame.visibleTabs(window).contains(page));
        fillingAvailable = false;
        assertFalse(page.isAvailable());
        assertFalse("it waits in its window unseen",
                WindowFrame.visibleTabs(window).contains(page));
    }

    /**
     * Closing the conversation typed in brings forward the tab to its
     * right, a page here, and the input waits in the window's other
     * conversation.
     */
    @Test
    public void closingTheConversationTypedInBringsTheTabToItsRightForward() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        Window window = WindowLayout.windowOf(global);
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.setLocked(window.getId(), false);
        WindowLayout.openTab(page, window.getId());
        WindowLayout.moveTab(page, window.getId(), 1);
        assertEquals(Arrays.<WindowPage>asList(global, page, ooc),
                window.getTabs());
        ClientChatChannelState.select(global);
        WindowLayout.setActiveTab(global);
        assertTrue(ClientChatChannelState.close(global));
        assertEquals("the tab to its right", page, window.getActiveTab());
        assertEquals(ooc, ClientChatChannelState.getSelected());
    }

    /**
     * A page taken out of its window by itself — a world page walked
     * away from, even out of a locked window — leaves nothing behind: it
     * comes back in its category's first window, as a page closed by hand
     * does.
     */
    @Test
    public void aPageTakenOutByItselfComesBackInItsCategorysFirstWindow() {
        final OtherPage page = WindowPages.tab(PAGE);
        Window window = WindowLayout.showPage(page);
        assertTrue(window.isLocked());
        WindowLayout.removeTabs(new WindowLayout.TabFilter() {
            @Override
            public boolean matches(WindowPage tab) {
                return page.equals(tab);
            }
        });
        assertNull(WindowLayout.windowOf(page));
        Window again = WindowLayout.showPage(page);
        assertTrue(again.isLocked());
        assertTrue(WindowPlacement.atDefaultPlace(again));
    }

    /**
     * A line the server says to answer a page's action stands over the
     * page's bar only while the page is shown; with no screen open it
     * stays in the chat, and the page says nothing.
     */
    @Test
    public void aLineForAPageNotShownStaysInTheChat() {
        OtherPage page = WindowPages.tab(PAGE);
        WindowLayout.showPage(page);
        assertFalse(page.isShown());
        assertFalse(WindowPages.answerOnPage("chat.test.page.saved", "Saved."));
        assertEquals("", page.content().lastAnswer().words());
        assertFalse(WindowPages.answerOnPage("chat.other.line", "Hello."));
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

    /** A page with nothing on it but its tone, and the lines it answers. */
    private static final class EmptyPage extends PageContent {
        @Override
        public int tone() {
            return TONE;
        }

        @Override
        public boolean answersLine(String key) {
            return key.startsWith("chat.test.page.");
        }

        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }
}
