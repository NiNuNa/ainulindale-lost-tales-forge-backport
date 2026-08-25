package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * Style marker on a line's header runs — the {@code Channel: } prefix and
 * the {@code [HH:mm] } timestamp — the components that stand before the
 * layout anchor. Each carries its exact colour past vanilla's shallow
 * style copies, and each names the chat state it belongs to:
 *
 * <ul>
 * <li>The <b>channel prefix</b> is for the closed HUD, where one feed
 * carries every channel and the prefix is what tells them apart. The open
 * screen has tabs for that, so it is skipped there.</li>
 * <li>The <b>timestamp</b> is for the open screen, where the player is
 * reading the history — but it is never part of the line's own flow: the
 * open screen draws it in the timestamp column at the window's left edge
 * ({@link ChatTimestampColumn}), and the feed is a glance at what was
 * just said, so inline it takes no width in either state.</li>
 * </ul>
 *
 * <p>{@link #isHidden} is the one answer to "does this component take any
 * width right now"; every walk over a line — drawing, head placement,
 * wrapping, hit testing — asks it, so they can never disagree.</p>
 */
final class ChatPrefixMarker {
    /** Header run shown only while the chat screen is closed. */
    private static final String CHANNEL = "losttales-chat-channel:";
    /** Header run shown only while the chat screen is open. */
    private static final String TIMESTAMP = "losttales-chat-time:";

    private ChatPrefixMarker() {}

    /** Marks a component as the channel prefix, in its colour. */
    static ChatComponentText channel(ChatComponentText component, int color) {
        return mark(component, CHANNEL, color);
    }

    /** Marks a component as part of the timestamp, in its colour. */
    static ChatComponentText timestamp(ChatComponentText component,
                                       int color) {
        return mark(component, TIMESTAMP, color);
    }

    /** The run's colour, or null when this is not a header run. */
    static Integer decode(IChatComponent component) {
        String value = payload(component);
        if (value == null) {
            return null;
        }
        String hex = value.startsWith(CHANNEL)
                ? value.substring(CHANNEL.length())
                : value.substring(TIMESTAMP.length());
        try {
            return Integer.valueOf(Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Whether the component is either kind of header run. */
    static boolean isMarker(IChatComponent component) {
        return payload(component) != null;
    }

    /** Whether the component is the channel prefix specifically. */
    static boolean isChannel(IChatComponent component) {
        String value = payload(component);
        return value != null && value.startsWith(CHANNEL);
    }

    /** Whether the component is part of the timestamp specifically. */
    static boolean isTimestamp(IChatComponent component) {
        String value = payload(component);
        return value != null && value.startsWith(TIMESTAMP);
    }

    /**
     * True when the component contributes nothing to the line's own flow
     * right now: the channel prefix while the chat screen is open, and
     * the timestamp always — the open screen draws it in the timestamp
     * column instead, and the closed feed does not show it at all.
     */
    static boolean isHidden(IChatComponent component, boolean chatOpen) {
        String value = payload(component);
        if (value == null) {
            return false;
        }
        return value.startsWith(TIMESTAMP)
                || (chatOpen && value.startsWith(CHANNEL));
    }

    private static ChatComponentText mark(ChatComponentText component,
                                          String kind, int color) {
        if (component != null) {
            ChatStyle style = component.getChatStyle().setChatClickEvent(
                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            kind + colorHex(color)));
            component.setChatStyle(style);
        }
        return component;
    }

    /** The marker payload of either kind, or null. */
    private static String payload(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND || value == null
                || !(value.startsWith(CHANNEL)
                        || value.startsWith(TIMESTAMP))) {
            return null;
        }
        return value;
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder result = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            result.append('0');
        }
        return result.append(hex).toString();
    }
}
