package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.SubWindowKind;

/**
 * The chat's kinds of sub-window, registered with the window system when
 * the chat is installed, before the layout file that remembers their
 * places is read.
 */
public final class ChatSubWindows {
    public static final SubWindowKind EMOJI = kind("emoji");
    public static final SubWindowKind REACTIONS = kind("reactions");
    public static final SubWindowKind ITEMS = kind("items");
    public static final SubWindowKind MARKERS = kind("markers");
    public static final SubWindowKind QUESTS = kind("quests");
    public static final SubWindowKind CARD = kind("card");
    /** A message's actions. */
    public static final SubWindowKind MESSAGE = kind("message");
    /** A person's: message them, ignore them. */
    public static final SubWindowKind PERSON = kind("person");
    /** The identities and the statuses, behind the head button. */
    public static final SubWindowKind CHARACTERS = kind("characters");
    /** The status line's field. */
    public static final SubWindowKind STATUS_LINE = kind("status_line");
    /** A message's report: a note and a reason. */
    public static final SubWindowKind REPORT = kind("report");
    /** The conversations a message can be forwarded to. */
    public static final SubWindowKind FORWARD = kind("forward");

    private ChatSubWindows() {}

    private static SubWindowKind kind(String id) {
        return SubWindowKind.register(id, "gui.losttales.chat.sub." + id);
    }

    /** Registers the kinds; the constants above do, as the class loads. */
    static void install() {
        EMOJI.toString();
    }
}
