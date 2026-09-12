package com.ninuna.losttales.chat.emoji;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A Unicode emoji the registry lacks is named on a chip's card as Discord
 * names it, from the bundled list, and by its code points only when the
 * list lacks it. The list's reader keeps to its bounds and skips any line
 * out of shape.
 */
public final class ChatEmojiShortcodesTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String LICENCE = "assets/losttales/emoji/shortcodes.LICENSE.txt";
    /**
     * The package and version the header names. The version itself is
     * recorded in docs/wiki/Development.md, not here.
     */
    private static final Pattern SOURCE_VERSION =
            Pattern.compile("emojibase-data \\d+\\.\\d+\\.\\d+");

    /**
     * Registry emoji the list names otherwise. The registry's frown is the
     * plain U+1F641, which Discord calls slight_frown, one of its aliases.
     */
    private static final Map<ChatEmoji, String> NAMED_OTHERWISE =
            new EnumMap<ChatEmoji, String>(ChatEmoji.class);
    /**
     * Registry names the list gives to another emoji. Discord's frowning
     * is the open-mouthed U+1F626, so a Discord member's reaction with it
     * is a '?' chip named :frowning:, as Discord names it.
     */
    private static final Map<String, String> NAME_ELSEWHERE =
            new HashMap<String, String>();

    static {
        NAMED_OTHERWISE.put(ChatEmoji.FROWNING, "slight_frown");
        NAME_ELSEWHERE.put("frowning", "1F626");
    }

    @Test
    public void theStarsAreNamedAsDiscordNamesThem() {
        String star = emoji(0x2B50);
        assertTrue("the registry has no star", ChatForeignEmoji.isUnicode(star));
        assertEquals(":star:", ChatForeignEmoji.label(star));
        assertEquals(":star2:", label(0x1F31F));
    }

    @Test
    public void aSkinToneHasItsToneName() {
        assertEquals(":thumbsup_tone3:", label(0x1F44D, 0x1F3FD));
        assertEquals(":thumbsup:", label(0x1F44D));
    }

    @Test
    public void aJoinedFamilyIsOneName() {
        assertEquals(":family_mwg:",
                label(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467));
    }

    @Test
    public void aVariationSelectorFindsTheSameName() {
        String star = emoji(0x2B50, 0xFE0F);
        assertTrue(ChatForeignEmoji.isUnicode(star));
        assertEquals("2B50", ChatEmojiShortcodes.hexcode(star));
        assertEquals(":star:", ChatForeignEmoji.label(star));
        assertEquals("a selector inside a joined emoji", ":heart_on_fire:",
                label(0x2764, 0xFE0F, 0x200D, 0x1F525));
        assertEquals(":one:", label(0x31, 0xFE0F, 0x20E3));
    }

    @Test
    public void anEmojiTheListLacksIsNamedByItsCodePoints() {
        String unlisted = emoji(0x1FAFF);
        assertTrue(ChatForeignEmoji.isUnicode(unlisted));
        assertNull(ChatEmojiShortcodes.nameOf("1FAFF"));
        assertEquals(":U+1FAFF:", ChatForeignEmoji.label(unlisted));
        assertEquals(":U+1F984-1FAFF:", label(0x1F984, 0xFE0F, 0x1FAFF));
    }

    @Test
    public void hexcodesAreSpelledAsTheListSpellsThem() {
        assertEquals("0031-20E3",
                ChatEmojiShortcodes.hexcode(emoji(0x31, 0xFE0F, 0x20E3)));
        assertEquals("1F44D-1F3FD",
                ChatEmojiShortcodes.hexcode(emoji(0x1F44D, 0x1F3FD)));
        assertEquals("", ChatEmojiShortcodes.hexcode(null));
        assertNull(ChatEmojiShortcodes.nameOf(null));
        assertNull("the list is looked up by hexcode only",
                ChatEmojiShortcodes.nameOf(emoji(0x2B50)));
    }

    @Test
    public void linesOutOfShapeAreSkipped() throws IOException {
        String sixteen = joined("1F31F", ChatForeignEmoji.MAX_UNICODE_CODE_POINTS);
        String longestName = repeat("f", ChatEmojiShortcodes.MAX_NAME_LENGTH);
        Map<String, String> names = read("# a comment\n"
                + "\n"
                + "2B50 star\n"
                + "1F984 unicorn\r\n"
                + "2B50 second_name\n"
                + "1f31f lowercase_hex\n"
                + "1F31F-FE0F selector\n"
                + "01F31F leading_zero\n"
                + "31 two_digits\n"
                + "1234567 seven_digits\n"
                + "110000 past_unicode\n"
                + "D83E surrogate\n"
                + "1F31F- trailing_dash\n"
                + "-1F31F leading_dash\n"
                + "1F31F--1F31F double_dash\n"
                + "1F31F Upper\n"
                + "1F31F with:colon\n"
                + "1F31F caf" + (char)0xE9 + "\n"
                + "1F31F two words\n"
                + "1F31F  double_space\n"
                + " 1F31F leading_space\n"
                + "1F31F\n"
                + "1F31F " + longestName + "f\n"
                + joined("1F31F", ChatForeignEmoji.MAX_UNICODE_CODE_POINTS + 1)
                + " too_many\n"
                + "1F389 " + repeat("x", ChatEmojiShortcodes.MAX_LINE_LENGTH) + "\n"
                + "1F38A confetti_ball\n"
                + "1F386 " + longestName + "\n"
                + sixteen + " sixteen\n"
                + "1F388 balloon");
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("2B50", "star");
        expected.put("1F984", "unicorn");
        expected.put("1F38A", "confetti_ball");
        expected.put("1F386", longestName);
        expected.put(sixteen, "sixteen");
        expected.put("1F388", "balloon");
        assertEquals(expected, names);
    }

    @Test
    public void readingStopsAtTheByteBound() throws IOException {
        String last = "2B50 star\n";
        Map<String, String> fits = read(
                padding(ChatEmojiShortcodes.MAX_BYTES - last.length()) + last);
        assertEquals("star", fits.get("2B50"));
        Map<String, String> over = read(
                padding(ChatEmojiShortcodes.MAX_BYTES - last.length() + 1) + last);
        assertTrue("the line the bound cuts is left out", over.isEmpty());
    }

    @Test
    public void readingStopsAtTheEntryBound() throws IOException {
        StringBuilder list = new StringBuilder();
        for (int index = 0; index <= ChatEmojiShortcodes.MAX_ENTRIES; index++) {
            list.append(hex(0x10000 + index)).append(" e").append(index)
                    .append('\n');
        }
        Map<String, String> names = read(list.toString());
        assertEquals(ChatEmojiShortcodes.MAX_ENTRIES, names.size());
        assertEquals("e0", names.get("10000"));
        assertNull(names.get(hex(0x10000 + ChatEmojiShortcodes.MAX_ENTRIES)));
    }

    @Test
    public void aMissingListNamesNothing() {
        assertTrue(ChatEmojiShortcodes.load(
                "assets/losttales/emoji/no_such_list.txt").isEmpty());
    }

    @Test
    public void theBundledListLoadsWithinItsBounds() throws IOException {
        byte[] bytes = resource(ChatEmojiShortcodes.RESOURCE);
        assertTrue("the list is past its byte bound",
                bytes.length <= ChatEmojiShortcodes.MAX_BYTES);
        for (byte value : bytes) {
            assertTrue("the list is ASCII", value >= 0);
        }
        String text = new String(bytes, UTF_8);
        int entries = 0;
        for (String line : text.split("\n")) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            assertTrue("a line past the bound: " + line,
                    line.length() <= ChatEmojiShortcodes.MAX_LINE_LENGTH);
            if (line.length() > 0 && line.charAt(0) != '#') {
                entries++;
            }
        }
        assertTrue(entries > 0);
        assertTrue(entries <= ChatEmojiShortcodes.MAX_ENTRIES);
        assertEquals("every line of the list is read", entries,
                ChatEmojiShortcodes.names().size());
        assertTrue("the header names the source",
                SOURCE_VERSION.matcher(text).find()
                        && text.contains("en/shortcodes/joypixels.json")
                        && text.contains("MIT"));
        String licence = new String(resource(LICENCE), UTF_8);
        assertTrue("the licence ships beside the list",
                licence.startsWith("MIT License"));
    }

    /**
     * Where the list has a registry emoji, it names it as the registry
     * does, so a name means one emoji on both sides of the bridge.
     */
    @Test
    public void theListNamesTheRegistrysEmojiAsTheRegistryDoes() {
        int compared = 0;
        for (ChatEmoji emoji : ChatEmoji.values()) {
            if (emoji.getUnicode().length() == 0) {
                continue;
            }
            String listed = ChatEmojiShortcodes.nameOf(
                    ChatEmojiShortcodes.hexcode(emoji.getUnicode()));
            if (listed == null) {
                continue;
            }
            compared++;
            String expected = NAMED_OTHERWISE.containsKey(emoji)
                    ? NAMED_OTHERWISE.get(emoji) : emoji.getName();
            assertEquals(emoji + " is named otherwise by the list",
                    expected, listed);
            assertSame("the list's name resolves to " + emoji, emoji,
                    ChatEmoji.fromInputName(listed));
        }
        assertTrue(compared > 0);
        for (Map.Entry<ChatEmoji, String> exception : NAMED_OTHERWISE.entrySet()) {
            assertEquals("an exception the list no longer needs",
                    exception.getValue(), ChatEmojiShortcodes.nameOf(
                            ChatEmojiShortcodes.hexcode(
                                    exception.getKey().getUnicode())));
        }
    }

    /**
     * A name the registry uses, as a name or an alias, is the registry's
     * emoji in the list too, so no '?' chip wears the name of a sprite.
     */
    @Test
    public void noOtherEmojiInTheListWearsARegistryName() {
        Map<String, String> byName = new HashMap<String, String>();
        for (Map.Entry<String, String> entry
                : ChatEmojiShortcodes.names().entrySet()) {
            byName.put(entry.getValue(), entry.getKey());
            ChatEmoji emoji = ChatEmoji.fromInputName(entry.getValue());
            if (emoji == null || NAME_ELSEWHERE.containsKey(entry.getValue())) {
                continue;
            }
            assertEquals(entry.getValue() + " is another emoji in the list",
                    ChatEmojiShortcodes.hexcode(emoji.getUnicode()),
                    entry.getKey());
        }
        for (Map.Entry<String, String> exception : NAME_ELSEWHERE.entrySet()) {
            assertEquals("an exception the list no longer needs",
                    exception.getValue(), byName.get(exception.getKey()));
        }
    }

    private static String emoji(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    private static String label(int... codePoints) {
        return ChatForeignEmoji.label(emoji(codePoints));
    }

    private static Map<String, String> read(String list) throws IOException {
        return ChatEmojiShortcodes.read(
                new ByteArrayInputStream(list.getBytes(UTF_8)));
    }

    /** A comment line of exactly {@code bytes} bytes, its line break included. */
    private static String padding(int bytes) {
        return "#" + repeat("x", bytes - 2) + "\n";
    }

    private static String hex(int codePoint) {
        return Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
    }

    private static String joined(String hexcode, int times) {
        StringBuilder builder = new StringBuilder(hexcode);
        for (int index = 1; index < times; index++) {
            builder.append('-').append(hexcode);
        }
        return builder.toString();
    }

    private static byte[] resource(String path) throws IOException {
        InputStream input = ChatEmojiShortcodesTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(path + " is missing", input);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        } finally {
            input.close();
        }
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < times; index++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
