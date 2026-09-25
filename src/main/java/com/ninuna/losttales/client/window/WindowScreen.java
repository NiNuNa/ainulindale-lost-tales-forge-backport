package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.LostTalesPointerOwner;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The screen every window stands on: the conversations, the journal, the
 * party. Tabs can be dragged along their row to reorder, away from it to
 * detach into a window of their own, onto another window's row to dock
 * there; a window is moved by its strip, its grip or its only tab,
 * resized by its edges, sent to a part of the screen by its fullscreen
 * control, the snap layouts and the keyboard. All of that edits
 * {@link WindowLayout}, the one model every window is drawn from. Every
 * overlay registers the rectangle it draws in {@link PointerRegions};
 * hover, tooltip and press all read one answer about what is under the
 * pointer ({@link WindowHover}), so whatever is painted on top is also
 * what owns the pointer.
 *
 * <p>A page is drawn, pressed, scrolled and typed into by the screen
 * itself ({@link PageContent}). A system with work of its own besides —
 * the chat, with its input bar, menus and conversations — is a
 * {@link ScreenPart}, asked at fixed points of everything the screen
 * does. It is the game's own chat screen underneath, so the game and
 * other mods see the chat open while it is, and the field the chat types
 * into is the game's.</p>
 */
public final class WindowScreen extends GuiChat
        implements LostTalesPointerOwner {
    /**
     * The longest gap between the two presses of a double click on a
     * window's strip: the desktop's usual half second.
     */
    private static final long DOUBLE_CLICK_NANOS = 500L * 1000000L;

    private static final List<ScreenPart.Maker> MAKERS =
            new CopyOnWriteArrayList<ScreenPart.Maker>();

    /** A page brought forward from outside, which takes the keys once the screen draws. */
    private static PageTab pageToFocus;
    /**
     * Whether the screen closed and the pages that close with it wait for
     * the game to show no screen; a screen opened over this one and gone
     * back from keeps them.
     */
    private static boolean closingPages;

    private final List<ScreenPart> parts = new ArrayList<ScreenPart>();
    private final PointerRegions regions = new PointerRegions();
    private final ToolStrip toolStrip = new ToolStrip();
    /** Snap assist: the other windows offered for a layout's empty zones. */
    private final SnapAssist snapAssist = new SnapAssist();
    /** The drags: tabs, windows, resizes, and whatever a window's content drags. */
    private final WindowGestures gestures = new WindowGestures(
            new WindowGestures.Host() {
                @Override
                public TabRow.Row rowFor(Window window, WindowFrame frame,
                                         LostTalesGuiAnimationSample opening) {
                    return WindowScreen.this.rowFor(window, frame, opening);
                }

                @Override
                public LostTalesGuiAnimationSample opening() {
                    return WindowOpening.sample();
                }

                @Override
                public void selectWindow(Window window) {
                    WindowScreen.this.selectWindow(window);
                }

                @Override
                public void selectTab(WindowTab tab) {
                    WindowScreen.this.selectTab(tab);
                }

                @Override
                public void windowsMoved() {
                    for (ScreenPart part : WindowScreen.this.parts) {
                        part.windowsMoved();
                    }
                }
            }, this.snapAssist);
    /** The snap layouts a window's fullscreen control opens. */
    private final SnapLayouts.Flyout snapFlyout = new SnapLayouts.Flyout();
    /** The pickers' windows, the menus and cards: whatever opens on a click and stays. */
    private final SubWindows subWindows = new SubWindows();
    /**
     * What the pointer is on this frame, found once before anything is
     * drawn: what every highlight, tip and card of the frame and the
     * pointer's pose read.
     */
    private WindowHover hover = WindowHover.NONE;
    private String hoverTip = "";
    private int hoverTipX;
    private int hoverTipY;
    /**
     * The last press on a window's bare strip or grip — which window,
     * when, where, and which of the screen's presses it was — so a second
     * press there completes a double click.
     */
    private String stripPressWindowId;
    private long stripPressNanos;
    private int stripPressX;
    private int stripPressY;
    private int stripPressNumber;
    /** The same for the last press on a window's top or bottom edge. */
    private String edgePressWindowId;
    private long edgePressNanos;
    private int edgePressX;
    private int edgePressY;
    private int edgePressNumber;
    /** The screen's presses, counted: a double click is two in a row. */
    private int pressCount;
    private boolean openAnimationStarted;
    /**
     * Whether the sub-windows open as the screen last closed are still to
     * come back: once the first frame has placed what they open by.
     */
    private boolean subWindowsToRestore;
    /**
     * The page in front of the keys: the one last pressed, or brought
     * forward by its key, until a press lands anywhere else. Its window
     * shows it; null while the keys are a part's.
     */
    private PageTab focusedPage;
    /** The page a held button went down on, which its drag and release go to; null for none. */
    private PageTab pressedPage;
    private int pressedPageButton;
    /** The pages drawn last frame, which are told when they leave the screen. */
    private final List<PageTab> shownPages = new ArrayList<PageTab>();
    /** Whether depth testing was on as this frame began, for a page drawn as a screen of its own. */
    private boolean depthTestAtStart;

    public WindowScreen(String defaultText) {
        super(defaultText == null ? "" : defaultText);
        for (ScreenPart.Maker maker : MAKERS) {
            ScreenPart part = maker.make(this);
            if (part != null) {
                this.parts.add(part);
            }
        }
    }

    /**
     * Once a client tick: the pages that close with the screen close once
     * the screen has closed and the game shows none.
     */
    public static void onClientTick(Minecraft minecraft) {
        if (closingPages && minecraft != null
                && minecraft.currentScreen == null) {
            closingPages = false;
            WindowPages.closeThoseClosingWithScreen();
        }
    }

    /** Leaving the world: the pages that close with the screen close now, whatever stands. */
    public static void leaveWorld() {
        closingPages = false;
        WindowPages.closeThoseClosingWithScreen();
    }

    /** Adds a system with work of its own on the screen; every screen opened from now on has one. */
    public static void addPart(ScreenPart.Maker maker) {
        if (maker != null && !MAKERS.contains(maker)) {
            MAKERS.add(maker);
        }
    }

    /** The window screen open now; null while none is. */
    public static WindowScreen current() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null
                && minecraft.currentScreen instanceof WindowScreen
                ? (WindowScreen)minecraft.currentScreen : null;
    }

    /**
     * Opens the screen with a page in front — the quest journal, the party
     * — in the window holding its tab, else in a window of its own where
     * the page last stood. With the screen already open the page only
     * comes forward. Nothing opens for a page no system registered, or
     * when every window there may be is out and none holds it.
     */
    public static void openPage(String pageId) {
        GuiScreen screen = screenForPage(pageId);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (screen != null && minecraft != null
                && minecraft.currentScreen != screen) {
            minecraft.displayGuiScreen(screen);
        }
    }

    /**
     * Brings a page forward in the layout and answers the screen that
     * shows it, the one already open or a new one, with the page holding
     * the keys; null when the page cannot come forward.
     */
    public static GuiScreen screenForPage(String pageId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        PageTab page = WindowPages.tab(pageId);
        if (minecraft == null || page == null
                || WindowLayout.showPage(page) == null) {
            return null;
        }
        pageToFocus = page;
        return minecraft.currentScreen instanceof WindowScreen
                ? minecraft.currentScreen : new WindowScreen("");
    }

    /* ---- What the parts reach ---- */

    /** The screen's part of this kind; null while it has none. */
    public <T extends ScreenPart> T part(Class<T> kind) {
        for (ScreenPart part : this.parts) {
            if (kind.isInstance(part)) {
                return kind.cast(part);
            }
        }
        return null;
    }

    /** The game's chat field, which the chat types into. */
    public GuiTextField inputField() {
        return this.inputField;
    }

    /** Puts another field in the game's field's place. */
    public void setInputField(GuiTextField field) {
        this.inputField = field;
    }

    public FontRenderer font() {
        return this.fontRendererObj;
    }

    public PointerRegions regions() {
        return this.regions;
    }

    public SubWindows subWindows() {
        return this.subWindows;
    }

    public WindowGestures gestures() {
        return this.gestures;
    }

    /** What the pointer is on this frame. */
    public WindowHover hover() {
        return this.hover;
    }

    /** The page holding the keys; null while a part's field has them. */
    public PageTab focusedPage() {
        return this.focusedPage;
    }

    /** The game's own handling of a key: typing into the field, Escape closing the screen. */
    public void vanillaKeyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
    }

    /** The game's item tooltip, the one every inventory shows. */
    public void drawItemTooltip(ItemStack stack, int x, int y) {
        renderToolTip(stack, x, y);
    }

    /**
     * Writes {@code token} into the field being typed in as a word of its
     * own, and gives the field the keys: a page's share, as a picker's
     * pick is written.
     */
    public void insertIntoInput(String token) {
        for (ScreenPart part : this.parts) {
            if (part.insertText(token)) {
                return;
            }
        }
    }

    /* ---- Focus ---- */

    /** Gives a page the keys: the input, the search and any sub-window let them go. */
    public void focusPage(PageTab page) {
        if (page == null || page.equals(this.focusedPage)) {
            return;
        }
        leavePage();
        if (this.toolStrip.isFocused()) {
            leaveSearch();
        }
        this.subWindows.blur();
        this.focusedPage = page;
        page.content().focusChanged(true);
        syncTypingFocus();
    }

    /** The page in front lets the keys go. */
    public void leavePage() {
        PageTab left = this.focusedPage;
        this.focusedPage = null;
        if (left != null) {
            left.content().focusChanged(false);
        }
    }

    /**
     * Takes the page brought forward from outside, and lets go of a page
     * no longer in front of its window.
     */
    private void syncPageFocus() {
        if (pageToFocus != null) {
            PageTab page = pageToFocus;
            pageToFocus = null;
            focusPage(page);
        }
        if (this.focusedPage != null) {
            Window window = WindowLayout.windowOf(this.focusedPage);
            if (window == null
                    || !this.focusedPage.equals(window.getActiveTab())) {
                leavePage();
                syncTypingFocus();
            }
        }
    }

    /**
     * One field holds the keys, and only it shows the caret: the search,
     * then the field of the sub-window in front, then the page in front of
     * the keys, and the parts' field when none does. A field that loses
     * the keys to one before it lets them go.
     */
    public void syncTypingFocus() {
        SubWindow front = this.subWindows.focused();
        if (this.toolStrip.isFocused() && front != null) {
            front.content.releaseKeys();
        }
        boolean elsewhere = this.toolStrip.isFocused()
                || front != null && front.content.holdsKeys()
                || this.focusedPage != null || !hasField();
        if (this.inputField.isFocused() == elsewhere) {
            this.inputField.setFocused(!elsewhere);
        }
    }

    /** Whether a part has a field that can take the keys now. */
    private boolean hasField() {
        for (ScreenPart part : this.parts) {
            if (part.hasField()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a field holding the keys keeps this press: anything but a
     * Ctrl shortcut of the screen's, and of those a field's own editing
     * ones — Backspace and Delete, the clipboard's and the selection's
     * letters. Ctrl+Shift+A is the screen's and passes.
     */
    public static boolean fieldKeeps(LostTalesKeyPress press) {
        return !press.command || press.is(Keyboard.KEY_BACK)
                || press.is(Keyboard.KEY_DELETE)
                || press.is(Keyboard.KEY_C) || press.is(Keyboard.KEY_V)
                || press.is(Keyboard.KEY_X)
                || press.is(Keyboard.KEY_A) && !press.shift;
    }

    /** The window typed in, whose sub-windows stand over every window; null for none. */
    public String typedWindowId() {
        for (ScreenPart part : this.parts) {
            String id = part.typedWindowId();
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** The window the keys go to: the page's holding them, else the one a part types into. */
    private Window keysWindow() {
        if (this.focusedPage != null) {
            return WindowLayout.windowOf(this.focusedPage);
        }
        for (ScreenPart part : this.parts) {
            Window window = part.keyWindow();
            if (window != null) {
                return window;
            }
        }
        return null;
    }

    /**
     * Whether no window has a tab the player can see. A valid state, not
     * an error: nothing is lost, nothing is being shown. The screen offers
     * what can be opened instead of keeping a window open to stand in.
     */
    public static boolean isEmpty() {
        for (Window window : WindowLayout.windows()) {
            for (WindowTab tab : window.getTabs()) {
                if (tab.isAvailable()) {
                    return false;
                }
            }
        }
        return true;
    }

    /* ---- Selection, through the parts ---- */

    /** A window taken hold of comes forward and takes the keys. */
    public void selectWindow(Window window) {
        for (ScreenPart part : this.parts) {
            if (part.selectWindow(window)) {
                return;
            }
        }
        if (window != null) {
            WindowLayout.raise(window.getId());
        }
    }

    /** A tab picked comes in front of its window and takes the keys. */
    public void selectTab(WindowTab tab) {
        for (ScreenPart part : this.parts) {
            if (part.selectTab(tab)) {
                return;
            }
        }
        if (tab instanceof PageTab) {
            WindowLayout.showPage((PageTab)tab);
        }
    }

    /** Closes one tab; the group it was marked with ends with it. */
    public void closeTab(WindowTab tab) {
        for (ScreenPart part : this.parts) {
            if (part.closeTab(tab)) {
                return;
            }
        }
        if (WindowLayout.close(tab)) {
            TabSelection.clear();
        }
    }

    /** Closes a whole window: its tabs leave it and the window goes. */
    public void closeWindow(Window window) {
        for (ScreenPart part : this.parts) {
            if (part.closeWindow(window)) {
                return;
            }
        }
        if (window != null && WindowLayout.closeWindow(window.getId())) {
            TabSelection.prune();
        }
    }

    /* ---- Life ---- */

    @Override
    public void initGui() {
        // Back on the screen, the pages that close with it stay.
        closingPages = false;
        for (ScreenPart part : this.parts) {
            part.beforeInit();
        }
        super.initGui();
        this.toolStrip.bind(searchField());
        this.gestures.bind(this.mc, this.fontRendererObj, this.width,
                this.height);
        this.subWindows.bind(this.width, this.height);
        for (ScreenPart part : this.parts) {
            part.init();
        }
        // initGui also runs on resize; the entrance only plays once per
        // opening.
        if (!this.openAnimationStarted) {
            this.openAnimationStarted = true;
            WindowOpening.start();
            for (ScreenPart part : this.parts) {
                part.opened();
            }
            this.subWindowsToRestore = true;
        }
    }

    /** The field the search well types into: a part's, else the game's own kind. */
    private GuiTextField searchField() {
        for (ScreenPart part : this.parts) {
            GuiTextField field = part.makeSearchField();
            if (field != null) {
                return field;
            }
        }
        return new GuiTextField(this.fontRendererObj, 0, 0, 10,
                ToolStrip.FIELD_HEIGHT);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // Every page in front of its window keeps time: a caret, a list
        // that follows the world.
        for (Window window : WindowLayout.windows()) {
            PageContent content = WindowPages.contentOf(window.getActiveTab());
            if (content != null) {
                content.tick();
            }
        }
        for (ScreenPart part : this.parts) {
            part.tick();
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // A screen closed mid-drag ends the drag where it stands: this
        // instance is gone and nothing else would ever release it.
        this.gestures.cancelDrags();
        this.subWindows.cancel();
        // The sub-windows close with the screen and come back with it.
        SubWindowPlaces.rememberOpen(this.subWindows.openWindows());
        // A search belongs to the open screen and goes with it.
        WindowSearch.close();
        leavePage();
        this.pressedPage = null;
        for (PageTab page : this.shownPages) {
            page.content().hidden();
        }
        this.shownPages.clear();
        closingPages = true;
        for (ScreenPart part : this.parts) {
            part.closed();
        }
    }

    /* ---- Keys ---- */

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // A press that types a character types it. Every shortcut here
        // takes a press that types nothing, so no symbol a keyboard
        // layout puts under Alt Gr is lost to the shortcut on its key.
        LostTalesKeyPress press = LostTalesKeyPress.read(typedChar, keyCode);
        syncTypingFocus();
        if (handleSnapKey(press)) {
            return;
        }
        for (ScreenPart part : this.parts) {
            if (part.keyOverAll(press)) {
                return;
            }
        }
        // The page in front takes its keys, the screen's own Ctrl
        // shortcuts aside; what it does not use goes no further, but for
        // Escape, which the screen answers as it does anywhere. A page's
        // key comes first, unless the page is typing or asking.
        if (this.focusedPage != null && fieldKeeps(press)) {
            PageContent content = WindowPages.contentOf(this.focusedPage);
            if (content != null && !content.holdsKeys()
                    && pressPageKey(press)) {
                return;
            }
            if (content != null && content.keyTyped(typedChar, keyCode)) {
                return;
            }
            if (keyCode != Keyboard.KEY_ESCAPE) {
                return;
            }
        }
        for (ScreenPart part : this.parts) {
            part.keyPassing(press);
        }
        if (keyCode == Keyboard.KEY_ESCAPE && this.snapFlyout.isOpen()) {
            this.snapFlyout.close();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE && this.snapAssist.isOpen()) {
            this.snapAssist.close();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE
                && (this.gestures.isDragging()
                        || this.subWindows.isHolding())) {
            this.gestures.cancelDrags();
            this.subWindows.cancel();
            return;
        }
        for (ScreenPart part : this.parts) {
            if (part.keyForTypedWindow(press)) {
                return;
            }
        }
        // Ctrl+F opens the search over the window the keys are in, and
        // while its field holds the keys they are its own: Enter walks
        // what it found, Escape closes it, and everything else is typed
        // into it.
        if (press.isCommand(Keyboard.KEY_F)) {
            openSearch();
            return;
        }
        // As with a sub-window's field, only the screen's own Ctrl
        // shortcuts reach past the search field.
        if (this.toolStrip.isFocused() && fieldKeeps(press)) {
            handleSearchKey(typedChar, keyCode);
            return;
        }
        // Escape puts away a list the field of the sub-window in front has
        // out, then closes that sub-window, before anything behind it.
        if (keyCode == Keyboard.KEY_ESCAPE) {
            SubWindow inFront = this.subWindows.focused();
            if (inFront != null && inFront.content.dismissPopup()) {
                return;
            }
            if (this.subWindows.closeFocused()) {
                syncTypingFocus();
                return;
            }
        }
        for (ScreenPart part : this.parts) {
            if (part.keyShortcut(press)) {
                return;
            }
        }
        // The field of the sub-window in front keeps the keys but for the
        // screen's own Ctrl shortcuts.
        SubWindow front = this.subWindows.focused();
        if (front != null && front.content.holdsKeys() && fieldKeeps(press)) {
            typeIntoSubWindow(front, press);
            return;
        }
        if (!hasField()) {
            // With nothing to type into, Escape closes the screen, and
            // Ctrl+W the page holding the keys.
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.mc.displayGuiScreen(null);
            } else if (press.isCommand(Keyboard.KEY_W)
                    && this.focusedPage != null) {
                closeTab(this.focusedPage);
            }
            return;
        }
        for (ScreenPart part : this.parts) {
            if (part.keyTyped(press)) {
                return;
            }
        }
    }

    /**
     * A page's key over the page holding the keys (N1 a): the page's own
     * key closes it, as Ctrl+W does, and another page's brings that page
     * forward with the keys. False when the press is no page's key, or
     * its page cannot come forward.
     */
    private boolean pressPageKey(LostTalesKeyPress press) {
        if (press.command || press.alt) {
            return false;
        }
        PageTab page = WindowPages.tabForKey(press.key);
        if (page == null || !page.isAvailable()) {
            return false;
        }
        if (page.equals(this.focusedPage)) {
            closeTab(page);
            return true;
        }
        if (WindowLayout.showPage(page) == null) {
            return false;
        }
        focusPage(page);
        return true;
    }

    /** A key for the field of the sub-window in front: a part's, else the sub-window's own. */
    private void typeIntoSubWindow(SubWindow front, LostTalesKeyPress press) {
        for (ScreenPart part : this.parts) {
            if (part.keyIntoSubWindow(front, press)) {
                syncTypingFocus();
                return;
            }
        }
        front.content.keyTyped(press.character, press.key);
    }

    /* ---- The search ---- */

    /**
     * Opens the search over the window the keys are in and gives it the
     * keys.
     */
    private void openSearch() {
        Window typed = keysWindow();
        WindowTab inFront = typed == null ? null : typed.getActiveTab();
        if (inFront == null || !inFront.hasToolStrip()) {
            // The search stands in a tool strip; a window with none has no search.
            return;
        }
        leavePage();
        // The search takes the keys, so a sub-window's field that held
        // them lets them go.
        SubWindow front = this.subWindows.focused();
        if (front != null) {
            front.content.releaseKeys();
        }
        WindowSearch.open(typed.getId());
        this.toolStrip.setText(WindowSearch.query());
        focusSearch(true);
    }

    /** Closes the search: what it lit goes, and the keys go back. */
    private void closeSearch() {
        WindowSearch.close();
        this.toolStrip.setText("");
        focusSearch(false);
    }

    /** The keys go to the search field, or back to whichever field comes next. */
    private void focusSearch(boolean on) {
        this.toolStrip.focus(on);
        syncTypingFocus();
    }

    /**
     * A menu opened from the keyboard takes the keys, so the search that
     * held them lets them go: an empty one closes.
     */
    public void leaveSearchForMenu() {
        if (this.toolStrip.isFocused()) {
            leaveSearch();
        }
    }

    /**
     * The keys go back: a search with words in it stays open with what it
     * found lit, and an empty one closes, since there is nothing of it to
     * keep.
     */
    private void leaveSearch() {
        if (WindowSearch.query().length() == 0) {
            closeSearch();
        } else {
            focusSearch(false);
        }
    }

    /**
     * The page the search is open over, while a page is in front of its
     * window; null for a part's search or none.
     */
    private static PageTab searchedPage() {
        Window searched = WindowLayout.window(WindowSearch.windowId());
        WindowTab front = searched == null ? null : searched.getActiveTab();
        return front instanceof PageTab ? (PageTab)front : null;
    }

    private void handleSearchKey(char typedChar, int keyCode) {
        PageTab page = searchedPage();
        boolean enter = keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeSearch();
            // A page's search closed hands the keys back to the page.
            focusPage(page);
            return;
        }
        if (page != null) {
            // A page walks what its search found and reads it on Return,
            // which gives the page the keys and keeps the words standing.
            PageContent content = WindowPages.contentOf(page);
            boolean taken = content != null && content.searchKey(keyCode);
            if (enter) {
                focusSearch(false);
                focusPage(page);
                return;
            }
            if (taken) {
                return;
            }
        } else if (enter) {
            walkSearch(!isShiftKeyDown());
            return;
        }
        if (this.toolStrip.keyTyped(typedChar, keyCode)) {
            WindowSearch.setQuery(this.toolStrip.text());
        }
    }

    /** Walks to the next find down, or the previous one up, in what a part shows. */
    private void walkSearch(boolean forward) {
        for (ScreenPart part : this.parts) {
            if (part.walkSearch(forward)) {
                return;
            }
        }
    }

    /**
     * Opens the search over a window and gives it the keys: the window
     * takes the keys, since the search follows them; a page's window only
     * comes forward, and the page lets the keys go.
     */
    private void searchIn(Window window) {
        selectWindow(window);
        leavePage();
        if (!WindowSearch.isOpenOn(window.getId())) {
            WindowSearch.open(window.getId());
            this.toolStrip.setText(WindowSearch.query());
        }
        focusSearch(true);
    }

    /**
     * A press on a window's tool strip: the panel button drives the tab's
     * panel out or back in, the cog opens the tab's menu, the member
     * list's button puts the list away or brings it out, the well opens
     * the search over the window and takes the caret, the magnifier does
     * the same and the cross it becomes clears the search, and the
     * chevrons walk what it found.
     */
    private void clickToolStrip(WindowHover press, double x, double y,
                                int button) {
        if (button != 0 || press.frame == null || press.window == null
                || press.stripPart == null || press.row == null) {
            return;
        }
        WindowTab front = press.row.selected;
        switch (press.stripPart) {
            case PANEL:
                if (front != null) {
                    front.togglePanel(press.window);
                }
                return;
            case SETTINGS:
                showTabMenu(press.window.getActiveTab(),
                        SubWindowAnchor.onToolStrip(press.frame, press.row,
                                (int)Math.floor(x), this.width, this.height),
                        true);
                return;
            case MEMBERS_TOGGLE:
                if (front != null) {
                    front.toggleMemberList(press.window);
                }
                return;
            case FIELD:
                searchIn(press.window);
                this.toolStrip.clickField(press.row, x, y);
                return;
            case ICON:
                if (WindowSearch.isOpenOn(press.window.getId())
                        && WindowSearch.query().length() > 0) {
                    closeSearch();
                } else {
                    searchIn(press.window);
                }
                return;
            case PREVIOUS:
                walkSearch(false);
                return;
            case NEXT:
                walkSearch(true);
                return;
            default:
                return;
        }
    }

    /* ---- Menus, through the parts ---- */

    private void showTabMenu(WindowTab tab, SubWindowAnchor anchor,
                             boolean toggle) {
        for (ScreenPart part : this.parts) {
            if (part.showTabMenu(tab, anchor, toggle)) {
                return;
            }
        }
    }

    private void showWindowMenu(Window window, SubWindowAnchor anchor,
                                boolean toggle) {
        for (ScreenPart part : this.parts) {
            if (part.showWindowMenu(window, anchor, toggle)) {
                return;
            }
        }
    }

    private boolean isMenuOpenFor(SubWindowKind kind, Object about) {
        for (ScreenPart part : this.parts) {
            if (part.isMenuOpenFor(kind, about)) {
                return true;
            }
        }
        return false;
    }

    /* ---- Snapping from the keyboard ---- */

    /**
     * Snapping from the keyboard, on the window the keys are in: Alt with
     * an arrow sends it on through the parts of the screen as the
     * desktop's Windows key does ({@link SnapKeys}), and Alt+Z opens its
     * snap layouts, which the arrows then walk, Enter takes and Escape —
     * or Alt+Z again — puts away. A press that types a character, as Alt
     * Gr and a Mac's Option do, is typing, not snapping.
     */
    private boolean handleSnapKey(LostTalesKeyPress press) {
        int keyCode = press.key;
        if (this.snapFlyout.isKeyboardOpen()) {
            SnapKeys.Direction walk = SnapKeys.direction(keyCode);
            if (walk != null) {
                this.snapFlyout.step(walk);
                return true;
            }
            if (keyCode == Keyboard.KEY_RETURN
                    || keyCode == Keyboard.KEY_NUMPADENTER) {
                int zone = this.snapFlyout.keyZone();
                Window window = WindowLayout.window(
                        this.snapFlyout.windowId());
                if (zone >= 0 && window != null && !window.isLocked()) {
                    sendWindowTo(window, this.snapFlyout.fillOf(zone),
                            this.snapFlyout.layoutOf(zone),
                            this.snapFlyout.companionsOf(zone));
                }
                this.snapFlyout.close();
                return true;
            }
        }
        if (!press.alt || this.gestures.isDragging()) {
            return false;
        }
        Window window = keysWindow();
        if (window == null || window.isLocked()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_Z) {
            if (this.snapFlyout.isKeyboardOpen()) {
                this.snapFlyout.close();
                return true;
            }
            WindowFrame frame = WindowFrame.find(window.getId());
            TabRow.Row row = frame == null || !frame.drawn ? null
                    : rowFor(window, frame, WindowOpening.sample());
            if (row != null) {
                this.snapFlyout.openFromKeyboard(window,
                        frame.tabBar.fullscreenControlBox(row), frame,
                        this.mc, this.width, this.height);
            }
            return true;
        }
        SnapKeys.Direction direction = SnapKeys.direction(keyCode);
        if (direction == null) {
            return false;
        }
        Window.ScreenFill next = SnapKeys.next(window.getFill(), direction);
        sendWindowTo(window, next, SnapLayouts.layoutFor(next),
                Collections.<String, Window.ScreenFill>emptyMap());
        return true;
    }

    /**
     * Sends a window to a part of the screen. A suggestion's other
     * windows, {@code companions}, go to their zones with it; without
     * them the rest of the layout that part is a zone of is offered to
     * the other windows.
     */
    private void sendWindowTo(Window window, Window.ScreenFill fill,
                              Window.ScreenFill[] layout,
                              Map<String, Window.ScreenFill> companions) {
        for (Map.Entry<String, Window.ScreenFill> companion
                : companions.entrySet()) {
            Window other = WindowLayout.window(companion.getKey());
            if (other != null && !other.isLocked()) {
                WindowLayout.takeFill(other, companion.getValue());
            }
        }
        WindowLayout.takeFill(window, fill);
        if (companions.isEmpty()) {
            this.snapAssist.offer(window.getId(), layout, fill);
        }
    }

    /**
     * The snap layouts under a window's fullscreen control: they open
     * once the pointer has rested on the control, and stay while it is on
     * the control or on them. A drag puts them away.
     */
    private void followSnapFlyout() {
        Window window = null;
        LostTalesUiHitBox control = null;
        if (this.hover.is(WindowHover.Kind.TAB_ROW)
                && this.hover.tabHit != null
                && this.hover.tabHit.kind == TabRow.HitKind.WINDOW_FULLSCREEN
                && this.hover.window != null && this.hover.frame != null
                && !this.hover.window.isLocked()) {
            window = this.hover.window;
            control = this.hover.frame.tabBar.fullscreenControlBox(
                    this.hover.row);
        }
        this.snapFlyout.follow(window, control,
                window == null ? null : this.hover.frame,
                this.hover.is(WindowHover.Kind.SNAP_LAYOUT),
                !this.gestures.isDragging() && !isEmpty(),
                this.mc, this.width, this.height);
    }

    /* ---- The wheel ---- */

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        // The wheel event carries no pointer of its own: it is read off
        // the mouse in window pixels and scaled to the screen, to the
        // same fraction the hover reads.
        double mouseX = Mouse.getEventX() * (double)this.width
                / this.mc.displayWidth;
        double mouseY = this.height - Mouse.getEventY() * (double)this.height
                / this.mc.displayHeight - 1.0D;
        // One turn of the wheel is two whole lines, one with Shift,
        // counted in the unit of what it scrolls.
        int lines = WheelStep.lines(wheel, isShiftKeyDown());
        // The wheel scrolls what the pointer is on, found as the hover
        // finds it: a sub-window's content, a page, else what a part
        // shows there.
        WindowHover under = hoverAt(mouseX, mouseY);
        if (under.subWindow != null) {
            // A sub-window takes the wheel wherever it is turned over it,
            // and its content scrolls; nothing behind it moves.
            under.subWindow.content.scrollBy(-lines);
            return;
        }
        if (under.is(WindowHover.Kind.PAGE)) {
            PageContent content = WindowPages.contentOf(under.frame.page);
            if (content != null) {
                LostTalesUiHitBox exact = WindowDrawing.pageBox(under.frame);
                LostTalesUiHitBox whole = WindowDrawing.wholePageBox(
                        under.frame);
                content.scroll(whole, mouseX - (exact.left - whole.left),
                        mouseY - (exact.top - whole.top), -lines);
            }
            return;
        }
        for (ScreenPart part : this.parts) {
            if (part.wheel(under, mouseX, mouseY, lines)) {
                return;
            }
        }
    }

    /* ---- A frame ---- */

    /**
     * Every window is one motion group: its content, row and bar all
     * take the same opening sample, so a window enters, settles and fades
     * as one piece. Rows are drawn on the fractional position the window
     * was drawn at, never on a rounded one, so they never jitter against
     * what the window holds.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // The screen is flat overlay content and takes no part in depth
        // testing, exactly as the HUD's chat pass does not: an item icon
        // drawn at a raised z leaves its depth behind, and with the test
        // on whatever is drawn over it afterwards is rejected where they
        // overlap.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        this.depthTestAtStart = depthTest;
        if (depthTest) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        try {
            drawAll(mouseX, mouseY, partialTicks);
        } finally {
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
        }
    }

    private void drawAll(int mouseX, int mouseY, float partialTicks) {
        for (ScreenPart part : this.parts) {
            part.beginFrame();
        }
        syncPageFocus();
        syncTypingFocus();
        for (ScreenPart part : this.parts) {
            part.beforeHover();
        }
        this.gestures.advance(mouseX, mouseY);
        // A sub-window held follows the pointer every frame, as a window
        // does, so it glides with it.
        if (this.subWindows.isHolding()) {
            this.subWindows.drag(pointerX(), pointerY());
        }
        // The pointer's exact GUI position, the one the drawn cursor tip
        // stands on. What is under it is found once, against what is on
        // screen — the windows, rows, lists and regions the last frame
        // drew — before any of it is drawn again, and every highlight,
        // tip and card of this frame, and the pointer's pose, read that
        // one answer.
        double pointerX = pointerX();
        double pointerY = pointerY();
        this.hover = hoverAt(pointerX, pointerY);
        followSnapFlyout();
        for (ScreenPart part : this.parts) {
            part.hovered(this.hover);
        }
        this.regions.reset();
        this.hoverTip = tipFor(this.hover);
        this.hoverTipX = mouseX;
        this.hoverTipY = mouseY;
        // The frame as drawn so far — world and HUD — is captured and
        // blurred once, before any window is on it; each window then
        // pastes its own rectangle of the result under its backdrop while
        // the rest of the screen stays sharp.
        if (LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur) {
            LostTalesGuiRegionBlur.getInstance().capture(this.mc,
                    partialTicks, (float)LostTalesConfig.guiBlurStrength);
        }
        for (ScreenPart part : this.parts) {
            part.beforeWindows(pointerX, pointerY);
        }
        // The sub-windows of the window typed in are drawn over every
        // window; the others with their own windows.
        this.subWindows.beginFrame(typedWindowId());
        drawWindows(mouseX, mouseY, pointerX, pointerY, partialTicks);
        for (ScreenPart part : this.parts) {
            part.afterWindows();
        }
        if (this.subWindowsToRestore) {
            this.subWindowsToRestore = false;
            restoreSubWindows();
        }
        boolean empty = isEmpty();
        boolean typing = !empty && hasField();
        for (ScreenPart part : this.parts) {
            part.drawUnderSubWindows(empty, typing, pointerX, pointerY);
        }
        // The sub-windows of the window typed in stand over every window
        // and whatever stands over the windows, and those on the bare
        // screen over them.
        String typed = typedWindowId();
        if (typing && typed != null) {
            this.subWindows.draw(this.mc, this.fontRendererObj, this.regions,
                    this.hover, pointerX, pointerY, false, typed);
        }
        this.subWindows.draw(this.mc, this.fontRendererObj, this.regions,
                this.hover, pointerX, pointerY, empty, null);
        for (ScreenPart part : this.parts) {
            part.drawOverSubWindows(typing, pointerX, pointerY, mouseX,
                    mouseY);
        }
        this.gestures.drawLinkHighlight();
        float shownOpacity = WindowOpening.sample().getOpacity();
        this.gestures.drawSnapBar(shownOpacity);
        this.snapAssist.layOut(this.mc, this.fontRendererObj, this.width,
                this.height);
        this.snapAssist.draw(this.mc, this.fontRendererObj, this.regions,
                this.hover.is(WindowHover.Kind.SNAP_ASSIST)
                        ? this.hover.assistCard : null, shownOpacity);
        this.snapFlyout.draw(this.mc, this.regions,
                this.hover.is(WindowHover.Kind.SNAP_LAYOUT)
                        && this.hover.snapZone >= 0 ? this.hover.snapZone
                        : this.snapFlyout.keyZone(), shownOpacity);
        this.subWindows.drawTip(this.mc, this.hover, mouseX, mouseY);
        // The snap layouts own their tooltips; controls behind them stay
        // quiet.
        if (this.hoverTip.length() > 0 && !this.snapFlyout.isShown()) {
            drawHoverTip();
        }
    }

    /**
     * The sub-windows open as the screen last closed come back where they
     * stood, each through the part it belongs to. None of them is in
     * front: Escape closes the screen again.
     */
    private void restoreSubWindows() {
        for (SubWindowPlaces.Reopening open : SubWindowPlaces.openAtClose()) {
            for (ScreenPart part : this.parts) {
                if (part.reopen(open)) {
                    break;
                }
            }
        }
        this.subWindows.blur();
    }

    public double pointerX() {
        return WindowPlacement.preciseMouseX(this.mc, this.width);
    }

    public double pointerY() {
        return WindowPlacement.preciseMouseY(this.mc, this.height);
    }

    /* ---- The windows ---- */

    /**
     * Every window, back to front, each complete before the next: what it
     * holds, then the tab row standing on it and carrying the window's
     * top rule as its last pixel row, its tool strip, the surface its
     * frame lies on, and its foot — a page's edges, or whatever a part
     * stands there. The row is the window's title strip and is there while
     * the window has a tab the player can see.
     */
    private void drawWindows(int mouseX, int mouseY, double pointerX,
                             double pointerY, float partialTicks) {
        LostTalesGuiAnimationSample opening = WindowOpening.sample();
        List<Window> windows = WindowLayout.stacked();
        // The search follows the input: moved to another window, it
        // closes. A page is never typed in, so its search stands for as
        // long as the page is in front of its window.
        if (searchedPage() == null) {
            WindowSearch.closeUnless(inputWindowId());
        }
        if (!WindowSearch.isOpen() && this.toolStrip.isFocused()) {
            focusSearch(false);
        }
        boolean blurActive = LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur;
        List<WindowPlacement.Box> drawnBoxes = blurActive
                ? new ArrayList<WindowPlacement.Box>(windows.size()) : null;
        String previewed = this.gestures.snapPreview().windowId();
        String typed = typedWindowId();
        List<PageTab> drawnPages = new ArrayList<PageTab>();
        for (int index = 0; index < windows.size(); index++) {
            Window window = windows.get(index);
            if (window.getId().equals(previewed)) {
                drawSnapPreview(window, drawnBoxes, opening, partialTicks);
            }
            // A window just made from carried tabs fades in on its own,
            // inside the screen's own motion.
            WindowFrame frame = WindowFrame.of(window);
            LostTalesGuiAnimationSample shown = opening.withOpacity(
                    frame.appearShare());
            if (drawnBoxes != null
                    && !WindowFrame.visibleTabs(window).isEmpty()) {
                // A window over another one pastes its rectangle of the
                // blurred frame; captured before any window, that
                // rectangle holds only the world and would erase what was
                // just drawn behind it. Re-capturing here puts the windows
                // already drawn into the front window's blur, so an
                // overlapped window stays visible — softened — behind the
                // one in front. Measured where the window stands this
                // frame, a window gliding to or from the screen included.
                frame.advanceFill(window.getFill());
                WindowPlacement.Box box = WindowPlacement.windowBounds(window,
                        this.mc, this.width, this.height);
                if (overlapsAny(drawnBoxes, box)) {
                    LostTalesGuiRegionBlur.getInstance().capture(this.mc,
                            partialTicks,
                            (float)LostTalesConfig.guiBlurStrength);
                }
                drawnBoxes.add(box);
            }
            layOutWindow(window, frame, shown);
            TabRow.Row row = rowFor(window, frame, shown);
            if (row == null) {
                continue;
            }
            // A page in front takes the window under its tool strip. It
            // reads the well's words, none while its search is closed,
            // before the strip counts what they found.
            boolean page = frame.page != null;
            PageContent pageContent = WindowPages.contentOf(frame.page);
            if (pageContent != null) {
                pageContent.search(WindowSearch.isOpenOn(window.getId())
                        ? WindowSearch.query() : "");
            }
            // The row is laid out in whole pixels and shifted by the
            // window's fractional remainder, so it sits exactly where what
            // the window holds does while the window glides. The row is
            // told the same remainder, since its scissors are cut outside
            // this matrix. The tool strip lays itself out first, so the
            // strip can leave its well out of the surface it paints.
            this.toolStrip.prepare(this.fontRendererObj, frame, row);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(row.fractionX, row.fractionY, 0.0F);
                // Only the row the pointer is on sees it: a row under a
                // menu, a list or another window is drawn with the
                // pointer away.
                boolean onRow = this.hover.isOnRowOf(frame);
                frame.tabBar.draw(this.fontRendererObj, this.regions, row,
                        onRow ? pointerX : WindowHover.AWAY,
                        onRow ? pointerY : WindowHover.AWAY,
                        shown.getOpacity());
                if (!page) {
                    // What the window's search found is read before the
                    // strip counts it.
                    for (ScreenPart part : this.parts) {
                        part.searchWindow(window, frame);
                    }
                }
                this.toolStrip.draw(this.fontRendererObj, frame, window,
                        row, shown.getOpacity(),
                        this.hover.is(WindowHover.Kind.TOOL_STRIP)
                                && this.hover.frame == frame
                                ? this.hover.stripPart : null,
                        isMenuOpenFor(SubWindowKind.TAB,
                                window.getActiveTab()));
            } finally {
                GL11.glPopMatrix();
            }
            WindowDrawing.drawFrameSurface(this.mc, frame, shown);
            if (page) {
                drawnPages.add(frame.page);
                if (!this.shownPages.contains(frame.page)) {
                    frame.page.content().shown();
                }
                drawPage(frame, shown, pointerX, pointerY, partialTicks);
                WindowDrawing.drawPageFrameEdges(this.mc, frame, shown);
            } else {
                for (ScreenPart part : this.parts) {
                    part.drawWindowFoot(window, frame, shown, mouseX, mouseY);
                }
            }
            // Its sub-windows, which the windows in front of it cover; the
            // typed window's come over everything, after what stands over
            // the windows.
            if (!window.getId().equals(typed)) {
                this.subWindows.draw(this.mc, this.fontRendererObj,
                        this.regions, this.hover, pointerX, pointerY, false,
                        window.getId());
            }
        }
        // A page no longer drawn has left the screen: behind another tab,
        // or its window closed.
        for (PageTab left : this.shownPages) {
            if (!drawnPages.contains(left)) {
                left.content().hidden();
            }
        }
        this.shownPages.clear();
        this.shownPages.addAll(drawnPages);
    }

    /**
     * What a window holds, laid out and drawn under its row: a page by the
     * screen, anything else by the part it belongs to. A window no part
     * draws is not on screen.
     */
    private void layOutWindow(Window window, WindowFrame frame,
                              LostTalesGuiAnimationSample shown) {
        WindowTab front = WindowFrame.activeTab(window,
                WindowFrame.visibleTabs(window));
        if (front instanceof PageTab) {
            WindowDrawing.layOutPage(this.mc, window, frame, (PageTab)front,
                    this.width, this.height, shown);
            return;
        }
        for (ScreenPart part : this.parts) {
            if (part.drawWindow(window, shown)) {
                return;
            }
        }
        frame.drawn = false;
    }

    /** The window whose input is live: the search follows it; null for none. */
    private String inputWindowId() {
        for (ScreenPart part : this.parts) {
            String id = part.inputWindowId();
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /**
     * The pane showing where a carried window goes, drawn under that
     * window and over every window behind it, and on a suggestion a pane
     * for each other window it sends along, growing out of that window
     * with its tab icon in the middle. Their glass blurs what lies behind
     * it as a window's does, the windows already drawn included, and the
     * window drawn over it blurs the panes in turn.
     */
    private void drawSnapPreview(Window window,
                                 List<WindowPlacement.Box> drawnBoxes,
                                 LostTalesGuiAnimationSample opening,
                                 float partialTicks) {
        SnapPreview preview = this.gestures.snapPreview();
        WindowFrame.of(window).advanceFill(window.getFill());
        SnapPreview.Pane pane = preview.advance(this.mc,
                WindowPlacement.windowBounds(window, this.mc, this.width,
                        this.height), this.width, this.height);
        if (pane != null) {
            blurUnderPane(pane, drawnBoxes, partialTicks);
            SnapPreview.draw(this.mc, pane,
                    preview.opacity() * opening.getOpacity());
        }
        List<SnapPreview.Shown> companions = preview.advanceCompanions(
                this.mc, new SnapPreview.Boxes() {
                    @Override
                    public WindowPlacement.Box of(String windowId) {
                        Window other = WindowLayout.window(windowId);
                        if (other == null) {
                            return null;
                        }
                        WindowFrame.of(other).advanceFill(other.getFill());
                        return WindowPlacement.windowBounds(other,
                                WindowScreen.this.mc,
                                WindowScreen.this.width,
                                WindowScreen.this.height);
                    }
                }, this.width, this.height);
        for (SnapPreview.Shown shown : companions) {
            blurUnderPane(shown.pane, drawnBoxes, partialTicks);
            float opacity = shown.opacity * opening.getOpacity();
            SnapPreview.draw(this.mc, shown.pane, opacity);
            SnapPreview.drawIcon(this.mc, shown.windowId, shown.pane,
                    opacity);
        }
    }

    /**
     * Counts a pane's framed box among what is drawn, capturing the frame
     * again first where the pane lies over something already drawn, so
     * its glass blurs that too.
     */
    private void blurUnderPane(SnapPreview.Pane pane,
                               List<WindowPlacement.Box> drawnBoxes,
                               float partialTicks) {
        if (drawnBoxes == null) {
            return;
        }
        WindowPlacement.Box box = pane.framed(
                WindowPlacement.barHeight(this.mc));
        if (overlapsAny(drawnBoxes, box)) {
            LostTalesGuiRegionBlur.getInstance().capture(this.mc,
                    partialTicks, (float)LostTalesConfig.guiBlurStrength);
        }
        drawnBoxes.add(box);
    }

    /** Whether the box crosses any of the boxes already drawn. */
    private static boolean overlapsAny(List<WindowPlacement.Box> boxes,
                                       WindowPlacement.Box box) {
        for (int index = 0; index < boxes.size(); index++) {
            WindowPlacement.Box other = boxes.get(index);
            if (box.x < other.x + other.width
                    && box.x + box.width > other.x
                    && box.y < other.y + other.height
                    && box.y + box.height > other.y) {
                return true;
            }
        }
        return false;
    }

    /**
     * The row description of a window for this frame, or null when the
     * window has no row to show right now.
     */
    public TabRow.Row rowFor(Window window, WindowFrame frame,
                             LostTalesGuiAnimationSample opening) {
        if (!frame.drawn) {
            return null;
        }
        List<WindowTab> tabs = WindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return null;
        }
        TabRow.Row row = new TabRow.Row();
        row.tabs = tabs;
        row.selected = WindowFrame.activeTab(window, tabs);
        row.marked = TabSelection.selectedIn(window);
        // Whole-pixel geometry of the drawn (motion included) position;
        // the fractional remainder is applied when the row is drawn.
        row.rowBottom = (int)Math.floor(frame.tabRowBottom());
        row.rowBottomExact = frame.tabRowBottom();
        row.fractionX = (float)(frame.drawnLeft()
                - Math.floor(frame.drawnLeft()));
        row.fractionY = (float)(row.rowBottomExact - row.rowBottom);
        row.left = (int)Math.floor(frame.drawnLeft()) + 2;
        row.right = (int)Math.floor(frame.drawnLeft()) + (int)Math.round(
                frame.boxRight - frame.boxLeft) - 2;
        // The edge as it really stands, so the tabs follow a resize by
        // the fraction the edge moves rather than a pixel at a time.
        row.rightExact = Math.floor(frame.drawnLeft())
                + (frame.boxRight - frame.boxLeft) - 2;
        row.offsetX = 0;
        row.locked = window.isLocked();
        row.moving = this.gestures.isMovingWindow(window.getId());
        row.resizing = this.gestures.isResizingWindow(window.getId());
        row.gliding = frame.isFillGliding();
        // A locked window keeps the tabs and the size it has, so it
        // offers neither a tab cross nor the window's own controls: they
        // are all refused anyway, and would only mislead.
        row.closable = WindowLayout.isClosable(row.selected);
        row.windowControls = !window.isLocked();
        row.fullscreenShare = frame.fullShare();
        row.showRestore = !window.isLocked() && hasRestorable();
        row.closedMark = row.showRestore ? restorableMark() : TabMark.NONE;
        // The controls say whether this row's own tab search or + menu
        // is out, so a control and its window can never disagree.
        row.toolStrip = row.selected == null || row.selected.hasToolStrip();
        row.searchOpen = isMenuOpenFor(SubWindowKind.TAB_SEARCH,
                window.getId());
        row.restoreOpen = isMenuOpenFor(SubWindowKind.OPEN, window.getId());
        WindowGestures.TabDrag tabDrag = this.gestures.activeTabDrag();
        if (tabDrag != null && window.contains(tabDrag.tab)) {
            // The tab keeps its place in the row and leans toward the
            // pointer; the row has already reordered around it, so
            // there is nothing to mark an insertion point for.
            row.dragging = tabDrag.tab;
            // Everything travelling with it, so a marked group leans,
            // changes places and stops as one long tab.
            row.draggedGroup = tabDrag.group;
            row.draggedLeft = tabDrag.pointerX - tabDrag.grabOffsetX;
            // The same from the pointer's exact position, in the row's
            // own space, which is drawn moved by its fraction: the tab
            // follows the hand a display pixel at a time.
            row.draggedLeftExact = WindowPlacement.preciseMouseX(this.mc,
                    this.width) - row.fractionX - tabDrag.grabOffsetX;
        }
        return row;
    }

    /** Whether the {@code +} has anything to offer again: a closed page, or a part's. */
    private boolean hasRestorable() {
        if (WindowPages.hasClosed()) {
            return true;
        }
        for (ScreenPart part : this.parts) {
            if (part.hasRestorable()) {
                return true;
            }
        }
        return false;
    }

    /** What waits in the {@code +}'s corner: the first part's that has something. */
    private TabMark restorableMark() {
        for (ScreenPart part : this.parts) {
            TabMark mark = part.restorableMark();
            if (mark != null && mark != TabMark.NONE) {
                return mark;
            }
        }
        return TabMark.NONE;
    }

    /* ---- Pages ---- */

    /**
     * A page window's page: its surface under the row, then the page drawn
     * on whole pixels in a matrix moved by the fraction the window stands
     * on, the pointer handed over only while it is on the page.
     */
    private void drawPage(WindowFrame frame, LostTalesGuiAnimationSample shown,
                          double pointerX, double pointerY,
                          float partialTicks) {
        WindowDrawing.drawPageSurface(this.mc, frame, shown);
        PageContent content = WindowPages.contentOf(frame.page);
        if (content == null) {
            return;
        }
        LostTalesUiHitBox exact = WindowDrawing.pageBox(frame);
        LostTalesUiHitBox whole = WindowDrawing.wholePageBox(frame);
        float fractionX = (float)(exact.left - whole.left);
        float fractionY = (float)(exact.top - whole.top);
        // The page a held button went down on keeps the pointer wherever
        // it goes, as a screen of its own would.
        boolean onPage = this.hover.is(WindowHover.Kind.PAGE)
                && this.hover.frame == frame
                || frame.page.equals(this.pressedPage);
        boolean depth = this.depthTestAtStart && content.wantsDepthTest();
        GL11.glPushMatrix();
        if (depth) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        try {
            GL11.glTranslatef(fractionX, fractionY, 0.0F);
            content.draw(this.mc, whole, exact.left, exact.top,
                    onPage ? pointerX - fractionX : WindowHover.AWAY,
                    onPage ? pointerY - fractionY : WindowHover.AWAY,
                    partialTicks, Math.round(255.0F * shown.getOpacity()));
        } finally {
            if (depth) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glPopMatrix();
        }
    }

    /**
     * A press on a page: its window comes to the front, the page takes the
     * keys and the press, in the page's own whole pixels.
     */
    private void pressPage(WindowHover press, double x, double y,
                           int button) {
        PageTab page = press.frame == null ? null : press.frame.page;
        PageContent content = WindowPages.contentOf(page);
        if (content == null) {
            return;
        }
        WindowLayout.raise(press.frame.windowId);
        focusPage(page);
        LostTalesUiHitBox exact = WindowDrawing.pageBox(press.frame);
        LostTalesUiHitBox whole = WindowDrawing.wholePageBox(press.frame);
        this.pressedPage = page;
        this.pressedPageButton = button;
        content.mousePressed(this.mc, whole, x - (exact.left - whole.left),
                y - (exact.top - whole.top), button);
        syncTypingFocus();
    }

    /**
     * Where a point stands on the page a button went down on, in the
     * page's own whole pixels, and the box it is in; null while that
     * page is no longer drawn.
     */
    private double[] onPressedPage(double x, double y) {
        Window window = WindowLayout.windowOf(this.pressedPage);
        WindowFrame frame = window == null ? null
                : WindowFrame.find(window.getId());
        if (frame == null || !frame.drawn
                || !this.pressedPage.equals(frame.page)) {
            return null;
        }
        LostTalesUiHitBox exact = WindowDrawing.pageBox(frame);
        LostTalesUiHitBox whole = WindowDrawing.wholePageBox(frame);
        return new double[] {x - (exact.left - whole.left),
                y - (exact.top - whole.top), whole.left, whole.top,
                whole.width, whole.height};
    }

    private static LostTalesUiHitBox boxOf(double[] at) {
        return new LostTalesUiHitBox(at[2], at[3], at[4], at[5]);
    }

    /**
     * The page under a point, as a hover of its own: the frontmost window
     * there shows a page, and the point is under its row.
     */
    private static WindowHover pageHoverAt(double x, double y) {
        WindowFrame front = WindowFrame.drawnAt(x, y);
        if (front == null || front.page == null) {
            return null;
        }
        LostTalesUiHitBox exact = WindowDrawing.pageBox(front);
        if (!exact.contains(x, y)) {
            return null;
        }
        WindowHover hover = new WindowHover(WindowHover.Kind.PAGE);
        hover.frame = front;
        hover.window = WindowLayout.window(front.windowId);
        PageContent content = WindowPages.contentOf(front.page);
        LostTalesUiHitBox whole = WindowDrawing.wholePageBox(front);
        hover.acts = content != null && content.acts(whole,
                x - (exact.left - whole.left), y - (exact.top - whole.top));
        return hover;
    }

    /* ---- What is under the pointer ---- */

    /**
     * What is under a GUI-space point, from the top of what is drawn
     * down, in the order a press is handled: snap assist, the snap
     * layouts, what a part stands over everything, the sub-windows front
     * to back, a window's edge, the tab rows, a page, what a part stands
     * under the rows, anything else painted over the windows, and what a
     * part's windows hold. Each is asked with the one hit test its
     * highlight and its press use, so the answer is the same whichever of
     * them asks. Nothing is under the pointer while something is being
     * dragged.
     */
    public WindowHover hoverAt(double x, double y) {
        if (this.gestures.isDragging() || this.subWindows.isDragging()) {
            return WindowHover.NONE;
        }
        SnapAssist.Card card = this.snapAssist.cardAt(x, y);
        if (card != null) {
            WindowHover hover = new WindowHover(WindowHover.Kind.SNAP_ASSIST);
            hover.assistCard = card;
            return hover;
        }
        if (this.snapFlyout.contains(x, y)) {
            WindowHover hover = new WindowHover(WindowHover.Kind.SNAP_LAYOUT);
            hover.snapZone = this.snapFlyout.zoneAt(x, y);
            hover.window = WindowLayout.window(this.snapFlyout.windowId());
            return hover;
        }
        if (isEmpty()) {
            WindowHover sub = this.subWindows.hoverAt(x, y);
            if (sub != null) {
                return sub;
            }
            for (ScreenPart part : this.parts) {
                WindowHover empty = part.hoverEmpty(x, y);
                if (empty != null) {
                    return empty;
                }
            }
            return WindowHover.NONE;
        }
        for (ScreenPart part : this.parts) {
            WindowHover over = part.hoverOverAll(x, y);
            if (over != null) {
                return over;
            }
        }
        WindowHover sub = this.subWindows.hoverAt(x, y);
        if (sub != null) {
            return sub;
        }
        WindowGestures.ResizeTarget edge =
                WindowGestures.resizeUnderPointer(x, y, this.regions);
        if (edge != null && WindowLayout.window(edge.frame.windowId) != null) {
            WindowHover hover = new WindowHover(WindowHover.Kind.RESIZE);
            hover.resize = edge;
            hover.frame = edge.frame;
            return hover;
        }
        WindowHover row = rowHoverAt(x, y);
        if (row != null) {
            return row;
        }
        WindowHover onPage = pageHoverAt(x, y);
        if (onPage != null) {
            return onPage;
        }
        for (ScreenPart part : this.parts) {
            WindowHover under = part.hoverUnderRows(x, y);
            if (under != null) {
                return under;
            }
        }
        if (this.regions.contains(x, y)) {
            return new WindowHover(WindowHover.Kind.OVERLAY);
        }
        for (ScreenPart part : this.parts) {
            WindowHover in = part.hoverInWindows(x, y);
            if (in != null) {
                return in;
            }
        }
        return WindowHover.NONE;
    }

    /**
     * The tab row, or the bare stretch of one, under a point: the rows
     * front to back, each asked by the one hit test its own draw asks,
     * and nothing while a tab is under the hand or a window's edge is
     * being dragged. A window in front that covers the point ends the
     * search: a row behind it answers nothing there.
     */
    private WindowHover rowHoverAt(double x, double y) {
        LostTalesGuiAnimationSample opening = WindowOpening.sample();
        List<Window> windows = WindowLayout.stacked();
        for (int index = windows.size() - 1; index >= 0; index--) {
            Window window = windows.get(index);
            WindowFrame frame = WindowFrame.of(window);
            TabRow.Row row = rowFor(window, frame, opening);
            if (row == null) {
                continue;
            }
            TabRow.Hit hit = row.dragging != null || row.resizing
                    ? null : frame.tabBar.hitAt(this.fontRendererObj, row,
                            x, y);
            ToolStrip.Part part = hit != null || row.dragging != null
                    || row.resizing ? null
                    : this.toolStrip.partAt(frame, row, x, y);
            if (part != null) {
                WindowHover hover = new WindowHover(WindowHover.Kind.TOOL_STRIP);
                hover.window = window;
                hover.frame = frame;
                hover.row = row;
                hover.stripPart = part;
                return hover;
            }
            if (hit == null && !frame.tabBar.stripContains(
                    this.fontRendererObj, row, x, y)) {
                if (frame.drawn && frame.contains(x, y)) {
                    return null;
                }
                continue;
            }
            WindowHover hover = new WindowHover(hit != null
                    ? WindowHover.Kind.TAB_ROW : WindowHover.Kind.STRIP);
            hover.window = window;
            hover.frame = frame;
            hover.row = row;
            hover.tabHit = hit;
            // Only the grip's own glyph offers the move tip; the bare
            // strip beside it drags without saying so.
            hover.overGrip = hit != null
                    && hit.kind == TabRow.HitKind.GRIP
                    && frame.tabBar.isOverGripHandle(this.fontRendererObj,
                            row, x, y);
            return hover;
        }
        return null;
    }

    /**
     * The pointer's pose for the frame just drawn: a held edge keeps its
     * resize wherever the pointer has gone, the way a pressed control
     * keeps its look; a drag keeps the arrow; otherwise the pose of what
     * the frame found under the pointer.
     */
    @Override
    public LostTalesMapCursor.Pose pointerPose(int mouseX, int mouseY) {
        WindowGestures.ResizeEdge resizing = this.gestures.armedResizeEdge();
        if (resizing != null) {
            return WindowGestures.cursorPose(resizing);
        }
        WindowGestures.ContentDrag drag = this.gestures.contentDrag();
        if (drag != null && drag.pose() != null) {
            return drag.pose();
        }
        if (this.subWindows.isDragging()) {
            WindowGestures.ResizeEdge edge = this.subWindows.heldEdge();
            return edge != null ? WindowGestures.cursorPose(edge)
                    : LostTalesMapCursor.Pose.ARROW;
        }
        return this.gestures.isDragging() ? LostTalesMapCursor.Pose.ARROW
                : this.hover.pose();
    }

    /** The words beside the pointer for what it rests on, empty for none. */
    private String tipFor(WindowHover hover) {
        switch (hover.kind) {
            case TAB_ROW:
                return hover.tabHit == null || hover.window == null ? ""
                        : tipFor(hover.tabHit, hover.window, hover.overGrip);
            case TOOL_STRIP:
                return ToolStrip.tipFor(hover.stripPart, hover.window);
            case PAGE: {
                PageContent content = WindowPages.contentOf(hover.frame.page);
                if (content == null) {
                    return "";
                }
                LostTalesUiHitBox exact = WindowDrawing.pageBox(hover.frame);
                LostTalesUiHitBox whole = WindowDrawing.wholePageBox(
                        hover.frame);
                return content.tipAt(whole,
                        pointerX() - (exact.left - whole.left),
                        pointerY() - (exact.top - whole.top));
            }
            case SUB_WINDOW_CLOSE:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.small_window.close");
            case CONTENT:
                for (ScreenPart part : this.parts) {
                    String tip = part.tipFor(hover);
                    if (tip != null) {
                        return tip;
                    }
                }
                return "";
            default:
                return "";
        }
    }

    /**
     * The label a hovered row control offers. A tab names itself whole
     * whether or not the row cut its name short — the marquee in the tab
     * shows the rest of a cut name too, and the tip says it plainly; the
     * grip speaks only for its own glyph, so the empty strip that also
     * drags stays silent.
     */
    private String tipFor(TabRow.Hit hit, Window window, boolean overGrip) {
        switch (hit.kind) {
            case TAB:
                return hit.tab.title();
            case CLOSE:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.close");
            case DRAFT:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.draft");
            case LOCK:
                return StatCollector.translateToLocal(window.isLocked()
                        ? "gui.losttales.chat.tab.unlock"
                        : "gui.losttales.chat.tab.lock");
            case SEARCH:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.search.tip");
            case WINDOW_MENU:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.window.menu");
            case WINDOW_FULLSCREEN:
                return StatCollector.translateToLocal(window.isFullscreen()
                        ? "gui.losttales.chat.window.exit_fullscreen"
                        : "gui.losttales.chat.window.fullscreen");
            case WINDOW_CLOSE:
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.window.close");
            case RESTORE:
                for (ScreenPart part : this.parts) {
                    String tip = part.restoreTip();
                    if (tip != null) {
                        return tip;
                    }
                }
                return StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.restore");
            case GRIP:
                return overGrip ? StatCollector.translateToLocal(
                        "gui.losttales.chat.tab.move") : "";
            default:
                return "";
        }
    }

    /**
     * The words beside the pointer for what it rests on: a one-line popup
     * four pixels above the pointer, or below it where the screen's top
     * is too near.
     */
    private void drawHoverTip() {
        int tipWidth = WindowStyle.popupLineWidth(this.fontRendererObj,
                this.hoverTip);
        int x = Math.max(2, Math.min(this.width - tipWidth - 2,
                this.hoverTipX + 8));
        int y = this.hoverTipY - 4 - WindowStyle.POPUP_LINE_HEIGHT;
        if (y < 2) {
            y = this.hoverTipY + 12;
        }
        WindowStyle.drawPopupLine(this.fontRendererObj, this.hoverTip, x, y,
                1.0F);
    }

    /* ---- Presses ---- */

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.pressCount++;
        // What a press lands on is found at the pointer's exact position,
        // the way the hover finds it; whole pixels only for a field and
        // for where a menu opens.
        double x = pointerX();
        double y = pointerY();
        // Snap assist takes a press on a card: its window fills the
        // card's zone. A press anywhere else ends the offer and goes on
        // to whatever is under it.
        if (this.snapAssist.isOpen()) {
            SnapAssist.Card card = this.snapAssist.cardAt(x, y);
            if (card != null) {
                if (button == 0) {
                    this.snapAssist.take(card);
                }
                return;
            }
            this.snapAssist.close();
        }
        // The snap layouts take a press on themselves: a zone lets the
        // window fill that part of the screen. A press anywhere else
        // puts them away and goes on to whatever is under it.
        if (this.snapFlyout.isOpen()) {
            if (this.snapFlyout.contains(x, y)) {
                int zone = this.snapFlyout.zoneAt(x, y);
                Window snapped = WindowLayout.window(
                        this.snapFlyout.windowId());
                if (button == 0 && zone >= 0 && snapped != null
                        && !snapped.isLocked()) {
                    sendWindowTo(snapped, this.snapFlyout.fillOf(zone),
                            this.snapFlyout.layoutOf(zone),
                            this.snapFlyout.companionsOf(zone));
                    this.snapFlyout.close();
                }
                return;
            }
            this.snapFlyout.close();
        }
        WindowHover press = hoverAt(x, y);
        // A press anywhere but the tool strip hands the keys back, a press
        // anywhere but a page leaves no page in front of them, and a press
        // anywhere but a sub-window leaves none of those in front.
        if (this.toolStrip.isFocused()
                && !press.is(WindowHover.Kind.TOOL_STRIP)) {
            leaveSearch();
        }
        if (this.focusedPage != null && !press.is(WindowHover.Kind.PAGE)) {
            leavePage();
        }
        if (press.subWindow != null) {
            // A sub-window is part of its window: pressing it brings that
            // window forward and types into it.
            Window parent = press.subWindow.parentId == null ? null
                    : WindowLayout.window(press.subWindow.parentId);
            if (parent != null) {
                selectWindow(parent);
            }
            this.subWindows.focus(press.subWindow);
            pressSubWindow(press, x, y, button);
            syncTypingFocus();
            return;
        }
        this.subWindows.blur();
        if (isEmpty()) {
            // What a part offers is the whole of the screen's furniture
            // here; a click anywhere else is the player closing what is
            // not there.
            for (ScreenPart part : this.parts) {
                if (part.pressEmpty(press, mouseX, mouseY, button)) {
                    break;
                }
            }
            syncTypingFocus();
            return;
        }
        switch (press.kind) {
            case TOOL_STRIP:
                clickToolStrip(press, x, y, button);
                return;
            case PAGE:
                pressPage(press, x, y, button);
                return;
            case RESIZE:
                if (button == 0) {
                    Window edgeWindow = WindowLayout.window(
                            press.resize.frame.windowId);
                    if (edgeWindow != null && !pressEdge(edgeWindow,
                            press.resize, mouseX, mouseY)) {
                        this.gestures.armResize(press.resize, edgeWindow,
                                mouseX, mouseY);
                    }
                    return;
                }
                break;
            default:
                break;
        }
        // The rows: the top resize border meets the row's band along the
        // window's edge, and only a left press there resizes; any other
        // press there is the row's.
        WindowHover onRow = press.is(WindowHover.Kind.TAB_ROW)
                || press.is(WindowHover.Kind.STRIP) ? press
                : press.is(WindowHover.Kind.RESIZE) ? rowHoverAt(x, y) : null;
        if (onRow != null) {
            if (button == 0) {
                handleRowClick(onRow, mouseX, mouseY);
            } else if (button == 1) {
                handleRowRightClick(onRow, mouseX);
            } else if (button == 2) {
                closeTabFrom(onRow);
            }
            return;
        }
        if (button == 0) {
            // The marks are the row's; a press anywhere else lets them go.
            TabSelection.clear();
        }
        for (ScreenPart part : this.parts) {
            if (part.pressControl(press, x, y, mouseX, mouseY, button)) {
                return;
            }
        }
        if (press.is(WindowHover.Kind.OVERLAY)) {
            // An overlay owns this spot even when nothing on it was hit;
            // what lies underneath must not receive the press.
            return;
        }
        for (ScreenPart part : this.parts) {
            part.pressWindows(press, x, y, mouseX, mouseY, button);
        }
    }

    /**
     * A press on a sub-window, which has just come in front: its cross
     * closes it, its strip and its edges move and resize it, and its
     * content answers through the part it belongs to.
     */
    private void pressSubWindow(WindowHover press, double x, double y,
                                int button) {
        switch (press.kind) {
            case SUB_WINDOW_CLOSE:
                if (button == 0) {
                    this.subWindows.close(press.subWindow);
                }
                return;
            case SUB_WINDOW_STRIP:
                if (button == 0) {
                    this.subWindows.armMove(press.subWindow, x, y);
                }
                return;
            case SUB_WINDOW_RESIZE:
                if (button == 0) {
                    this.subWindows.armResize(press.subWindow, press.subEdge,
                            x, y);
                }
                return;
            default:
                for (ScreenPart part : this.parts) {
                    if (part.pressSubWindow(press, x, y, button)) {
                        return;
                    }
                }
        }
    }

    /**
     * Sends the window in front at this point to the back and brings the
     * one under it forward, moving the keys with it — so pressing the
     * same empty spot again and again walks through everything stacked
     * there and comes back round.
     */
    public void cycleWindowsAt(double x, double y) {
        WindowFrame front = WindowFrame.drawnAt(x, y);
        WindowFrame under = cycleTargetAt(x, y);
        Window behind = under == null ? null
                : WindowLayout.window(under.windowId);
        if (front == null || behind == null) {
            return;
        }
        WindowLayout.lower(front.windowId);
        selectWindow(behind);
    }

    /**
     * The window a press at this point brings forward by cycling the
     * stack: the one under the window in front here, while the point is
     * in the front window's lines; else null, for a window standing on
     * its own or a point outside its lines. The pointer's hand and the
     * press both ask this.
     */
    public static WindowFrame cycleTargetAt(double x, double y) {
        List<WindowFrame> frames = WindowFrame.drawnFrames();
        WindowFrame front = null;
        for (int index = frames.size() - 1; index >= 0; index--) {
            WindowFrame frame = frames.get(index);
            if (!frame.contains(x, y)) {
                continue;
            }
            if (front != null) {
                return WindowLayout.window(frame.windowId) == null
                        ? null : frame;
            }
            // Only the lines cycle. The strips and the grip move the
            // window and the bar is the input; what is left is the
            // history, which is where the player is pointing when they
            // mean "the one behind this".
            if (y < frame.historyTop() || y >= frame.barTop()) {
                return null;
            }
            front = frame;
        }
        return null;
    }

    /* ---- Tab rows: clicks, drags, docking, detaching, window moves ---- */

    /**
     * A middle press on a tab closes it, the way browser tabs close;
     * anywhere else on a row is swallowed so the click does not fall
     * through. Same guarded close as the cross.
     */
    private void closeTabFrom(WindowHover press) {
        if (press.tabHit != null && press.tabHit.tab != null) {
            // The others keep their width while the pointer stays on the
            // row, so the next tab to close is under it.
            if (press.frame != null) {
                press.frame.tabBar.holdWidthsForClose();
            }
            closeTab(press.tabHit.tab);
        }
    }

    /**
     * A right press on one of the rows: on a tab — its name or its cross
     * — it opens the tab's menu at the pointer; anywhere else on the
     * strip — the bare stretch, the grip, the end controls — it opens the
     * window's own menu where the window's dots would, on the same terms
     * as the dots: a locked window offers neither. The press belongs to
     * that window like any other, and is spent on the strip either way.
     */
    private void handleRowRightClick(WindowHover press, int mouseX) {
        Window window = press.window;
        TabRow.Row row = press.row;
        if (window == null || row == null) {
            return;
        }
        SubWindowAnchor anchor = SubWindowAnchor.onRow(press.frame, row,
                mouseX, this.width, this.height);
        selectWindow(window);
        if (press.tabHit != null && press.tabHit.tab != null) {
            showTabMenu(press.tabHit.tab, anchor, false);
        } else if (!window.isLocked()) {
            showWindowMenu(window, anchor, false);
        }
    }

    /**
     * A left press on one of the rows. Picking a tab also arms a drag;
     * the controls act at once, and a control that opens a menu is a
     * switch: pressed with its own menu out, it puts it away. Shift marks
     * the tab instead of picking it, so several tabs of one row can be
     * moved or closed together.
     */
    private void handleRowClick(WindowHover press, int mouseX, int mouseY) {
        Window window = press.window;
        WindowFrame frame = press.frame;
        TabRow.Row row = press.row;
        TabRow.Hit hit = press.tabHit;
        if (window == null || frame == null || row == null) {
            return;
        }
        if (hit == null) {
            // The strip itself belongs to the window, and moves it; a
            // double click on it lets the window fill the screen or gives
            // the screen back, as a title bar does.
            TabSelection.clear();
            selectWindow(window);
            if (!window.isLocked()) {
                pressStrip(window, frame, mouseX, mouseY);
            }
            return;
        }
        // Anything on a window's strip is a press on that window, so the
        // keys move there whichever control was pressed.
        selectWindow(window);
        switch (hit.kind) {
            case TAB: {
                if (isShiftKeyDown()) {
                    // Marking a tab does not bring it forward: what is
                    // being typed stays where it was, and a set being
                    // started is seeded with it, so what is marked always
                    // includes the tab in front.
                    TabSelection.toggle(window.getId(),
                            WindowFrame.activeTab(window, row.tabs), hit.tab);
                    return;
                }
                // A press on a tab already in a group keeps the group:
                // the drag it may start is the group's, and a press that
                // never travels collapses it on the release instead.
                List<WindowTab> group = WindowGestures.draggedGroup(window,
                        hit.tab);
                boolean kept = group.size() > 1;
                // Everything the window holds — its only tab, or all of
                // them marked at once — has nothing to be taken out of and
                // nowhere to be reordered: the tabs are the window's title
                // bar, so the drag begins already torn off into the window
                // they are in and the window itself is what moves.
                // Carrying it onto another window's row docks the tabs
                // there, the way a browser merges a window back into
                // another.
                boolean wholeWindow = group.size() >= window.getTabs().size();
                if (!kept) {
                    TabSelection.selectOnly(window.getId(), hit.tab);
                }
                selectTab(hit.tab);
                if (hit.tab instanceof PageTab) {
                    // A page pressed in its row takes the keys.
                    focusPage((PageTab)hit.tab);
                }
                if (!window.isLocked()) {
                    this.gestures.armTabDrag(window, frame, row, hit.tab,
                            group, mouseX, mouseY, kept, wholeWindow);
                } else if (kept) {
                    // A locked row starts no drag, so the press is only
                    // ever a pick.
                    TabSelection.selectOnly(window.getId(), hit.tab);
                }
                return;
            }
            case CLOSE:
                // The others keep their width while the pointer stays on
                // the row, so the next cross is under it.
                frame.tabBar.holdWidthsForClose();
                closeTab(hit.tab);
                return;
            case DRAFT:
                // The draft mark takes the keys to its tab, which brings
                // the draft into the field, and chooses every word of it
                // as Ctrl+A would there. A press, not a drag.
                TabSelection.selectOnly(window.getId(), hit.tab);
                selectTab(hit.tab);
                this.inputField.setCursorPositionEnd();
                this.inputField.setSelectionPos(0);
                return;
            case LOCK:
                WindowLinks.setLocked(window, !window.isLocked());
                return;
            case RESTORE:
                // A switch like the search control beside it: a press
                // with this window's list already out puts it away, and
                // one with another window's out turns it to this one.
                for (ScreenPart part : this.parts) {
                    if (part.toggleRestoreMenu(window, SubWindowAnchor.onRow(
                            frame, row, mouseX, this.width, this.height))) {
                        break;
                    }
                }
                syncTypingFocus();
                return;
            case SEARCH:
                for (ScreenPart part : this.parts) {
                    if (part.toggleTabSearch(window, SubWindowAnchor.onRow(
                            frame, row, mouseX, this.width, this.height))) {
                        break;
                    }
                }
                syncTypingFocus();
                return;
            case WINDOW_MENU:
                showWindowMenu(window, SubWindowAnchor.onRow(frame, row,
                        mouseX, this.width, this.height), true);
                return;
            case WINDOW_FULLSCREEN:
                WindowLayout.takeFill(window, window.isFullscreen()
                        ? Window.ScreenFill.NONE : Window.ScreenFill.FULL);
                return;
            case WINDOW_CLOSE:
                closeWindow(window);
                return;
            case GRIP:
                if (!window.isLocked()) {
                    pressStrip(window, frame, mouseX, mouseY);
                }
                return;
            default:
                return;
        }
    }

    /**
     * A press on a window's edge: the second of a double click on its top
     * or bottom edge — the very next press, on the same window, within
     * {@link #DOUBLE_CLICK_NANOS} and a drag's threshold of the first —
     * stretches it to the screen's whole height in its own column, as a
     * desktop window's edge does, and answers true; any other press is
     * the start of a resize.
     */
    private boolean pressEdge(Window window, WindowGestures.ResizeTarget target,
                              int mouseX, int mouseY) {
        long now = System.nanoTime();
        boolean vertical = target.edge == WindowGestures.ResizeEdge.TOP
                || target.edge == WindowGestures.ResizeEdge.BOTTOM;
        boolean second = vertical
                && window.getFill() == Window.ScreenFill.NONE
                && window.getId().equals(this.edgePressWindowId)
                && this.edgePressNumber == this.pressCount - 1
                && now - this.edgePressNanos <= DOUBLE_CLICK_NANOS
                && Math.abs(mouseX - this.edgePressX)
                        <= WindowGestures.DRAG_THRESHOLD
                && Math.abs(mouseY - this.edgePressY)
                        <= WindowGestures.DRAG_THRESHOLD;
        if (second) {
            this.edgePressWindowId = null;
            WindowPlacement.Box box = WindowPlacement.restingBounds(window,
                    this.mc, this.width, this.height);
            WindowLayout.takeFill(window, WindowPlacement.columnFill(box.x,
                    box.right(), this.width));
            return true;
        }
        this.edgePressWindowId = vertical ? window.getId() : null;
        this.edgePressNumber = this.pressCount;
        this.edgePressNanos = now;
        this.edgePressX = mouseX;
        this.edgePressY = mouseY;
        return false;
    }

    /**
     * A press on a window's bare strip or grip: the first of a double
     * click takes hold of the window to move it; the second — the very
     * next press, on the same window, within {@link #DOUBLE_CLICK_NANOS}
     * and a drag's threshold of the first — lets it fill the screen or
     * gives the screen back.
     */
    private void pressStrip(Window window, WindowFrame frame, int mouseX,
                            int mouseY) {
        long now = System.nanoTime();
        boolean second = window.getId().equals(this.stripPressWindowId)
                && this.stripPressNumber == this.pressCount - 1
                && now - this.stripPressNanos <= DOUBLE_CLICK_NANOS
                && Math.abs(mouseX - this.stripPressX)
                        <= WindowGestures.DRAG_THRESHOLD
                && Math.abs(mouseY - this.stripPressY)
                        <= WindowGestures.DRAG_THRESHOLD;
        if (second) {
            // A double click is spent by its second press; a third press
            // starts another.
            this.stripPressWindowId = null;
            WindowLayout.takeFill(window, window.isFullscreen()
                    ? Window.ScreenFill.NONE : Window.ScreenFill.FULL);
            return;
        }
        this.stripPressWindowId = window.getId();
        this.stripPressNumber = this.pressCount;
        this.stripPressNanos = now;
        this.stripPressX = mouseX;
        this.stripPressY = mouseY;
        this.gestures.armWindowDrag(frame, mouseX, mouseY);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY,
                                  int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (clickedMouseButton == 0 && this.subWindows.isHolding()) {
            this.subWindows.drag(pointerX(), pointerY());
            return;
        }
        if (clickedMouseButton == 0
                && this.gestures.onDragMove(mouseX, mouseY)) {
            return;
        }
        if (this.pressedPage != null
                && clickedMouseButton == this.pressedPageButton) {
            double[] at = onPressedPage(pointerX(), pointerY());
            if (at != null) {
                this.pressedPage.content().mouseDragged(this.mc, boxOf(at),
                        at[0], at[1], clickedMouseButton);
            }
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton,
                timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.subWindows.isHolding()) {
            this.subWindows.release();
        } else if (mouseButton == 0) {
            this.gestures.onRelease();
        }
        if (this.pressedPage != null && mouseButton == this.pressedPageButton) {
            PageTab released = this.pressedPage;
            double[] at = onPressedPage(pointerX(), pointerY());
            this.pressedPage = null;
            if (at != null) {
                released.content().mouseReleased(this.mc, boxOf(at), at[0],
                        at[1], mouseButton);
            }
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    /* ---- The game's chat screen underneath ---- */

    /**
     * The completion answer, which the game delivers to this method by
     * name.
     */
    @Override
    public void func_146406_a(String[] serverCompletions) {
        for (ScreenPart part : this.parts) {
            if (part.serverCompletions(serverCompletions)) {
                return;
            }
        }
    }

    /** A line to send, however it was asked for: the part that types sends it. */
    @Override
    public void func_146403_a(String text) {
        for (ScreenPart part : this.parts) {
            if (part.send(text)) {
                return;
            }
        }
        super.func_146403_a(text);
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        for (ScreenPart part : this.parts) {
            if (part.confirmClicked(result, id)) {
                return;
            }
        }
        super.confirmClicked(result, id);
    }
}
