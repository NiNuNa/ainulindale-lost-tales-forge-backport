package com.ninuna.losttales.fellowship.model;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * A member's or an inviter's character name as each player reads it. A
 * name the server does not know is kept and sent empty, and drawn as the
 * word for an unknown one in the reader's language.
 */
public final class FellowshipNames {
    /** The word for a character the server knows no name for. */
    public static final String UNKNOWN_KEY = "gui.losttales.fellowship.unknown";

    private FellowshipNames() {}

    /** The name as this side reads it: the name, or the word for an unknown one. */
    public static String shown(String name) {
        return name == null || name.trim().length() == 0
                ? StatCollector.translateToLocal(UNKNOWN_KEY) : name;
    }

    /** The name as an argument of a line the server sends, which each reader's game words. */
    public static IChatComponent component(String name) {
        return name == null || name.trim().length() == 0
                ? new ChatComponentTranslation(UNKNOWN_KEY)
                : new ChatComponentText(name);
    }
}
