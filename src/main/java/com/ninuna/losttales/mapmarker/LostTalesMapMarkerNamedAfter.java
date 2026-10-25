package com.ninuna.losttales.mapmarker;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * What a marker with no name of its own is called after, kept beside its
 * empty name so each game words it in its own language.
 *
 * <p>A waystone its placer has not named is kept as
 * {@code character:<id>}, the character that placed it (WAY-01), and the
 * server sends it as {@code player:Aragorn}, by that character's name as
 * it stands ({@link #shown}), so every game reads {@code Aragorn's
 * Waystone} and a rename reaches it. A character the server cannot find
 * any more, purged, is sent as it is kept, and reads {@code Waystone}.
 * {@code entity:losttales.Nia} names a quest giver by its kind,
 * {@code block:losttales:cheese_wheel} a block that gives a quest. A name
 * a player or an operator gave a marker always stands over it.</p>
 */
public final class LostTalesMapMarkerNamedAfter {
    public static final String CHARACTER = "character:";
    public static final String PLAYER = "player:";
    public static final String ENTITY = "entity:";
    public static final String BLOCK = "block:";
    /** The longest name, kind or block id a marker is called after. */
    public static final int MAX_SUBJECT_LENGTH = 128;
    /** The longest a whole reference is: its prefix and its subject. */
    public static final int MAX_LENGTH = MAX_SUBJECT_LENGTH + 8;
    /** What a waystone its placer has not named is called: {@code %s's Waystone}. */
    public static final String WAYSTONE_NAME_KEY = "gui.losttales.waystone.default_name";
    /** What it is called once the server cannot find its placer: {@code Waystone}. */
    public static final String UNKNOWN_PLACER_KEY =
            "gui.losttales.waystone.default_name.unknown";

    /** The server's characters by id, as a reference to one is worded. */
    public interface Names {
        /** The character's name as it stands; null or empty for one the server cannot find. */
        String nameOf(UUID characterId);
    }

    private LostTalesMapMarkerNamedAfter() {}

    /** A waystone called after the character that placed it, kept by its id. */
    public static String character(UUID characterId) {
        return characterId == null ? ""
                : CHARACTER + characterId.toString().toLowerCase(Locale.ROOT);
    }

    /** The character a reference keeps a waystone called after; null for any other reference. */
    public static UUID characterId(String namedAfter) {
        if (namedAfter == null || !namedAfter.startsWith(CHARACTER)
                || !isValid(namedAfter)) {
            return null;
        }
        return UUID.fromString(namedAfter.substring(CHARACTER.length()));
    }

    /**
     * Whether a marker record may keep the reference: nothing, or the
     * character a waystone is called after. A name only travels.
     */
    public static boolean isKept(String namedAfter) {
        return namedAfter == null || namedAfter.length() == 0
                || characterId(namedAfter) != null;
    }

    /**
     * The reference as the server sends it: a character by its name as
     * {@code names} gives it ({@code player:Aragorn}); one {@code names}
     * cannot name stays as it is kept, and reads {@code Waystone}. Every
     * other reference is sent as it is.
     */
    public static String shown(String namedAfter, Names names) {
        String kept = namedAfter == null ? "" : namedAfter;
        UUID characterId = characterId(kept);
        if (characterId == null || names == null) {
            return kept;
        }
        String name;
        try {
            name = names.nameOf(characterId);
        } catch (RuntimeException unreadable) {
            name = null;
        }
        String called = player(name);
        return called.length() > 0 ? called : kept;
    }

    /** A waystone called after the character that placed it, by its name. */
    public static String player(String placerName) {
        return of(PLAYER, placerName);
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
     * Whether {@code namedAfter} is nothing, or one of the four kinds: a
     * placing character's id as {@link #character} writes it, its name of
     * printable characters, or a creature kind or a block id of letters,
     * digits and {@code _ . : -}, as the game's registries write them.
     * Only the last two ever name a lang key.
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
        if (namedAfter.startsWith(CHARACTER)) {
            return subject.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
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

    /** The placer, kind or block the reference names; empty for none. */
    public static String subject(String namedAfter) {
        String value = namedAfter == null ? "" : namedAfter;
        for (String prefix : new String[] {CHARACTER, PLAYER, ENTITY, BLOCK}) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return "";
    }

    /**
     * The name it gives in the language of the side that asks: the
     * waystone line, the game's name for the creature or the block, else
     * the subject made readable; empty for nothing. A placing character
     * the server could not name reads {@code Waystone}.
     */
    public static String shownName(String namedAfter) {
        if (!isValid(namedAfter)) {
            return "";
        }
        String subject = subject(namedAfter);
        if (subject.length() == 0) {
            return "";
        }
        if (namedAfter.startsWith(CHARACTER)) {
            return StatCollector.translateToLocal(UNKNOWN_PLACER_KEY);
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
        if (namedAfter.startsWith(CHARACTER)) {
            return new ChatComponentTranslation(UNKNOWN_PLACER_KEY);
        }
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
