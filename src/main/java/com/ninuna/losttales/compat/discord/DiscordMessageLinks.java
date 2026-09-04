package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bridge's memory of which game message is which Discord message,
 * in both directions: a game line posted to the webhook learns its
 * Discord id from the post's reply, and a Discord line delivered into
 * the game is filed under the id the chat gave it. It is what lets a
 * reply cross the bridge whole — a Discord reply names a Discord id and
 * the game knows it as a message, a game reply names a message and the
 * webhook post can point back at the Discord original — and what lets
 * an edit or a removal follow its message across: the webhook can
 * rewrite or take back its own posts by exactly these ids. A posted
 * line's card (its channel, colour, account, time and reply header) is
 * kept beside its id, since an edit rewrites the whole post and the
 * card has to be drawn again, and so is the binding it went through: a bound channel may live in another Discord
 * channel than the game's own, a correction has to go where the post
 * went, and a quote must never carry words from one bound channel into
 * another — the bridge checks the binding before asking for one.
 *
 * <p>Only lines of bridgeable channels are ever linked, so a Discord id
 * can never lead to a line from a private one. Bounded to the newest
 * {@link #MAX_LINKS} pairs — the same
 * order of reach the chat's own
 * {@link com.ninuna.losttales.chat.server.ChatMessageLog} has — and
 * cleared when the bridge stops: a link is about one session, exactly
 * like the history it indexes into. Written from the server thread
 * (inbound) and the bridge's worker (outbound), so every touch is
 * synchronized.</p>
 */
final class DiscordMessageLinks {
    /** Pairs remembered; past it the oldest link goes first. */
    private static final int MAX_LINKS = 512;

    private final LinkedHashMap<Long, Entry> entryByMessage =
            new LinkedHashMap<Long, Entry>();
    private final LinkedHashMap<String, Long> messageIdByDiscord =
            new LinkedHashMap<String, Long>();

    /** Remembers one pair; ignores anything without both halves. */
    void link(long messageId, String discordId) {
        link(messageId, discordId, "", "");
    }

    /**
     * As above, with the reply header the message was posted under, for
     * an edit to open with again; empty for a headerless line.
     */
    void link(long messageId, String discordId, String header) {
        link(messageId, discordId, header, "");
    }

    /**
     * As above, saying as well which binding the message crossed
     * through, so an edit or a removal follows it to the right webhook
     * and a quote stays within its own bound channel.
     */
    void link(long messageId, String discordId, String header,
              String bindingKey) {
        link(messageId, discordId, bindingKey,
                DiscordJson.LineCard.NONE.withHeader(header));
    }

    /**
     * As above, with the whole card the line was posted as, so an edit
     * can draw the same card again.
     */
    synchronized void link(long messageId, String discordId, String bindingKey,
                           DiscordJson.LineCard card) {
        if (!ChatMessageIds.isServerId(messageId) || discordId == null
                || discordId.length() == 0) {
            return;
        }
        Entry previous = this.entryByMessage.put(Long.valueOf(messageId),
                new Entry(discordId, bindingKey == null ? "" : bindingKey,
                        card == null ? DiscordJson.LineCard.NONE : card));
        if (previous != null && !previous.discordId.equals(discordId)) {
            // A message relinked drops its old pair, so the reverse map
            // stays exactly as bounded as the forward one.
            this.messageIdByDiscord.remove(previous.discordId);
        }
        this.messageIdByDiscord.put(discordId, Long.valueOf(messageId));
        while (this.entryByMessage.size() > MAX_LINKS) {
            Iterator<Map.Entry<Long, Entry>> oldest =
                    this.entryByMessage.entrySet().iterator();
            Map.Entry<Long, Entry> entry = oldest.next();
            oldest.remove();
            this.messageIdByDiscord.remove(entry.getValue().discordId);
        }
    }

    /** The Discord id of a game message, or empty for none known. */
    synchronized String discordIdOf(long messageId) {
        Entry entry = this.entryByMessage.get(Long.valueOf(messageId));
        return entry == null ? "" : entry.discordId;
    }

    /** The reply header a game message was posted under; empty for none. */
    synchronized String headerOf(long messageId) {
        return cardOf(messageId).header;
    }

    /**
     * The card a game message was posted as, for an edit to draw again;
     * {@link DiscordJson.LineCard#NONE} for a message not known.
     */
    synchronized DiscordJson.LineCard cardOf(long messageId) {
        Entry entry = this.entryByMessage.get(Long.valueOf(messageId));
        return entry == null ? DiscordJson.LineCard.NONE : entry.card;
    }

    /** The binding a game message crossed through; empty for none known. */
    synchronized String bindingKeyOf(long messageId) {
        Entry entry = this.entryByMessage.get(Long.valueOf(messageId));
        return entry == null ? "" : entry.bindingKey;
    }

    /**
     * The game message a Discord id names, or
     * {@link ChatMessageIds#NONE} for none known.
     */
    synchronized long messageIdOf(String discordId) {
        Long messageId = discordId == null ? null
                : this.messageIdByDiscord.get(discordId);
        return messageId == null ? ChatMessageIds.NONE
                : messageId.longValue();
    }

    /** Forgets everything; what a stopping bridge calls. */
    synchronized void clear() {
        this.entryByMessage.clear();
        this.messageIdByDiscord.clear();
    }

    /** Test hook: pairs currently held. */
    synchronized int size() {
        return this.entryByMessage.size();
    }

    /** One posted or delivered message: its Discord id, binding and card. */
    private static final class Entry {
        final String discordId;
        final String bindingKey;
        final DiscordJson.LineCard card;

        Entry(String discordId, String bindingKey, DiscordJson.LineCard card) {
            this.discordId = discordId;
            this.bindingKey = bindingKey;
            this.card = card;
        }
    }
}
