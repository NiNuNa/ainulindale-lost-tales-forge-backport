package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.PageKeys;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.input.Keyboard;

import static com.ninuna.losttales.client.window.PageKeys.CLICK;
import static com.ninuna.losttales.client.window.PageKeys.COMMAND;
import static com.ninuna.losttales.client.window.PageKeys.OR;
import static com.ninuna.losttales.client.window.PageKeys.PLUS;
import static com.ninuna.losttales.client.window.PageKeys.RIGHT_CLICK;
import static com.ninuna.losttales.client.window.PageKeys.WHEEL;

/**
 * The keys a conversation answers to, none left out, in areas by what
 * they work on: typing, searching, the pickers, the messages, scrolling.
 * A conversation's help lists them, and so does the Client Settings page;
 * the keys every page shares are the window system's
 * ({@link PageKeys#windowAreas}).
 */
final class ChatShortcuts {
    private static final int SHIFT = Keyboard.KEY_LSHIFT;
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

    private ChatShortcuts() {}

    private static PageKeys.Shortcut shortcut(String id, Object... parts) {
        return PageKeys.shortcut("gui.losttales.chat.shortcut." + id, parts);
    }

    private static PageKeys.Area area(String id, PageKeys.Shortcut... shortcuts) {
        return PageKeys.area("gui.losttales.chat.shortcuts.area." + id,
                shortcuts);
    }

    /** Every area, in the order the lists show them. */
    static List<PageKeys.Area> areas() {
        return Arrays.asList(
                area("typing",
                        shortcut("typing.send", RETURN),
                        shortcut("typing.paragraph", SHIFT, PLUS, RETURN),
                        shortcut("typing.rows", UP, OR, DOWN),
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
                        shortcut("typing.action", ChatInputRules.ACTION_VERB),
                        shortcut("typing.close_chat", ESCAPE)),
                area("search",
                        shortcut("search.newer", RETURN),
                        shortcut("search.older", SHIFT, PLUS, RETURN),
                        shortcut("search.from", "from:"),
                        shortcut("search.close", ESCAPE)),
                area("lists",
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
                area("messages",
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
                area("scrolling",
                        shortcut("scrolling.lines", WHEEL),
                        shortcut("scrolling.line", SHIFT, PLUS, WHEEL),
                        shortcut("scrolling.page", PAGE_UP, OR, PAGE_DOWN)));
    }
}
