package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server's memory of the messages it has distributed: what each one
 * said, who was sent it, and who else may still be shown it.
 *
 * <p>Three things read it. A <em>reply</em> names a message, and is
 * honoured only when the message is here and the player replying was
 * sent it — otherwise naming an id would quote a private line into a
 * channel of the replier's choosing. An <em>edit</em> or a
 * <em>removal</em> asks whether the player is the author, and where the
 * change has to go, which is the same recorded set. And a player
 * <em>joining</em> is shown the recent messages they are entitled to,
 * so a conversation that went on without them is not lost: every entry
 * keeps the line exactly as it was sent, and an {@link Audience} that
 * says who may be shown it.</p>
 *
 * <p>The audience is decided when the message is sent and checked again
 * when it is replayed, and both have to agree: a whisper reaches its two
 * parties, a party line the members the party had, a faction line every
 * account with a character that was in the faction then, a
 * staff line those who were sent it and may still read the channel.
 * Gaining a role, a party or a faction afterwards never opens what was
 * said before. Proximity lines reach only those who were near, since
 * where a player stood then cannot be asked again.</p>
 *
 * <p>Bounded per channel ({@link #MAX_PER_CHANNEL}) and in all
 * ({@link #MAX_TOTAL}), the oldest going first; a replay hands a player
 * at most {@link #MAX_REPLAY_PER_CHANNEL} of a channel and
 * {@link #MAX_REPLAY_TOTAL} in all. In memory only, cleared with the
 * rest of the server's chat state; the audit log is the record that
 * outlives a restart.</p>
 */
public final class ChatHistory {
    /** Messages kept per channel; what a channel's replay can reach back over. */
    public static final int MAX_PER_CHANNEL = 200;
    /** Messages kept in all, whatever the channels. */
    public static final int MAX_TOTAL = 2000;
    /** The most of one channel a joining player is shown. */
    public static final int MAX_REPLAY_PER_CHANNEL = 100;
    /** The most a joining player is shown in all. */
    public static final int MAX_REPLAY_TOTAL = 400;

    private static final LinkedHashMap<Long, Entry> ENTRIES =
            new LinkedHashMap<Long, Entry>();
    private static final Map<String, Integer> COUNT_BY_CHANNEL =
            new HashMap<String, Integer>();

    private ChatHistory() {}

    /**
     * Remembers a distributed message: the account that sent it, the
     * name it was signed with, the line as the sender and as everyone
     * else received it, every account it was sent to, and who may be
     * shown it later. Anything without a server id — a line nobody can
     * name — is not recorded.
     */
    public static synchronized void record(long messageId, UUID authorId,
                                           String author,
                                           LostTalesChatMessagePacket forSender,
                                           LostTalesChatMessagePacket forOthers,
                                           Collection<UUID> recipients,
                                           Audience audience) {
        if (!ChatMessageIds.isServerId(messageId) || author == null
                || author.trim().length() == 0 || forOthers == null
                || forOthers.getChannel() == null) {
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
        String channelId = forOthers.getChannel().getId();
        Entry replaced = ENTRIES.put(Long.valueOf(messageId), new Entry(
                authorId, author.trim(),
                ChatReplyReference.excerptOf(forOthers.getMessage()), seenBy,
                channelId, forSender == null ? forOthers : forSender,
                forOthers, audience == null ? Audience.nobody() : audience,
                forOthers.getTimestampMillis()));
        if (replaced != null) {
            count(replaced.channelId, -1);
        }
        count(channelId, 1);
        trim(channelId);
    }

    /**
     * The quote {@code replier} may show for the message they named, in
     * the channel they are replying in, or {@link ChatReplyReference#NONE}
     * when there is none they may.
     *
     * <p>Having seen a line is not enough. A quote carries the words of
     * the message it names to everyone the reply reaches, so it may only
     * be shown back into the conversation it was said in: the same
     * channel, and for a channel that has conversations of its own — a
     * party, a faction, a whisper — the same one. Otherwise naming an id
     * would quote a private line into a channel of the replier's
     * choosing, which is the one thing this check exists to stop.</p>
     */
    public static synchronized ChatReplyReference quoteFor(
            long messageId, UUID replier,
            ChatChannel replyChannel, String replyScopeValue) {
        Entry entry = ENTRIES.get(Long.valueOf(messageId));
        if (entry == null || replier == null || replyChannel == null
                || !entry.seenBy.contains(replier)) {
            return ChatReplyReference.NONE;
        }
        if (!entry.channelId.equals(replyChannel.getId())) {
            return ChatReplyReference.NONE;
        }
        String quotedScope = entry.forOthers.getScopeValue();
        String replyScope = replyScopeValue == null ? "" : replyScopeValue;
        if (!(quotedScope == null ? "" : quotedScope).equals(replyScope)) {
            return ChatReplyReference.NONE;
        }
        return ChatReplyReference.of(messageId, entry.author, entry.excerpt,
                entry.forOthers.getNameColor());
    }

    /**
     * The quote for a message the Discord bridge names, with no
     * recipient to check it against: a Discord member is nobody's
     * account, so the promise the recipient check keeps is kept by the
     * caller instead — the bridge only ever asks about ids from its own
     * link table, which holds nothing but lines of bridgeable channels
     * that crossed to or from Discord, and it asks only for a line that
     * crossed through the same binding the reply arrived by, so a quote
     * never carries one bound channel's words into another. Nothing
     * private can be named through it.
     */
    public static synchronized ChatReplyReference quoteForDiscordChannel(
            long messageId) {
        Entry entry = ENTRIES.get(Long.valueOf(messageId));
        return entry == null ? ChatReplyReference.NONE
                : ChatReplyReference.of(messageId, entry.author,
                        entry.excerpt, entry.forOthers.getNameColor());
    }

    /**
     * Rewrites what a message says and answers with everyone who has to
     * be told, or null when {@code editor} may not change it: no such
     * message, or one they did not write. The excerpt is recut, so a
     * reply made after the edit quotes what the message says now, and
     * the kept lines say it too, so a replay shows the edited words.
     */
    public static synchronized Set<UUID> applyEdit(long messageId,
                                                   UUID editor,
                                                   String message) {
        Entry entry = authored(messageId, editor);
        if (entry == null) {
            return null;
        }
        LostTalesChatMessagePacket forSender;
        LostTalesChatMessagePacket forOthers;
        try {
            forSender = entry.forSender.withMessage(message);
            forOthers = entry.forOthers.withMessage(message);
        } catch (RuntimeException refused) {
            // The caller validated the text; a line that still cannot be
            // rebuilt is left as it was rather than half-changed.
            return null;
        }
        ENTRIES.put(Long.valueOf(messageId), new Entry(entry.authorId,
                entry.author, ChatReplyReference.excerptOf(message),
                entry.seenBy, entry.channelId, forSender, forOthers,
                entry.audience, entry.timestampMillis));
        return Collections.unmodifiableSet(new HashSet<UUID>(entry.seenBy));
    }

    /**
     * Forgets a message and answers with everyone who has to be told,
     * or null when {@code remover} may not take it back. The id is not
     * reused, so a reply still naming it simply finds nothing, and a
     * replay never shows it again.
     */
    public static synchronized Set<UUID> remove(long messageId,
                                                UUID remover) {
        Entry entry = authored(messageId, remover);
        if (entry == null) {
            return null;
        }
        forget(messageId);
        return Collections.unmodifiableSet(new HashSet<UUID>(entry.seenBy));
    }

    /**
     * Forgets a message whoever wrote it and answers with who wrote it
     * and who has to be told, or null for a message out of reach. What
     * a moderator's removal calls: the author check is the caller's,
     * made against the moderator's own standing rather than the entry.
     */
    public static synchronized Removal removeByOperator(long messageId) {
        Entry entry = ENTRIES.get(Long.valueOf(messageId));
        if (entry == null) {
            return null;
        }
        forget(messageId);
        return new Removal(entry.authorId, entry.author,
                Collections.unmodifiableSet(new HashSet<UUID>(entry.seenBy)));
    }

    /**
     * The recent messages {@code requester} may be shown, oldest first:
     * every kept message newer than {@code sinceMessageId} whose audience
     * admits them, at most {@link #MAX_REPLAY_PER_CHANNEL} of any channel
     * and {@link #MAX_REPLAY_TOTAL} in all — the newest of each channel
     * when there are more. The requester is handed their own copy of a
     * line they sent and everyone else's copy of every other, exactly as
     * they would have been sent it at the time.
     */
    public static synchronized List<LostTalesChatMessagePacket> replayFor(
            Requester requester, long sinceMessageId) {
        if (requester == null) {
            return Collections.emptyList();
        }
        // Newest first, so the per-channel cap keeps the latest.
        List<Entry> admitted = new ArrayList<Entry>();
        List<Entry> all = new ArrayList<Entry>(ENTRIES.values());
        Map<String, Integer> taken = new HashMap<String, Integer>();
        for (int index = all.size() - 1; index >= 0
                && admitted.size() < MAX_REPLAY_TOTAL; index--) {
            Entry entry = all.get(index);
            if (entry.forOthers.getMessageId() <= sinceMessageId
                    || !entry.audience.admits(requester, entry)) {
                continue;
            }
            Integer count = taken.get(entry.channelId);
            int soFar = count == null ? 0 : count.intValue();
            if (soFar >= MAX_REPLAY_PER_CHANNEL) {
                continue;
            }
            taken.put(entry.channelId, Integer.valueOf(soFar + 1));
            admitted.add(entry);
        }
        List<LostTalesChatMessagePacket> lines =
                new ArrayList<LostTalesChatMessagePacket>(admitted.size());
        for (int index = admitted.size() - 1; index >= 0; index--) {
            Entry entry = admitted.get(index);
            lines.add(requester.accountId != null
                    && requester.accountId.equals(entry.authorId)
                    ? entry.forSender : entry.forOthers);
        }
        return lines;
    }

    /**
     * The recent messages of one conversation the requester may be shown,
     * oldest first: every kept message newer than {@code sinceMessageId}
     * that was said in {@code scopeValue} on {@code channel} and whose
     * audience admits them, at most {@link #MAX_REPLAY_PER_CHANNEL}. The
     * requester's own entitlement decides, exactly as it does for a
     * whole replay, so asking about a conversation the account has no
     * character in answers with nothing.
     */
    public static synchronized List<LostTalesChatMessagePacket> replayForContext(
            Requester requester, ChatChannel channel, String scopeValue,
            long sinceMessageId) {
        if (requester == null || channel == null || scopeValue == null
                || scopeValue.length() == 0) {
            return Collections.emptyList();
        }
        String channelId = channel.getId();
        List<Entry> admitted = new ArrayList<Entry>();
        List<Entry> all = new ArrayList<Entry>(ENTRIES.values());
        for (int index = all.size() - 1; index >= 0
                && admitted.size() < MAX_REPLAY_PER_CHANNEL; index--) {
            Entry entry = all.get(index);
            if (entry.forOthers.getMessageId() <= sinceMessageId
                    || !channelId.equals(entry.channelId)
                    || !scopeValue.equals(entry.forOthers.getScopeValue())
                    || !entry.audience.admits(requester, entry)) {
                continue;
            }
            admitted.add(entry);
        }
        List<LostTalesChatMessagePacket> lines =
                new ArrayList<LostTalesChatMessagePacket>(admitted.size());
        for (int index = admitted.size() - 1; index >= 0; index--) {
            Entry entry = admitted.get(index);
            lines.add(requester.accountId != null
                    && requester.accountId.equals(entry.authorId)
                    ? entry.forSender : entry.forOthers);
        }
        return lines;
    }

    /** A message a moderator took back: whose it was and who saw it. */
    public static final class Removal {
        public final UUID authorId;
        public final String author;
        public final Set<UUID> recipients;

        Removal(UUID authorId, String author, Set<UUID> recipients) {
            this.authorId = authorId;
            this.author = author;
            this.recipients = recipients;
        }
    }

    /**
     * Who may be shown a message after the fact. Every part named must
     * agree, and a part left out asks nothing: the accounts it was for,
     * the party it was said in, the faction it was said to, and whether
     * the channel must still be readable by the asker.
     */
    public static final class Audience {
        /** The accounts, or null for anyone the other parts admit. */
        private final Set<UUID> accounts;
        private final UUID partyId;
        private final String factionId;
        /** Whether the channel's read gate is asked again on replay. */
        private final boolean gated;

        private Audience(Collection<UUID> accounts, UUID partyId,
                         String factionId, boolean gated) {
            this.accounts = accounts == null ? null
                    : Collections.unmodifiableSet(new HashSet<UUID>(accounts));
            this.partyId = partyId;
            this.factionId = factionId == null || factionId.length() == 0
                    ? null : factionId;
            this.gated = gated;
        }

        /** Everyone online now and later: an open, world-wide channel. */
        public static Audience everyone() {
            return new Audience(null, null, null, false);
        }

        /**
         * The accounts the line was sent to, and nobody else: a whisper's
         * two parties, the players who were near a proximity line, the
         * readers a staff line reached. {@code gated} asks besides that
         * the channel still be readable by whoever is shown it.
         */
        public static Audience accounts(Collection<UUID> accounts, boolean gated) {
            return new Audience(accounts == null
                    ? Collections.<UUID>emptySet() : accounts, null, null, gated);
        }

        /**
         * The accounts that owned the party's members when the line was
         * said, while they are still in that party.
         */
        public static Audience party(UUID partyId, Collection<UUID> memberOwners) {
            return new Audience(memberOwners == null
                    ? Collections.<UUID>emptySet() : memberOwners, partyId, null,
                    false);
        }

        /**
         * Every account with a character in the faction, made before the
         * line was said. A character's faction is fixed when it is made,
         * so "was in it then" is "existed then", and an account may play
         * any of its characters at will, so owning one is being reachable
         * by it. {@code gated} asks the channel's read gate again besides.
         */
        public static Audience faction(String factionId, Collection<UUID> sentTo,
                                       boolean gated) {
            return new Audience(gated ? sentTo : null, null, factionId, gated);
        }

        /** Nobody: what a line recorded without an audience gets. */
        static Audience nobody() {
            return new Audience(Collections.<UUID>emptySet(), null, null, false);
        }

        boolean admits(Requester requester, Entry entry) {
            if (this.accounts != null && (requester.accountId == null
                    || !this.accounts.contains(requester.accountId))) {
                return false;
            }
            if (this.partyId != null && !this.partyId.equals(requester.partyId)) {
                return false;
            }
            if (this.factionId != null) {
                Long earliest = requester.earliestCharacterIn(this.factionId);
                if (earliest == null
                        || earliest.longValue() > entry.timestampMillis) {
                    return false;
                }
            }
            return !this.gated || requester.readableChannels.contains(entry.channelId);
        }
    }

    /**
     * What the server knows about the player asking for a replay, all of
     * it re-derived from the live server at the moment of asking.
     */
    public static final class Requester {
        final UUID accountId;
        /**
         * Every faction the account has a character in, and when its
         * earliest such character was made. A faction line is replayed
         * to whoever could read it by playing that character.
         */
        final Map<String, Long> ownedFactions;
        /** The party the played identity is in; null for none. */
        final UUID partyId;
        /** The ids of the channels the player may read right now. */
        final Set<String> readableChannels;

        public Requester(UUID accountId, Map<String, Long> ownedFactions,
                         UUID partyId, Collection<ChatChannel> readable) {
            this.accountId = accountId;
            this.ownedFactions = ownedFactions == null
                    ? Collections.<String, Long>emptyMap()
                    : Collections.unmodifiableMap(
                            new HashMap<String, Long>(ownedFactions));
            this.partyId = partyId;
            Set<String> ids = new HashSet<String>();
            if (readable != null) {
                for (ChatChannel channel : readable) {
                    if (channel != null) {
                        ids.add(channel.getId());
                    }
                }
            }
            this.readableChannels = Collections.unmodifiableSet(ids);
        }

        /**
         * An account with one character, in that faction, made then; a
         * faction id of nothing is an account with no character in any.
         * The shape most callers and every test have to describe.
         */
        public Requester(UUID accountId, String factionId, long characterCreatedAt,
                         UUID partyId, Collection<ChatChannel> readable) {
            this(accountId, oneFaction(factionId, characterCreatedAt), partyId,
                    readable);
        }

        private static Map<String, Long> oneFaction(String factionId,
                                                    long characterCreatedAt) {
            if (factionId == null || factionId.length() == 0) {
                return Collections.emptyMap();
            }
            Map<String, Long> owned = new HashMap<String, Long>();
            owned.put(factionId, Long.valueOf(characterCreatedAt));
            return owned;
        }

        /** When the earliest character in that faction was made; null for none. */
        Long earliestCharacterIn(String factionId) {
            return factionId == null ? null : this.ownedFactions.get(factionId);
        }
    }

    /** The message, but only for the account that wrote it. */
    private static Entry authored(long messageId, UUID account) {
        Entry entry = ENTRIES.get(Long.valueOf(messageId));
        return entry == null || account == null || entry.authorId == null
                || !account.equals(entry.authorId) ? null : entry;
    }

    private static void forget(long messageId) {
        Entry gone = ENTRIES.remove(Long.valueOf(messageId));
        if (gone != null) {
            count(gone.channelId, -1);
        }
    }

    private static void count(String channelId, int delta) {
        Integer current = COUNT_BY_CHANNEL.get(channelId);
        int next = (current == null ? 0 : current.intValue()) + delta;
        if (next <= 0) {
            COUNT_BY_CHANNEL.remove(channelId);
        } else {
            COUNT_BY_CHANNEL.put(channelId, Integer.valueOf(next));
        }
    }

    /** Drops the oldest of a channel past its cap, then the oldest of all past the total. */
    private static void trim(String channelId) {
        Integer count = COUNT_BY_CHANNEL.get(channelId);
        if (count != null && count.intValue() > MAX_PER_CHANNEL) {
            Iterator<Map.Entry<Long, Entry>> oldest = ENTRIES.entrySet().iterator();
            while (oldest.hasNext()) {
                Map.Entry<Long, Entry> candidate = oldest.next();
                if (channelId.equals(candidate.getValue().channelId)) {
                    oldest.remove();
                    count(channelId, -1);
                    break;
                }
            }
        }
        while (ENTRIES.size() > MAX_TOTAL) {
            Iterator<Map.Entry<Long, Entry>> oldest = ENTRIES.entrySet().iterator();
            Map.Entry<Long, Entry> gone = oldest.next();
            oldest.remove();
            count(gone.getValue().channelId, -1);
        }
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        ENTRIES.clear();
        COUNT_BY_CHANNEL.clear();
    }

    /** Test and diagnostics hook: messages currently within reach. */
    static synchronized int size() {
        return ENTRIES.size();
    }

    /** One distributed message: who wrote it, how it reads, who saw it, who may. */
    private static final class Entry {
        final UUID authorId;
        final String author;
        final String excerpt;
        final Set<UUID> seenBy;
        final String channelId;
        final LostTalesChatMessagePacket forSender;
        final LostTalesChatMessagePacket forOthers;
        final Audience audience;
        final long timestampMillis;

        Entry(UUID authorId, String author, String excerpt, Set<UUID> seenBy,
              String channelId, LostTalesChatMessagePacket forSender,
              LostTalesChatMessagePacket forOthers, Audience audience,
              long timestampMillis) {
            this.authorId = authorId;
            this.author = author;
            this.excerpt = excerpt;
            this.seenBy = seenBy;
            this.channelId = channelId;
            this.forSender = forSender;
            this.forOthers = forOthers;
            this.audience = audience;
            this.timestampMillis = timestampMillis;
        }
    }
}
