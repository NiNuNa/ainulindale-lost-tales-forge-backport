package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiSuggester;
import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.client.window.WindowFields;
import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * The windows' fields in the chat's look: the input bar's own field with
 * its one caret, its selection and its previews, drawn at its window's
 * fade. A field that shows emoji opens the emoji list while a
 * {@code :name} stands at its caret, as the bar does.
 */
final class ChatWindowFields implements WindowFields.Kit {
    private ChatWindowFields() {}

    /** Makes every window's field the chat's; before any screen opens. */
    static void install() {
        WindowFields.install(new ChatWindowFields());
    }

    @Override
    public GuiTextField make(int height, int limit, boolean showsEmoji) {
        ChatInputField made = new ChatInputField(
                Minecraft.getMinecraft().fontRenderer, 0, 0, 1, height);
        made.setEnableBackgroundDrawing(false);
        if (limit > 0) {
            made.setMaxStringLength(limit);
        }
        return showsEmoji ? made.emojiOnly() : made.plainText();
    }

    @Override
    public void draw(GuiTextField field, int alpha) {
        ChatInputBar.beginFade(alpha / 255.0F);
        try {
            field.drawTextBox();
        } finally {
            ChatInputBar.endFade();
        }
    }

    @Override
    public WindowFields.FieldList listFor(GuiTextField field,
                                          boolean showsEmoji) {
        return showsEmoji ? new EmojiList() : null;
    }

    /** The emoji list over a field, while the chat's emoji are on. */
    private static final class EmojiList implements WindowFields.FieldList {
        private final ChatEmojiSuggestionBox box = new ChatEmojiSuggestionBox();

        @Override
        public void update(GuiTextField field) {
            if (LostTalesConfig.enableChatEmojis) {
                this.box.update(field.getText(), field.getCursorPosition());
            } else {
                this.box.update("", 0);
            }
        }

        @Override
        public boolean isActive() {
            return this.box.isActive();
        }

        @Override
        public void dismiss() {
            this.box.dismiss();
        }

        /** Up and Down walk it; Tab and Enter take the emoji, as the bar's list does. */
        @Override
        public boolean serve(GuiTextField field, LostTalesKeyPress press) {
            if (press.is(Keyboard.KEY_UP) || press.is(Keyboard.KEY_DOWN)) {
                this.box.moveSelection(press.is(Keyboard.KEY_UP) ? -1 : 1);
                return true;
            }
            if (press.is(Keyboard.KEY_TAB) || press.is(Keyboard.KEY_RETURN)
                    || press.is(Keyboard.KEY_NUMPADENTER)) {
                take(field, this.box.getSelected());
                return true;
            }
            return false;
        }

        @Override
        public void take(GuiTextField field, int row) {
            take(field, this.box.at(row));
        }

        /**
         * The emoji in place of the {@code :name} typed at the caret, a
         * space after it, as the bar takes one from its list.
         */
        private void take(GuiTextField field, ChatEmoji emoji) {
            ChatEmojiSuggester.Query query = this.box.getQuery();
            if (emoji == null || query == null) {
                return;
            }
            String text = field.getText();
            int start = Math.max(0, Math.min(query.colonIndex, text.length()));
            int cursor = Math.max(start, Math.min(field.getCursorPosition(),
                    text.length()));
            String replacement = emoji.getShortcode() + " ";
            field.setText(text.substring(0, start) + replacement
                    + text.substring(cursor));
            field.setCursorPosition(Math.min(field.getText().length(),
                    start + replacement.length()));
            update(field);
        }

        @Override
        public void draw(Minecraft minecraft, PointerRegions regions,
                         int left, int fieldTop, double pointerX,
                         double pointerY) {
            this.box.draw(minecraft, minecraft.fontRenderer, regions,
                    anchor(fieldTop), left, pointerX, pointerY);
        }

        @Override
        public int rowAt(double x, double y, int left, int fieldTop) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (!this.box.contains(minecraft.fontRenderer, x, y,
                    anchor(fieldTop), left)) {
                return -2;
            }
            return this.box.rowAt(minecraft.fontRenderer, x, y,
                    anchor(fieldTop), left);
        }

        /** The list's bottom a clear pixel above the field's row. */
        private static int anchor(int fieldTop) {
            return ChatEmojiSuggestionBox.anchorEndingAt(fieldTop - 1);
        }
    }
}
