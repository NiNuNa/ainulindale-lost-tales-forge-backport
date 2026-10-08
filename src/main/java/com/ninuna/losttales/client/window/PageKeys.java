package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * The keys the pages answer to, as a page's help and the Client Settings
 * page list them: what each does beside how it is done — keys in the
 * mod's own key icons, a mouse press in words, and what is typed in
 * italics, as the chat's inline code — in areas by what they work on. A
 * list to read: the keys are the pages' own, not bindings the game's
 * Controls screen changes. Ctrl stands for Cmd on a Mac, where the list
 * reads Cmd for it.
 *
 * <p>The window system's own areas — pages, windows, menus — work on
 * every page, so every page's help ends with them. A system whose tabs
 * are no pages, the chat, adds its own ({@link #addKind}); a page gives
 * its own ({@link PageContent#keyAreas}).</p>
 */
public final class PageKeys {
    /** Stands for the key shortcuts are made with: Ctrl, or Cmd on a Mac. */
    public static final int COMMAND = -1;

    /** A word of the language file among a shortcut's parts. */
    public static final class Word {
        final String key;

        private Word(String id) {
            this.key = "gui.losttales.keys." + id;
        }
    }

    /** Joins keys held together. */
    public static final Word PLUS = new Word("plus");
    /** Parts one way of doing a shortcut from another. */
    public static final Word OR = new Word("or");
    /** Joins the first and the last of a run of keys. */
    public static final Word TO = new Word("to");
    public static final Word CLICK = new Word("mouse.click");
    public static final Word RIGHT_CLICK = new Word("mouse.right_click");
    public static final Word MIDDLE_CLICK = new Word("mouse.middle_click");
    public static final Word DOUBLE_CLICK = new Word("mouse.double_click");
    public static final Word DRAG = new Word("mouse.drag");
    public static final Word RIGHT_DRAG = new Word("mouse.right_drag");
    public static final Word HOVER = new Word("mouse.hover");
    public static final Word WHEEL = new Word("mouse.wheel");

    /**
     * One shortcut: what it does, and how it is done — key codes and
     * {@link #COMMAND}, words, and text typed as it is written.
     */
    public static final class Shortcut {
        final String labelKey;
        final Object[] parts;

        private Shortcut(String labelKey, Object[] parts) {
            this.labelKey = labelKey;
            this.parts = parts;
        }
    }

    /** A part of a page, or of every page, and the shortcuts that work on it. */
    public static final class Area {
        final String labelKey;
        final List<Shortcut> shortcuts;

        private Area(String labelKey, Shortcut[] shortcuts) {
            this.labelKey = labelKey;
            this.shortcuts = Collections.unmodifiableList(
                    new ArrayList<Shortcut>(Arrays.asList(shortcuts)));
        }
    }

    /** The keys of each kind of tab that is no page, as its system added them: the chat's. */
    private static final List<List<Area>> KINDS =
            new CopyOnWriteArrayList<List<Area>>();

    private PageKeys() {}

    /** Adds the keys of a kind of tab that is no page; once, as its system installs. */
    public static void addKind(List<Area> areas) {
        if (areas != null && !areas.isEmpty()) {
            KINDS.add(Collections.unmodifiableList(new ArrayList<Area>(areas)));
        }
    }

    /**
     * Every key there is, as the Client Settings page lists them: the
     * window system's, each kind's, then each page's, in the order the
     * pages were registered.
     */
    public static List<Area> everyArea() {
        List<Area> every = new ArrayList<Area>(windowAreas());
        for (List<Area> kind : KINDS) {
            every.addAll(kind);
        }
        for (WindowPages.Page page : WindowPages.all()) {
            every.addAll(page.content().keyAreas());
        }
        return every;
    }

    /** A shortcut named by {@code labelKey}, done with {@code parts}. */
    public static Shortcut shortcut(String labelKey, Object... parts) {
        return new Shortcut(labelKey, parts == null ? new Object[0] : parts);
    }

    /**
     * A page's shortcut, its words under {@code gui.losttales.help.keys.}
     * and the page's code name: {@code journal.track}.
     */
    public static Shortcut pageKey(String pageId, String id, Object... parts) {
        return shortcut("gui.losttales.help.keys." + pageId + "." + id, parts);
    }

    /** A page's one area, named as the page is. */
    public static List<Area> pageArea(String pageTitleKey,
                                      Shortcut... shortcuts) {
        return Collections.singletonList(area(pageTitleKey, shortcuts));
    }

    /** An area named by {@code labelKey}, holding {@code shortcuts}. */
    public static Area area(String labelKey, Shortcut... shortcuts) {
        return new Area(labelKey, shortcuts == null ? new Shortcut[0]
                : shortcuts);
    }

    /**
     * Every area's name over its shortcuts, each a row that is read, not
     * taken. With a filter, only the shortcuts whose words or keys match
     * it, under the names of their areas; an area whose name matches
     * keeps all of them.
     */
    public static List<MenuWindow.Entry> rows(List<Area> areas, String filter) {
        String wanted = filter == null ? ""
                : filter.trim().toLowerCase(Locale.ROOT);
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (Area area : areas) {
            String name = StatCollector.translateToLocal(area.labelKey);
            boolean whole = wanted.length() == 0
                    || name.toLowerCase(Locale.ROOT).contains(wanted);
            List<MenuWindow.Entry> matched = new ArrayList<MenuWindow.Entry>();
            for (Shortcut shortcut : area.shortcuts) {
                Object[] shown = shown(shortcut.parts);
                String label = StatCollector.translateToLocal(shortcut.labelKey);
                if (whole || matches(label, shown, wanted)) {
                    matched.add(MenuWindow.Entry.passive(label).withKeys(shown));
                }
            }
            if (!matched.isEmpty()) {
                rows.add(MenuWindow.Entry.group(name, null,
                        LostTalesColors.rgb(LostTalesColors.SAND)));
                rows.addAll(matched);
            }
        }
        return rows;
    }

    /** Whether a shortcut's words, or the names its key icons write, hold {@code wanted}. */
    private static boolean matches(String label, Object[] shown, String wanted) {
        if (label.toLowerCase(Locale.ROOT).contains(wanted)) {
            return true;
        }
        for (Object part : shown) {
            String word = part instanceof Integer
                    ? WindowKeys.keyName(((Integer)part).intValue())
                    : EnumChatFormatting.getTextWithoutFormattingCodes(
                            String.valueOf(part));
            if (word != null && word.toLowerCase(Locale.ROOT).contains(wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A shortcut's parts as a row draws them, for a row of a menu that
     * names its key: the command key this computer's, words read, and
     * typed text in italics, the chat's inline code.
     */
    public static Object[] keysOf(Object... parts) {
        return shown(parts == null ? new Object[0] : parts);
    }

    /** {@link #keysOf}, for the rows this lists. */
    private static Object[] shown(Object[] parts) {
        Object[] shown = new Object[parts.length];
        for (int index = 0; index < parts.length; index++) {
            Object part = parts[index];
            if (part instanceof Integer
                    && ((Integer)part).intValue() == COMMAND) {
                shown[index] = Integer.valueOf(WindowKeys.commandKey());
            } else if (part instanceof Word) {
                shown[index] = StatCollector.translateToLocal(((Word)part).key);
            } else if (part instanceof String) {
                shown[index] = EnumChatFormatting.ITALIC + (String)part;
            } else {
                shown[index] = part;
            }
        }
        return shown;
    }

    private static Shortcut window(String id, Object... parts) {
        return shortcut("gui.losttales.window.keys." + id, parts);
    }

    /** The window system's own areas, which work on every page: pages, windows, menus. */
    public static List<Area> windowAreas() {
        int shift = Keyboard.KEY_LSHIFT;
        int alt = Keyboard.KEY_LMENU;
        int escape = Keyboard.KEY_ESCAPE;
        int tab = Keyboard.KEY_TAB;
        int left = Keyboard.KEY_LEFT;
        int right = Keyboard.KEY_RIGHT;
        int up = Keyboard.KEY_UP;
        int down = Keyboard.KEY_DOWN;
        int enter = Keyboard.KEY_RETURN;
        return Arrays.asList(
                area("gui.losttales.window.keys.area.pages",
                        window("pages.next_all", COMMAND, PLUS, tab),
                        window("pages.previous_all", COMMAND, PLUS, shift, PLUS, tab),
                        window("pages.next", COMMAND, PLUS, right, OR, Keyboard.KEY_NEXT),
                        window("pages.previous", COMMAND, PLUS, left, OR, Keyboard.KEY_PRIOR),
                        window("pages.ordinal", COMMAND, PLUS, Keyboard.KEY_1, TO, Keyboard.KEY_8),
                        window("pages.last", COMMAND, PLUS, Keyboard.KEY_9),
                        window("pages.empty_next", tab, OR, right),
                        window("pages.empty_previous", shift, PLUS, tab, OR, left),
                        window("pages.indicator", CLICK, OR, RIGHT_CLICK),
                        window("pages.close", COMMAND, PLUS, Keyboard.KEY_W),
                        window("pages.middle_close", MIDDLE_CLICK),
                        window("pages.mark", shift, PLUS, CLICK),
                        window("pages.clear_marks", CLICK),
                        window("pages.options", CLICK, OR, RIGHT_CLICK),
                        window("pages.option_buttons", CLICK),
                        window("pages.settings", CLICK),
                        window("pages.help", Keyboard.KEY_F1, OR, CLICK),
                        window("pages.search", COMMAND, PLUS, Keyboard.KEY_F),
                        window("pages.borderless", alt, PLUS, enter, OR, CLICK),
                        window("pages.split", CLICK),
                        window("pages.split_drop", DRAG),
                        window("pages.split_divider", DRAG, OR, DOUBLE_CLICK),
                        window("pages.draft", CLICK),
                        window("pages.reorder", DRAG),
                        window("pages.tear_off", DRAG),
                        window("pages.dock", DRAG)),
                area("gui.losttales.window.keys.area.windows",
                        window("windows.move", DRAG),
                        window("windows.fullscreen", DOUBLE_CLICK, OR, CLICK),
                        window("windows.snap_edge", DRAG),
                        window("windows.snap_bar", DRAG),
                        window("windows.snap_flyout", HOVER),
                        window("windows.snap_assist", CLICK),
                        window("windows.snap_side", alt, PLUS, left, OR, right),
                        window("windows.snap_up", alt, PLUS, up),
                        window("windows.snap_down", alt, PLUS, down),
                        window("windows.layouts", alt, PLUS, Keyboard.KEY_Z),
                        window("windows.layouts_walk", left, OR, right, OR, up, OR, down),
                        window("windows.layouts_take", enter),
                        window("windows.layouts_close", escape),
                        window("windows.resize", DRAG),
                        window("windows.stretch", DOUBLE_CLICK),
                        window("windows.put_back", escape),
                        window("windows.drag_stop", escape),
                        window("windows.menu", RIGHT_CLICK, OR, CLICK),
                        window("windows.type_in", CLICK),
                        window("windows.cycle", CLICK),
                        window("windows.members", DRAG),
                        window("windows.scrollbar", DRAG, OR, CLICK)),
                area("gui.losttales.window.keys.area.menus",
                        window("menus.lost_tales_menu",
                                LostTalesKeyBindings.getMenuKeyBinding().getKeyCode()),
                        window("menus.views", CLICK),
                        window("menus.settings", COMMAND, PLUS, Keyboard.KEY_COMMA),
                        window("menus.open", COMMAND, PLUS, Keyboard.KEY_N),
                        window("menus.tab_search", COMMAND, PLUS, shift, PLUS, Keyboard.KEY_A),
                        window("menus.switcher", COMMAND, PLUS, Keyboard.KEY_K),
                        window("menus.toggle", CLICK),
                        window("menus.close_front", escape),
                        window("menus.first_found", enter),
                        window("menus.step_back", RIGHT_CLICK),
                        window("menus.move", DRAG),
                        window("menus.resize", DRAG),
                        window("menus.put_back", escape),
                        window("menus.wheel", WHEEL)));
    }
}
