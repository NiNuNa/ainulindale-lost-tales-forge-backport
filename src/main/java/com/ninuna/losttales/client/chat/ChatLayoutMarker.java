package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * Zero-width layout markers inside a Lost Tales chat line, carried the
 * same way as every other marker (a click event that survives vanilla's
 * shallow style copies) on an empty text component, so they draw nothing
 * and measure nothing on their own.
 *
 * <p>The <em>anchor</em> marker sits where continuation lines align: after
 * the channel prefix and timestamp, in front of the opening bracket of
 * the sender, so a wrapped message reads as one block under its header.
 * {@link ChatLineWrapper} measures everything before it. An <em>indent</em>
 * marker opens every continuation line of a wrapped message and records
 * the anchor's offset twice — for the closed HUD (channel prefix shown)
 * and the open screen (prefix hidden) — so continuation lines land under
 * the anchor in both states. The renderer and every hit test advance the
 * cursor by that width.</p>
 */
final class ChatLayoutMarker {
    private static final String PREFIX = "losttales-chat-layout:";
    private static final String ANCHOR = "anchor";
    private static final String INDENT = "indent:";

    private ChatLayoutMarker() {}

    static ChatComponentText anchor() {
        return marker(PREFIX + ANCHOR);
    }

    static ChatComponentText indent(int closedWidth, int openWidth) {
        return indent(closedWidth, openWidth, -1, -1);
    }

    /**
     * As above, carrying the sender's own colours. A wrapped message
     * leaves its head marker on the first line, and the head marker is
     * where the renderer reads the sender's name and title colours from;
     * a continuation line carries them here instead, so a name that
     * wrapped is drawn in the same colour as one that did not.
     */
    static ChatComponentText indent(int closedWidth, int openWidth,
                                    int nameColor, int titleColor) {
        return marker(PREFIX + INDENT + Math.max(0, closedWidth) + ':'
                + Math.max(0, openWidth) + ':' + nameColor + ':'
                + titleColor);
    }

    private static ChatComponentText marker(String value) {
        ChatComponentText marker = new ChatComponentText("");
        ChatStyle style = marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, value));
        marker.setChatStyle(style);
        return marker;
    }

    static Data decode(IChatComponent component) {
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
        String payload = value.substring(PREFIX.length());
        if (ANCHOR.equals(payload)) {
            return Data.ANCHOR;
        }
        if (!payload.startsWith(INDENT)) {
            return null;
        }
        String[] fields = payload.substring(INDENT.length()).split(":", -1);
        if (fields.length != 2 && fields.length != 4) {
            return null;
        }
        try {
            return new Data(false, Integer.parseInt(fields[0]),
                    Integer.parseInt(fields[1]),
                    fields.length == 4 ? Integer.parseInt(fields[2]) : -1,
                    fields.length == 4 ? Integer.parseInt(fields[3]) : -1);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isAnchor(IChatComponent component) {
        Data data = decode(component);
        return data != null && data.anchor;
    }

    static final class Data {
        static final Data ANCHOR = new Data(true, 0, 0, -1, -1);

        final boolean anchor;
        private final int closedIndent;
        private final int openIndent;
        /** The sender's colours, or -1 when the line carries none. */
        final int nameColor;
        final int titleColor;

        private Data(boolean anchor, int closedIndent, int openIndent,
                     int nameColor, int titleColor) {
            this.anchor = anchor;
            this.closedIndent = Math.max(0, closedIndent);
            this.openIndent = Math.max(0, openIndent);
            this.nameColor = nameColor;
            this.titleColor = titleColor;
        }

        /** Whether this marker knows what colour the sender's name is. */
        boolean hasColors() {
            return this.nameColor >= 0;
        }

        /** Pixels the cursor advances for this marker in the given state. */
        int indent(boolean chatOpen) {
            return chatOpen ? this.openIndent : this.closedIndent;
        }
    }
}
