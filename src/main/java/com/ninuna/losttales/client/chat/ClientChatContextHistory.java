package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatContextHistoryPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Asks the server what was said in one conversation of a channel that
 * has more than one, the first time this client reads it. The login
 * replay hands over every conversation the account was entitled to
 * then; a player who reads the chat as another of their characters
 * afterwards opens a conversation this client has never been sent.
 *
 * <p>Asked once per conversation per session. The request carries the
 * newest message already held for it, so the answer adds only what is
 * missing — the client appends replayed lines without clearing, and a
 * second answer that repeated the first would show every line twice.</p>
 */
final class ClientChatContextHistory {

    /** The conversations already asked about, by tab id. */
    private static final Set<String> ASKED = new HashSet<String>();
    /** The newest message seen in each conversation, by tab id. */
    private static final Map<String, Long> NEWEST = new HashMap<String, Long>();

    private ClientChatContextHistory() {}

    /**
     * Remembers the newest message a conversation has been shown, so a
     * later request asks only for what came after it.
     */
    static synchronized void remember(ChatTab tab, long messageId) {
        if (tab == null || !ChatMessageIds.isServerId(messageId)
                || !isScoped(tab)) {
            return;
        }
        Long newest = NEWEST.get(tab.id());
        if (newest == null || messageId > newest.longValue()) {
            NEWEST.put(tab.id(), Long.valueOf(messageId));
        }
    }

    /**
     * Asks for the conversation the tab stands for, unless this session
     * already has. Answers whether a request went out, for the tests and
     * for the caller's own bookkeeping.
     */
    static synchronized boolean request(ChatTab tab, String scopeValue) {
        if (tab == null || !isScoped(tab) || scopeValue == null
                || scopeValue.length() == 0 || !ASKED.add(tab.id())) {
            return false;
        }
        Long newest = NEWEST.get(tab.id());
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatContextHistoryPacket(tab.getChannel(), scopeValue,
                        newest == null ? ChatMessageIds.NONE : newest.longValue()));
        return true;
    }

    /** Whether the tab names one conversation of a channel that has several. */
    private static boolean isScoped(ChatTab tab) {
        return tab != null && !tab.isWhisper() && tab.getChannel() != null
                && tab.getChannel().isIdentityScoped()
                && tab.getOwnerKey().length() > 0;
    }

    /** Test hook: whether the conversation has been asked about. */
    static synchronized boolean hasAsked(ChatTab tab) {
        return tab != null && ASKED.contains(tab.id());
    }

    /** The conversation is asked about again in the next world. */
    static synchronized void clear() {
        ASKED.clear();
        NEWEST.clear();
    }

    /**
     * The conversation the tab stands for, if the roster still names the
     * identity it is read as; empty when it does not, which is what a
     * request must not be made for.
     */
    static String scopeOf(ChatTab tab) {
        if (!isScoped(tab)) {
            return "";
        }
        ChatChannel channel = tab.getChannel();
        return ClientChatChannelState.scopeOfIdentity(channel, tab.getOwnerKey());
    }
}
