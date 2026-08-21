package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Style marker on the {@code Channel: } prefix components of a line. The
 * prefix is built once with the message, but whether it is shown depends
 * on the moment: with the chat closed the HUD shows every channel in one
 * feed and needs the prefix to tell them apart; with the chat screen open
 * the tabs already separate channels, so the prefix is skipped — drawn
 * with no width — and the line reads {@code [HH:mm] <Name> message}. Same
 * mechanism as {@link ChatColorMarker}, which it also replaces for these
 * components so the exact channel colour survives vanilla's wrapping.
 */
final class ChatChannelPrefixMarker {
    private static final String PREFIX = "losttales-chat-channel:";

    private ChatChannelPrefixMarker() {}

    static ChatComponentText apply(ChatComponentText component, int color) {
        if (component != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            PREFIX + colorHex(color))));
        }
        return component;
    }

    /** The prefix colour, or null when this is not a prefix component. */
    static Integer decode(IChatComponent component) {
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
        try {
            return Integer.valueOf(Integer.parseInt(
                    value.substring(PREFIX.length()), 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    /**
     * True when the component contributes nothing on screen right now.
     * Every walk over a line's components — drawing, head placement, hit
     * testing — must agree on this, so they all ask here.
     */
    static boolean isHidden(IChatComponent component, boolean chatOpen) {
        return chatOpen && isMarker(component);
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
