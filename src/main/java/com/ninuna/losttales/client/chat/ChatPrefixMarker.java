package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * Style marker on a line's channel prefix — the {@code Channel: } runs
 * standing before the layout anchor. It carries the prefix's exact
 * colour past vanilla's shallow style copies, and says which chat state
 * the prefix belongs to: the closed HUD, where one feed carries every
 * channel and the prefix is what tells them apart. The open screen has
 * tabs for that, so it is skipped there.
 *
 * <p>{@link #isHidden} is the one answer to "does this component take any
 * width right now"; every walk over a line — drawing, head placement,
 * wrapping, hit testing — asks it, so they can never disagree.</p>
 */
final class ChatPrefixMarker {
    /** Header run shown only while the chat screen is closed. */
    private static final String CHANNEL = "losttales-chat-channel:";

    private ChatPrefixMarker() {}

    /** Marks a component as the channel prefix, in its colour. */
    static ChatComponentText channel(ChatComponentText component, int color) {
        if (component != null) {
            ChatStyle style = component.getChatStyle().setChatClickEvent(
                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            CHANNEL + colorHex(color)));
            component.setChatStyle(style);
        }
        return component;
    }

    /** The prefix's colour, or null when this is not a prefix run. */
    static Integer decode(IChatComponent component) {
        String value = payload(component);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(
                    value.substring(CHANNEL.length()), 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Whether the component is a prefix run. */
    static boolean isMarker(IChatComponent component) {
        return payload(component) != null;
    }

    /**
     * True when the component contributes nothing to the line's own flow
     * right now: the channel prefix while the chat screen is open.
     */
    static boolean isHidden(IChatComponent component, boolean chatOpen) {
        return chatOpen && isMarker(component);
    }

    /** The marker payload, or null. */
    private static String payload(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND || value == null
                || !value.startsWith(CHANNEL)) {
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
