package com.ninuna.losttales.chat;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Translated words are plain text and words translations alone, at any
 * place in a line, their arguments plain words or translated words in turn.
 */
public final class ChatTranslatedWordsTest {
    private static final String WORDS = ChatTranslatedWords.PREFIX + "discord_sticker";

    @Test
    public void plainTextAndWordsTranslationsAreTranslatedWords() {
        IChatComponent line = new ChatComponentText("");
        line.appendSibling(new ChatComponentText("look "));
        line.appendSibling(new ChatComponentTranslation(WORDS, "Wave"));
        assertTrue(ChatTranslatedWords.isWordsComponent(line));
        assertTrue(ChatTranslatedWords.isWordsComponent(new ChatComponentTranslation(
                WORDS, new ChatComponentTranslation(WORDS, "Wave"))));
    }

    @Test
    public void anythingElseIsNot() {
        assertFalse(ChatTranslatedWords.isWordsComponent(null));
        assertFalse("no translation at all",
                ChatTranslatedWords.isWordsComponent(new ChatComponentText("look")));
        assertFalse("a key that is no words key", ChatTranslatedWords.isWordsComponent(
                new ChatComponentTranslation("chat.type.text", "a", "b")));
        assertFalse("an argument that is no words", ChatTranslatedWords.isWordsComponent(
                new ChatComponentTranslation(WORDS,
                        new ChatComponentTranslation("chat.type.text", "a", "b"))));
        assertFalse("an argument of another kind", ChatTranslatedWords.isWordsComponent(
                new ChatComponentTranslation(WORDS, Integer.valueOf(3))));
        IChatComponent styled = new ChatComponentText("look ");
        styled.getChatStyle().setColor(EnumChatFormatting.RED);
        IChatComponent line = new ChatComponentText("");
        line.appendSibling(styled);
        line.appendSibling(new ChatComponentTranslation(WORDS, "Wave"));
        assertFalse("a style anywhere", ChatTranslatedWords.isWordsComponent(line));
        IChatComponent deep = new ChatComponentTranslation(WORDS, "Wave");
        for (int depth = 0; depth < 6; depth++) {
            deep = new ChatComponentTranslation(WORDS, deep);
        }
        assertFalse("nested past any line's need", ChatTranslatedWords.isWordsComponent(deep));
    }
}
