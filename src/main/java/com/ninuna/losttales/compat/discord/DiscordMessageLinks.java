package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bridge's memory of which game message is which Discord message,
 * in both directions: a game line posted to a webhook learns its
 * Discord id from the post's reply, and a Discord line delivered into
 * the game is filed under the id the chat gave it. It is what lets a
 * reply cross the bridge whole — a Discord reply names a Discord id and
 * the game knows it as a message, a game reply names a message and the
 * webhook post can point back at the Discord original — and what lets
 * an edit or a removal follow its message across: the webhook can
 * rewrite or take back its own posts by exactly these ids.
 *
 * <p>A game message may have several <em>copies</em>, one per Discord
 * channel its game channel goes to, each with the reply header it was
 * posted under (an edit rewrites the whole post and has to open with
 * the same header) and the webhook it went through (a correction has
 * to go where the post went); a Discord line has one, in the channel it
 * was read from. Every copy names its destination — the Discord channel
 * it lives in where that is known, else the webhook it went through —
 * which is how a reply finds the copy in the very channel it is being
 * posted to, and how a quote is kept from carrying words from one bound
 * channel into another: the bridge checks the destination before asking
 * for one.</p>
 *
 * <p>Only lines of bridgeable channels are ever linked, so a Discord id
 * can never lead to a line from a private one. Bounded to the newest
 * {@link #MAX_LINKS} messages — the same order of reach the chat's own
 * {@link com.ninuna.losttales.chat.server.ChatMessageLog} has — and
 * cleared when the bridge stops: a link is about one session, exactly
 * like the history it indexes into. Written from the server thread
 * (inbound) and the bridge's worker (outbound), so every touch is
 * synchronized.</p>
 */
final class DiscordMessageLinks {
    /** Messages remembered; past it the oldest message's copies go first. */
    private static final int MAX_LINKS = 512;

    private final LinkedHashMap<Long, List<Copy>> copiesByMessage =
            new LinkedHashMap<Long, List<Copy>>();
    private final LinkedHashMap<String, Long> messageIdByDiscord =
            new LinkedHashMap<String, Long>();

    /** One Discord copy of a game message. */
    static final class Copy {
        final String discordId;
        /** The Discord channel the copy is in, or the webhook it went through. */
        final String destination;
        /** The webhook the copy was posted through; empty for a Discord line. */
        final String webhookUrl;
        /** The reply header the copy opened with; empty for none. */
        final String header;

        Copy(String discordId, String destination, String webhookUrl,
             String header) {
            this.discordId = discordId;
            this.destination = destination == null ? "" : destination;
            this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
            this.header = header == null ? "" : header;
        }
    }

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
     * As above, saying as well which destination the copy is in, so a
     * correction and a quote find the copy in the right channel.
     */
    void link(long messageId, String discordId, String header,
              String destination) {
        link(messageId, discordId, header, destination, "");
    }

    /**
     * As above, with the webhook the copy was posted through, so an edit
     * or a removal can be sent where the post went. A message already
     * holding a copy in the same destination has that copy replaced.
     */
    synchronized void link(long messageId, String discordId, String header,
                           String destination, String webhookUrl) {
        if (!ChatMessageIds.isServerId(messageId) || discordId == null
                || discordId.length() == 0) {
            return;
        }
        Long key = Long.valueOf(messageId);
        List<Copy> copies = this.copiesByMessage.remove(key);
        if (copies == null) {
            copies = new ArrayList<Copy>(2);
        }
        Copy replacement = new Copy(discordId, destination, webhookUrl, header);
        for (Iterator<Copy> held = copies.iterator(); held.hasNext();) {
            Copy copy = held.next();
            if (copy.destination.equals(replacement.destination)) {
                // A message relinked in a destination drops its old pair
                // there, so the reverse map stays exactly as bounded as
                // the forward one.
                held.remove();
                this.messageIdByDiscord.remove(copy.discordId);
            }
        }
        copies.add(replacement);
        // Re-put, so the message counts as the newest remembered.
        this.copiesByMessage.put(key, copies);
        this.messageIdByDiscord.put(discordId, key);
        while (this.copiesByMessage.size() > MAX_LINKS) {
            Iterator<Map.Entry<Long, List<Copy>>> oldest =
                    this.copiesByMessage.entrySet().iterator();
            Map.Entry<Long, List<Copy>> entry = oldest.next();
            oldest.remove();
            for (Copy copy : entry.getValue()) {
                this.messageIdByDiscord.remove(copy.discordId);
            }
        }
    }

    /** The Discord id of a game message's first copy, or empty for none known. */
    synchronized String discordIdOf(long messageId) {
        List<Copy> copies = this.copiesByMessage.get(Long.valueOf(messageId));
        return copies == null || copies.isEmpty() ? "" : copies.get(0).discordId;
    }

    /** The Discord id of a game message's copy in {@code destination}, or empty. */
    synchronized String discordIdOf(long messageId, String destination) {
        List<Copy> copies = this.copiesByMessage.get(Long.valueOf(messageId));
        if (copies == null || destination == null) {
            return "";
        }
        for (Copy copy : copies) {
            if (copy.destination.equals(destination)) {
                return copy.discordId;
            }
        }
        return "";
    }

    /** Whether a game message has a copy in {@code destination}. */
    synchronized boolean hasCopyIn(long messageId, String destination) {
        return discordIdOf(messageId, destination).length() > 0;
    }

    /**
     * The copy of a game message that went through {@code webhookUrl},
     * or null: what an edit or a removal sent through that webhook
     * corrects.
     */
    synchronized Copy copyThrough(long messageId, String webhookUrl) {
        List<Copy> copies = this.copiesByMessage.get(Long.valueOf(messageId));
        if (copies == null || webhookUrl == null || webhookUrl.length() == 0) {
            return null;
        }
        for (Copy copy : copies) {
            if (copy.webhookUrl.equals(webhookUrl)) {
                return copy;
            }
        }
        return null;
    }

    /** The reply header a game message's first copy was posted under; empty for none. */
    synchronized String headerOf(long messageId) {
        List<Copy> copies = this.copiesByMessage.get(Long.valueOf(messageId));
        return copies == null || copies.isEmpty() ? "" : copies.get(0).header;
    }

    /** The destination of a game message's first copy; empty for none known. */
    synchronized String destinationOf(long messageId) {
        List<Copy> copies = this.copiesByMessage.get(Long.valueOf(messageId));
        return copies == null || copies.isEmpty() ? "" : copies.get(0).destination;
    }

    /**
     * Every Discord copy of a game message, in the order they were
     * made; empty for a message not known. A snapshot: safe to walk
     * while posts go on being linked.
     */
    synchronized List<Copy> copiesOf(long messageId) {
        List<Copy> copies = this.copiesByMessage.get(Long.valueOf(messageId));
        return copies == null ? Collections.<Copy>emptyList()
                : Collections.unmodifiableList(new ArrayList<Copy>(copies));
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
        this.copiesByMessage.clear();
        this.messageIdByDiscord.clear();
    }

    /** Test hook: messages currently held. */
    synchronized int size() {
        return this.copiesByMessage.size();
    }
}
