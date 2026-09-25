package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
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
 * its own that opens at a page's size, comes back where it last stood,
 * and is never more than the eight windows the chat may have.
 */
public final class ChatPagesTest {
    private static final String PAGE = "test_page";
    /** The tone the test page gives itself. */
    private static final int TONE = 0x3E3B66;

    @BeforeClass
    public static void registerPage() {
        if (ChatPages.byId(PAGE) == null) {
            ChatPages.register(PAGE, "gui.test.page",
                    new ItemStack(Items.book), new ChatPages.Factory() {
                        @Override
                        public ChatPageContent create() {
                            return new EmptyPage();
                        }
                    });
        }
    }

    @Before
    public void reset() {
        ChatWindowLayout.reset();
        ClientChatChannelState.clear();
    }

    @After
    public void cleanUp() {
        ChatWindowLayout.reset();
        ChatWindowLayout.setChangeListener(null);
        ClientChatChannelState.clear();
    }

    @Test
    public void aPageIsATabOfItsOwnWithNoChannel() {
        ChatTab page = ChatTab.page(PAGE);
        assertNotNull(page);
        assertTrue(page.isPage());
        assertNull(page.getChannel());
        assertFalse(page.isWhisper());
        assertFalse(page.isNpc());
        assertSame("one tab per page", page, ChatTab.page(PAGE));
        assertEquals("page:" + PAGE, page.id());
        assertEquals(page, ChatTab.fromId(page.id()));
        assertFalse(page.equals(ChatTab.of(ChatChannel.GLOBAL)));
        assertFalse(ChatTab.of(ChatChannel.GLOBAL).equals(page));
        assertSame("no conversation stands behind it", page, ChatTab.viewed(page));
        assertNull("an unregistered page is no tab", ChatTab.page("nobody"));
        assertNull(ChatTab.fromId("page:nobody"));
    }

    @Test
    public void aPageOpensInAWindowOfItsOwnAtAPagesSize() {
        ChatTab page = ChatTab.page(PAGE);
        ChatWindow window = ChatWindowLayout.showPage(page);
        assertNotNull(window);
        assertEquals(Arrays.asList(page), window.getTabs());
        assertEquals(page, window.getActiveTab());
        assertEquals(ChatWindowLayout.PAGE_LINES, window.getMaxLines(), 1.0E-9D);
        assertEquals(ChatWindowLayout.PAGE_WIDTH, window.getWidth());
        assertSame("shown again, the same window comes forward", window,
                ChatWindowLayout.showPage(page));
    }

    @Test
    public void aClosedPageComesBackWhereItsWindowStood() {
        ChatTab page = ChatTab.page(PAGE);
        ChatWindow window = ChatWindowLayout.showPage(page);
        ChatWindowLayout.setPosition(window.getId(), 20.0D, 30.0D, false);
        ChatWindowLayout.close(page);
        assertNull(ChatWindowLayout.windowOf(page));
        ChatWindow again = ChatWindowLayout.showPage(page);
        assertEquals(20.0D, again.getOffsetX(), 1.0E-9D);
        assertEquals(30.0D, again.getOffsetY(), 1.0E-9D);
        List<String> described = ChatWindowLayoutStore.describe();
        assertTrue(described.toString(), described.contains("page " + PAGE
                + " x=20.00 y=30.00 lines=18.00 width=360"));
    }

    @Test
    public void aConversationPickedComesInFrontOfAPageInItsWindow() {
        ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
        ChatWindow window = ChatWindowLayout.windowOf(global);
        ClientChatChannelState.select(global);
        ChatTab page = ChatTab.page(PAGE);
        ChatWindowLayout.openTab(page, window.getId());
        ChatWindowLayout.showPage(page);
        assertTrue(ChatWindowLayout.showsPage(window));

        ChatTabActions actions = new ChatTabActions(new ChatInputBar(),
                new ChatInputCompletion(null), new ChatComposer());
        actions.bind(null, new GuiTextField(null, 0, 0, 100, 12));
        actions.selectChannel(global);

        assertEquals("the pick beats the page", global,
                window.getActiveTab());
        assertEquals(global, ClientChatChannelState.getSelected());
        assertFalse(ChatWindowLayout.showsPage(window));
    }

    @Test
    public void aPagesTabWearsTheToneThePageGivesItself() {
        assertEquals(TONE, ClientChatChannelState.displayColor(
                ChatTab.page(PAGE)));
    }

    @Test
    public void aPageNeverOpensAWindowPastTheEight() {
        for (int index = ChatWindowLayout.windows().size();
             index < ChatWindowLayout.MAX_WINDOWS; index++) {
            ChatWindowLayout.openInNewWindow(ChatTab.whisper("friend" + index));
        }
        assertNull(ChatWindowLayout.showPage(ChatTab.page(PAGE)));
    }

    /** A page with nothing on it but its tone. */
    private static final class EmptyPage extends ChatPageContent {
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
