package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The light markup a message may be typed with, as the chat shows it:
 * {@code **bold**}, {@code *italic*} or {@code _italic_},
 * {@code __underlined__}, {@code ~~struck~~}, {@code `code`} and
 * {@code ||spoiler||} — the inline marks Discord reads the same way, so
 * a message reads alike on both sides of the bridge without being
 * rewritten for either.
 *
 * <p>Display only. The wire, the copy text and the log keep exactly what
 * was typed, markers and all, so nothing here can change what a message
 * <em>is</em> — only how it reads. Anything that is not a complete pair
 * stays the literal characters it was typed as, which is what makes the
 * markup safe to apply to text nobody wrote for it.</p>
 *
 * <p>A marker only opens when something follows it directly and only
 * closes when something precedes it directly, so arithmetic and
 * ordinary punctuation are left alone: {@code 2 * 3 * 4} is a sum, not
 * an italic. A single underscore also has to stand at a word's edge, as
 * Discord reads one: it opens only where no letter, digit or underscore
 * stands before it and closes only where none stands after it, so
 * {@code snake_case} and a name like {@code Cool_Player_1} keep theirs.
 * Code spans are literal inside — the point of quoting something is
 * that it is not read again — and every other span nests.</p>
 *
 * <p>Free of Minecraft imports, so it is testable without a game
 * runtime.</p>
 */
public final class ChatMarkdown {
    /** How deep spans may nest before the rest is left literal. */
    private static final int MAX_DEPTH = 8;
    private static final String[] DELIMITERS = {
            "**", "__", "~~", "||", "*", "_", "`" };

    private ChatMarkdown() {}

    /** The message as styled runs, in order; never empty for real text. */
    public static List<Span> parse(String message) {
        List<Span> spans = new ArrayList<Span>();
        if (message == null || message.length() == 0) {
            return spans;
        }
        scan(message, 0, Span.PLAIN, 0, spans, null);
        return spans;
    }

    /**
     * The style of every character of the message <em>as typed</em>: the
     * marks each character is shown with, and {@link Span#MARK} on the
     * marker characters themselves, which {@link #parse} drops. What the
     * input field draws its live preview from, laid out by the same scan
     * the chat line is, so the two can never disagree about which
     * characters are markup. Every character keeps its place: the array
     * is as long as the message.
     */
    public static int[] layout(String message) {
        int[] styles = new int[message == null ? 0 : message.length()];
        if (styles.length > 0) {
            scan(message, 0, Span.PLAIN, 0, null, styles);
        }
        return styles;
    }

    /** Whether the text carries any markup at all; a quick way out. */
    public static boolean hasMarkup(String message) {
        if (message == null) {
            return false;
        }
        for (int index = 0; index < DELIMITERS.length; index++) {
            if (message.indexOf(DELIMITERS[index]) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * One scan serves both readings: {@code spans} collects the runs
     * without their markers, {@code styles} records every character's
     * style at its place in the whole message, {@code base} away from
     * this text's start. Either may be null.
     */
    private static void scan(String text, int base, int style, int depth,
                             List<Span> spans, int[] styles) {
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            String delimiter = depth >= MAX_DEPTH ? null
                    : openerAt(text, cursor, style);
            int close = delimiter == null ? -1
                    : closerOf(text, cursor + delimiter.length(),
                            delimiter);
            if (close < 0) {
                cursor++;
                continue;
            }
            if (literalStart < cursor) {
                add(spans, styles, text, literalStart, cursor, base, style);
            }
            int innerStart = cursor + delimiter.length();
            mark(styles, base + cursor, delimiter.length(), style);
            mark(styles, base + close, delimiter.length(), style);
            String inner = text.substring(innerStart, close);
            int nested = style | styleOf(delimiter);
            if ((nested & Span.CODE) != 0) {
                // Quoted text is quoted: nothing inside it is markup.
                add(spans, styles, text, innerStart, close, base, nested);
            } else {
                scan(inner, base + innerStart, nested, depth + 1, spans,
                        styles);
            }
            cursor = close + delimiter.length();
            literalStart = cursor;
        }
        if (literalStart < text.length()) {
            add(spans, styles, text, literalStart, text.length(), base,
                    style);
        }
    }

    /** A marker's characters: shown as typed, in the style around them. */
    private static void mark(int[] styles, int at, int length, int style) {
        if (styles == null) {
            return;
        }
        for (int index = at; index < at + length && index < styles.length;
                index++) {
            styles[index] = style | Span.MARK;
        }
    }

    /**
     * The marker opening at {@code index}, or null. A marker whose style
     * is already in force is not one — the closer of the span this text
     * is inside cannot open another of the same kind.
     */
    private static String openerAt(String text, int index, int style) {
        for (int which = 0; which < DELIMITERS.length; which++) {
            String delimiter = DELIMITERS[which];
            if (!text.startsWith(delimiter, index)
                    || (style & styleOf(delimiter)) != 0) {
                continue;
            }
            int after = index + delimiter.length();
            if (after < text.length()
                    && !Character.isWhitespace(text.charAt(after))
                    && (!"_".equals(delimiter)
                            || opensUnderscore(text, index))) {
                return delimiter;
            }
        }
        return null;
    }

    /**
     * Where the marker closes, or -1: it must follow real text.
     *
     * <p>A two-character marker closes at the <em>end</em> of the run of
     * that character, not its start, so the marks that share a character
     * unwind in the order they were opened: the three asterisks ending
     * {@code **bold *and italic***} close the italic first and the bold
     * after it, rather than the bold swallowing the italic's own.</p>
     */
    private static int closerOf(String text, int from, String delimiter) {
        int at = from;
        while (at < text.length()) {
            at = text.indexOf(delimiter, at);
            if (at < 0) {
                return -1;
            }
            if (at > from && !Character.isWhitespace(text.charAt(at - 1))
                    && (!"_".equals(delimiter)
                            || closesUnderscore(text, at))) {
                if (delimiter.length() > 1) {
                    char mark = delimiter.charAt(0);
                    while (at + delimiter.length() < text.length()
                            && text.charAt(at + delimiter.length())
                                    == mark) {
                        at++;
                    }
                }
                return at;
            }
            at += delimiter.length();
        }
        return -1;
    }

    /**
     * Whether a single underscore at {@code index} may open an italic: at
     * the start of a word, with no letter, digit or underscore before it,
     * and not the first of two.
     */
    private static boolean opensUnderscore(String text, int index) {
        return (index == 0 || !isWordCharacter(text.charAt(index - 1)))
                && index + 1 < text.length()
                && text.charAt(index + 1) != '_';
    }

    /**
     * Whether a single underscore at {@code index} may close an italic: at
     * the end of a word, with no underscore before it and no letter, digit
     * or underscore after it.
     */
    private static boolean closesUnderscore(String text, int index) {
        return text.charAt(index - 1) != '_'
                && (index + 1 >= text.length()
                        || !isWordCharacter(text.charAt(index + 1)));
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static int styleOf(String delimiter) {
        if ("**".equals(delimiter)) {
            return Span.BOLD;
        }
        if ("__".equals(delimiter)) {
            return Span.UNDERLINE;
        }
        if ("~~".equals(delimiter)) {
            return Span.STRIKETHROUGH;
        }
        if ("||".equals(delimiter)) {
            return Span.SPOILER;
        }
        return "`".equals(delimiter) ? Span.CODE : Span.ITALIC;
    }

    private static void add(List<Span> spans, int[] styles, String text,
                            int from, int to, int base, int style) {
        if (from >= to) {
            return;
        }
        if (spans != null) {
            spans.add(new Span(text.substring(from, to), style));
        }
        if (styles != null) {
            for (int index = from; index < to; index++) {
                styles[base + index] = style;
            }
        }
    }

    /** One run of the message and the marks it is shown with. */
    public static final class Span {
        public static final int PLAIN = 0;
        public static final int BOLD = 1;
        public static final int ITALIC = 2;
        public static final int STRIKETHROUGH = 4;
        public static final int CODE = 8;
        public static final int SPOILER = 16;
        public static final int UNDERLINE = 32;
        /**
         * A marker character itself, in {@link ChatMarkdown#layout}
         * only: shown as typed while the message is edited, gone once
         * it is sent. Never set on a parsed span.
         */
        public static final int MARK = 64;

        private final String text;
        private final int style;

        Span(String text, int style) {
            this.text = text;
            this.style = style;
        }

        /** The run as it is shown: the markers are gone from it. */
        public String getText() {
            return this.text;
        }

        public boolean isBold() {
            return (this.style & BOLD) != 0;
        }

        public boolean isItalic() {
            return (this.style & ITALIC) != 0;
        }

        public boolean isStrikethrough() {
            return (this.style & STRIKETHROUGH) != 0;
        }

        public boolean isUnderlined() {
            return (this.style & UNDERLINE) != 0;
        }

        public boolean isCode() {
            return (this.style & CODE) != 0;
        }

        public boolean isSpoiler() {
            return (this.style & SPOILER) != 0;
        }

        /** Whether the run is shown exactly as it was typed. */
        public boolean isPlain() {
            return this.style == PLAIN;
        }
    }

    /** A message with no markup at all, as one plain run. */
    public static List<Span> plain(String message) {
        return message == null || message.length() == 0
                ? Collections.<Span>emptyList()
                : Collections.singletonList(new Span(message, Span.PLAIN));
    }
}
