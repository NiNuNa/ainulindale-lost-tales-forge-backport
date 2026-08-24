package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * A gap of an exact number of pixels inside a line.
 *
 * <p>A run of spaces cannot make every width: a space is four pixels and
 * a bold one five, so nothing between them is reachable. A spacer draws
 * nothing and declares how far the cursor moves past it, which every
 * walk over a line reads through {@link ChatInlineIcons#declaredWidth} —
 * drawing, wrapping and hit testing alike, so none of them can disagree
 * about where the next glyph starts.</p>
 */
final class ChatSpacerMarker {
    private static final String PREFIX = "losttales-chat-space:";
    /** Wide enough for any gap a line needs, narrow enough to be a gap. */
    private static final int MAX_WIDTH = 64;

    private ChatSpacerMarker() {}

    /** A gap of {@code width} pixels; nothing is drawn for it. */
    static ChatComponentText of(int width) {
        ChatComponentText marker = new ChatComponentText("");
        ChatStyle style = marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        PREFIX + Math.max(0, Math.min(MAX_WIDTH, width))));
        marker.setChatStyle(style);
        return marker;
    }

    /** The gap this component stands for, or -1 when it is not one. */
    static int decode(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return -1;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND
                || value == null || !value.startsWith(PREFIX)) {
            return -1;
        }
        try {
            int width = Integer.parseInt(value.substring(PREFIX.length()));
            return Math.max(0, Math.min(MAX_WIDTH, width));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) >= 0;
    }
}
