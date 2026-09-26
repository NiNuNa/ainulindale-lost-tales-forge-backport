package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import net.minecraft.client.Minecraft;

/**
 * One of the chat's emoji drawn as the chat draws it, with the one shadow,
 * for the screens outside the chat that show one: a profile's glances.
 */
public final class ChatEmojiIcon {
    /** An emoji's box, one texel to a pixel. */
    public static final int SIZE = ChatEmoji.SPRITE_SIZE;

    private ChatEmojiIcon() {}

    /** Draws the emoji in the box from {@code (x, y)}, at {@code alpha}. */
    public static void draw(Minecraft minecraft, ChatEmoji emoji, int x, int y,
                            int alpha) {
        if (minecraft != null && emoji != null) {
            ChatInlineIcons.drawEmoji(minecraft, emoji, x, y, SIZE, alpha);
        }
    }
}
