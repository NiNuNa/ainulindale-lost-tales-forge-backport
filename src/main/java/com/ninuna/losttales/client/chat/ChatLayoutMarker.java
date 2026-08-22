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
        return marker(PREFIX + INDENT + Math.max(0, closedWidth) + ':'
                + Math.max(0, openWidth));
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
        String[] widths = payload.substring(INDENT.length()).split(":", -1);
        if (widths.length != 2) {
            return null;
        }
        try {
            return new Data(false, Integer.parseInt(widths[0]),
                    Integer.parseInt(widths[1]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isAnchor(IChatComponent component) {
        Data data = decode(component);
        return data != null && data.anchor;
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    static final class Data {
        static final Data ANCHOR = new Data(true, 0, 0);

        final boolean anchor;
        private final int closedIndent;
        private final int openIndent;

        private Data(boolean anchor, int closedIndent, int openIndent) {
            this.anchor = anchor;
            this.closedIndent = Math.max(0, closedIndent);
            this.openIndent = Math.max(0, openIndent);
        }

        /** Pixels the cursor advances for this marker in the given state. */
        int indent(boolean chatOpen) {
            return chatOpen ? this.openIndent : this.closedIndent;
        }
    }
}
