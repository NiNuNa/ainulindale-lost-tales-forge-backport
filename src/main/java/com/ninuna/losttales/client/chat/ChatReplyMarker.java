package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * The quote a reply opens with: its colour, and the message it quotes.
 *
 * <p>Both ride one marker because a component carries one click event
 * and the quote needs both — the muted tone it is drawn in, and the id
 * of the message it points at, which is what a click on it resolves
 * through {@link ClientChatMessageIds} to find the original among the
 * lines on screen. Same carrier as every other marker: a click event
 * that survives vanilla's wrapped-chat component copies.</p>
 */
final class ChatReplyMarker {
    private static final String PREFIX = "losttales-chat-reply:";

    private ChatReplyMarker() {}

    /** Marks a run of the quote, in its colour, for {@code messageId}. */
    static ChatComponentText apply(ChatComponentText component, int color,
                                   long messageId) {
        if (component != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ':' + messageId)));
        }
        return component;
    }

    /** The quoted message's id, or 0 when this is not a quote run. */
    static long messageIdOf(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return 0L;
        }
        int separator = payload.indexOf(':');
        if (separator < 0) {
            return 0L;
        }
        try {
            return Long.parseLong(payload.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /** The quote's colour, or null when this is not a quote run. */
    static Integer colorOf(IChatComponent component) {
        String payload = payload(component);
        if (payload == null) {
            return null;
        }
        int separator = payload.indexOf(':');
        if (separator < 0) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(
                    payload.substring(0, separator), 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return payload(component) != null;
    }

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

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder result = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            result.append('0');
        }
        return result.append(hex).toString();
    }
}
