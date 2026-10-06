package com.ninuna.losttales.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * The English lang file's words, as a server running in English writes
 * them: what a test checks a status, a topic or a Discord answer
 * against, so the words live in the lang file alone. A key the file
 * lacks fails the test.
 */
public final class EnglishWords implements LostTalesWords {
    public static final EnglishWords INSTANCE = new EnglishWords();

    private final Map<String, String> lines;

    /** Read as the mod reads it ({@link LostTalesLangFile}), a missing file failing at once. */
    private EnglishWords() {
        InputStream in = EnglishWords.class.getResourceAsStream(LostTalesLangFile.ENGLISH);
        if (in == null) {
            throw new IllegalStateException("the English lang file is missing");
        }
        try {
            this.lines = LostTalesLangFile.read(in);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    @Override
    public String format(String key, Object... arguments) {
        String pattern = this.lines.get(key);
        if (pattern == null) {
            throw new AssertionError("no English line for " + key);
        }
        return String.format(pattern, arguments);
    }

    /** Whether the English lang file has a line under {@code key}. */
    public boolean has(String key) {
        return this.lines.containsKey(key);
    }

    /**
     * A chat line as a game in English shows it: each translation's line
     * with its arguments, which are read the same way, and then the parts
     * that follow it.
     */
    public String read(IChatComponent component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof ChatComponentTranslation) {
            ChatComponentTranslation translation = (ChatComponentTranslation)component;
            Object[] arguments = translation.getFormatArgs();
            Object[] read = new Object[arguments == null ? 0 : arguments.length];
            for (int index = 0; index < read.length; index++) {
                read[index] = arguments[index] instanceof IChatComponent
                        ? read((IChatComponent)arguments[index])
                        : String.valueOf(arguments[index]);
            }
            text.append(format(translation.getKey(), read));
        } else if (component instanceof ChatComponentText) {
            text.append(((ChatComponentText)component).getChatComponentText_TextValue());
        } else if (component != null) {
            text.append(component.getUnformattedTextForChat());
        }
        if (component != null) {
            for (Object sibling : component.getSiblings()) {
                text.append(read((IChatComponent)sibling));
            }
        }
        return text.toString();
    }
}
