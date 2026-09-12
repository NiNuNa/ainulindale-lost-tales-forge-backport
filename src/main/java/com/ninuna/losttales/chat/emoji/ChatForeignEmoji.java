package com.ninuna.losttales.chat.emoji;

import com.ninuna.losttales.chat.ChatReactionSummary;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Reaction keys: how a reaction names its emoji on the wire, in the save
 * and in a chip. An emoji of the registry is its canonical name. An
 * emoji the registry does not carry, which only a Discord member can
 * bring, is a <em>foreign</em> key made of what Discord calls it:
 * {@code name:id} for a custom emoji, the Unicode itself for any other.
 *
 * <p>A foreign key is never a registry name. Registry names are
 * lowercase ASCII; a custom key holds a colon, and a Unicode key holds
 * at least one character past ASCII. One Discord emoji has exactly one
 * key, so an add and a removal meet: a custom emoji named as a registry
 * name, and a Unicode emoji the registry carries, are the registry's
 * and have no foreign key.</p>
 *
 * <p>A Unicode key is shown by the name Discord gives the emoji,
 * {@code :unicorn:} for U+1F984, from the bundled list in
 * {@link ChatEmojiShortcodes}: Discord sends such an emoji without a
 * name, and the game's font cannot draw most of them. An emoji newer
 * than the list is shown by its code points, {@code :U+1FAFF:}. Free of
 * Minecraft imports, so a dedicated server loads it.</p>
 */
public final class ChatForeignEmoji {
    /** A custom emoji's name on Discord: two to thirty-two of these. */
    public static final int MIN_CUSTOM_NAME_LENGTH = 2;
    public static final int MAX_CUSTOM_NAME_LENGTH = 32;
    /** A Discord id's digits; a snowflake fits in twenty. */
    public static final int MAX_CUSTOM_ID_DIGITS = 20;
    /**
     * Code points in one Unicode emoji. The longest Discord sends — a
     * couple with skin tones, a subdivision flag — stay near ten.
     */
    public static final int MAX_UNICODE_CODE_POINTS = 16;

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int VARIATION_SELECTOR = 0xFE0F;
    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int KEYCAP = 0x20E3;

    private ChatForeignEmoji() {}

    /** Whether {@code key} names a reaction's emoji: a registry name or a foreign key. */
    public static boolean isReactionKey(String key) {
        return ChatEmoji.fromName(key) != null || isForeign(key);
    }

    /** Whether {@code key} is a well-formed foreign key of either kind. */
    public static boolean isForeign(String key) {
        return isCustom(key) || isUnicode(key);
    }

    /** Whether {@code key} is a custom emoji's {@code name:id}. */
    public static boolean isCustom(String key) {
        if (key == null) {
            return false;
        }
        int colon = key.indexOf(':');
        return colon > 0 && key.equals(customKey(key.substring(0, colon),
                key.substring(colon + 1)));
    }

    /** Whether {@code key} is a Unicode emoji the registry does not carry. */
    public static boolean isUnicode(String key) {
        return key != null && key.equals(unicodeKey(key));
    }

    /**
     * The key of a custom emoji by the name and id Discord gives it, or
     * null: for a name or an id out of Discord's shape, and for a name
     * the registry resolves, whose emoji is the registry's.
     */
    public static String customKey(String name, String id) {
        if (!isCustomName(name) || !isSnowflake(id)
                || ChatEmoji.fromInputName(name.toLowerCase(Locale.ROOT)) != null) {
            return null;
        }
        return name + ':' + id;
    }

    /**
     * The key of a Unicode emoji exactly as Discord sends it, or null:
     * for text that is not one emoji, one past the bounds, and one the
     * registry carries.
     */
    public static String unicodeKey(String unicode) {
        if (unicode == null || unicode.length() == 0
                || unicode.getBytes(UTF_8).length
                        > ChatReactionSummary.MAX_EMOJI_BYTES
                || !isEmojiSequence(unicode)
                || registryEmojiOf(unicode) != null) {
            return null;
        }
        return unicode;
    }

    /**
     * The key a reaction read back from the save is kept under, or null
     * for none. A key that is a reaction key today stays as it is. A key
     * saved as foreign whose emoji the registry has come to carry since
     * — its Unicode, or a custom emoji's name as a name or an alias — is
     * that emoji's registry name, so the registry growing never makes a
     * saved reaction unreadable, and one emoji keeps one key. Anything
     * out of a key's shape is null.
     */
    public static String restoredKey(String key) {
        if (key == null || key.length() == 0) {
            return null;
        }
        if (isReactionKey(key)) {
            return key;
        }
        ChatEmoji known;
        int colon = key.indexOf(':');
        if (colon > 0) {
            String name = key.substring(0, colon);
            if (!isCustomName(name) || !isSnowflake(key.substring(colon + 1))) {
                return null;
            }
            known = ChatEmoji.fromInputName(name.toLowerCase(Locale.ROOT));
        } else {
            if (key.getBytes(UTF_8).length > ChatReactionSummary.MAX_EMOJI_BYTES
                    || !isEmojiSequence(key)) {
                return null;
            }
            known = registryEmojiOf(key);
        }
        return known == null ? null : known.getName();
    }

    /**
     * Whether {@code id} is a custom emoji's Discord id as a key holds
     * it: digits with no leading zero, within a snowflake's length.
     */
    public static boolean isCustomId(String id) {
        return isSnowflake(id);
    }

    /**
     * The registry emoji {@code unicode} is in full, or null. Trailing
     * variation selectors are ignored, since Discord adds them to some
     * emoji and not others.
     */
    public static ChatEmoji registryEmojiOf(String unicode) {
        ChatEmoji.UnicodeMatch match = ChatEmoji.matchUnicode(unicode, 0);
        if (match == null) {
            return null;
        }
        int end = match.length;
        while (end < unicode.length()
                && unicode.charAt(end) == VARIATION_SELECTOR) {
            end++;
        }
        return end == unicode.length() ? match.emoji : null;
    }

    /**
     * How a chip's card names the emoji, between colons: a registry
     * emoji's shortcode, a custom emoji's Discord name, and a Unicode
     * emoji's Discord name from the bundled list. A Unicode emoji the
     * list lacks is named by its code points in hex, {@code :U+1FAFF:},
     * variation selectors left out. Empty for anything that is not a key.
     * A display name only: the key stays what the wire and the save hold.
     */
    public static String label(String key) {
        ChatEmoji known = ChatEmoji.fromName(key);
        if (known != null) {
            return known.getShortcode();
        }
        if (isCustom(key)) {
            return ':' + key.substring(0, key.indexOf(':')) + ':';
        }
        if (!isUnicode(key)) {
            return "";
        }
        String hexcode = ChatEmojiShortcodes.hexcode(key);
        String name = ChatEmojiShortcodes.nameOf(hexcode);
        return name != null ? ':' + name + ':' : ":U+" + hexcode + ':';
    }

    /**
     * The emoji as Discord's reaction endpoints name it: a registry
     * emoji's Unicode form, empty for the mod's own sprites that have
     * none; a foreign key as it stands. Empty for anything else.
     */
    public static String discordForm(String key) {
        ChatEmoji known = ChatEmoji.fromName(key);
        if (known != null) {
            return known.getUnicode();
        }
        return isForeign(key) ? key : "";
    }

    private static boolean isCustomName(String name) {
        if (name == null || name.length() < MIN_CUSTOM_NAME_LENGTH
                || name.length() > MAX_CUSTOM_NAME_LENGTH) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '_') {
                return false;
            }
        }
        return true;
    }

    /** Digits with no leading zero, so one id has one spelling. */
    private static boolean isSnowflake(String id) {
        if (id == null || id.length() == 0
                || id.length() > MAX_CUSTOM_ID_DIGITS || id.charAt(0) == '0') {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * One emoji as code points: a pictograph first — or a digit, # or *
     * when a keycap mark follows — then pictographs, joiners, variation
     * selectors, the keycap mark and tag characters. Nothing else passes:
     * no letters, spaces, controls, bidirectional marks or lone halves
     * of a surrogate pair.
     */
    private static boolean isEmojiSequence(String text) {
        boolean keycap = text.indexOf(KEYCAP) >= 0;
        int count = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            count++;
            if (count > MAX_UNICODE_CODE_POINTS) {
                return false;
            }
            if (count == 1) {
                if (!isPictograph(codePoint)
                        && !(keycap && isKeycapBase(codePoint))) {
                    return false;
                }
            } else if (!isPictograph(codePoint) && !isJoining(codePoint)) {
                return false;
            }
        }
        return count > 0;
    }

    /** The blocks and symbols Unicode's emoji are drawn from. */
    private static boolean isPictograph(int codePoint) {
        return codePoint == 0x00A9 || codePoint == 0x00AE
                || codePoint == 0x203C || codePoint == 0x2049
                || codePoint == 0x2122 || codePoint == 0x2139
                || (codePoint >= 0x2194 && codePoint <= 0x21AA)
                || (codePoint >= 0x231A && codePoint <= 0x23FF)
                || codePoint == 0x24C2
                || (codePoint >= 0x25AA && codePoint <= 0x27BF)
                || (codePoint >= 0x2934 && codePoint <= 0x2935)
                || (codePoint >= 0x2B05 && codePoint <= 0x2B55)
                || codePoint == 0x3030 || codePoint == 0x303D
                || codePoint == 0x3297 || codePoint == 0x3299
                || (codePoint >= 0x1F000 && codePoint <= 0x1FAFF);
    }

    /** What joins, styles or tags a pictograph within one emoji. */
    private static boolean isJoining(int codePoint) {
        return codePoint == ZERO_WIDTH_JOINER
                || codePoint == VARIATION_SELECTOR
                || codePoint == KEYCAP
                || (codePoint >= 0xE0020 && codePoint <= 0xE007F);
    }

    private static boolean isKeycapBase(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9')
                || codePoint == '#' || codePoint == '*';
    }
}
