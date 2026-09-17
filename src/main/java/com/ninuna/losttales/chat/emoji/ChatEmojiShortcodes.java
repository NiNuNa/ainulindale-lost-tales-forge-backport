package com.ninuna.losttales.chat.emoji;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Discord's names for Unicode emoji, from the bundled list
 * {@code assets/losttales/chat/emoji/shortcodes.txt}. Discord sends a Unicode
 * emoji as its characters only, never by name; this list gives a chip
 * the name Discord shows, {@code :star:} for U+2B50.
 *
 * <p>The list is emojibase's JoyPixels shortcodes, whose names are the
 * ones Discord uses. A line is a hexcode, a space and the name. The
 * hexcode is the emoji's code points in uppercase hex, each at least
 * four digits, joined by {@code -}, with the variation selector U+FE0F
 * left out; the name is the entry's first. Blank lines and lines opening
 * with {@code #} are passed over.</p>
 *
 * <p>The list is read through the class loader the first time a name is
 * asked for, once, within bounds on its size, its lines and its entries.
 * A line out of shape is skipped. A list that is missing or unreadable
 * is logged once and names nothing, so every emoji keeps its code
 * points. Free of Minecraft imports, so a dedicated server loads it.</p>
 */
public final class ChatEmojiShortcodes {
    /** Where the list lies on the class path. */
    static final String RESOURCE = "assets/losttales/chat/emoji/shortcodes.txt";
    /** Bytes read at most; the bundled list is about half as long. */
    static final int MAX_BYTES = 256 * 1024;
    /** Characters in one line, its line break not counted. */
    static final int MAX_LINE_LENGTH = 192;
    /** Entries kept at most; about twice what the bundled list holds. */
    static final int MAX_ENTRIES = 8192;
    /** Characters in one name. */
    static final int MAX_NAME_LENGTH = 64;

    private static final int VARIATION_SELECTOR = 0xFE0F;
    private static final int MIN_HEX_DIGITS = 4;
    private static final int MAX_HEX_DIGITS = 6;

    private ChatEmojiShortcodes() {}

    /** The name the list gives the emoji with {@code hexcode}, or null. */
    public static String nameOf(String hexcode) {
        return hexcode == null ? null : Holder.NAMES.get(hexcode);
    }

    /**
     * The code points of {@code unicode} as the list writes them:
     * uppercase hex padded to four digits, joined by {@code -}, the
     * variation selector left out.
     */
    public static String hexcode(String unicode) {
        StringBuilder hexcode = new StringBuilder();
        if (unicode == null) {
            return "";
        }
        for (int index = 0; index < unicode.length(); ) {
            int codePoint = unicode.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == VARIATION_SELECTOR) {
                continue;
            }
            if (hexcode.length() > 0) {
                hexcode.append('-');
            }
            String hex = Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
            for (int pad = hex.length(); pad < MIN_HEX_DIGITS; pad++) {
                hexcode.append('0');
            }
            hexcode.append(hex);
        }
        return hexcode.toString();
    }

    /** Every entry of the bundled list, hexcode to name. */
    static Map<String, String> names() {
        return Holder.NAMES;
    }

    /**
     * The list at {@code resource} on the class path, or an empty map
     * when it is missing or unreadable. Never throws: the one warning is
     * all a failure costs.
     */
    static Map<String, String> load(String resource) {
        InputStream input = null;
        try {
            input = ChatEmojiShortcodes.class.getClassLoader()
                    .getResourceAsStream(resource);
            if (input == null) {
                warn("Emoji name list %s is missing; emoji the game lacks are named by their code points",
                        resource);
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(read(input));
        } catch (IOException e) {
            warn("Emoji name list %s could not be read; emoji the game lacks are named by their code points: %s",
                    resource, e);
        } catch (RuntimeException e) {
            warn("Emoji name list %s could not be read; emoji the game lacks are named by their code points: %s",
                    resource, e);
        } finally {
            closeAfterReading(input);
        }
        return Collections.emptyMap();
    }

    /**
     * Reads a list from {@code input}. A line out of shape is skipped, and
     * a hexcode keeps the first name it is given. Reading stops after
     * {@link #MAX_BYTES} bytes, where the line cut short is left out, and
     * at {@link #MAX_ENTRIES} entries. Carriage returns are passed over,
     * so a checkout with Windows line ends reads the same.
     */
    static Map<String, String> read(InputStream input) throws IOException {
        Map<String, String> names = new HashMap<String, String>();
        InputStream buffered = new BufferedInputStream(input);
        StringBuilder line = new StringBuilder(MAX_LINE_LENGTH);
        boolean overlong = false;
        int bytes = 0;
        while (names.size() < MAX_ENTRIES) {
            int next = buffered.read();
            if (next >= 0 && ++bytes > MAX_BYTES) {
                break;
            }
            if (next < 0 || next == '\n') {
                if (!overlong) {
                    addEntry(line.toString(), names);
                }
                if (next < 0) {
                    break;
                }
                line.setLength(0);
                overlong = false;
            } else if (next != '\r') {
                if (line.length() < MAX_LINE_LENGTH) {
                    line.append((char)next);
                } else {
                    overlong = true;
                }
            }
        }
        return names;
    }

    private static void addEntry(String line, Map<String, String> names) {
        if (line.length() == 0 || line.charAt(0) == '#') {
            return;
        }
        int space = line.indexOf(' ');
        if (space <= 0 || line.indexOf(' ', space + 1) >= 0) {
            return;
        }
        String hexcode = line.substring(0, space);
        String name = line.substring(space + 1);
        if (isName(name) && isHexcode(hexcode) && !names.containsKey(hexcode)) {
            names.put(hexcode, name);
        }
    }

    /** Lowercase letters, digits, {@code _} and {@code -}, within the bound. */
    private static boolean isName(String name) {
        if (name.length() == 0 || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code hexcode} is one emoji spelled exactly as
     * {@link #hexcode(String)} spells it: no more code points than a key
     * holds, no variation selector, no surrogate, no extra leading zero.
     */
    private static boolean isHexcode(String hexcode) {
        int[] codePoints = new int[ChatForeignEmoji.MAX_UNICODE_CODE_POINTS];
        int count = 0;
        int start = 0;
        while (true) {
            int end = hexcode.indexOf('-', start);
            if (end < 0) {
                end = hexcode.length();
            }
            int codePoint = parseCodePoint(hexcode, start, end);
            if (codePoint < 0 || count == codePoints.length) {
                return false;
            }
            codePoints[count++] = codePoint;
            if (end == hexcode.length()) {
                break;
            }
            start = end + 1;
        }
        return hexcode.equals(hexcode(new String(codePoints, 0, count)));
    }

    /** One code point in four to six uppercase hex digits, or -1. */
    private static int parseCodePoint(String text, int start, int end) {
        if (end - start < MIN_HEX_DIGITS || end - start > MAX_HEX_DIGITS) {
            return -1;
        }
        int value = 0;
        for (int index = start; index < end; index++) {
            char character = text.charAt(index);
            int digit;
            if (character >= '0' && character <= '9') {
                digit = character - '0';
            } else if (character >= 'A' && character <= 'F') {
                digit = character - 'A' + 10;
            } else {
                return -1;
            }
            value = value * 16 + digit;
        }
        if (value > Character.MAX_CODE_POINT
                || (value >= Character.MIN_SURROGATE
                        && value <= Character.MAX_SURROGATE)) {
            return -1;
        }
        return value;
    }

    private static void closeAfterReading(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // The list is read by then; a failed close loses nothing.
        }
    }

    private static void warn(String format, Object... args) {
        Object[] values = new Object[args.length + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        System.arraycopy(args, 0, values, 1, args.length);
        try {
            FMLLog.warning("[%s] " + format, values);
        } catch (RuntimeException ignored) {
            // FML's logger is not set up in runtime-free unit tests.
        }
    }

    /** The bundled list, read once, the first time a name is asked for. */
    private static final class Holder {
        static final Map<String, String> NAMES = load(RESOURCE);
    }
}
