package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * The chevron a message body opens with: the chat's own punctuation
 * between a sender and their words, drawn in the sender's colour on the
 * row under them.
 *
 * <p>It is <em>not</em> part of what the message says. The wrapper adds
 * it while laying a message out, so it never reaches the stored message
 * at all, and everything that reads a message back as text — the
 * clipboard above all — skips it, so copying a line copies the words
 * and nothing else.</p>
 *
 * <p>The colour is carried here rather than left to a formatting code
 * because a sender's colour is an exact RGB, and because the mark has to
 * survive vanilla's shallow style copies like every other one.</p>
 */
final class ChatBodyMarker {
    private static final String PREFIX = "losttales-chat-body:";
    /** Payload of a separator whose sender colour is unknown. */
    private static final String NO_COLOR = "none";

    private ChatBodyMarker() {}

    /**
     * The separator run, in the sender's colour; a negative colour
     * leaves it in the body's own tone.
     */
    static ChatComponentText separator(String text, int color) {
        ChatComponentText component = new ChatComponentText(text);
        ChatStyle style = component.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        PREFIX + (color < 0 ? NO_COLOR : colorHex(color))));
        component.setChatStyle(style);
        return component;
    }

    /** The separator's colour, or null when it is not one or has none. */
    static Integer decode(IChatComponent component) {
        String payload = payload(component);
        if (payload == null || NO_COLOR.equals(payload)) {
            return null;
        }
        try {
            return Integer.valueOf(
                    Integer.parseInt(payload, 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Whether the component is the separator rather than the message. */
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
