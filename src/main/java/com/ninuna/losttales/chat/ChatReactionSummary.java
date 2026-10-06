package com.ninuna.losttales.chat;

import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * The reactions on one message as one reader is shown them: each emoji
 * reacted with, in the order it was first used, how many reacted with
 * it, which of the reader's own identities are among them, and the
 * first few names.
 *
 * <p>What a reader is shown differs by reader — "is it mine" is theirs
 * alone — so the server builds one of these per reader from the record
 * it keeps, and a client never learns who reacted beyond the names it
 * is handed. A reader's identities are their account and their
 * characters: each copy of a conversation reacts as the identity it
 * speaks as, and lights the chips that identity is in. Bounded on both
 * sides of the wire.</p>
 */
public final class ChatReactionSummary {
    /** Distinct emoji on one message; Discord's own ceiling. */
    public static final int MAX_KINDS = 20;
    /** Names a reader is shown per emoji; the count says the rest. */
    public static final int MAX_NAMES = 5;
    /** A shown name, as characters; the server cuts longer ones. */
    public static final int MAX_NAME_CHARS = 32;
    /** A shown name, as UTF-8 on the wire. */
    public static final int MAX_NAME_BYTES = MAX_NAME_CHARS * 3;
    /**
     * An emoji's reaction key on the wire: a registry name, or a foreign
     * key ({@link ChatForeignEmoji}), which is held to this bound too.
     */
    public static final int MAX_EMOJI_BYTES = 64;
    /** The most reactions one emoji may count; well past any server. */
    public static final int MAX_COUNT = 100000;
    /** The most of one reader's identities named as having reacted with one emoji. */
    public static final int MAX_MINE = 32;

    public static final ChatReactionSummary EMPTY =
            new ChatReactionSummary(Collections.<Reaction>emptyList());

    private final List<Reaction> reactions;

    public ChatReactionSummary(List<Reaction> reactions) {
        List<Reaction> kept = new ArrayList<Reaction>();
        if (reactions != null) {
            for (Reaction reaction : reactions) {
                if (reaction != null) {
                    kept.add(reaction);
                }
            }
        }
        if (kept.size() > MAX_KINDS) {
            throw new IllegalArgumentException("too many reactions");
        }
        this.reactions = Collections.unmodifiableList(kept);
    }

    /** Every emoji reacted with, in the order each was first used. */
    public List<Reaction> getReactions() {
        return this.reactions;
    }

    public boolean isEmpty() {
        return this.reactions.isEmpty();
    }

    /** The reaction with {@code emoji}, or null when nobody used it. */
    public Reaction find(String emoji) {
        for (int index = 0; index < this.reactions.size(); index++) {
            Reaction reaction = this.reactions.get(index);
            if (reaction.emoji.equals(emoji)) {
                return reaction;
            }
        }
        return null;
    }

    /** One emoji's reactions. */
    public static final class Reaction {
        /**
         * The emoji's reaction key: the registry's name for it, or the
         * foreign key of an emoji the registry lacks.
         */
        public final String emoji;
        public final int count;
        /**
         * The reader's own identities among those who reacted, in the
         * order they did: the account's id, a character's id; empty for
         * none. At most {@link #MAX_MINE}.
         */
        public final List<UUID> mineAs;
        /** The first few who reacted, in order; at most {@link #MAX_NAMES}. */
        public final List<String> names;

        public Reaction(String emoji, int count, List<UUID> mineAs,
                        List<String> names) {
            if (!ChatForeignEmoji.isReactionKey(emoji)
                    || count < 1 || count > MAX_COUNT) {
                throw new IllegalArgumentException("invalid reaction");
            }
            List<UUID> mine = mineAs == null ? Collections.<UUID>emptyList()
                    : new ArrayList<UUID>(mineAs);
            if (mine.size() > MAX_MINE || mine.size() > count
                    || mine.contains(null)
                    || new HashSet<UUID>(mine).size() != mine.size()) {
                throw new IllegalArgumentException("invalid reaction identities");
            }
            List<String> kept = new ArrayList<String>();
            if (names != null) {
                for (String name : names) {
                    if (name != null && name.trim().length() > 0) {
                        kept.add(name.trim());
                    }
                }
            }
            if (kept.size() > MAX_NAMES || kept.size() > count) {
                throw new IllegalArgumentException("invalid reaction names");
            }
            this.emoji = emoji;
            this.count = count;
            this.mineAs = Collections.unmodifiableList(mine);
            this.names = Collections.unmodifiableList(kept);
        }

        /** Whether the identity {@code identityId} is among those who reacted. */
        public boolean isMine(UUID identityId) {
            return identityId != null && this.mineAs.contains(identityId);
        }

        /** How many reacted beyond the names shown. */
        public int others() {
            return Math.max(0, this.count - this.names.size());
        }
    }
}
