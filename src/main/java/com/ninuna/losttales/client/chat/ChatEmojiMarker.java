package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Invisible style marker carried by the two bold spaces reserved for an
 * inline emote sprite. Same mechanism as {@link ChatHeadMarker}: the click
 * event survives vanilla's wrapped-chat shallow style copies, while the bold
 * spaces reserve the ten pixels the sprite is drawn into.
 */
final class ChatEmojiMarker {
    private static final String PREFIX = "losttales-chat-emoji:";
    /** Two bold spaces measure exactly {@link ChatEmoji#SPRITE_SIZE} pixels. */
    private static final String PLACEHOLDER = "  ";
    private static final char FORMATTING_ESCAPE = 167;

    private ChatEmojiMarker() {}

    static ChatComponentText create(ChatEmoji emoji) {
        ChatComponentText marker = new ChatComponentText(PLACEHOLDER);
        ChatStyle style = marker.getChatStyle()
                .setColor(EnumChatFormatting.WHITE);
        style.setBold(Boolean.TRUE);
        style.setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                PREFIX + emoji.getName()));
        marker.setChatStyle(style);
        return marker;
    }

    static ChatEmoji decode(IChatComponent component) {
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
        return ChatEmoji.fromName(value.substring(PREFIX.length()));
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    /**
     * True while the component still carries both reserved spaces. Vanilla's
     * wrapper may split a node that straddles the line end, leaving each half
     * with the marker's copied style but only part of the placeholder; drawing
     * nothing in that rare case beats drawing the sprite twice.
     */
    static boolean reservesFullSlot(String componentText) {
        if (componentText == null) {
            return false;
        }
        int visible = 0;
        for (int index = 0; index < componentText.length(); index++) {
            if (componentText.charAt(index) == FORMATTING_ESCAPE
                    && index + 1 < componentText.length()) {
                index++;
                continue;
            }
            visible++;
        }
        return visible == PLACEHOLDER.length();
    }
}
