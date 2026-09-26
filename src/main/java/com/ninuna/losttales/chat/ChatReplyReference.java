package com.ninuna.losttales.chat;

import java.util.UUID;

/**
 * The message a line is a reply to, as its recipients are shown it.
 *
 * <p>The quote travels with the reply rather than being looked up by
 * each client: only the server knows whether the replier could see the
 * message they named, and only the server can promise every recipient
 * the same quote — one who joined after the original, or whose history
 * has trimmed past it, has nothing of their own to quote from. So the
 * server resolves the reference once, against its own record of what it
 * distributed, and sends the author and a short excerpt alongside the
 * id.</p>
 *
 * <p>The id is kept as well as the text: it is what a click on the quote
 * jumps to, and what a later edit or removal of the original would be
 * matched against. {@link #NONE} is a line that replies to nothing.</p>
 *
 * <p>A forwarded message wears one too ({@link #forward}): the message it
 * carries on from, with the link to where that was said, and no excerpt,
 * since the forward's own words are the whole message.</p>
 */
public final class ChatReplyReference {
    /** An author whose colour the quote was not told. */
    public static final int NO_COLOR = -1;
    /** A line that is not a reply. */
    public static final ChatReplyReference NONE =
            new ChatReplyReference(ChatMessageIds.NONE, "", "", NO_COLOR);
    /** The quoted sender's name, bounded like any other identity name. */
    public static final int MAX_AUTHOR_BYTES = 256;
    /** The quoted text: one glanceable line, not the message again. */
    public static final int MAX_EXCERPT_CHARACTERS = 96;
    /** Worst-case UTF-8 for {@link #MAX_EXCERPT_CHARACTERS}, plus the mark. */
    public static final int MAX_EXCERPT_BYTES =
            (MAX_EXCERPT_CHARACTERS + 3) * 3;
    /** What stands in for the text an excerpt had to cut. */
    private static final String ELLIPSIS = "...";
    /** The quoted sender's skin id, bounded as the message packet bounds its own. */
    public static final int MAX_SKIN_ID_BYTES = 128;
    /** A forward's link to where its message was said: {@code #code/id}. */
    public static final int MAX_LINK_BYTES = 128;

    private final long messageId;
    private final String author;
    private final String excerpt;
    private final int authorColor;
    /**
     * The sender the quoted line wore, for its head: the sender id,
     * whether the line wore the account, and the skin it was drawn
     * with. Null, false and empty for a quote told none.
     */
    private final UUID senderId;
    private final boolean accountLine;
    private final String skinId;
    /**
     * Whether the quoted line is an NPC's, whose head is its portrait:
     * {@link #skinId} then holds the portrait's texture path. Only a
     * client quotes an NPC — its speech never reaches a server — so this
     * never travels.
     */
    private final boolean npcLine;
    /**
     * For a forward, the link to the message it carries on, as
     * {@code #code/id} names it; empty for a reply.
     */
    private final String forwardedFrom;

    private ChatReplyReference(long messageId, String author,
                               String excerpt, int authorColor) {
        this(messageId, author, excerpt, authorColor, null, false, "",
                false, "");
    }

    private ChatReplyReference(long messageId, String author,
                               String excerpt, int authorColor,
                               UUID senderId, boolean accountLine,
                               String skinId, boolean npcLine,
                               String forwardedFrom) {
        this.messageId = messageId;
        this.author = author == null ? "" : author;
        this.excerpt = excerpt == null ? "" : excerpt;
        this.authorColor = authorColor;
        this.senderId = senderId;
        this.accountLine = accountLine;
        this.skinId = skinId == null ? "" : skinId;
        this.npcLine = npcLine;
        this.forwardedFrom = forwardedFrom == null ? "" : forwardedFrom;
    }

    /**
     * The same quote wearing the quoted sender's head: what the server
     * adds from its record of the line, so the quote is drawn with the
     * face the line was, whether or not the reader still holds it. A
     * quote of nothing stays nothing.
     */
    public ChatReplyReference withHead(UUID senderId, boolean accountLine,
                                       String skinId) {
        if (!exists() || senderId == null) {
            return this;
        }
        return new ChatReplyReference(this.messageId, this.author,
                this.excerpt, this.authorColor, senderId, accountLine,
                skinId, false, this.forwardedFrom);
    }

    /**
     * The same quote wearing an NPC's portrait for a head, by the NPC's
     * id and the portrait's texture path: the quote of a line in an NPC
     * conversation, which only the client that holds it ever builds.
     */
    public ChatReplyReference withNpcHead(UUID npcId, String texturePath) {
        if (!exists() || npcId == null) {
            return this;
        }
        return new ChatReplyReference(this.messageId, this.author,
                this.excerpt, this.authorColor, npcId, false, texturePath,
                true, this.forwardedFrom);
    }

    /**
     * The same quote wearing the head {@code other} wears, or none when
     * it wears none: what a quote cut afresh keeps of the one it
     * replaces.
     */
    public ChatReplyReference withHeadOf(ChatReplyReference other) {
        if (other == null || !other.hasHead()) {
            return this;
        }
        return other.npcLine
                ? withNpcHead(other.senderId, other.skinId)
                : withHead(other.senderId, other.accountLine, other.skinId);
    }

    /** Whether the quote was told whose head to wear. */
    public boolean hasHead() {
        return this.senderId != null;
    }

    /** Whether the head is an NPC's portrait rather than a player's face. */
    public boolean isNpcLine() {
        return this.npcLine;
    }

    /** The quoted sender's id, or null for a quote told no head. */
    public UUID getSenderId() {
        return this.senderId;
    }

    /** Whether the quoted line wore the account rather than a character. */
    public boolean isAccountLine() {
        return this.accountLine;
    }

    /** The skin the quoted line's head was drawn with; empty for the account's own. */
    public String getSkinId() {
        return this.skinId;
    }

    /**
     * A reference to {@code messageId}, quoting {@code author}; the text
     * is cut to {@link #MAX_EXCERPT_CHARACTERS}. A nameless author, or
     * no message at all, is {@link #NONE}: a quote nobody can be shown
     * is not a reply.
     *
     * <p>The id may be a client's own as well as a server's — an NPC's
     * conversation is answered the same way, and nobody but that client
     * ever sees either half of it. Only the server's ids travel: the
     * message packet refuses a local one off the wire.</p>
     */
    public static ChatReplyReference of(long messageId, String author,
                                        String message) {
        return of(messageId, author, message, NO_COLOR);
    }

    /**
     * As above, carrying the colour the quoted author's name was drawn
     * in.
     *
     * <p>A quote names whoever wrote the line it quotes, and that name
     * has a colour of its own — a faction's, a role's. Only the server
     * knows it: an in-character name belongs to a character, and a
     * client holds name colours for the accounts it has been told about,
     * which is not the same list. So it travels with the quote, and a
     * quote that was not told one is drawn quietly.</p>
     */
    public static ChatReplyReference of(long messageId, String author,
                                        String message, int authorColor) {
        String name = author == null ? "" : author.trim();
        if (messageId == ChatMessageIds.NONE || name.length() == 0) {
            return NONE;
        }
        return new ChatReplyReference(messageId, name, excerptOf(message),
                authorColor);
    }

    /**
     * A quote of a line no server named — an announcement, a death
     * message, a console notice, a command's echo, an NPC's speech —
     * which travels as its author and its words alone, with no id to
     * jump to or to match a later edit against. The author is whoever
     * the line was signed by, or the chat's own word for a line nobody
     * signed. A nameless one is {@link #NONE}.
     */
    public static ChatReplyReference unanchored(String author,
                                                String message,
                                                int authorColor) {
        String name = author == null ? "" : author.trim();
        if (name.length() == 0) {
            return NONE;
        }
        return new ChatReplyReference(ChatMessageIds.NONE, name,
                excerptOf(message), authorColor);
    }

    /**
     * The message {@code messageId} carried on to another conversation,
     * said by {@code author} where {@code link} names — {@code #ooc/42} —
     * whose words the forward is. Nameless, without an id or without a
     * link, it is {@link #NONE}.
     */
    public static ChatReplyReference forward(long messageId, String author,
                                             int authorColor, String link) {
        String name = author == null ? "" : author.trim();
        if (messageId == ChatMessageIds.NONE || name.length() == 0
                || link == null || link.length() == 0) {
            return NONE;
        }
        return new ChatReplyReference(messageId, name, "", authorColor, null,
                false, "", false, link);
    }

    /** Whether this is a forward's: the message a forward carries on. */
    public boolean isForward() {
        return this.forwardedFrom.length() > 0;
    }

    /** A forward's link to where its message was said; empty for a reply. */
    public String getForwardedFrom() {
        return this.forwardedFrom;
    }

    /** The message as one glanceable line, cut with a trailing mark. */
    public static String excerptOf(String message) {
        String text = message == null ? "" : message.trim();
        if (text.length() <= MAX_EXCERPT_CHARACTERS) {
            return text;
        }
        return text.substring(0, MAX_EXCERPT_CHARACTERS) + ELLIPSIS;
    }

    /**
     * The colour the quoted author's name was drawn in, or
     * {@link #NO_COLOR} when the quote was not told one.
     */
    public int getAuthorColor() {
        return this.authorColor;
    }

    /** Whether the line replies to anything at all. */
    public boolean exists() {
        return this.author.length() > 0;
    }

    /**
     * Whether the quote names a message by id — one a click can jump
     * to and an edit can be matched against — rather than quoting a
     * line nobody named.
     */
    public boolean isAnchored() {
        return this.messageId != ChatMessageIds.NONE;
    }

    /** The message replied to; {@link ChatMessageIds#NONE} for none. */
    public long getMessageId() {
        return this.messageId;
    }

    /** The quoted sender's name as the original was signed. */
    public String getAuthor() {
        return this.author;
    }

    /** The quoted text, already cut to one line. */
    public String getExcerpt() {
        return this.excerpt;
    }
}
