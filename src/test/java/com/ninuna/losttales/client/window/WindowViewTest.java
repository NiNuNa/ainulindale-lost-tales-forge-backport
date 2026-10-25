package com.ninuna.losttales.client.window;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.chat.ConversationPage;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.chat.TwoWindowLayout;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The screen shows one view at a time: each category's view, or a custom
 * one. Any page may stand in any view: a page the game or a key opens goes
 * to its category's view, one opened by hand to the view on screen. A view
 * with no window opens with its one window at the default place, and Reset
 * View puts it back so; custom views are made, named, keyed in Controls
 * and deleted; a window pinned to the GUI shows in every view, one pinned
 * to the HUD stays while playing; a locked window cannot be closed.
 */
public final class WindowViewTest {
    private static final String PAGE = "view_page";
    private static final String OTHER = "view_other";
    private static final String WORLD = "view_world";
    /** The pages the categories' views open with, by the ids their systems register them under. */
    private static final String MAP = "map";
    private static final String JOURNAL = "journal";
    private static final String FELLOWSHIPS = "fellowship";
    private static final String CHARACTERS = "characters";

    private static final ConversationPage GLOBAL = ConversationPage.of(ChatChannel.GLOBAL);
    private static final ConversationPage OOC = ConversationPage.of(ChatChannel.OOC);
    private static final ConversationPage CONSOLE =
            ConversationPage.of(ChatChannel.CLIENT_CONSOLE);

    @BeforeClass
    public static void registerPages() {
        register(PAGE, Items.book, PageCategory.QUEST_JOURNAL);
        register(OTHER, Items.compass, PageCategory.MAP);
        if (WindowPages.byId(WORLD) == null) {
            WindowPages.registerGamePage(WORLD, "gui.test.view.world",
                    new ItemStack(Items.compass), PageCategory.MAP, EMPTY);
        }
        register(MAP, Items.map, PageCategory.MAP);
        register(JOURNAL, Items.writable_book, PageCategory.QUEST_JOURNAL);
        register(FELLOWSHIPS, Items.iron_helmet, PageCategory.FELLOWSHIPS);
        register(CHARACTERS, Items.name_tag, PageCategory.PROFILE);
        register(NewPage.PAGE_ID, Items.paper, PageCategory.NEW_PAGE);
    }

    private static void register(String id, net.minecraft.item.Item item,
                                 PageCategory category) {
        if (WindowPages.byId(id) == null) {
            WindowPages.register(id, "gui.test.view." + id,
                    new ItemStack(item), null, category, EMPTY);
        }
    }

    private static final WindowPages.Factory EMPTY = new WindowPages.Factory() {
        @Override
        public PageContent create() {
            return new EmptyPage();
        }
    };

    @Before
    public void reset() {
        Views.reset();
        TwoWindowLayout.reset();
        WindowView.clear();
        WindowView.forgetPrevious();
    }

    @After
    public void cleanUp() {
        WindowView.clear();
        for (int number = 1; number <= Views.MAX_CUSTOM; number++) {
            LostTalesKeyBindings.getViewKeyBinding(number).setKeyCode(0);
        }
        Views.reset();
        TwoWindowLayout.reset();
        WindowLayout.setChangeListener(null);
        ChatChannel.resetToBuiltIn();
    }

    private static View view(PageCategory category) {
        return Views.of(category);
    }

    private static OtherPage tab(String id) {
        return WindowPages.tab(id);
    }

    /* ---- One view at a time ---- */

    @Test
    public void withNoScreenOpenEveryTabCountsAsShown() {
        WindowLayout.showPage(tab(PAGE));
        assertFalse(WindowView.isOn(view(PageCategory.CHANNELS)));
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(tab(PAGE)));
        assertFalse("no tab is no tab in any view", WindowView.shows(null));
    }

    /** The chat's key shows the Chat view's windows; the command key the Consoles view's. */
    @Test
    public void theChatAndTheConsolesAreViewsOfTheirOwn() {
        WindowLayout.showPage(tab(PAGE));
        WindowView.forChat();
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue(WindowView.shows(OOC));
        assertFalse(WindowView.shows(CONSOLE));
        assertFalse("the page waits in its own view", WindowView.shows(tab(PAGE)));
        assertTrue(WindowLayout.isOpen(tab(PAGE)));
        WindowView.forConsole();
        assertTrue(WindowView.shows(CONSOLE));
        assertTrue("its window shows whole: Operator stands beside it",
                WindowView.shows(ConversationPage.of(ChatChannel.OPERATOR)));
        assertFalse(WindowView.shows(GLOBAL));
    }

    /** With no screen open, a page goes to its category's view, and a page's key swaps to that view. */
    @Test
    public void aPageOpenedByTheGameGoesToItsCategorysView() {
        Window journal = WindowLayout.showPage(tab(PAGE));
        Window map = WindowLayout.showPage(tab(OTHER));
        Window world = WindowLayout.showPage(tab(WORLD));
        assertEquals(view(PageCategory.QUEST_JOURNAL), WindowLayout.viewOf(journal));
        assertEquals(view(PageCategory.MAP), WindowLayout.viewOf(map));
        assertEquals(view(PageCategory.MAP), WindowLayout.viewOf(world));
        WindowView.forPage(tab(PAGE));
        assertTrue(WindowView.isFor(tab(PAGE)));
        assertFalse(WindowView.isFor(tab(OTHER)));
        assertTrue(WindowView.shows(tab(PAGE)));
        assertFalse(WindowView.shows(tab(OTHER)));
        WindowView.forPage(tab(OTHER));
        assertTrue("one key for the whole category", WindowView.isFor(tab(WORLD)));
        assertTrue(WindowView.shows(tab(WORLD)));
        assertFalse(WindowView.shows(tab(PAGE)));
    }

    /**
     * On the screen, any page opens in the view shown, whatever its
     * category; one open in another view opens there as a copy of its
     * own, and each view keeps its own.
     */
    @Test
    public void anyPageOpensInTheViewOnScreen() {
        WindowView.forView(view(PageCategory.MAP));
        WindowPage global = WindowLayout.openByHand(GLOBAL, null);
        assertNotNull(global);
        assertTrue("a copy of its own: the Chat view keeps Global",
                global.isCopyOf(GLOBAL) && !global.equals(GLOBAL));
        assertEquals(view(PageCategory.MAP),
                WindowLayout.viewOf(WindowLayout.windowOf(global)));
        assertTrue(WindowView.shows(global));
        assertFalse(WindowView.shows(GLOBAL));
        WindowView.forChat();
        assertTrue(WindowView.shows(GLOBAL));
        assertFalse(WindowView.shows(global));
        assertEquals(view(PageCategory.CHANNELS), WindowView.handView(tab(PAGE)));
    }

    /** A view swaps for another: one view at a time, never two. */
    @Test
    public void aViewSwapsForAnother() {
        WindowLayout.showPage(tab(OTHER));
        WindowView.forChat();
        assertTrue(WindowView.isOn(Views.of(PageCategory.WHISPERS)));
        WindowView.forView(view(PageCategory.MAP));
        assertTrue(WindowView.shows(tab(OTHER)));
        assertFalse(WindowView.shows(GLOBAL));
        assertFalse(WindowView.isOn(view(PageCategory.CHANNELS)));
        WindowView.forView(null);
        assertTrue("no view is the Chat view",
                WindowView.isOn(view(PageCategory.CHANNELS)));
    }

    /**
     * The view shown before the one shown now is remembered, across the
     * screen closing too, so a deleted view can swap back to it.
     */
    @Test
    public void theViewShownBeforeIsRemembered() {
        View map = view(PageCategory.MAP);
        View journal = view(PageCategory.QUEST_JOURNAL);
        WindowView.forView(map);
        WindowView.forView(journal);
        assertEquals(map, WindowView.shownBefore(journal));
        WindowView.forView(journal);
        assertEquals("the same view again changes nothing",
                map, WindowView.shownBefore(journal));
        WindowView.clear();
        WindowView.forView(map);
        assertEquals("the one shown last before the screen closed",
                journal, WindowView.shownBefore(map));
        WindowView.forgetPrevious();
        assertEquals(view(PageCategory.CHANNELS), WindowView.shownBefore(map));
    }

    /** Reopening the screen on the view it closed on keeps the one shown before that. */
    @Test
    public void reopeningOnTheSameViewKeepsTheOneBefore() {
        View map = view(PageCategory.MAP);
        View made = Views.make();
        WindowView.forView(map);
        WindowView.forView(made);
        WindowView.clear();
        WindowView.forView(made);
        assertEquals(map, WindowView.shownBefore(made));
    }

    /** A deleted view is never gone back to; with nothing standing it is the Chat view. */
    @Test
    public void aDeletedViewIsNeverGoneBackTo() {
        View map = view(PageCategory.MAP);
        View first = Views.make();
        View second = Views.make();
        WindowView.forView(map);
        WindowView.forView(first);
        WindowView.forView(second);
        assertTrue(Views.delete(first));
        assertEquals(view(PageCategory.CHANNELS), WindowView.shownBefore(second));
        WindowView.forView(map);
        assertTrue(Views.delete(second));
        assertEquals("the deleted one is not remembered", view(PageCategory.CHANNELS),
                WindowView.shownBefore(map));
    }

    /** A tab gone to by hand swaps the screen to the view of its window. */
    @Test
    public void goingToATabSwapsToItsView() {
        WindowLayout.showPage(tab(PAGE));
        WindowView.forChat();
        WindowView.show(tab(PAGE));
        assertTrue(WindowView.isOn(view(PageCategory.QUEST_JOURNAL)));
        assertTrue(WindowView.shows(tab(PAGE)));
    }

    /* ---- Defaults ---- */

    /**
     * Each category's view opens with one window at the default place,
     * locked, its pages as tabs, the first in front; a page another view
     * holds opens as a copy.
     */
    @Test
    public void eachViewOpensWithItsOneWindow() {
        assertTrue(WindowLayout.buildView(view(PageCategory.MAP)));
        Window map = WindowLayout.windowOf(tab(MAP));
        assertTrue(map.isLocked());
        assertEquals(Window.ScreenFill.NONE, map.getFill());
        assertFalse("built once", WindowLayout.buildView(view(PageCategory.MAP)));

        assertTrue(WindowLayout.buildView(view(PageCategory.QUEST_JOURNAL)));
        Window journal = WindowLayout.windowOf(tab(JOURNAL));
        assertEquals(Window.ScreenFill.NONE, journal.getFill());
        assertTrue(journal.isLocked());
        assertEquals(tab(JOURNAL), journal.getActiveTab());
        assertEquals("the map beside the journal, in the same window",
                journal, windowIn(view(PageCategory.QUEST_JOURNAL), tab(MAP)));
        assertEquals(1, windowsIn(view(PageCategory.QUEST_JOURNAL)));
        assertTrue("the Map view keeps its own", map.contains(tab(MAP)));

        assertTrue(WindowLayout.buildView(view(PageCategory.FELLOWSHIPS)));
        Window fellowships = WindowLayout.windowOf(tab(FELLOWSHIPS));
        assertEquals(tab(FELLOWSHIPS), fellowships.getActiveTab());
        assertEquals(fellowships,
                windowIn(view(PageCategory.FELLOWSHIPS), tab(MAP)));
        assertEquals(1, windowsIn(view(PageCategory.FELLOWSHIPS)));
        assertTrue(WindowLayout.buildView(view(PageCategory.PROFILE)));
        assertEquals("the Profile view's window stands on the left third",
                Window.ScreenFill.LEFT_THIRD,
                WindowLayout.windowOf(tab(CHARACTERS)).getFill());
    }

    /** The Chat view's window: Global and OOC, Global in front; the Consoles view's every console. */
    @Test
    public void theChatsViewsOpenAtTheDefaultPlace() {
        for (Window window : WindowLayout.windows()) {
            window.setLocked(false);
        }
        assertTrue(WindowLayout.closeWindow(WindowLayout.windowOf(GLOBAL).getId()));
        assertTrue(WindowLayout.closeWindow(WindowLayout.windowOf(CONSOLE).getId()));
        assertTrue(WindowLayout.buildView(view(PageCategory.CHANNELS)));
        Window chat = WindowLayout.windowOf(GLOBAL);
        assertEquals(Arrays.<WindowPage>asList(GLOBAL, OOC), chat.getTabs());
        assertEquals(GLOBAL, chat.getActiveTab());
        assertEquals(Window.ScreenFill.NONE, chat.getFill());
        assertTrue(chat.isLocked());
        assertTrue(WindowLayout.buildView(view(PageCategory.CONSOLES)));
        Window consoles = WindowLayout.windowOf(CONSOLE);
        assertEquals(Window.ScreenFill.NONE, consoles.getFill());
        assertTrue(consoles.isLocked());
    }

    /** A custom view opens with a New Page in an unlocked window at the default place. */
    @Test
    public void aCustomViewOpensWithANewPage() {
        View made = Views.make();
        assertNotNull(made);
        assertTrue(WindowLayout.buildView(made));
        Window window = WindowLayout.windowOf(tab(NewPage.PAGE_ID));
        assertEquals(made, WindowLayout.viewOf(window));
        assertFalse(window.isLocked());
        assertEquals(Window.ScreenFill.NONE, window.getFill());
    }

    /** A view left with no window opens with its defaults again; Reset View does so at once. */
    @Test
    public void anEmptiedViewOpensWithItsDefaultsAgain() {
        View mapView = view(PageCategory.MAP);
        WindowLayout.buildView(mapView);
        Window map = WindowLayout.windowOf(tab(MAP));
        WindowView.forView(mapView);
        WindowLayout.openByHand(tab(PAGE), null);
        assertEquals(2, windowsIn(mapView));
        WindowLayout.resetView(mapView);
        assertEquals("only what it opens with", 1, windowsIn(mapView));
        Window again = WindowLayout.windowOf(tab(MAP));
        again.setLocked(false);
        assertTrue(WindowLayout.closeWindow(again.getId()));
        assertFalse(WindowLayout.hasWindowIn(mapView));
        assertTrue(WindowLayout.buildView(mapView));
        assertTrue(WindowLayout.windowOf(tab(MAP)).isLocked());
        assertFalse(map == WindowLayout.windowOf(tab(MAP)));
    }

    /** Reset Window Layout puts a window back at the default place, locked. */
    @Test
    public void resettingAWindowPutsItAtTheDefaultPlace() {
        WindowLayout.buildView(view(PageCategory.QUEST_JOURNAL));
        Window journal = WindowLayout.windowOf(tab(JOURNAL));
        journal.setLocked(false);
        WindowLayout.setFill(journal.getId(), Window.ScreenFill.LEFT, false);
        WindowLayout.setPinnedToGui(journal.getId(), true);
        assertTrue(WindowLayout.resetWindow(journal.getId()));
        assertEquals(Window.ScreenFill.NONE, journal.getFill());
        assertTrue(journal.isLocked());
        assertFalse(journal.isPinnedToGui());
    }

    /** A page's key opens its category's view with its defaults, the page forward there. */
    @Test
    public void aPagesKeyOpensItsViewWithItsDefaults() {
        Window held = WindowLayout.showView(tab(JOURNAL));
        assertNotNull(held);
        assertEquals(view(PageCategory.QUEST_JOURNAL), WindowLayout.viewOf(held));
        assertEquals(Window.ScreenFill.NONE, held.getFill());
        assertEquals("the map beside it, as a tab", held, windowIn(
                view(PageCategory.QUEST_JOURNAL), tab(MAP)));
    }

    /* ---- Custom views ---- */

    /** A new player has no custom view; they are made up to the most, each by its number. */
    @Test
    public void customViewsAreMadeUpToTheMost() {
        assertTrue(Views.custom().isEmpty());
        View made = Views.make();
        assertEquals("v1", made.id());
        assertEquals(1, made.number());
        assertEquals(made, Views.numbered(1));
        while (Views.canMake()) {
            assertNotNull(Views.make());
        }
        assertEquals(Views.MAX_CUSTOM, Views.custom().size());
        assertNull(Views.make());
        assertTrue(Views.delete(made));
        assertFalse("a category's view stays", Views.delete(
                view(PageCategory.MAP)));
        assertEquals("the lowest number free", "v1", Views.make().id());
    }

    /** A key bound to a number no view has makes that view, last on the bar. */
    @Test
    public void aViewIsMadeByItsNumber() {
        View first = Views.make();
        View third = Views.make(3);
        assertNotNull(third);
        assertEquals("v3", third.id());
        assertEquals(Arrays.asList(first, third), Views.custom());
        assertNull("one view per number", Views.make(3));
        assertNull(Views.make(0));
        assertNull(Views.make(Views.MAX_CUSTOM + 1));
        assertEquals("v2", Views.make().id());
    }

    /**
     * A view's key is its number's binding in Controls: any key nothing
     * else holds; deleting the view takes it away. A view's name is
     * cleaned.
     */
    @Test
    public void aViewsKeyAndNameFollowTheRules() {
        View made = Views.make();
        View other = Views.make();
        assertNotNull(Views.keyRefusal(made, Keyboard.KEY_ESCAPE));
        assertNull(Views.keyRefusal(made, Keyboard.KEY_G));
        assertTrue(Views.setKey(made, Keyboard.KEY_G));
        assertEquals(Keyboard.KEY_G, made.key());
        assertEquals(Keyboard.KEY_G, LostTalesKeyBindings.getViewKeyBinding(
                made.number()).getKeyCode());
        assertEquals(made.number(), Views.numberForKey(Keyboard.KEY_G));
        assertNotNull("another view's key", Views.keyRefusal(other,
                Keyboard.KEY_G));
        assertFalse(Views.setKey(other, Keyboard.KEY_G));
        assertFalse("a category's view keeps its binding",
                Views.setKey(view(PageCategory.MAP), Keyboard.KEY_H));
        assertTrue("no key at all", Views.setKey(made, 0));
        assertEquals(0, Views.numberForKey(Keyboard.KEY_G));
        assertTrue(Views.setKey(other, Keyboard.KEY_H));
        assertTrue(Views.delete(other));
        assertEquals("deleting takes the key away", 0,
                Views.numberForKey(Keyboard.KEY_H));
        Views.rename(made, "  §cHunting§r Party of the Long Grey Road ");
        assertEquals("Hunting Party of the Lon", made.title());
        Views.rename(Views.of(PageCategory.MAP), "Atlas");
        assertEquals("a category's view keeps its name",
                PageCategory.MAP.title(), Views.of(PageCategory.MAP).title());
    }

    /** A view deleted takes its windows and their pages with it. */
    @Test
    public void aDeletedViewLetsItsWindowsGo() {
        View made = Views.make();
        WindowView.forView(made);
        WindowLayout.openByHand(tab(PAGE), null);
        assertEquals(made, WindowLayout.viewOf(WindowLayout.windowOf(tab(PAGE))));
        assertTrue(Views.delete(made));
        WindowLayout.forgetView(made);
        assertFalse(WindowLayout.isOpen(tab(PAGE)));
        assertFalse(WindowLayout.hasWindowIn(made));
    }

    /** A window keeps the view it stands in through the layout file. */
    @Test
    public void aWindowsViewRoundTripsThroughTheLayoutFile() {
        View made = Views.make();
        WindowView.forView(made);
        Window window = WindowLayout.showPage(tab(PAGE));
        assertEquals(made, WindowLayout.viewOf(window));
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(), described.toString().contains(" view=v1 "));
        TwoWindowLayout.reset();
        Views.reset();
        WindowLayoutStore.load(described);
        assertEquals(Views.byId("v1"), WindowLayout.viewOf(
                WindowLayout.window(window.getId())));
    }

    /* ---- Pins and the padlock ---- */

    @Test
    public void aWindowPinnedToTheGuiShowsInEveryView() {
        WindowLayout.showPage(tab(PAGE));
        String chat = WindowLayout.windowOf(GLOBAL).getId();
        assertTrue(WindowLayout.setPinnedToGui(chat, true));
        assertFalse("pinned already", WindowLayout.setPinnedToGui(chat, true));
        WindowView.forPage(tab(PAGE));
        assertTrue(WindowView.shows(GLOBAL));
        assertTrue("every page the window holds", WindowView.shows(OOC));
        assertFalse("another window's pages wait for their view",
                WindowView.shows(CONSOLE));
        assertTrue(WindowLayout.setPinnedToGui(chat, false));
        assertFalse(WindowView.shows(GLOBAL));
    }

    @Test
    public void aWindowPinnedToTheHudAloneStaysOutOfOtherViews() {
        WindowLayout.showPage(tab(PAGE));
        String chat = WindowLayout.windowOf(GLOBAL).getId();
        assertTrue(WindowLayout.setPinnedToHud(chat, true));
        WindowView.forPage(tab(PAGE));
        assertFalse(WindowView.shows(GLOBAL));
        WindowView.forChat();
        assertTrue("its own view still shows it", WindowView.shows(GLOBAL));
    }

    @Test
    public void whilePlayingOnlyTheWindowsPinnedToTheHudShow() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        WindowLayout.setPinnedToHud(chat.getId(), true);
        WindowLayout.setPinnedToGui(WindowLayout.windowOf(CONSOLE).getId(), true);
        assertEquals(Arrays.asList(chat), WindowLayout.hudWindows());
        WindowView.beginPinnedPass();
        try {
            assertTrue(WindowView.shows(GLOBAL));
            assertTrue(WindowView.shows(OOC));
            assertFalse("pinned to the GUI only", WindowView.shows(CONSOLE));
        } finally {
            WindowView.endPinnedPass();
        }
        assertTrue(WindowView.shows(CONSOLE));
    }

    @Test
    public void theTwoPinsAreSwitchedApart() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        assertTrue(WindowLayout.setPinnedToHud(chat.getId(), true));
        assertFalse(chat.isPinnedToGui());
        assertTrue(WindowLayout.setPinnedToGui(chat.getId(), true));
        assertTrue(WindowLayout.setPinnedToHud(chat.getId(), false));
        assertTrue("letting the HUD go leaves the GUI's pin",
                chat.isPinnedToGui());
        assertTrue(WindowLayout.hudWindows().isEmpty());
        assertFalse(WindowLayout.setPinnedToHud("nope", true));
        assertFalse(WindowLayout.setPinnedToGui("nope", true));
    }

    @Test
    public void aPageOfTheWorldNeverShowsPinned() {
        OtherPage world = tab(WORLD);
        assertNotNull(world);
        assertFalse(WindowLayout.staysPut(world));
        assertFalse(WindowLayout.staysPut(null));
        assertTrue(WindowLayout.staysPut(GLOBAL));
        Window window = WindowLayout.showPage(world);
        assertNotNull(window);
        WindowLayout.setPinnedToHud(window.getId(), true);
        WindowLayout.setPinnedToGui(window.getId(), true);
        assertFalse(WindowLayout.isOnHud(world));
        assertFalse(WindowLayout.isOnGui(world));
    }

    /** Closing an unlocked window closes every page of it; a locked one stays. */
    @Test
    public void aLockedWindowCannotBeClosed() {
        Window window = WindowLayout.windowOf(GLOBAL);
        window.setLocked(true);
        assertFalse(WindowLayout.closeWindow(window.getId()));
        assertTrue(WindowLayout.isOpen(GLOBAL));
        window.setLocked(false);
        assertTrue(WindowLayout.closeWindow(window.getId()));
        assertNull(WindowLayout.window(window.getId()));
        assertFalse(WindowLayout.isOpen(GLOBAL));
        assertFalse(WindowLayout.isOpen(OOC));
    }

    @Test
    public void theWindowsPinsRoundTripThroughTheLayoutFile() {
        Window chat = WindowLayout.windowOf(GLOBAL);
        Window consoles = WindowLayout.windowOf(CONSOLE);
        WindowLayout.setPinnedToHud(chat.getId(), true);
        WindowLayout.setPinnedToGui(consoles.getId(), true);
        List<String> described = WindowLayoutStore.describe();
        assertTrue(described.toString(),
                described.toString().contains(" hud=true"));
        assertTrue(described.toString(),
                described.toString().contains(" gui=true"));
        TwoWindowLayout.reset();
        assertTrue(WindowLayout.hudWindows().isEmpty());
        WindowLayoutStore.load(described);
        assertTrue(WindowLayout.window(chat.getId()).isPinnedToHud());
        assertFalse(WindowLayout.window(chat.getId()).isPinnedToGui());
        assertTrue(WindowLayout.window(consoles.getId()).isPinnedToGui());
        assertFalse(WindowLayout.window(consoles.getId()).isPinnedToHud());
        assertEquals(described, WindowLayoutStore.describe());
    }

    /** The window of {@code view} holding a copy of {@code page}; null for none. */
    private static Window windowIn(View view, WindowPage page) {
        for (Window window : WindowLayout.windows()) {
            if (WindowLayout.viewOf(window) != view) {
                continue;
            }
            for (WindowPage held : window.getTabs()) {
                if (held.isCopyOf(page)) {
                    return window;
                }
            }
        }
        return null;
    }

    private static int windowsIn(View view) {
        int count = 0;
        for (Window window : WindowLayout.windows()) {
            if (WindowLayout.viewOf(window) == view) {
                count++;
            }
        }
        return count;
    }

    /** A page with nothing on it. */
    private static final class EmptyPage extends PageContent {
        @Override
        public void draw(Minecraft minecraft, LostTalesUiHitBox box,
                         double clipX, double clipY, double pointerX,
                         double pointerY, float partialTicks, int alpha) {
        }
    }
}
