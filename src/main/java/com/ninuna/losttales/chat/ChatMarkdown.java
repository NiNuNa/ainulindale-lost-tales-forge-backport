package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The light markup a message may be typed with, as the chat shows it:
 * {@code **bold**}, {@code *italic*}, {@code ~~struck~~},
 * {@code `code`} and {@code ||spoiler||}.
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
 * an italic. Code spans are literal inside — the point of quoting
 * something is that it is not read again — and every other span nests.</p>
 *
 * <p>Free of Minecraft imports, so it is testable without a game
 * runtime.</p>
 */
public final class ChatMarkdown {
    /** How deep spans may nest before the rest is left literal. */
    private static final int MAX_DEPTH = 8;
    private static final String[] DELIMITERS = {
            "**", "~~", "||", "*", "`" };

    private ChatMarkdown() {}

    /** The message as styled runs, in order; never empty for real text. */
    public static List<Span> parse(String message) {
        List<Span> spans = new ArrayList<Span>();
        if (message == null || message.length() == 0) {
            return spans;
        }
        scan(message, Span.PLAIN, 0, spans);
        return spans;
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

    private static void scan(String text, int style, int depth,
                             List<Span> spans) {
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
                add(spans, text.substring(literalStart, cursor), style);
            }
            String inner = text.substring(cursor + delimiter.length(),
                    close);
            int nested = style | styleOf(delimiter);
            if ((nested & Span.CODE) != 0) {
                // Quoted text is quoted: nothing inside it is markup.
                add(spans, inner, nested);
            } else {
                scan(inner, nested, depth + 1, spans);
            }
            cursor = close + delimiter.length();
            literalStart = cursor;
        }
        if (literalStart < text.length()) {
            add(spans, text.substring(literalStart), style);
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
                    && !Character.isWhitespace(text.charAt(after))) {
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
            if (at > from && !Character.isWhitespace(text.charAt(at - 1))) {
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

    private static int styleOf(String delimiter) {
        if ("**".equals(delimiter)) {
            return Span.BOLD;
        }
        if ("~~".equals(delimiter)) {
            return Span.STRIKETHROUGH;
        }
        if ("||".equals(delimiter)) {
            return Span.SPOILER;
        }
        return "`".equals(delimiter) ? Span.CODE : Span.ITALIC;
    }

    private static void add(List<Span> spans, String text, int style) {
        if (text.length() > 0) {
            spans.add(new Span(text, style));
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
