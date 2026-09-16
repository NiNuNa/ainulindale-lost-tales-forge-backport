package com.ninuna.losttales.chat.profanity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A list of words the chat filters, each with the silly word that stands
 * in for it: one entry per line as {@code word=replacement}, both a run
 * of letters. The mod bundles one, a server may add its own in
 * {@code chat.profanityWords} (sent to every client with its chat
 * access), and a client may add more in its own file.
 *
 * <p>A word is kept by its <em>collapsed</em> spelling — every run of
 * one letter shortened to that letter, so {@code fuuuck} is found under
 * {@code fuck} — together with how long each run is in the listed word,
 * so {@code as} never reads as {@code ass}: a match must carry at least
 * every letter the listed word carries. Lists combine with
 * {@link #plus}, the later list's stand-in winning for a word both
 * name, so a server may change the mod's choice of silly word.</p>
 *
 * <p>Free of Minecraft imports, so it is testable without a game
 * runtime.</p>
 */
public final class ChatProfanityWords {
    /** The most entries a server's or a client's own list may hold. */
    public static final int MAX_WORDS = 256;
    /** The longest word or stand-in, in characters. */
    public static final int MAX_WORD_LENGTH = 24;
    /** Worst-case UTF-8 for one {@code word=replacement} entry on the wire. */
    public static final int MAX_ENTRY_BYTES = (2 * MAX_WORD_LENGTH + 1) * 3;

    /** No words at all. */
    public static final ChatProfanityWords NONE = new ChatProfanityWords(
            Collections.<String, Entry>emptyMap());

    /** One listed word: its spelling, its run lengths and its stand-in. */
    static final class Entry {
        final String word;
        final String collapsed;
        final int[] runs;
        final String replacement;

        Entry(String word, String replacement) {
            this.word = word;
            this.collapsed = collapse(word);
            this.runs = runsOf(word);
            this.replacement = replacement;
        }
    }

    private final Map<String, Entry> byCollapsed;

    private ChatProfanityWords(Map<String, Entry> byCollapsed) {
        this.byCollapsed = byCollapsed;
    }

    /**
     * The list the given lines describe, at most {@code limit} entries.
     * A blank line or one opening with {@code #} is skipped silently; a
     * line that is no {@code word=replacement} of letters, a word listed
     * twice, or a line past the limit is skipped with a word in
     * {@code warnings} (which may be null) saying why.
     */
    public static ChatProfanityWords parse(String[] lines, int limit,
                                           List<String> warnings) {
        Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
        if (lines == null) {
            return new ChatProfanityWords(entries);
        }
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index] == null ? "" : lines[index].trim();
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            int equals = line.indexOf('=');
            String word = lowercase(equals < 0 ? line
                    : line.substring(0, equals).trim());
            String replacement = equals < 0 ? ""
                    : lowercase(line.substring(equals + 1).trim());
            if (!isWord(word) || !isWord(replacement)) {
                warn(warnings, "Profanity entry '" + line + "' is not "
                        + "word=replacement, both a run of letters up to "
                        + MAX_WORD_LENGTH + " long; skipped");
                continue;
            }
            Entry entry = new Entry(word, replacement);
            if (entries.containsKey(entry.collapsed)) {
                warn(warnings, "Profanity entry '" + line + "' lists a word "
                        + "already listed (" + entries.get(entry.collapsed).word
                        + "); skipped");
                continue;
            }
            if (entries.size() >= limit) {
                warn(warnings, "Profanity entry '" + line + "' is past the "
                        + limit + " words a list may hold; it and any after "
                        + "it were skipped");
                break;
            }
            entries.put(entry.collapsed, entry);
        }
        return new ChatProfanityWords(entries);
    }

    /**
     * This list with the other's entries added, the other's stand-in
     * winning for a word both list.
     */
    public ChatProfanityWords plus(ChatProfanityWords other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Map<String, Entry> merged = new LinkedHashMap<String, Entry>(this.byCollapsed);
        merged.putAll(other.byCollapsed);
        return new ChatProfanityWords(merged);
    }

    public boolean isEmpty() {
        return this.byCollapsed.isEmpty();
    }

    public int size() {
        return this.byCollapsed.size();
    }

    /** The entries as lines, {@code word=replacement}, in list order. */
    public List<String> entries() {
        List<String> lines = new ArrayList<String>(this.byCollapsed.size());
        for (Entry entry : this.byCollapsed.values()) {
            lines.add(entry.word + "=" + entry.replacement);
        }
        return lines;
    }

    /**
     * The entry a collapsed, lowercased spelling names, when the text's
     * runs are each at least as long as the listed word's; null otherwise.
     */
    Entry lookup(String collapsed, int[] runs) {
        Entry entry = this.byCollapsed.get(collapsed);
        if (entry == null || runs.length != entry.runs.length) {
            return null;
        }
        for (int index = 0; index < runs.length; index++) {
            if (runs[index] < entry.runs[index]) {
                return null;
            }
        }
        return entry;
    }

    /** The word with every run of one letter shortened to that letter. */
    static String collapse(String word) {
        StringBuilder collapsed = new StringBuilder(word.length());
        for (int index = 0; index < word.length(); index++) {
            char letter = word.charAt(index);
            if (index == 0 || letter != word.charAt(index - 1)) {
                collapsed.append(letter);
            }
        }
        return collapsed.toString();
    }

    /** How long each run of one letter is, in the order the runs stand. */
    static int[] runsOf(String word) {
        List<Integer> runs = new ArrayList<Integer>();
        int length = 0;
        for (int index = 0; index < word.length(); index++) {
            length++;
            if (index + 1 == word.length()
                    || word.charAt(index + 1) != word.charAt(index)) {
                runs.add(Integer.valueOf(length));
                length = 0;
            }
        }
        int[] lengths = new int[runs.size()];
        for (int index = 0; index < lengths.length; index++) {
            lengths[index] = runs.get(index).intValue();
        }
        return lengths;
    }

    /** Lowercased letter by letter, so the length never changes. */
    static String lowercase(String text) {
        StringBuilder lowered = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            lowered.append(Character.toLowerCase(text.charAt(index)));
        }
        return lowered.toString();
    }

    private static boolean isWord(String text) {
        if (text.length() == 0 || text.length() > MAX_WORD_LENGTH) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            if (!Character.isLetter(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static void warn(List<String> warnings, String message) {
        if (warnings != null) {
            warnings.add(message);
        }
    }
}
