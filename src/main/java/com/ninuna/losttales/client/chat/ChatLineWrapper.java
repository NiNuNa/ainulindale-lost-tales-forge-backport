package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Line layout for Lost Tales chat messages, replacing vanilla's wrapping
 * for them. A message is a header (channel, timestamp) followed by the
 * sender and body; the wrapper measures the header up to the
 * {@link ChatLayoutMarker#anchor() anchor marker}, lays the rest out
 * against the remaining width, and opens every continuation line with an
 * {@link ChatLayoutMarker#indent indent marker} of the header's width, so
 * wrapped text lines up under the sender's opening bracket:
 *
 * <pre>
 * [12:34] &lt;Aragorn&gt; This is a long message that
 *         continues under the sender
 * </pre>
 *
 * <p>Widths come from the same measure the renderer draws with (the
 * component's style formatting code plus its text), so inline formatting
 * codes never count as visible width. Text breaks at spaces, the break
 * space itself is dropped, and a word longer than a whole line is cut
 * hard so any input makes progress; inline emojis and showcase icons are
 * atomic words. Formatting codes active at a split are carried onto the
 * next piece.</p>
 *
 * <p>A line is laid out for the state it will be drawn in: the closed
 * HUD shows the channel prefix and reserves its width, the open screen
 * hides it (the tabs already name the channel) and gives that width back
 * to the message body. Either way the indent marker carries both
 * offsets, so the renderer can align continuation lines in both
 * states.</p>
 *
 * <p>Pure layout over component text and widths: no rendering, no
 * Minecraft runtime, so it is unit-tested with a fake measure.</p>
 */
final class ChatLineWrapper {
    /** Room the body must have on a line before it is worth starting there. */
    static final int MIN_BODY_WIDTH = 40;
    /** A very long prefix indents continuation lines by at most this share. */
    static final float MAX_INDENT_RATIO = 0.5F;
    private static final char FORMATTING_ESCAPE = 167;

    /** {@code FontRenderer.getStringWidth} semantics over formatted text. */
    interface TextMetrics {
        int width(String text);
    }

    private ChatLineWrapper() {}

    /**
     * Wraps a Lost Tales line for the closed HUD, channel prefix and all.
     */
    static List<IChatComponent> wrap(TextMetrics metrics, IChatComponent root,
                                     int width) {
        return wrap(metrics, root, width, false);
    }

    /**
     * Wraps a Lost Tales line to {@code width}, or returns null when the
     * root carries no anchor marker (not a Lost Tales line) or its header
     * alone exceeds the width, in which case vanilla's wrapping applies.
     * {@code chatOpen} lays the line out for the open chat screen, where
     * the channel prefix is not drawn and its width belongs to the body.
     */
    static List<IChatComponent> wrap(TextMetrics metrics, IChatComponent root,
                                     int width, boolean chatOpen) {
        if (metrics == null || root == null || width <= 0) {
            return null;
        }
        List<IChatComponent> parts = flatten(root);
        // The head marker sits on the first line only, and it is where
        // the renderer reads the sender's colours from; the continuation
        // lines are given them so a wrapped name keeps its colour.
        int nameColor = -1;
        int titleColor = -1;
        for (int index = 0; index < parts.size(); index++) {
            ChatHeadMarker.Data head = ChatHeadMarker.decode(
                    parts.get(index));
            if (head != null) {
                nameColor = head.nameColor & 0xFFFFFF;
                titleColor = head.titleColor & 0xFFFFFF;
                break;
            }
        }
        int bodyIndex = -1;
        for (int index = 0; index < parts.size(); index++) {
            if (ChatLayoutMarker.isAnchor(parts.get(index))) {
                bodyIndex = index;
                break;
            }
        }
        if (bodyIndex < 0) {
            return null;
        }
        // Each state pays only for the header runs it draws inline: the
        // closed feed for the channel prefix, the open screen for none —
        // its timestamp lives in the column at the window's edge.
        int closedPrefix = 0;
        int openPrefix = 0;
        for (int index = 0; index < bodyIndex; index++) {
            IChatComponent part = parts.get(index);
            int partWidth = partWidth(metrics, part);
            if (!ChatPrefixMarker.isHidden(part, false)) {
                closedPrefix += partWidth;
            }
            if (!ChatPrefixMarker.isHidden(part, true)) {
                openPrefix += partWidth;
            }
        }
        // The header the drawn state actually shows is what the body
        // has to make room for.
        int prefix = chatOpen ? openPrefix : closedPrefix;
        if (prefix > width) {
            return null;
        }
        int maxIndent = Math.max(0, Math.min(
                Math.round(width * MAX_INDENT_RATIO), width - MIN_BODY_WIDTH));
        // Closed-feed continuation lines start at the left edge rather
        // than under the channel prefix: the feed is a glance, and a
        // full-width continuation reads better there than an indent
        // aligning with a prefix several lines up.
        Builder builder = new Builder(metrics, width, 0,
                Math.min(openPrefix, maxIndent), chatOpen, nameColor,
                titleColor);
        for (int index = 0; index <= bodyIndex; index++) {
            builder.place(copy(parts.get(index)), 0);
        }
        builder.used = prefix;
        builder.lineStart = prefix;
        if (bodyIndex + 1 < parts.size()) {
            builder.ensureBodyRoom();
        }
        for (int index = bodyIndex + 1; index < parts.size(); index++) {
            IChatComponent part = parts.get(index);
            if (isAtomic(part)) {
                builder.appendAtomic(part);
            } else {
                builder.appendText(part);
            }
        }
        return builder.finish();
    }

    /** Width of one component as the renderer advances past it. */
    static int partWidth(TextMetrics metrics, IChatComponent part) {
        // A head's slot and a plain gap declare their width rather
        // than spelling it out in spaces; the layout has to advance by
        // the same amount the renderer does, or a line breaks where
        // nothing is drawn.
        int declared = ChatInlineIcons.declaredWidth(part);
        if (declared >= 0) {
            return declared;
        }
        return metrics.width(part.getChatStyle().getFormattingCode()
                + part.getUnformattedTextForChat());
    }

    /** Glyph slots are single indivisible words, spaces or not. */
    private static boolean isAtomic(IChatComponent part) {
        if (ChatEmojiMarker.isMarker(part) || ChatHeadMarker.isMarker(part)
                || ChatSpacerMarker.isMarker(part)
                || ChatGroupMarker.isMarker(part)) {
            return true;
        }
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
        return share != null && share.icon;
    }

    private static List<IChatComponent> flatten(IChatComponent root) {
        List<IChatComponent> parts = new ArrayList<IChatComponent>();
        for (Object value : root) {
            if (value instanceof IChatComponent) {
                parts.add((IChatComponent)value);
            }
        }
        return parts;
    }

    private static ChatComponentText copy(IChatComponent part) {
        return copy(part, part.getUnformattedTextForChat());
    }

    /** A text node with the part's style, the way vanilla copies pieces. */
    private static ChatComponentText copy(IChatComponent part, String text) {
        ChatComponentText piece = new ChatComponentText(text);
        piece.setChatStyle(part.getChatStyle().createShallowCopy());
        return piece;
    }

    /**
     * The inline formatting in force at the end of {@code text}: the last
     * colour (which also clears decorations) plus decorations after it,
     * cleared by a reset. Mirrors how {@code FontRenderer} applies codes.
     */
    static String activeFormatting(String text) {
        StringBuilder active = new StringBuilder();
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) != FORMATTING_ESCAPE) {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            index++;
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                active.setLength(0);
                active.append(FORMATTING_ESCAPE).append(code);
            } else if (code == 'r') {
                active.setLength(0);
            } else if (code >= 'k' && code <= 'o') {
                active.append(FORMATTING_ESCAPE).append(code);
            }
        }
        return active.toString();
    }

    /**
     * The longest prefix of {@code text} that fits {@code room} when drawn
     * after {@code formatting}. Widths never shrink as text grows, so the
     * answer is found by bisection; a cut that would strand a section
     * sign is pulled back before it.
     */
    static int fitLength(TextMetrics metrics, String formatting, String text,
                         int room) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (metrics.width(formatting + text.substring(0, middle)) <= room) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        if (low > 0 && low < text.length()
                && text.charAt(low - 1) == FORMATTING_ESCAPE) {
            low--;
        }
        return low;
    }

    private static final class Builder {
        private final TextMetrics metrics;
        private final int width;
        private final int closedIndent;
        private final int openIndent;
        /** The one of the two this layout reserves on every line. */
        private final int indent;
        /** The sender's colours, carried onto every continuation line. */
        private final int nameColor;
        private final int titleColor;
        private final List<IChatComponent> lines =
                new ArrayList<IChatComponent>();
        private ChatComponentText current = new ChatComponentText("");
        /** Pixels taken on the current line, prefix or indent included. */
        int used;
        /** Where the current line's body room starts. */
        int lineStart;
        private boolean firstLine = true;
        /** True after a line break until something is placed on the line. */
        private boolean fresh;

        Builder(TextMetrics metrics, int width, int closedIndent,
                int openIndent, boolean chatOpen, int nameColor,
                int titleColor) {
            this.metrics = metrics;
            this.width = width;
            this.closedIndent = closedIndent;
            this.openIndent = openIndent;
            this.indent = chatOpen ? openIndent : closedIndent;
            this.nameColor = nameColor;
            this.titleColor = titleColor;
        }

        void place(IChatComponent piece, int pieceWidth) {
            this.current.appendSibling(piece);
            this.used += pieceWidth;
            this.fresh = false;
        }

        private void newLine() {
            this.lines.add(this.current);
            this.current = new ChatComponentText("");
            this.current.appendSibling(ChatLayoutMarker.indent(
                    this.closedIndent, this.openIndent,
                    this.nameColor, this.titleColor));
            this.used = this.indent;
            this.lineStart = this.indent;
            this.firstLine = false;
            this.fresh = true;
        }

        /**
         * Called once before the body: a prefix that leaves no real room
         * on its line starts the body a line down instead.
         */
        void ensureBodyRoom() {
            if (this.firstLine && this.width - this.used < MIN_BODY_WIDTH
                    && this.width - this.indent >= MIN_BODY_WIDTH) {
                newLine();
            }
        }

        void appendAtomic(IChatComponent part) {
            int partWidth = partWidth(this.metrics, part);
            if (this.used > this.lineStart
                    && this.used + partWidth > this.width) {
                newLine();
            }
            place(copy(part), partWidth);
        }

        void appendText(IChatComponent part) {
            String text = part.getUnformattedTextForChat();
            if (text.length() == 0) {
                place(copy(part), 0);
                return;
            }
            String formatting = part.getChatStyle().getFormattingCode();
            String consumed = "";
            String remaining = text;
            while (true) {
                if (this.fresh && remaining.startsWith(" ")) {
                    // The break space belongs to the break, not the line.
                    remaining = remaining.substring(1);
                }
                if (remaining.length() == 0) {
                    return;
                }
                String carried = activeFormatting(consumed);
                String candidate = carried + remaining;
                int candidateWidth = this.metrics.width(formatting + candidate);
                if (this.used + candidateWidth <= this.width) {
                    place(copy(part, candidate), candidateWidth);
                    return;
                }
                int fit = fitLength(this.metrics, formatting, candidate,
                        this.width - this.used) - carried.length();
                int breakAt = candidate.lastIndexOf(' ',
                        Math.min(candidate.length() - 1,
                                fit + carried.length())) - carried.length();
                String head;
                String tail;
                if (breakAt > 0) {
                    head = remaining.substring(0, breakAt);
                    tail = remaining.substring(breakAt + 1);
                } else if (this.used > this.lineStart) {
                    // The word does not fit beside what is already here;
                    // it moves down whole.
                    head = "";
                    tail = remaining;
                } else {
                    // Longer than a whole line: cut it, always taking at
                    // least one character so the layout advances.
                    int cut = Math.max(1, Math.min(remaining.length(), fit));
                    if (cut < remaining.length()
                            && remaining.charAt(cut - 1) == FORMATTING_ESCAPE) {
                        cut = cut > 1 ? cut - 1 : Math.min(2, remaining.length());
                    }
                    head = remaining.substring(0, cut);
                    tail = remaining.substring(cut);
                }
                if (head.length() > 0) {
                    String piece = carried + head;
                    place(copy(part, piece),
                            this.metrics.width(formatting + piece));
                    consumed += head;
                }
                newLine();
                remaining = tail;
            }
        }

        List<IChatComponent> finish() {
            this.lines.add(this.current);
            return this.lines;
        }
    }
}
