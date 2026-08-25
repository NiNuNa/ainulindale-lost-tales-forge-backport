package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Marks an {@code @mention} of a player inside a message: it carries the
 * mention's exact RGB and the mentioned account, so the renderer colours
 * it, a click opens the conversation with that player, and a hover shows
 * their card — a mention answers to the pointer the way a sender's name
 * does. Same mechanism as {@link ChatColorMarker}: the click event is
 * the carrier, and it survives vanilla's wrapped-chat component copies.
 */
final class ChatMentionMarker {
    private static final String PREFIX = "losttales-chat-mention:";

    private ChatMentionMarker() {}

    static ChatComponentText apply(ChatComponentText component, int color,
                                   String account) {
        if (component != null && account != null && account.length() > 0) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color) + ":" + account)));
        }
        return component;
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
        String body = value.substring(PREFIX.length());
        int separator = body.indexOf(':');
        if (separator != 6 || body.length() <= 7) {
            return null;
        }
        try {
            return new Data(Integer.parseInt(
                    body.substring(0, separator), 16) & 0xFFFFFF,
                    body.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** The mention's RGB, for the renderer's colour resolution. */
    static Integer colorOf(IChatComponent component) {
        Data data = decode(component);
        return data == null ? null : Integer.valueOf(data.color);
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder result = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            result.append('0');
        }
        return result.append(hex).toString();
    }

    /** One mention: its colour and the account it reaches. */
    static final class Data {
        final int color;
        final String account;

        private Data(int color, String account) {
            this.color = color;
            this.account = account;
        }
    }
}
