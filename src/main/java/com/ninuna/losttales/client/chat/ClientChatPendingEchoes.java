package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The messages this client has shown but the server has not yet
 * confirmed.
 *
 * <p>A message is drawn the moment it is typed rather than when it comes
 * back, so the conversation answers the keyboard instead of the round
 * trip. That line is a promise, not a fact: it is faint until the
 * server's own copy of it arrives, and it is only then that it becomes
 * the message everything else can act on — replies, edits and quotes
 * all name a message by the id the server stamped on it, and a line
 * that has not been confirmed has no such id to give.</p>
 *
 * <p>Each is remembered under a name the client picks for it and sends
 * along, which the server hands back on the sender's copy alone. Names
 * are matched only against messages this player signed, so one arriving
 * on somebody else's line — which cannot happen, but costs nothing to
 * rule out — is ignored rather than allowed to replace a line it has
 * nothing to do with.</p>
 *
 * <p>Nothing waits forever: a promise the server never answers is
 * marked undelivered, so a dropped message is visibly dropped rather
 * than sitting faint in the history looking sent. Bounded, and cleared
 * with the rest of the client's chat state.</p>
 */
final class ClientChatPendingEchoes {
    /**
     * How long a message may stay unconfirmed. Generously past any
     * round trip a playable connection has, since the cost of waiting
     * too little is calling a delivered message lost.
     */
    static final long TIMEOUT_MILLIS = 10L * 1000L;
    /**
     * How faint an unconfirmed line is drawn, out of a whole. Enough to
     * read as not-yet-said without becoming hard to read.
     */
    static final float PENDING_OPACITY = 0.55F;
    /** More than can be in flight on any connection worth using. */
    private static final int MAX_PENDING = 64;

    private static final LinkedHashMap<Long, Pending> BY_NONCE =
            new LinkedHashMap<Long, Pending>();
    private static final Map<Integer, Long> BY_LINE =
            new HashMap<Integer, Long>();
    /** Names run upward from one; zero is reserved for "no name". */
    private static long lastNonce;

    private ClientChatPendingEchoes() {}

    /** A name for one outgoing message; never zero. */
    static synchronized long nextNonce() {
        lastNonce++;
        if (lastNonce == 0L) {
            lastNonce = 1L;
        }
        return lastNonce;
    }

    /** Remembers a line that is on screen but not yet confirmed. */
    static synchronized void remember(long nonce, int chatLineId,
                                      LostTalesChatMessagePacket packet,
                                      ChatTab tab, int[] showcaseIds,
                                      long sentAtMillis) {
        if (nonce == 0L || packet == null || tab == null) {
            return;
        }
        BY_NONCE.put(Long.valueOf(nonce), new Pending(nonce, chatLineId,
                packet, tab, showcaseIds, sentAtMillis));
        BY_LINE.put(Integer.valueOf(chatLineId), Long.valueOf(nonce));
        while (BY_NONCE.size() > MAX_PENDING) {
            Iterator<Map.Entry<Long, Pending>> oldest =
                    BY_NONCE.entrySet().iterator();
            Map.Entry<Long, Pending> entry = oldest.next();
            oldest.remove();
            forgetLine(entry.getValue());
        }
    }

    /**
     * The line that message was shown on, and forgets it: the promise
     * has been kept and the line is about to become the real one.
     */
    static synchronized Pending take(long nonce) {
        Pending pending = BY_NONCE.remove(Long.valueOf(nonce));
        if (pending != null) {
            forgetLine(pending);
        }
        return pending;
    }

    /** Whether a drawn line is still waiting to be confirmed. */
    static synchronized boolean isPending(int chatLineId) {
        return BY_LINE.containsKey(Integer.valueOf(chatLineId));
    }

    /**
     * Every promise older than the timeout, forgotten as it is handed
     * back: each is now a message to be marked undelivered, and marking
     * it is the caller's to do.
     */
    static synchronized List<Pending> expired(long nowMillis) {
        List<Pending> gone = null;
        Iterator<Map.Entry<Long, Pending>> entries =
                BY_NONCE.entrySet().iterator();
        while (entries.hasNext()) {
            Pending pending = entries.next().getValue();
            if (nowMillis - pending.sentAtMillis < TIMEOUT_MILLIS) {
                // Insertion order is send order, so nothing after this
                // one can have expired either.
                break;
            }
            entries.remove();
            forgetLine(pending);
            if (gone == null) {
                gone = new ArrayList<Pending>();
            }
            gone.add(pending);
        }
        return gone == null
                ? java.util.Collections.<Pending>emptyList() : gone;
    }

    /** Only if the line still points at this promise. */
    private static void forgetLine(Pending pending) {
        Long nonce = BY_LINE.get(Integer.valueOf(pending.chatLineId));
        if (nonce != null && nonce.longValue() == pending.nonce) {
            BY_LINE.remove(Integer.valueOf(pending.chatLineId));
        }
    }

    static synchronized void clear() {
        BY_NONCE.clear();
        BY_LINE.clear();
        lastNonce = 0L;
    }

    /** Test and diagnostics hook: messages still in flight. */
    static synchronized int size() {
        return BY_NONCE.size();
    }

    /** One shown-but-unconfirmed message. */
    static final class Pending {
        final long nonce;
        final int chatLineId;
        final LostTalesChatMessagePacket packet;
        final ChatTab tab;
        final int[] showcaseIds;
        final long sentAtMillis;

        private Pending(long nonce, int chatLineId,
                        LostTalesChatMessagePacket packet, ChatTab tab,
                        int[] showcaseIds, long sentAtMillis) {
            this.nonce = nonce;
            this.chatLineId = chatLineId;
            this.packet = packet;
            this.tab = tab;
            this.showcaseIds = showcaseIds;
            this.sentAtMillis = sentAtMillis;
        }
    }
}
