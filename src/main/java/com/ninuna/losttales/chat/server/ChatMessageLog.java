package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

/**
 * The server's memory of the messages it has just distributed, and of
 * who it distributed each of them to.
 *
 * <p>A reply names a message. Two things have to be checked before that
 * name is honoured, and only the server can check either: that the
 * message exists, and that the player replying to it was <em>sent</em>
 * it. Without the second, replying to an id nobody showed you would
 * quote a private message back into a channel you choose — so the
 * recipients are recorded rather than re-derived, since who could see a
 * message is a fact about the moment it was sent: a party someone has
 * since joined, a proximity call they have since walked into.</p>
 *
 * <p>Changing a message afterwards — editing its text, taking it back —
 * asks the same list two further questions: whether the player asking
 * is the one who wrote it, which is why the author is remembered by
 * account rather than by the name the line was signed with, and where
 * the change has to be sent, which is the same recorded set. A change
 * reaches exactly who saw the original and nobody who has since walked
 * into range.</p>
 *
 * <p>Bounded to the last {@link #MAX_MESSAGES}, which is what a reply
 * reaches back over and equally how far back a message stays editable —
 * an old enough message is simply no longer either — and cleared with
 * the rest of the server's chat state. The excerpt is cut when it is
 * recorded, so a quote costs a line of text per message rather than the
 * message itself.</p>
 */
public final class ChatMessageLog {
    /** How far back a reply, an edit, or a removal may reach. */
    private static final int MAX_MESSAGES = 256;
    private static final LinkedHashMap<Long, Entry> ENTRIES =
            new LinkedHashMap<Long, Entry>();

    private ChatMessageLog() {}

    /**
     * Remembers a distributed message: the account that sent it, the
     * name it was signed with, what it said, and every account it was
     * sent to. Anything without a server id — a line nobody can name —
     * is not recorded.
     */
    public static synchronized void record(long messageId, UUID authorId,
                                           String author, String message,
                                           Collection<UUID> recipients) {
        if (!ChatMessageIds.isServerId(messageId) || author == null
                || author.trim().length() == 0) {
            return;
        }
        Set<UUID> seenBy = new HashSet<UUID>();
        if (recipients != null) {
            for (UUID recipient : recipients) {
                if (recipient != null) {
                    seenBy.add(recipient);
                }
            }
        }
        ENTRIES.put(Long.valueOf(messageId), new Entry(authorId, author.trim(),
                ChatReplyReference.excerptOf(message), seenBy));
        while (ENTRIES.size() > MAX_MESSAGES) {
            Iterator<Long> oldest = ENTRIES.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * The quote {@code replier} may show for the message they named, or
     * {@link ChatReplyReference#NONE} when there is none they may: the
     * message has fallen out of reach, or was never sent to them.
     */
    public static synchronized ChatReplyReference quoteFor(long messageId,
                                                           UUID replier) {
        Entry entry = ENTRIES.get(Long.valueOf(messageId));
        if (entry == null || replier == null
                || !entry.seenBy.contains(replier)) {
            return ChatReplyReference.NONE;
        }
        return ChatReplyReference.of(messageId, entry.author, entry.excerpt);
    }

    /**
     * Rewrites what a message says and answers with everyone who has to
     * be told, or null when {@code editor} may not change it: no such
     * message, or one they did not write. The excerpt is recut, so a
     * reply made after the edit quotes what the message says now.
     */
    public static synchronized Set<UUID> applyEdit(long messageId,
                                                   UUID editor,
                                                   String message) {
        Entry entry = authored(messageId, editor);
        if (entry == null) {
            return null;
        }
        ENTRIES.put(Long.valueOf(messageId), new Entry(entry.authorId,
                entry.author, ChatReplyReference.excerptOf(message),
                entry.seenBy));
        return Collections.unmodifiableSet(new HashSet<UUID>(entry.seenBy));
    }

    /**
     * Forgets a message and answers with everyone who has to be told,
     * or null when {@code remover} may not take it back. The id is not
     * reused, so a reply still naming it simply finds nothing.
     */
    public static synchronized Set<UUID> remove(long messageId,
                                                UUID remover) {
        Entry entry = authored(messageId, remover);
        if (entry == null) {
            return null;
        }
        ENTRIES.remove(Long.valueOf(messageId));
        return Collections.unmodifiableSet(new HashSet<UUID>(entry.seenBy));
    }

    /** The message, but only for the account that wrote it. */
    private static Entry authored(long messageId, UUID account) {
        Entry entry = ENTRIES.get(Long.valueOf(messageId));
        return entry == null || account == null || entry.authorId == null
                || !account.equals(entry.authorId) ? null : entry;
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        ENTRIES.clear();
    }

    /** Test and diagnostics hook: messages currently within reach. */
    static synchronized int size() {
        return ENTRIES.size();
    }

    /** One distributed message: who wrote it, how it reads quoted, who saw it. */
    private static final class Entry {
        final UUID authorId;
        final String author;
        final String excerpt;
        final Set<UUID> seenBy;

        Entry(UUID authorId, String author, String excerpt, Set<UUID> seenBy) {
            this.authorId = authorId;
            this.author = author;
            this.excerpt = excerpt;
            this.seenBy = seenBy;
        }
    }
}
