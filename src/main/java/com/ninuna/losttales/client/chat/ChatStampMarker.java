package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * The time a message's name row wears behind the name in an open
 * window, the way Discord dates a message beside its author
 * ({@link ChatTimestampFormatter#formatStamp}): the chat's small text in
 * its aside tone, the time itself in italics.
 *
 * <p>The run holds no text of its own. It carries the words it stands
 * for and declares the width they take at the small size in the row's
 * own units, which every walk over the row reads through
 * {@link ChatInlineIcons#declaredWidth} — drawing, wrapping and hit
 * testing alike — while the renderer draws the words at the small size
 * where the run stands. Nothing draws it at the row's own size, and a
 * copy of the row never picks the time up.</p>
 *
 * <p>It is laid into the row by the wrapper, for the open window alone,
 * since what it says depends on the day the window is read on; the
 * stored message never holds it.</p>
 */
final class ChatStampMarker {
    private static final String PREFIX = "losttales-chat-stamp:";
    /** Far wider than any stamp can be, narrow enough to stay a stamp. */
    private static final int MAX_WIDTH = 1024;

    private ChatStampMarker() {}

    /** A stamp reading {@code text}, {@code width} pixels wide in its row. */
    static ChatComponentText of(String text, int width) {
        ChatComponentText marker = new ChatComponentText("");
        ChatStyle style = marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        PREFIX + Math.max(0, Math.min(MAX_WIDTH, width))
                                + ":" + (text == null ? "" : text)));
        marker.setChatStyle(style);
        return marker;
    }

    static boolean isMarker(IChatComponent component) {
        return payload(component) != null;
    }

    /** The width the stamp takes in its row, or -1 when this is not one. */
    static int widthOf(IChatComponent component) {
        String value = payload(component);
        int colon = value == null ? -1 : value.indexOf(':');
        if (colon < 0) {
            return -1;
        }
        try {
            int width = Integer.parseInt(value.substring(0, colon));
            return Math.max(0, Math.min(MAX_WIDTH, width));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** The words the stamp stands for, or null when this is not one. */
    static String textOf(IChatComponent component) {
        String value = payload(component);
        int colon = value == null ? -1 : value.indexOf(':');
        return colon < 0 ? null : value.substring(colon + 1);
    }

    /** What follows the prefix, or null for any other run. */
    private static String payload(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND
                || value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        return value.substring(PREFIX.length());
    }
}
