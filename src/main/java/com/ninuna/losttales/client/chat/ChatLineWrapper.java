package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Line layout for Lost Tales chat messages, replacing vanilla's wrapping
 * for them. A message is a header (channel, then the sender) followed by
 * its body, and the two stand on rows of their own: the
 * wrapper measures the header up to the
 * {@link ChatLayoutMarker#anchor() anchor marker}, lays the sender out
 * against the remaining width, and opens the body on the next row at
 * the left edge behind a chevron in the sender's colour
 * ({@link ChatBodyMarker}). Every continuation line of the body opens
 * with an {@link ChatLayoutMarker#indent indent marker} of the
 * chevron's width, so a wrapped body reads as one block beside it. The
 * closed feed names the sender in brackets with the small head inside
 * them; an open window names them plainly, their head standing as the
 * avatar in the window's timestamp area ({@link ChatAvatar}) and taking
 * no room in the row, and their time standing behind the name
 * ({@link ChatStampMarker}):
 *
 * <pre>
 * feed:  &lt;[head] Aragorn&gt;       window:  [avatar] Aragorn 9:54 PM
 *        &gt; This is a long message          &gt; This is a long message
 *          continues beside the chevron       continues beside it
 * </pre>
 *
 * <p>The brackets are the chat's punctuation, like the chevron, so the
 * open window leaves them out as it lays the row out, together with the
 * clear space the closing one keeps; a reply's quote loses its opening
 * bracket the same way, and its closing one stands a word's space after
 * the quoted name as the chevron before the quoted words.</p>
 *
 * <p>The rows are one message: they share a chat line id, so identity,
 * grouping, hover, scrolling and removal all still see a single
 * message. A grouped continuation carries no sender at all and so has
 * no header row; its body starts on the row it is already on, behind
 * the same chevron, and the run stays aligned. A line may name its own
 * separator instead of the chevron, and is laid out the same way behind
 * it. The separator is
 * the chat's own punctuation rather than the sender's words: it is added
 * here, so the stored message never holds it and copying a line copies
 * what was said.</p>
 *
 * <p>A row drawn at another size than the words has another width in
 * its own text ({@link #roomFor}), so the header and the body are each
 * laid out against the room their size leaves them and marked for it
 * ({@link ChatLayoutMarker#header()},
 * {@link ChatLayoutMarker#bodyRow()}): the open window's speaker row is
 * larger and has less room, the closed feed's words are smaller and have
 * more.</p>
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
    /**
     * What a message body opens with: the chat's chevron and one space,
     * drawn in the sender's colour. Its measured width is also the inset
     * every continuation line of that body takes, so a wrapped body
     * lines up beside the chevron rather than under it.
     */
    static final String BODY_SEPARATOR = "> ";
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
     * Wraps a Lost Tales line with every row at the words' own size, for
     * a caller with no display to ask what the other sizes are.
     */
    static List<IChatComponent> wrap(TextMetrics metrics, IChatComponent root,
                                     int width, boolean chatOpen) {
        return wrap(metrics, root, width, chatOpen, 1.0F, 1.0F);
    }

    /** As below, with a reply's quote row at the words' own size. */
    static List<IChatComponent> wrap(TextMetrics metrics, IChatComponent root,
                                     int width, boolean chatOpen,
                                     float headerScale, float bodyScale) {
        return wrap(metrics, root, width, chatOpen, headerScale, bodyScale,
                1.0F, null);
    }

    /**
     * The width a row drawn at {@code rowScale} of the words has in its
     * own (unscaled) text: less for a row drawn larger, more for one
     * drawn smaller, so what is laid out is what is drawn. Never under a
     * pixel, so a layout always makes progress.
     */
    static int roomFor(int width, float rowScale) {
        if (rowScale == 1.0F || rowScale <= 0.0F) {
            return width;
        }
        return Math.max(1, (int)Math.floor(width / rowScale));
    }

    /**
     * Wraps a Lost Tales line to {@code width}, or returns null when the
     * root carries no anchor marker (not a Lost Tales line) or its header
     * alone exceeds the width, in which case vanilla's wrapping applies.
     * {@code chatOpen} lays the line out for the open chat screen, where
     * the channel prefix is not drawn and its width belongs to the body.
     * {@code headerScale}, {@code bodyScale} and {@code quoteScale} are
     * the sizes the header's rows, the body's and a reply's quote row
     * are drawn at against the words of a message in the open window,
     * which is the room each of them has. {@code stamp}, when there is
     * one, is the time the name row wears behind the name in the open
     * window ({@link ChatStampMarker}); a line with no name row — a
     * grouped line, a line that is words from its first row — has
     * nowhere to wear it and leaves it out.
     */
    static List<IChatComponent> wrap(TextMetrics metrics, IChatComponent root,
                                     int width, boolean chatOpen,
                                     float headerScale, float bodyScale,
                                     float quoteScale, IChatComponent stamp) {
        if (metrics == null || root == null || width <= 0) {
            return null;
        }
        List<IChatComponent> parts = flatten(root);
        // A row of its own above the message — the quote a reply opens
        // with — ends at the break marker. It is cut to one line rather
        // than wrapped: a quote is a glance at what is being answered,
        // and a long one must not push the answer down the window.
        int breakIndex = -1;
        for (int index = 0; index < parts.size(); index++) {
            if (ChatLayoutMarker.isLineBreak(parts.get(index))) {
                breakIndex = index;
                break;
            }
        }
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
        for (int index = breakIndex + 1; index < parts.size(); index++) {
            if (ChatLayoutMarker.isAnchor(parts.get(index))) {
                bodyIndex = index;
                break;
            }
        }
        if (bodyIndex < 0) {
            return null;
        }
        // Each state pays only for the header runs it draws inline: the
        // closed feed for the channel prefix, the open screen for none.
        int closedPrefix = 0;
        int openPrefix = 0;
        for (int index = breakIndex + 1; index < bodyIndex; index++) {
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
        // Only a line that opens a body of its own has a header to draw
        // larger, and only then is it laid out against the smaller room
        // that size leaves it; a line that is words from its first row
        // down keeps the whole width.
        boolean opensBody = false;
        for (int index = bodyIndex + 1; index < parts.size(); index++) {
            if (ChatLayoutMarker.isBodyBreak(parts.get(index))) {
                opensBody = true;
                break;
            }
        }
        int bodyWidth = roomFor(width, bodyScale);
        int headerWidth = opensBody ? roomFor(width, headerScale) : bodyWidth;
        // Laid out for an open window, the speaker's brackets go and the
        // head becomes the row's avatar.
        boolean[] dropped = new boolean[parts.size()];
        int avatarIndex = chatOpen && opensBody
                ? markAvatarHeader(parts, bodyIndex, dropped) : -1;
        if (prefix > headerWidth) {
            return null;
        }
        int maxIndent = Math.max(0, Math.min(
                Math.round(bodyWidth * MAX_INDENT_RATIO),
                bodyWidth - MIN_BODY_WIDTH));
        // Closed-feed continuation lines start at the left edge rather
        // than under the channel prefix: the feed is a glance, and a
        // full-width continuation reads better there than an indent
        // aligning with a prefix several lines up.
        Builder builder = new Builder(metrics, bodyWidth, headerWidth, 0,
                Math.min(openPrefix, maxIndent), maxIndent, chatOpen,
                nameColor, titleColor);
        if (breakIndex >= 0) {
            // The quote is cut to one line at its own size, which is a
            // row of its own and may be smaller than the words.
            placeLeadingRow(builder, metrics, parts, breakIndex,
                    roomFor(width, quoteScale), chatOpen);
            builder.breakLine();
        }
        for (int index = breakIndex + 1; index <= bodyIndex; index++) {
            builder.place(copy(parts.get(index)), 0);
        }
        builder.used = prefix;
        builder.lineStart = prefix;
        if (bodyIndex + 1 < parts.size()) {
            builder.ensureBodyRoom();
        }
        for (int index = bodyIndex + 1; index < parts.size(); index++) {
            IChatComponent part = parts.get(index);
            if (dropped[index]) {
                continue;
            }
            if (index == avatarIndex) {
                // The head stands in the timestamp area; in the row it
                // is a place the avatar answers for, with no width.
                builder.place(ChatHeadMarker.asAvatar(part), 0);
                continue;
            }
            if (ChatLayoutMarker.isRowBreak(part)) {
                // The reactions stand on a row of their own under the
                // words, where the body's own continuations start.
                builder.breakRow();
            } else if (ChatLayoutMarker.isBodyBreak(part)) {
                if (chatOpen && stamp != null && builder.used > 0) {
                    // The name's row ends on the time, a space clear of
                    // the name, as Discord dates a message.
                    builder.appendStamp(stamp, metrics.width(" "));
                }
                int senderColor = ChatLayoutMarker.bodyColor(part);
                String label = ChatLayoutMarker.bodyLabel(part);
                builder.beginBody(senderColor < 0 ? nameColor
                        : senderColor,
                        label == null ? BODY_SEPARATOR : label);
            } else if (isAtomic(part)) {
                builder.appendAtomic(part);
            } else {
                builder.appendText(part);
            }
        }
        return builder.finish();
    }

    /**
     * Finds the speaker's head among a header's runs, from the anchor to
     * the body break, and marks the brackets round the speaker's name for
     * leaving out: the opening one just before the head, the closing one
     * at the header's end, and the spacer that keeps the closing one clear
     * of the name. Returns where the head stands, or -1 for a header with
     * none.
     */
    static int markAvatarHeader(List<IChatComponent> parts, int bodyIndex,
                                boolean[] dropped) {
        int bodyBreak = -1;
        for (int index = bodyIndex + 1; index < parts.size(); index++) {
            if (ChatLayoutMarker.isBodyBreak(parts.get(index))) {
                bodyBreak = index;
                break;
            }
        }
        int head = -1;
        for (int index = bodyIndex + 1; index < bodyBreak; index++) {
            if (ChatHeadMarker.decode(parts.get(index)) != null) {
                head = index;
                break;
            }
        }
        if (head < 0) {
            return -1;
        }
        if (head - 1 > bodyIndex && isBracket(parts.get(head - 1), "<")) {
            dropped[head - 1] = true;
        }
        for (int index = bodyBreak - 1; index > head; index--) {
            IChatComponent part = parts.get(index);
            if (isBracket(part, ">")) {
                dropped[index] = true;
                if (index - 1 > head
                        && ChatSpacerMarker.decode(parts.get(index - 1)) >= 0) {
                    dropped[index - 1] = true;
                }
                break;
            }
            if (part.getUnformattedTextForChat().trim().length() > 0) {
                break;
            }
        }
        return head;
    }

    /** Whether a run is a bracket round a name: nothing but the bracket, and its space. */
    private static boolean isBracket(IChatComponent part, String bracket) {
        return bracket.equals(part.getUnformattedTextForChat().trim());
    }

    /**
     * The row above the message, cut to the width rather than wrapped:
     * parts are placed while they fit whole, the one that does not is
     * trimmed to what is left, and anything after it is dropped. Laid
     * out for an open window, the quoted name loses its opening bracket
     * and its closing one stands a word's space after the name, as the
     * chevron before the quoted words.
     */
    private static void placeLeadingRow(Builder builder,
                                        TextMetrics metrics,
                                        List<IChatComponent> parts,
                                        int breakIndex, int width,
                                        boolean chatOpen) {
        int used = 0;
        for (int index = 0; index < breakIndex; index++) {
            IChatComponent part = parts.get(index);
            if (chatOpen && ChatReplyMarker.isMarker(part)
                    && isBracket(part, "<")) {
                continue;
            }
            if (chatOpen && ChatSpacerMarker.decode(part) >= 0
                    && index + 1 < breakIndex
                    && ChatReplyMarker.isMarker(parts.get(index + 1))
                    && isBracket(parts.get(index + 1), ">")) {
                part = ChatSpacerMarker.of(metrics.width(" "));
            }
            int partWidth = partWidth(metrics, part);
            if (used + partWidth <= width) {
                builder.place(copy(part), 0);
                used += partWidth;
                continue;
            }
            String text = part.getUnformattedTextForChat();
            String formatting = part.getChatStyle().getFormattingCode();
            int fits = fitLength(metrics, formatting, text, width - used);
            if (fits > 0) {
                builder.place(copy(part, text.substring(0, fits)), 0);
            }
            return;
        }
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
                || ChatReactionMarker.isMarker(part)
                || ChatReactionMarker.isAddButton(part)) {
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
        /** The width of the rows below the header. */
        private final int bodyWidth;
        /**
         * The width the rows being laid out have: the header's, which is
         * drawn larger, and the body's from the body break on.
         */
        private int width;
        /**
         * Inset every continuation line of the run being laid out opens
         * with, in each of the two states. The header's own
         * continuations align under the header; from the body break on,
         * both are the body separator's width.
         */
        private int closedIndent;
        private int openIndent;
        /** The one of the two this layout reserves on every line. */
        private int indent;
        /** The most any continuation line may be inset by. */
        private final int maxIndent;
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
        /** The first row of the message itself, past a reply's quote row. */
        private int messageFirstRow;
        /** The first row of the body, or -1 while the line has opened none. */
        private int bodyFirstRow = -1;

        Builder(TextMetrics metrics, int bodyWidth, int headerWidth,
                int closedIndent, int openIndent, int maxIndent,
                boolean chatOpen, int nameColor, int titleColor) {
            this.metrics = metrics;
            this.bodyWidth = bodyWidth;
            this.width = headerWidth;
            this.closedIndent = closedIndent;
            this.openIndent = openIndent;
            this.maxIndent = maxIndent;
            this.indent = chatOpen ? openIndent : closedIndent;
            this.nameColor = nameColor;
            this.titleColor = titleColor;
        }

        /**
         * Ends the header and opens the message body at the left edge,
         * behind {@code separator} — the chat's chevron, or the label a
         * line names — drawn in the sender's colour. A header that drew
         * nothing — a grouped continuation, whose runs are all hidden in
         * this state — keeps the row it is on, so the body of a run
         * always begins in the same place. From here on every
         * continuation line is inset by the separator's width, up to
         * the same ceiling a long header's indent has, and has the whole
         * width: only the header is drawn at the large size.
         */
        void beginBody(int senderColor, String separator) {
            this.width = this.bodyWidth;
            int separatorWidth = this.metrics.width(separator);
            int inset = Math.min(separatorWidth, this.maxIndent);
            this.closedIndent = inset;
            this.openIndent = inset;
            this.indent = inset;
            if (this.used > 0) {
                // The header's rows are finished; the body opens the
                // next one, at the edge rather than at an indent. Each
                // of them is marked as the speaker's, so the stack lays
                // it out, measures it and draws it at the large size.
                this.lines.add(this.current);
                for (int row = this.messageFirstRow;
                     row < this.lines.size(); row++) {
                    this.lines.get(row).appendSibling(
                            ChatLayoutMarker.header());
                }
                this.current = new ChatComponentText("");
                this.firstLine = false;
            }
            this.bodyFirstRow = this.lines.size();
            this.used = 0;
            place(ChatBodyMarker.separator(separator, senderColor),
                    separatorWidth);
            this.lineStart = separatorWidth;
        }

        void place(IChatComponent piece, int pieceWidth) {
            this.current.appendSibling(piece);
            this.used += pieceWidth;
            this.fresh = false;
        }

        /**
         * Ends the row above the message and opens the message's own:
         * no indent marker, since what follows is a first line rather
         * than a continuation of one.
         */
        void breakLine() {
            this.lines.add(this.current);
            this.current = new ChatComponentText("");
            this.messageFirstRow = this.lines.size();
            this.used = 0;
            this.lineStart = 0;
            this.firstLine = true;
            this.fresh = true;
        }

        /**
         * Ends the body's last row and opens a continuation row under
         * it, at the body's own inset; a row just opened is kept.
         */
        void breakRow() {
            if (!this.fresh) {
                newLine();
            }
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

        /**
         * Stands the stamp behind what the header's row holds, {@code gap}
         * clear of it; a row without room for both starts the stamp on a
         * row of its own under it.
         */
        void appendStamp(IChatComponent stamp, int gap) {
            int stampWidth = partWidth(this.metrics, stamp);
            if (this.used > this.lineStart
                    && this.used + gap + stampWidth > this.width) {
                newLine();
            } else {
                place(ChatSpacerMarker.of(gap), gap);
            }
            place(copy(stamp), stampWidth);
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
            // Every row from the body break down carries a message's own
            // words, the reactions under them included, so the feed can
            // draw them at a size of its own.
            for (int row = Math.max(0, this.bodyFirstRow);
                 this.bodyFirstRow >= 0 && row < this.lines.size(); row++) {
                this.lines.get(row).appendSibling(
                        ChatLayoutMarker.bodyRow());
            }
            return this.lines;
        }
    }
}
