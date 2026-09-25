package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import net.minecraft.client.gui.GuiTextField;

/**
 * A system with work of its own on the window screen besides what its
 * tabs show: the chat, with its input bar, its menus, its completion
 * lists and its conversations. The screen makes one of each registered
 * part when it opens ({@link WindowScreen#addPart}) and asks it at fixed
 * points of every key, press, turn of the wheel and frame, in the order
 * the screen handles them; each method says where. Everything is
 * optional: a part leaves alone what it has no part in, and a method that
 * answers whether it took something answers false for that.
 */
public abstract class ScreenPart {
    /** Makes a screen's part; each screen that opens gets its own. */
    public interface Maker {
        ScreenPart make(WindowScreen screen);
    }

    protected final WindowScreen screen;

    protected ScreenPart(WindowScreen screen) {
        this.screen = screen;
    }

    /* ---- Life ---- */

    /** Before the screen builds its field: the screen was made or resized. */
    public void beforeInit() {}

    /** After the screen built its field and bound its own parts. */
    public void init() {}

    /** Once per opening, after the first {@link #init}. */
    public void opened() {}

    /** Every tick while the screen is open. */
    public void tick() {}

    /** The screen closed. */
    public void closed() {}

    /* ---- What the part holds ---- */

    /**
     * The window the part types into, whose sub-windows stand over every
     * window; null for none.
     */
    public String typedWindowId() {
        return null;
    }

    /** The window whose input is live: the search follows it to another window by closing. */
    public String inputWindowId() {
        return null;
    }

    /** The window the keys go to while no page holds them. */
    public Window keyWindow() {
        return null;
    }

    /** Whether the part has a field that can take the keys now. */
    public boolean hasField() {
        return false;
    }

    /** The field the tool strip's search well types into; null to leave it to another part. */
    public GuiTextField makeSearchField() {
        return null;
    }

    /** Writes words a page hands over into the field typed in; false for a part with none. */
    public boolean insertText(String token) {
        return false;
    }

    /* ---- What the windows ask of it ---- */

    /** A window was taken hold of: false leaves the screen to bring it forward. */
    public boolean selectWindow(Window window) {
        return false;
    }

    /** A tab was picked: false leaves the screen to bring it in front. */
    public boolean selectTab(WindowTab tab) {
        return false;
    }

    /** A tab's cross or Ctrl+W: false leaves the screen to close it. */
    public boolean closeTab(WindowTab tab) {
        return false;
    }

    /** A window's cross: false leaves the screen to close it. */
    public boolean closeWindow(Window window) {
        return false;
    }

    /** A window moved or changed its size: whatever of the part follows it follows. */
    public void windowsMoved() {}

    /** Opens a tab's menu, or with {@code toggle} puts it away when it is out. */
    public boolean showTabMenu(WindowTab tab, SubWindowAnchor anchor,
                               boolean toggle) {
        return false;
    }

    /** Opens a window's menu, or with {@code toggle} puts it away when it is out. */
    public boolean showWindowMenu(Window window, SubWindowAnchor anchor,
                                  boolean toggle) {
        return false;
    }

    /** The {@code +}'s list of what can be opened again, a switch. */
    public boolean toggleRestoreMenu(Window window, SubWindowAnchor anchor) {
        return false;
    }

    /** The tab search's list, a switch. */
    public boolean toggleTabSearch(Window window, SubWindowAnchor anchor) {
        return false;
    }

    /** Whether a menu of this kind about this subject is out: its control rests lit. */
    public boolean isMenuOpenFor(SubWindowKind kind, Object about) {
        return false;
    }

    /** Whether the part has anything closed to offer again under the {@code +}. */
    public boolean hasRestorable() {
        return false;
    }

    /** What waits in the {@code +}'s corner. */
    public TabMark restorableMark() {
        return TabMark.NONE;
    }

    /** What the {@code +} says under the pointer; null leaves it to another part. */
    public String restoreTip() {
        return null;
    }

    /** The tool strip's chevrons, Return and Shift+Return in its well over what the part shows. */
    public boolean walkSearch(boolean forward) {
        return false;
    }

    /* ---- Keys, in the screen's order ---- */

    /** First after snapping: a list standing over everything. */
    public boolean keyOverAll(LostTalesKeyPress press) {
        return false;
    }

    /** Every key that gets past a page holding the keys. */
    public void keyPassing(LostTalesKeyPress press) {}

    /** After the snap layouts and drags let Escape pass: keys the typed window takes whatever field has them. */
    public boolean keyForTypedWindow(LostTalesKeyPress press) {
        return false;
    }

    /** After Escape passed the sub-windows: the part's own shortcuts. */
    public boolean keyShortcut(LostTalesKeyPress press) {
        return false;
    }

    /** A key for the field of the sub-window in front; false leaves it to the sub-window. */
    public boolean keyIntoSubWindow(SubWindow front, LostTalesKeyPress press) {
        return false;
    }

    /** Last, while the part's field can take the keys. */
    public boolean keyTyped(LostTalesKeyPress press) {
        return false;
    }

    /* ---- The pointer ---- */

    /** Under the sub-windows while no window shows anything. */
    public WindowHover hoverEmpty(double x, double y) {
        return null;
    }

    /** Over the sub-windows and everything else. */
    public WindowHover hoverOverAll(double x, double y) {
        return null;
    }

    /** Under the windows' rows and pages, over anything painted over the windows. */
    public WindowHover hoverUnderRows(double x, double y) {
        return null;
    }

    /** Under anything painted over the windows: the part's windows themselves. */
    public WindowHover hoverInWindows(double x, double y) {
        return null;
    }

    /** What the frame found under the pointer, before anything is drawn. */
    public void hovered(WindowHover hover) {}

    /** The words beside the pointer for a hover of the part's; null for another part's. */
    public String tipFor(WindowHover hover) {
        return null;
    }

    /** A press while no window shows anything. */
    public boolean pressEmpty(WindowHover press, int mouseX, int mouseY,
                              int button) {
        return false;
    }

    /** A press on a sub-window's content, the sub-window just brought in front. */
    public boolean pressSubWindow(WindowHover press, double x, double y,
                                  int button) {
        return false;
    }

    /** A press past the rows on a control of the part's. */
    public boolean pressControl(WindowHover press, double x, double y,
                                int mouseX, int mouseY, int button) {
        return false;
    }

    /** A press past the rows and anything painted over the windows. */
    public void pressWindows(WindowHover press, double x, double y,
                             int mouseX, int mouseY, int button) {}

    /** A turn of the wheel no sub-window or page took. */
    public boolean wheel(WindowHover under, double x, double y, int lines) {
        return false;
    }

    /* ---- A frame, in the screen's order ---- */

    /** Before anything else: the part catches up with what changed. */
    public void beginFrame() {}

    /** After the keys found their field, before the pointer is read. */
    public void beforeHover() {}

    /** After the hover, before the windows are drawn. */
    public void beforeWindows(double pointerX, double pointerY) {}

    /**
     * Lays out and draws what a window holds under its row, for a window
     * whose tab in front is the part's; false for another part's.
     */
    public boolean drawWindow(Window window,
                              LostTalesGuiAnimationSample shown) {
        return false;
    }

    /** While the window's row stands laid out: its search reads what it shows. */
    public void searchWindow(Window window, WindowFrame frame) {}

    /** Over a window of the part's, after its row: whatever stands at its foot. */
    public void drawWindowFoot(Window window, WindowFrame frame,
                               LostTalesGuiAnimationSample shown,
                               int mouseX, int mouseY) {}

    /** After every window. */
    public void afterWindows() {}

    /** A sub-window open when the screen last closed comes back; false for another part's. */
    public boolean reopen(SubWindowPlaces.Reopening open) {
        return false;
    }

    /** Under the sub-windows: what stands over every window. */
    public void drawUnderSubWindows(boolean empty, boolean typing,
                                    double pointerX, double pointerY) {}

    /** Over the sub-windows. */
    public void drawOverSubWindows(boolean typing, double pointerX,
                                   double pointerY, int mouseX, int mouseY) {}

    /* ---- The game's chat screen underneath ---- */

    /** The server's answer to a completion request. */
    public boolean serverCompletions(String[] completions) {
        return false;
    }

    /** A line to send, however it was asked for. */
    public boolean send(String text) {
        return false;
    }

    /** A yes-or-no screen the part opened answered. */
    public boolean confirmClicked(boolean result, int id) {
        return false;
    }
}
