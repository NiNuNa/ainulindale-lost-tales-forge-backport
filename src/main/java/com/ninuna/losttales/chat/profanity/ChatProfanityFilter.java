package com.ninuna.losttales.chat.profanity;

import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import java.util.List;
import java.util.Locale;

/**
 * Replaces the words on a {@link ChatProfanityWords} list in a run of
 * text, the way King Arthur's Gold does: {@code fuck} reads {@code flip}
 * and {@code shit} reads {@code poop} in {@link ChatProfanityMode#SILLY},
 * and {@code f***} and {@code s***} in {@link ChatProfanityMode#STARS}.
 *
 * <p>A word is a run of letters, matched whole: {@code Scunthorpe} and
 * {@code assassin} stay as they are. Stretched letters are caught
 * ({@code fuuuck}), and so are the common endings — {@code fucking},
 * {@code shits}, {@code bitches}, {@code asshole} — which the silly word
 * takes over with English spelling ({@code flipping}, {@code poops},
 * {@code witches}, {@code bumhole}). The word's case is kept:
 * {@code Fuck} reads {@code Flip} and {@code FUCK} reads {@code FLIP}.
 * Stars keep the word's first letter and its length.</p>
 *
 * <p>Display only: the wire, the history, copies and the audit log keep
 * what was typed. {@link #filter} is for a run of plain words, once the
 * chat has split the message's tokens, emojis, links and mentions out;
 * {@link #filterMessage} is for a whole message as typed, and leaves
 * those alone itself. Free of Minecraft imports.</p>
 */
public final class ChatProfanityFilter {
    /**
     * The endings a listed word may carry, longest first so that
     * {@code es} is tried before {@code s} and {@code heads} before
     * {@code s}.
     */
    private static final String[] ENDINGS = {
            "heads", "holes", "faces", "iest", "head", "hole", "face",
            "ies", "ing", "ers", "ier", "es", "ed", "er", "in", "s", "y"};

    private ChatProfanityFilter() {}

    /**
     * The text with every listed word replaced as the mode says; the
     * text itself when the mode is off, the list is empty or nothing is
     * listed in it.
     */
    public static String filter(String text, ChatProfanityMode mode,
                                ChatProfanityWords words) {
        if (text == null) {
            return "";
        }
        if (mode == null || mode == ChatProfanityMode.OFF || words == null
                || words.isEmpty()) {
            return text;
        }
        StringBuilder out = null;
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            if (!Character.isLetter(text.charAt(cursor))) {
                cursor++;
                continue;
            }
            int end = cursor + 1;
            while (end < text.length() && Character.isLetter(text.charAt(end))) {
                end++;
            }
            String replacement = replacementFor(text.substring(cursor, end),
                    mode, words);
            if (replacement != null) {
                if (out == null) {
                    out = new StringBuilder(text.length() + 16);
                }
                out.append(text, literalStart, cursor).append(replacement);
                literalStart = end;
            }
            cursor = end;
        }
        if (out == null) {
            return text;
        }
        return out.append(text, literalStart, text.length()).toString();
    }

    /**
     * {@link #filter} over a message as typed, leaving alone what is not
     * words: share tokens, emoji shortcodes, web addresses, mentions,
     * channel links and quoted {@code `code`}.
     */
    public static String filterMessage(String message, ChatProfanityMode mode,
                                       ChatProfanityWords words) {
        if (message == null) {
            return "";
        }
        if (mode == null || mode == ChatProfanityMode.OFF || words == null
                || words.isEmpty()) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 16);
        List<ChatShareTokenParser.Token> tokens = ChatShareTokenParser.parse(message);
        int literalStart = 0;
        for (int index = 0; index < tokens.size(); index++) {
            ChatShareTokenParser.Token token = tokens.get(index);
            appendOutsideEmojis(out, message.substring(literalStart, token.start),
                    mode, words);
            out.append(message, token.start, token.end);
            literalStart = token.end;
        }
        appendOutsideEmojis(out, message.substring(literalStart), mode, words);
        return out.toString();
    }

    /** Whether the text holds a listed word at all. */
    public static boolean hasListedWord(String text, ChatProfanityWords words) {
        return words != null && !words.isEmpty()
                && !filter(text, ChatProfanityMode.STARS, words).equals(
                        text == null ? "" : text);
    }

    private static void appendOutsideEmojis(StringBuilder out, String text,
                                            ChatProfanityMode mode,
                                            ChatProfanityWords words) {
        for (ChatEmojiParser.Segment segment : ChatEmojiParser.split(text)) {
            if (segment.isEmoji()) {
                out.append(segment.getEmoji().getShortcode());
            } else {
                appendOutsideCode(out, segment.getText(), mode, words);
            }
        }
    }

    /**
     * Quoted text is quoted: what stands between a pair of backticks —
     * one opening against text and one closing against text, as the
     * chat's markup reads them — is left as typed.
     */
    private static void appendOutsideCode(StringBuilder out, String text,
                                          ChatProfanityMode mode,
                                          ChatProfanityWords words) {
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int open = text.indexOf('`', cursor);
            if (open < 0) {
                break;
            }
            int close = open + 1 < text.length()
                    && !Character.isWhitespace(text.charAt(open + 1))
                    ? text.indexOf('`', open + 2) : -1;
            if (close < 0 || Character.isWhitespace(text.charAt(close - 1))) {
                cursor = open + 1;
                continue;
            }
            appendWords(out, text.substring(literalStart, open), mode, words);
            out.append(text, open, close + 1);
            literalStart = close + 1;
            cursor = close + 1;
        }
        appendWords(out, text.substring(literalStart), mode, words);
    }

    /**
     * The words of a run, chunk by whitespace-separated chunk: a chunk
     * that is a web address, a mention or a channel link is kept whole,
     * every other is filtered.
     */
    private static void appendWords(StringBuilder out, String text,
                                    ChatProfanityMode mode,
                                    ChatProfanityWords words) {
        int cursor = 0;
        while (cursor < text.length()) {
            if (Character.isWhitespace(text.charAt(cursor))) {
                out.append(text.charAt(cursor));
                cursor++;
                continue;
            }
            int end = cursor;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            String chunk = text.substring(cursor, end);
            out.append(isKeptWhole(chunk) ? chunk : filter(chunk, mode, words));
            cursor = end;
        }
    }

    private static boolean isKeptWhole(String chunk) {
        char first = chunk.charAt(0);
        if (first == '@' || first == '#') {
            return true;
        }
        String lowered = chunk.toLowerCase(Locale.ROOT);
        return lowered.startsWith("http://") || lowered.startsWith("https://");
    }

    /**
     * What one word reads as, or null when it is not listed: the word
     * itself, or a listed word with one of the endings, its letters
     * stretched or not.
     */
    private static String replacementFor(String word, ChatProfanityMode mode,
                                         ChatProfanityWords words) {
        String lowered = ChatProfanityWords.lowercase(word);
        String collapsed = ChatProfanityWords.collapse(lowered);
        int[] runs = ChatProfanityWords.runsOf(lowered);
        ChatProfanityWords.Entry whole = words.lookup(collapsed, runs);
        if (whole != null) {
            return render(word, whole.replacement, "", mode);
        }
        for (int index = 0; index < ENDINGS.length; index++) {
            String ending = ENDINGS[index];
            if (collapsed.length() <= ending.length()
                    || !collapsed.endsWith(ending)) {
                continue;
            }
            // The ending's own letters end the base; a stretched last
            // letter of the base is still the base's, so the runs are
            // taken from the collapsed spelling's length.
            String base = collapsed.substring(0, collapsed.length() - ending.length());
            int[] baseRuns = new int[base.length()];
            System.arraycopy(runs, 0, baseRuns, 0, base.length());
            ChatProfanityWords.Entry entry = words.lookup(base, baseRuns);
            if (entry == null && ending.charAt(0) == 'i') {
                // An ending in i took the place of a final y: pussies,
                // shittier and shittiest come from pussy and shitty.
                int[] withY = new int[baseRuns.length + 1];
                System.arraycopy(baseRuns, 0, withY, 0, baseRuns.length);
                withY[baseRuns.length] = 1;
                entry = words.lookup(base + "y", withY);
            }
            if (entry != null) {
                return render(word, entry.replacement, ending, mode);
            }
        }
        return null;
    }

    private static String render(String word, String replacement, String ending,
                                 ChatProfanityMode mode) {
        if (mode == ChatProfanityMode.STARS) {
            StringBuilder stars = new StringBuilder(word.length());
            stars.append(word.charAt(0));
            for (int index = 1; index < word.length(); index++) {
                stars.append('*');
            }
            return stars.toString();
        }
        return matchCase(word, withEnding(replacement, ending));
    }

    /**
     * The silly word carrying the listed word's ending, spelled as
     * English spells it: {@code flip} + {@code ing} is {@code flipping},
     * {@code witch} + {@code s} is {@code witches}, {@code kitty} +
     * {@code ies} is {@code kitties}, {@code spice} + {@code ed} is
     * {@code spiced}.
     */
    static String withEnding(String replacement, String ending) {
        if (ending.length() == 0) {
            return replacement;
        }
        if (ending.equals("s") || ending.equals("es")) {
            return plural(replacement);
        }
        if (ending.equals("ies") || ending.equals("ier") || ending.equals("iest")) {
            // A final y turns to i before these: kitty, kitties, kittier.
            return replacement.endsWith("y")
                    ? replacement.substring(0, replacement.length() - 1) + ending
                    : replacement + ending;
        }
        if (ending.startsWith("head") || ending.startsWith("hole")
                || ending.startsWith("face")) {
            return replacement + ending;
        }
        // A vowel ending: a silent e goes, and a short word ending in
        // one consonant after one vowel doubles it (flip, flipping).
        if (replacement.endsWith("e")) {
            return replacement.substring(0, replacement.length() - 1) + ending;
        }
        if (replacement.length() <= 4 && endsConsonantVowelConsonant(replacement)) {
            return replacement + replacement.charAt(replacement.length() - 1) + ending;
        }
        return replacement + ending;
    }

    private static String plural(String word) {
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("z")
                || word.endsWith("ch") || word.endsWith("sh")) {
            return word + "es";
        }
        return word + "s";
    }

    private static boolean endsConsonantVowelConsonant(String word) {
        if (word.length() < 3) {
            return false;
        }
        char last = word.charAt(word.length() - 1);
        char middle = word.charAt(word.length() - 2);
        char first = word.charAt(word.length() - 3);
        return !isVowel(first) && isVowel(middle) && !isVowel(last)
                && last != 'w' && last != 'x' && last != 'y';
    }

    private static boolean isVowel(char letter) {
        return "aeiou".indexOf(letter) >= 0;
    }

    /** The replacement in the word's case: all capitals, a capital, or as listed. */
    static String matchCase(String word, String replacement) {
        boolean upper = word.length() > 1;
        for (int index = 0; index < word.length() && upper; index++) {
            upper = !Character.isLowerCase(word.charAt(index));
        }
        if (upper) {
            return replacement.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(word.charAt(0)) && replacement.length() > 0) {
            return Character.toUpperCase(replacement.charAt(0))
                    + replacement.substring(1);
        }
        return replacement;
    }
}
