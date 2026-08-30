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
 * {@link ChatLineWrapper} measures everything before it. A <em>line
 * break</em> marker ahead of the anchor closes a row of its own above
 * the message — the quote a reply opens with — and the rest of the line
 * is laid out after it exactly as an unbroken one would be. A <em>body
 * break</em> marker after the sender ends the header row and opens the
 * message body on the next one, behind the chevron the wrapper draws in
 * the sender's colour ({@link ChatBodyMarker}); it carries that colour,
 * since a grouped continuation has no head marker to read it from. An
 * <em>indent</em>
 * marker opens every continuation line of a wrapped message and records
 * the anchor's offset twice — for the closed HUD (channel prefix shown)
 * and the open screen (prefix hidden) — so continuation lines land under
 * the anchor in both states. The renderer and every hit test advance the
 * cursor by that width.</p>
 */
final class ChatLayoutMarker {
    private static final String PREFIX = "losttales-chat-layout:";
    private static final String ANCHOR = "anchor";
    private static final String BREAK = "break";
    private static final String BODY = "body:";
    private static final String INDENT = "indent:";

    private ChatLayoutMarker() {}

    static ChatComponentText anchor() {
        return marker(PREFIX + ANCHOR);
    }

    /**
     * Closes a row of its own above the message. Everything before it is
     * laid out on that row and cut to one line; the message itself
     * begins on the next.
     */
    static ChatComponentText lineBreak() {
        return marker(PREFIX + BREAK);
    }

    /**
     * Ends the header row and opens the message body. Everything before
     * it is the message's header; everything after it is its body, which
     * begins on a row of its own. {@code senderColor} is the colour the
     * body's chevron is drawn in: the marker carries it because a
     * grouped line has no head marker to read it from.
     */
    static ChatComponentText bodyBreak(int senderColor) {
        return marker(PREFIX + BODY + (senderColor & 0xFFFFFF));
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

    /** The marker payload this component carries, or null. */
    private static String payloadOf(IChatComponent component) {
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

    static Data decode(IChatComponent component) {
        String payload = payloadOf(component);
        if (payload == null) {
            return null;
        }
        if (ANCHOR.equals(payload)) {
            return Data.ANCHOR;
        }
        if (BREAK.equals(payload)) {
            return Data.BREAK;
        }
        if (payload.startsWith(BODY)) {
            return Data.BODY;
        }
        if (!payload.startsWith(INDENT)) {
            return null;
        }
        String[] fields = payload.substring(INDENT.length()).split(":", -1);
        if (fields.length != 2 && fields.length != 4) {
            return null;
        }
        try {
            return new Data(Integer.parseInt(fields[0]),
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

    static boolean isLineBreak(IChatComponent component) {
        Data data = decode(component);
        return data != null && data.lineBreak;
    }

    static boolean isBodyBreak(IChatComponent component) {
        Data data = decode(component);
        return data != null && data.bodyBreak;
    }

    /**
     * The sender colour a body break carries, or -1 when the component
     * is not one or names no colour. Read straight from the payload
     * rather than through {@link Data}: the colours a {@code Data} holds
     * are an indent marker's, and a line that falls back to vanilla's
     * wrapping still carries its body break.
     */
    static int bodyColor(IChatComponent component) {
        String payload = payloadOf(component);
        if (payload == null || !payload.startsWith(BODY)) {
            return -1;
        }
        try {
            return Integer.parseInt(payload.substring(BODY.length()))
                    & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static final class Data {
        static final Data ANCHOR =
                new Data(true, false, false, 0, 0, -1, -1);
        static final Data BREAK =
                new Data(false, true, false, 0, 0, -1, -1);
        static final Data BODY =
                new Data(false, false, true, 0, 0, -1, -1);

        final boolean anchor;
        final boolean lineBreak;
        final boolean bodyBreak;
        private final int closedIndent;
        private final int openIndent;
        /** The sender's colours, or -1 when the line carries none. */
        final int nameColor;
        final int titleColor;

        private Data(int closedIndent, int openIndent, int nameColor,
                     int titleColor) {
            this(false, false, false, closedIndent, openIndent, nameColor,
                    titleColor);
        }

        private Data(boolean anchor, boolean lineBreak, boolean bodyBreak,
                     int closedIndent, int openIndent, int nameColor,
                     int titleColor) {
            this.anchor = anchor;
            this.lineBreak = lineBreak;
            this.bodyBreak = bodyBreak;
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
