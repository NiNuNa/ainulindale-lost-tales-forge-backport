package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Every shortcut the chat has, as Settings lists them (Nils,
 * 2026-09-24: "ALL of them"): what each does beside how it is done —
 * keys in the mod's own key icons, a mouse press in words, and what is
 * typed as the chat's inline code — in groups by what they work on. A
 * list to read: the keys are the chat's own, not bindings the game's
 * Controls screen changes. Ctrl stands for Cmd on a Mac, where the chat
 * reads Cmd for it.
 */
final class ChatShortcuts {
    /** Stands for the key the chat's shortcuts are made with: Ctrl, or Cmd on a Mac. */
    private static final int COMMAND = -1;
    private static final int SHIFT = Keyboard.KEY_LSHIFT;
    private static final int ALT = Keyboard.KEY_LMENU;
    private static final int RETURN = Keyboard.KEY_RETURN;
    private static final int ESCAPE = Keyboard.KEY_ESCAPE;
    private static final int TAB = Keyboard.KEY_TAB;
    private static final int UP = Keyboard.KEY_UP;
    private static final int DOWN = Keyboard.KEY_DOWN;
    private static final int LEFT = Keyboard.KEY_LEFT;
    private static final int RIGHT = Keyboard.KEY_RIGHT;
    private static final int HOME = Keyboard.KEY_HOME;
    private static final int END = Keyboard.KEY_END;
    private static final int PAGE_UP = Keyboard.KEY_PRIOR;
    private static final int PAGE_DOWN = Keyboard.KEY_NEXT;

    /** A word of the language file among a shortcut's parts. */
    private static final class Word {
        final String key;

        Word(String id) {
            this.key = "gui.losttales.chat.shortcuts." + id;
        }
    }

    /** Joins keys held together. */
    private static final Word PLUS = new Word("plus");
    /** Parts one way of doing a shortcut from another. */
    private static final Word OR = new Word("or");
    /** Joins the first and the last of a run of keys. */
    private static final Word TO = new Word("to");
    private static final Word CLICK = new Word("mouse.click");
    private static final Word RIGHT_CLICK = new Word("mouse.right_click");
    private static final Word MIDDLE_CLICK = new Word("mouse.middle_click");
    private static final Word DOUBLE_CLICK = new Word("mouse.double_click");
    private static final Word DRAG = new Word("mouse.drag");
    private static final Word HOVER = new Word("mouse.hover");
    private static final Word WHEEL = new Word("mouse.wheel");

    /**
     * One shortcut: what it does, and how it is done — key codes and
     * {@link #COMMAND}, words, and text typed as it is written.
     */
    private static final class Shortcut {
        final String labelKey;
        final Object[] parts;

        Shortcut(String labelKey, Object[] parts) {
            this.labelKey = labelKey;
            this.parts = parts;
        }
    }

    /** A part of the chat and the shortcuts that work on it. */
    private static final class Area {
        final String labelKey;
        final Shortcut[] shortcuts;

        Area(String id, Shortcut... shortcuts) {
            this.labelKey = "gui.losttales.chat.shortcuts.area." + id;
            this.shortcuts = shortcuts;
        }
    }

    private ChatShortcuts() {}

    private static Shortcut shortcut(String id, Object... parts) {
        return new Shortcut("gui.losttales.chat.shortcut." + id, parts);
    }

    /** Every area's name over its shortcuts, each a row that is read, not taken. */
    static List<ChatMenu.Entry> rows() {
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        for (Area area : areas()) {
            rows.add(ChatMenu.Entry.group(StatCollector.translateToLocal(
                    area.labelKey), null,
                    LostTalesColors.rgb(LostTalesColors.SAND)));
            for (Shortcut shortcut : area.shortcuts) {
                rows.add(ChatMenu.Entry.passive(StatCollector.translateToLocal(
                        shortcut.labelKey)).withKeys(shown(shortcut.parts)));
            }
        }
        return rows;
    }

    /** Every language key the list reads, in the order it reads them: areas, shortcuts, words. */
    static List<String> languageKeys() {
        List<String> keys = new ArrayList<String>();
        for (Area area : areas()) {
            keys.add(area.labelKey);
            for (Shortcut shortcut : area.shortcuts) {
                keys.add(shortcut.labelKey);
                for (Object part : shortcut.parts) {
                    if (part instanceof Word) {
                        keys.add(((Word)part).key);
                    }
                }
            }
        }
        return keys;
    }

    /**
     * A shortcut's parts as a row draws them: the command key this
     * computer's, words read, and typed text in italics, the chat's
     * inline code.
     */
    private static Object[] shown(Object[] parts) {
        Object[] shown = new Object[parts.length];
        for (int index = 0; index < parts.length; index++) {
            Object part = parts[index];
            if (part instanceof Integer
                    && ((Integer)part).intValue() == COMMAND) {
                shown[index] = Integer.valueOf(commandKey());
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

    /** The key the chat's shortcuts are made with here. */
    static int commandKey() {
        return Minecraft.isRunningOnMac ? Keyboard.KEY_LMETA
                : Keyboard.KEY_LCONTROL;
    }

    /** The command key with {@code keys}, held together, as a field's hint names them. */
    static int[] withCommand(int... keys) {
        int[] held = new int[keys.length + 1];
        held[0] = commandKey();
        System.arraycopy(keys, 0, held, 1, keys.length);
        return held;
    }

    /** A key as the search finds it by: its name as a key icon writes it. */
    static String keyName(int keyCode) {
        return LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, keyCode)
                .toLowerCase(Locale.ROOT);
    }

    /** Every area, in the order the list shows them. */
    private static Area[] areas() {
        return new Area[] {
                new Area("typing",
                        shortcut("typing.send", RETURN),
                        shortcut("typing.recall", UP, OR, DOWN),
                        shortcut("typing.complete", TAB),
                        shortcut("typing.caret", LEFT, OR, RIGHT),
                        shortcut("typing.ends", HOME, OR, END),
                        shortcut("typing.select", SHIFT, PLUS, LEFT, OR, RIGHT),
                        shortcut("typing.select_ends", SHIFT, PLUS, HOME, OR, END),
                        shortcut("typing.select_all", COMMAND, PLUS, Keyboard.KEY_A),
                        shortcut("typing.copy", COMMAND, PLUS, Keyboard.KEY_C),
                        shortcut("typing.cut", COMMAND, PLUS, Keyboard.KEY_X),
                        shortcut("typing.paste", COMMAND, PLUS, Keyboard.KEY_V),
                        shortcut("typing.word_back", COMMAND, PLUS, Keyboard.KEY_BACK),
                        shortcut("typing.word_ahead", COMMAND, PLUS, Keyboard.KEY_DELETE),
                        shortcut("typing.emoji_space", ":"),
                        shortcut("typing.emoticons", ":)", OR, "<3"),
                        shortcut("typing.whisper", "/msg", OR, "/tell", OR, "/w"),
                        shortcut("typing.close_chat", ESCAPE)),
                new Area("tabs",
                        shortcut("tabs.next_all", COMMAND, PLUS, TAB),
                        shortcut("tabs.previous_all", COMMAND, PLUS, SHIFT, PLUS, TAB),
                        shortcut("tabs.next", COMMAND, PLUS, RIGHT),
                        shortcut("tabs.previous", COMMAND, PLUS, LEFT),
                        shortcut("tabs.ordinal", COMMAND, PLUS, Keyboard.KEY_1, TO, Keyboard.KEY_8),
                        shortcut("tabs.last", COMMAND, PLUS, Keyboard.KEY_9),
                        shortcut("tabs.empty_next", TAB, OR, RIGHT),
                        shortcut("tabs.empty_previous", LEFT),
                        shortcut("tabs.indicator", CLICK, OR, RIGHT_CLICK),
                        shortcut("tabs.close", COMMAND, PLUS, Keyboard.KEY_W),
                        shortcut("tabs.middle_close", MIDDLE_CLICK),
                        shortcut("tabs.mark", SHIFT, PLUS, CLICK),
                        shortcut("tabs.clear_marks", CLICK),
                        shortcut("tabs.menu", RIGHT_CLICK),
                        shortcut("tabs.draft", CLICK),
                        shortcut("tabs.reorder", DRAG),
                        shortcut("tabs.tear_off", DRAG),
                        shortcut("tabs.dock", DRAG)),
                new Area("windows",
                        shortcut("windows.move", DRAG),
                        shortcut("windows.fullscreen", DOUBLE_CLICK, OR, CLICK),
                        shortcut("windows.snap_edge", DRAG),
                        shortcut("windows.snap_bar", DRAG),
                        shortcut("windows.snap_flyout", HOVER),
                        shortcut("windows.snap_assist", CLICK),
                        shortcut("windows.snap_side", ALT, PLUS, LEFT, OR, RIGHT),
                        shortcut("windows.snap_up", ALT, PLUS, UP),
                        shortcut("windows.snap_down", ALT, PLUS, DOWN),
                        shortcut("windows.layouts", ALT, PLUS, Keyboard.KEY_Z),
                        shortcut("windows.layouts_walk", LEFT, OR, RIGHT, OR, UP, OR, DOWN),
                        shortcut("windows.layouts_take", RETURN),
                        shortcut("windows.layouts_close", ESCAPE),
                        shortcut("windows.resize", DRAG),
                        shortcut("windows.stretch", DOUBLE_CLICK),
                        shortcut("windows.put_back", ESCAPE),
                        shortcut("windows.drag_stop", ESCAPE),
                        shortcut("windows.menu", RIGHT_CLICK, OR, CLICK),
                        shortcut("windows.type_in", CLICK),
                        shortcut("windows.cycle", CLICK),
                        shortcut("windows.members", DRAG),
                        shortcut("windows.scrollbar", DRAG, OR, CLICK)),
                new Area("menus",
                        shortcut("menus.settings", COMMAND, PLUS, Keyboard.KEY_COMMA),
                        shortcut("menus.open", COMMAND, PLUS, Keyboard.KEY_N),
                        shortcut("menus.tab_search", COMMAND, PLUS, SHIFT, PLUS, Keyboard.KEY_A),
                        shortcut("menus.toggle", CLICK),
                        shortcut("menus.close_front", ESCAPE),
                        shortcut("menus.first_found", RETURN),
                        shortcut("menus.step_back", RIGHT_CLICK),
                        shortcut("menus.move", DRAG),
                        shortcut("menus.resize", DRAG),
                        shortcut("menus.put_back", ESCAPE)),
                new Area("search",
                        shortcut("search.open", COMMAND, PLUS, Keyboard.KEY_F),
                        shortcut("search.newer", RETURN),
                        shortcut("search.older", SHIFT, PLUS, RETURN),
                        shortcut("search.from", "from:"),
                        shortcut("search.close", ESCAPE)),
                new Area("lists",
                        shortcut("lists.emoji", ":"),
                        shortcut("lists.mention", "@"),
                        shortcut("lists.channel", "#"),
                        shortcut("lists.item", "[i:"),
                        shortcut("lists.marker", "[m:"),
                        shortcut("lists.quest", "[q:"),
                        shortcut("lists.walk", UP, OR, DOWN),
                        shortcut("lists.take", TAB, OR, RETURN),
                        shortcut("lists.commands", TAB, OR, UP, OR, DOWN),
                        shortcut("lists.hide", ESCAPE),
                        shortcut("lists.first_found", RETURN),
                        shortcut("lists.favourite", RIGHT_CLICK),
                        shortcut("lists.fold", CLICK)),
                new Area("messages",
                        shortcut("messages.card", CLICK),
                        shortcut("messages.person_menu", RIGHT_CLICK),
                        shortcut("messages.message_menu", RIGHT_CLICK),
                        shortcut("messages.quote", CLICK),
                        shortcut("messages.link", CLICK),
                        shortcut("messages.link_text", SHIFT, PLUS, CLICK),
                        shortcut("messages.command", CLICK),
                        shortcut("messages.share", CLICK),
                        shortcut("messages.achievement", CLICK),
                        shortcut("messages.spoiler", CLICK),
                        shortcut("messages.react", CLICK),
                        shortcut("messages.cancel_reply", ESCAPE)),
                new Area("scrolling",
                        shortcut("scrolling.lines", WHEEL),
                        shortcut("scrolling.line", SHIFT, PLUS, WHEEL),
                        shortcut("scrolling.page", PAGE_UP, OR, PAGE_DOWN),
                        shortcut("scrolling.small_window", WHEEL))
        };
    }
}
