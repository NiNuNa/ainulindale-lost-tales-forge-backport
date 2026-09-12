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
 * {@link com.ninuna.losttales.chat.server.ChatHistory} has. The links
 * belong to the world, like the history they index into: a bridge that
 * stops or reloads keeps them, and the world save keeps them across a
 * restart through {@link DiscordMessageLinkWorldData}, without their
 * webhooks — a copy brought back names the binding it went through, and
 * the bridge finds its webhook again among the bindings of its
 * message's channel. What may be done with a copy at all is
 * {@link DiscordCopyLiveness}'s to say; a link is never dropped because
 * its pair is unbound. Written from the server thread
 * (inbound) and the bridge's worker (outbound), so every touch is
 * synchronized, and every change moves the {@link #revision()} on,
 * which is how the save knows it has something to write.</p>
 */
final class DiscordMessageLinks {
    /** Messages remembered, as many as the history keeps in all; past it the oldest message's copies go first. */
    static final int MAX_LINKS = 2000;

    private final LinkedHashMap<Long, List<Copy>> copiesByMessage =
            new LinkedHashMap<Long, List<Copy>>();
    private final LinkedHashMap<String, Long> messageIdByDiscord =
            new LinkedHashMap<String, Long>();
    /** Moves on with every change; what the save compares with what it last wrote. */
    private long revision;

    /** One Discord copy of a game message. */
    static final class Copy {
        final String discordId;
        /**
         * The Discord channel the copy is in ({@code channel:<id>}). For
         * a post whose channel Discord never named: the webhook it went
         * through, or, brought back from the save, the binding it went
         * through ({@code binding:<id>}).
         */
        final String destination;
        /**
         * The webhook the copy was posted through; empty for a Discord
         * line, and for a copy brought back from the save, whose webhook
         * the bridge finds again among the bindings of its message's
         * channel.
         */
        final String webhookUrl;
        /** The reply header the copy opened with; empty for none. */
        final String header;
        /** The id of the binding the copy was posted through; empty for a Discord line. */
        final String bindingId;

        Copy(String discordId, String destination, String webhookUrl,
             String header) {
            this(discordId, destination, webhookUrl, header, "");
        }

        Copy(String discordId, String destination, String webhookUrl,
             String header, String bindingId) {
            this.discordId = discordId;
            this.destination = destination == null ? "" : destination;
            this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
            this.header = header == null ? "" : header;
            this.bindingId = bindingId == null ? "" : bindingId;
        }
    }

    /** One message as the save keeps it: its id and its copies, in the order they were made. */
    static final class SavedLink {
        final long messageId;
        final List<SavedCopy> copies;

        SavedLink(long messageId, List<SavedCopy> copies) {
            this.messageId = messageId;
            this.copies = copies == null ? Collections.<SavedCopy>emptyList()
                    : Collections.unmodifiableList(new ArrayList<SavedCopy>(copies));
        }
    }

    /**
     * One copy as the save keeps it. It names no webhook, since a
     * webhook URL is a credential: the binding stands in for it.
     */
    static final class SavedCopy {
        final String discordId;
        /** {@code channel:<id>}, or {@code binding:<id>} for a post whose channel Discord never named. */
        final String destination;
        /** The binding the copy was posted through; empty for a Discord line. */
        final String bindingId;
        final String header;

        SavedCopy(String discordId, String destination, String bindingId,
                  String header) {
            this.discordId = discordId == null ? "" : discordId;
            this.destination = destination == null ? "" : destination;
            this.bindingId = bindingId == null ? "" : bindingId;
            this.header = header == null ? "" : header;
        }
    }

    /** What the save writes: every message held, oldest first, as of one revision. */
    static final class Snapshot {
        final List<SavedLink> links;
        final long revision;

        Snapshot(List<SavedLink> links, long revision) {
            this.links = Collections.unmodifiableList(links);
            this.revision = revision;
        }
    }

    /** Which game messages are still kept; what a restore asks of each link. */
    interface MessageIndex {
        boolean holds(long messageId);
    }

    /**
     * Remembers one copy of a game message: its Discord id, the reply
     * header it was posted under (for an edit to open with again; empty
     * for a headerless line), the destination it is in (so a correction
     * and a quote find the copy in the right channel) and the webhook it
     * went through (so an edit or a removal is sent where the post
     * went). Anything without both ids is ignored; a message already
     * holding a copy in the same destination has that copy replaced.
     */
    synchronized void link(long messageId, String discordId, String header,
                           String destination, String webhookUrl) {
        link(messageId, discordId, header, destination, webhookUrl, "");
    }

    /**
     * The same, naming the binding a post went through as well: what
     * the save keeps of the copy in place of its webhook.
     */
    synchronized void link(long messageId, String discordId, String header,
                           String destination, String webhookUrl,
                           String bindingId) {
        if (!ChatMessageIds.isServerId(messageId) || discordId == null
                || discordId.length() == 0) {
            return;
        }
        Long key = Long.valueOf(messageId);
        List<Copy> copies = this.copiesByMessage.remove(key);
        if (copies == null) {
            copies = new ArrayList<Copy>(2);
        }
        Copy replacement = new Copy(discordId, destination, webhookUrl, header,
                bindingId);
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
        this.revision++;
        trimToBound();
    }

    /** Forgets the oldest messages' copies past {@link #MAX_LINKS}. */
    private void trimToBound() {
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

    /** Forgets everything. */
    synchronized void clear() {
        this.copiesByMessage.clear();
        this.messageIdByDiscord.clear();
        this.revision++;
    }

    /** Moves on with every change; the save writes when it differs from what it wrote last. */
    synchronized long revision() {
        return this.revision;
    }

    /**
     * Every message held, oldest first, as the save writes it, with the
     * revision it is as of. A copy is written with its channel. A post
     * whose channel Discord never named has no other name than its
     * webhook's URL, so it is written under its binding instead, and one
     * with no binding either is left out. No webhook URL is part of it.
     */
    synchronized Snapshot snapshot() {
        List<SavedLink> saved = new ArrayList<SavedLink>(this.copiesByMessage.size());
        for (Map.Entry<Long, List<Copy>> entry : this.copiesByMessage.entrySet()) {
            List<SavedCopy> copies = new ArrayList<SavedCopy>(entry.getValue().size());
            for (Copy copy : entry.getValue()) {
                String destination = savedDestinationOf(copy);
                if (destination.length() > 0) {
                    copies.add(new SavedCopy(copy.discordId, destination,
                            copy.bindingId, copy.header));
                }
            }
            if (!copies.isEmpty()) {
                saved.add(new SavedLink(entry.getKey().longValue(), copies));
            }
        }
        return new Snapshot(saved, this.revision);
    }

    /** How a copy's destination is saved; empty for one that cannot be. */
    private static String savedDestinationOf(Copy copy) {
        if (copy.destination.startsWith(DiscordMessageLinkNbtCodec.CHANNEL_PREFIX)
                || copy.destination.startsWith(DiscordMessageLinkNbtCodec.BINDING_PREFIX)) {
            return copy.destination;
        }
        return copy.bindingId.length() == 0 ? ""
                : DiscordMessageLinkNbtCodec.BINDING_PREFIX + copy.bindingId;
    }

    /**
     * Takes the save's links back in place of whatever is held, oldest
     * first, for the messages {@code index} says are still kept: an id
     * whose message is gone promises nothing about the message a later
     * id names. A copy brought back has no webhook of its own. A Discord
     * id already taken and a second copy in one destination are passed
     * over, and the bound is {@link #link}'s. Answers how many messages
     * are held afterwards.
     */
    synchronized int restore(List<SavedLink> saved, MessageIndex index) {
        this.copiesByMessage.clear();
        this.messageIdByDiscord.clear();
        this.revision++;
        if (saved == null || index == null) {
            return 0;
        }
        for (SavedLink link : saved) {
            if (link == null || !ChatMessageIds.isServerId(link.messageId)
                    || !index.holds(link.messageId)) {
                continue;
            }
            Long key = Long.valueOf(link.messageId);
            if (this.copiesByMessage.containsKey(key)) {
                continue;
            }
            List<Copy> copies = new ArrayList<Copy>(link.copies.size());
            for (SavedCopy copy : link.copies) {
                if (copy.discordId.length() == 0
                        || this.messageIdByDiscord.containsKey(copy.discordId)
                        || holdsDestination(copies, copy.destination)) {
                    continue;
                }
                copies.add(new Copy(copy.discordId, copy.destination, "",
                        copy.header, copy.bindingId));
                this.messageIdByDiscord.put(copy.discordId, key);
            }
            if (!copies.isEmpty()) {
                this.copiesByMessage.put(key, copies);
            }
        }
        trimToBound();
        return this.copiesByMessage.size();
    }

    private static boolean holdsDestination(List<Copy> copies, String destination) {
        for (Copy copy : copies) {
            if (copy.destination.equals(destination)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The Discord ids of the Discord members' own lines held in
     * {@code destination}, oldest first: what a newly started worker
     * watches again for edits and deletions while it polls.
     */
    synchronized List<String> discordLinesIn(String destination) {
        List<String> ids = new ArrayList<String>();
        if (destination == null) {
            return ids;
        }
        for (List<Copy> copies : this.copiesByMessage.values()) {
            for (Copy copy : copies) {
                if (copy.bindingId.length() == 0 && copy.webhookUrl.length() == 0
                        && copy.destination.equals(destination)) {
                    ids.add(copy.discordId);
                }
            }
        }
        return ids;
    }

    /** Test hook: messages currently held. */
    synchronized int size() {
        return this.copiesByMessage.size();
    }
}
