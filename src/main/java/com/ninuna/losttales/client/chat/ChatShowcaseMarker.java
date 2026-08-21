package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Invisible style marker for an inline shared thing — an item or a map
 * marker: the two bold spaces reserving the icon slot and the bracketed
 * name beside it. Same mechanism as {@link ChatEmojiMarker}. The marker
 * carries only a key into {@link ClientChatShowcaseStore}; the decoded
 * payload itself never lives in the component, so wrapped-line copies stay
 * cheap and nothing is re-parsed while rendering.
 */
final class ChatShowcaseMarker {
    private static final String PREFIX = "losttales-chat-share:";
    /** Two bold spaces measure ten pixels, matching the emote slot. */
    private static final String PLACEHOLDER = "  ";

    private ChatShowcaseMarker() {}

    static ChatComponentText createIcon(ChatShareKind kind, int showcaseId) {
        ChatComponentText marker = new ChatComponentText(PLACEHOLDER);
        ChatStyle style = marker.getChatStyle()
                .setColor(EnumChatFormatting.WHITE);
        style.setBold(Boolean.TRUE);
        style.setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                encode(kind, showcaseId, true, 0xFFFFFF)));
        marker.setChatStyle(style);
        return marker;
    }

    static ChatComponentText createText(ChatShareKind kind, int showcaseId,
                                        String text,
                                        EnumChatFormatting nearest,
                                        int rgb) {
        ChatComponentText marker = new ChatComponentText(
                text == null ? "" : text);
        ChatStyle style = marker.getChatStyle().setColor(nearest);
        style.setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                encode(kind, showcaseId, false, rgb)));
        marker.setChatStyle(style);
        return marker;
    }

    private static String encode(ChatShareKind kind, int showcaseId,
                                 boolean icon, int rgb) {
        return PREFIX + kind.getCode() + ':' + showcaseId + ':'
                + (icon ? 'i' : 't') + ':' + colorHex(rgb);
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
        String[] fields = value.substring(PREFIX.length()).split(":", -1);
        if (fields.length != 4 || fields[0].length() != 1
                || fields[3].length() != 6
                || (!"i".equals(fields[2]) && !"t".equals(fields[2]))) {
            return null;
        }
        ChatShareKind kind = ChatShareKind.fromCode(fields[0].charAt(0));
        if (kind == null) {
            return null;
        }
        try {
            return new Data(kind, Integer.parseInt(fields[1]),
                    "i".equals(fields[2]),
                    Integer.parseInt(fields[3], 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    private static String colorHex(int color) {
        String hex = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder padded = new StringBuilder(6);
        for (int index = hex.length(); index < 6; index++) {
            padded.append('0');
        }
        return padded.append(hex).toString();
    }

    static final class Data {
        final ChatShareKind kind;
        final int showcaseId;
        /** True for the icon slot, false for the bracketed name text. */
        final boolean icon;
        final int textColor;

        private Data(ChatShareKind kind, int showcaseId, boolean icon,
                     int textColor) {
            this.kind = kind;
            this.showcaseId = showcaseId;
            this.icon = icon;
            this.textColor = textColor;
        }
    }
}
