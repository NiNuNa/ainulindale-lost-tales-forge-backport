package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.util.LostTalesWords;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A Discord member's line as the chat takes it: its text in the server's
 * words, and the same text in pieces, each either plain words or a mark the
 * lang file words (a forward's, a sticker's), which each game reads in its
 * own language ({@link DiscordInboundBody}). The pieces always join to the
 * text exactly; a line without a mark has nothing to translate.
 */
public final class DiscordInboundLine {
    /** A line with nothing sayable in it. */
    public static final DiscordInboundLine EMPTY =
            new DiscordInboundLine(Collections.<Piece>emptyList());

    private final List<Piece> pieces;
    private final String text;

    DiscordInboundLine(List<Piece> pieces) {
        this.pieces = Collections.unmodifiableList(joined(pieces));
        this.text = textOf(this.pieces);
    }

    /** The line in the server's words; empty for nothing sayable. */
    public String getText() { return this.text; }

    /** The line in pieces, plain words and marks, in order. */
    public List<Piece> getPieces() { return this.pieces; }

    /** Whether the line holds a mark each game words for itself. */
    public boolean hasMarks() {
        for (Piece piece : this.pieces) {
            if (piece.isMark()) {
                return true;
            }
        }
        return false;
    }

    /** One run of a line: plain words, or a mark under a lang key. */
    public static final class Piece {
        /** The run in the server's words. */
        public final String words;
        /** The mark's lang key; null for plain words. */
        public final String key;
        /** The mark's arguments, plain words each; empty for plain words. */
        public final List<String> arguments;

        private Piece(String words, String key, List<String> arguments) {
            this.words = words == null ? "" : words;
            this.key = key;
            this.arguments = arguments;
        }

        /** Plain words, the same in every game. */
        static Piece plain(String words) {
            return new Piece(words, null, Collections.<String>emptyList());
        }

        /** A mark under {@code key}, read in the server's words for the text. */
        static Piece mark(LostTalesWords words, String key, String... arguments) {
            String[] copied = arguments.clone();
            return new Piece(words.format(key, (Object[])copied), key,
                    Collections.unmodifiableList(Arrays.asList(copied)));
        }

        /** Whether the run is a mark rather than plain words. */
        public boolean isMark() { return this.key != null; }
    }

    /** The pieces' words, joined. */
    static String textOf(List<Piece> pieces) {
        StringBuilder text = new StringBuilder();
        for (Piece piece : pieces) {
            text.append(piece.words);
        }
        return text.toString();
    }

    /** How many characters the pieces hold. */
    static int length(List<Piece> pieces) {
        int length = 0;
        for (Piece piece : pieces) {
            length += piece.words.length();
        }
        return length;
    }

    /**
     * The characters from {@code start} to {@code end} of the pieces, as
     * pieces: a mark wholly inside stays a mark, and one either end cuts
     * keeps its part inside as plain words, so the pieces join to the text
     * cut the same way.
     */
    static List<Piece> slice(List<Piece> pieces, int start, int end) {
        List<Piece> kept = new ArrayList<Piece>();
        int at = 0;
        for (Piece piece : pieces) {
            int length = piece.words.length();
            int from = Math.max(start, at);
            int to = Math.min(end, at + length);
            if (from < to) {
                kept.add(from == at && to == at + length ? piece
                        : Piece.plain(piece.words.substring(from - at, to - at)));
            }
            at += length;
        }
        return kept;
    }

    /** The pieces without what {@link String#trim} takes off either end of their text. */
    static List<Piece> trim(List<Piece> pieces) {
        String text = textOf(pieces);
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && text.charAt(end - 1) <= ' ') {
            end--;
        }
        return slice(pieces, start, end);
    }

    /** Neighbouring plain words run together, and empty runs left out. */
    private static List<Piece> joined(List<Piece> pieces) {
        List<Piece> joined = new ArrayList<Piece>(pieces.size());
        StringBuilder plain = new StringBuilder();
        for (Piece piece : pieces) {
            if (piece.words.length() == 0) {
                continue;
            }
            if (!piece.isMark()) {
                plain.append(piece.words);
                continue;
            }
            if (plain.length() > 0) {
                joined.add(Piece.plain(plain.toString()));
                plain.setLength(0);
            }
            joined.add(piece);
        }
        if (plain.length() > 0) {
            joined.add(Piece.plain(plain.toString()));
        }
        return joined;
    }
}
