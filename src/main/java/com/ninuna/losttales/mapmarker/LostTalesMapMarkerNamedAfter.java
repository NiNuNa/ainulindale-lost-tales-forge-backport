package com.ninuna.losttales.mapmarker;

import net.minecraft.block.Block;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * What a marker with no name of its own is called after, kept beside its
 * empty name so each game words it in its own language:
 * {@code player:Nils} for a waystone its placer has not named
 * ({@code Nils's Waystone}), {@code entity:losttales.Nia} for a quest
 * giver known by its kind, {@code block:losttales:cheese_wheel} for a
 * block that gives a quest. A name a player or an operator gave a marker
 * always stands over it.
 */
public final class LostTalesMapMarkerNamedAfter {
    public static final String PLAYER = "player:";
    public static final String ENTITY = "entity:";
    public static final String BLOCK = "block:";
    /** The longest name, kind or block id a marker is called after. */
    public static final int MAX_SUBJECT_LENGTH = 128;
    /** The longest a whole reference is: its prefix and its subject. */
    public static final int MAX_LENGTH = MAX_SUBJECT_LENGTH + 8;
    /** What a waystone its placer has not named is called: {@code %s's Waystone}. */
    public static final String WAYSTONE_NAME_KEY = "gui.losttales.waystone.default_name";

    private LostTalesMapMarkerNamedAfter() {}

    /** A waystone called after the account name of the player who placed it. */
    public static String player(String accountName) {
        return of(PLAYER, accountName);
    }

    /** A marker called after a creature's kind, as {@code EntityList} names it. */
    public static String entity(String entityName) {
        return of(ENTITY, entityName);
    }

    /** A marker called after a block, by its registry name. */
    public static String block(String registryName) {
        return of(BLOCK, registryName);
    }

    /**
     * Whether {@code namedAfter} is nothing, or one of the three kinds:
     * an account name of printable characters, or a creature kind or a
     * block id of letters, digits and {@code _ . : -}, as the game's
     * registries write them. Only the last two ever name a lang key.
     */
    public static boolean isValid(String namedAfter) {
        if (namedAfter == null || namedAfter.length() == 0) {
            return true;
        }
        if (namedAfter.length() > MAX_LENGTH) {
            return false;
        }
        String subject = subject(namedAfter);
        if (subject.length() == 0 || subject.length() > MAX_SUBJECT_LENGTH
                || !subject.equals(subject.trim())) {
            return false;
        }
        if (namedAfter.startsWith(PLAYER)) {
            for (int index = 0; index < subject.length(); index++) {
                char character = subject.charAt(index);
                if (character < ' ' || character == '\u007f'
                        || character == '\u00a7') {
                    return false;
                }
            }
            return true;
        }
        return subject.matches("[A-Za-z0-9_.:\\-]+");
    }

    /** The account, kind or block the reference names; empty for none. */
    public static String subject(String namedAfter) {
        String value = namedAfter == null ? "" : namedAfter;
        for (String prefix : new String[] {PLAYER, ENTITY, BLOCK}) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return "";
    }

    /** Whether it names a waystone after its placer. */
    public static boolean isPlayer(String namedAfter) {
        return namedAfter != null && namedAfter.startsWith(PLAYER)
                && subject(namedAfter).length() > 0;
    }

    /**
     * The name it gives in the language of the side that asks: the
     * waystone line, the game's name for the creature or the block, else
     * the subject made readable; empty for nothing.
     */
    public static String shownName(String namedAfter) {
        if (!isValid(namedAfter)) {
            return "";
        }
        String subject = subject(namedAfter);
        if (subject.length() == 0) {
            return "";
        }
        if (namedAfter.startsWith(PLAYER)) {
            return StatCollector.translateToLocalFormatted(WAYSTONE_NAME_KEY, subject);
        }
        if (namedAfter.startsWith(ENTITY)) {
            String key = "entity." + subject + ".name";
            return StatCollector.canTranslate(key)
                    ? StatCollector.translateToLocal(key) : readable(subject);
        }
        String key = blockKey(subject);
        return key.length() > 0 && StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : readable(subject);
    }

    /**
     * The name it gives as an argument of a line the server sends, which
     * each reader's game words; null for nothing.
     */
    public static IChatComponent component(String namedAfter) {
        if (!isValid(namedAfter) || subject(namedAfter).length() == 0) {
            return null;
        }
        String subject = subject(namedAfter);
        if (namedAfter.startsWith(PLAYER)) {
            return new ChatComponentTranslation(WAYSTONE_NAME_KEY, subject);
        }
        String key = namedAfter.startsWith(ENTITY)
                ? "entity." + subject + ".name" : blockKey(subject);
        return key.length() > 0 && StatCollector.canTranslate(key)
                ? new ChatComponentTranslation(key)
                : new ChatComponentText(readable(subject));
    }

    /** The lang key a block is named by; empty for a block this game does not have. */
    private static String blockKey(String registryName) {
        Block block;
        try {
            block = Block.getBlockFromName(registryName);
        } catch (RuntimeException unknown) {
            return "";
        }
        if (block == null) {
            return "";
        }
        try {
            String unlocalized = block.getUnlocalizedName();
            return unlocalized == null ? "" : unlocalized + ".name";
        } catch (RuntimeException unnamed) {
            // Another mod's block may fail to say its name.
            return "";
        }
    }

    /** {@code losttales.HobbitFarmer} as {@code HobbitFarmer}, {@code cheese_wheel} as {@code cheese wheel}. */
    private static String readable(String subject) {
        String text = subject;
        int cut = Math.max(text.lastIndexOf('.'), text.lastIndexOf(':'));
        if (cut >= 0 && cut + 1 < text.length()) {
            text = text.substring(cut + 1);
        }
        return text.replace('_', ' ').replace('-', ' ');
    }

    private static String of(String prefix, String subject) {
        String value = subject == null ? "" : subject.trim();
        String reference = prefix + value;
        return value.length() == 0 || !isValid(reference) ? "" : reference;
    }
}
