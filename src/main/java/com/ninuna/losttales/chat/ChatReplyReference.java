package com.ninuna.losttales.chat;

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
 */
public final class ChatReplyReference {
    /** A line that is not a reply. */
    public static final ChatReplyReference NONE =
            new ChatReplyReference(ChatMessageIds.NONE, "", "");
    /** The quoted sender's name, bounded like any other identity name. */
    public static final int MAX_AUTHOR_BYTES = 256;
    /** The quoted text: one glanceable line, not the message again. */
    public static final int MAX_EXCERPT_CHARACTERS = 96;
    /** Worst-case UTF-8 for {@link #MAX_EXCERPT_CHARACTERS}, plus the mark. */
    public static final int MAX_EXCERPT_BYTES =
            (MAX_EXCERPT_CHARACTERS + 3) * 3;
    /** What stands in for the text an excerpt had to cut. */
    private static final String ELLIPSIS = "...";

    private final long messageId;
    private final String author;
    private final String excerpt;

    private ChatReplyReference(long messageId, String author,
                               String excerpt) {
        this.messageId = messageId;
        this.author = author == null ? "" : author;
        this.excerpt = excerpt == null ? "" : excerpt;
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
        String name = author == null ? "" : author.trim();
        if (messageId == ChatMessageIds.NONE || name.length() == 0) {
            return NONE;
        }
        return new ChatReplyReference(messageId, name, excerptOf(message));
    }

    /** The message as one glanceable line, cut with a trailing mark. */
    public static String excerptOf(String message) {
        String text = message == null ? "" : message.trim();
        if (text.length() <= MAX_EXCERPT_CHARACTERS) {
            return text;
        }
        return text.substring(0, MAX_EXCERPT_CHARACTERS) + ELLIPSIS;
    }

    /** Whether the line replies to anything at all. */
    public boolean exists() {
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
