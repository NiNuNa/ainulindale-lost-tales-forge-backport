package com.ninuna.losttales.client.chat;

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

    /** The conversations already asked about, by page id. */
    private static final Set<String> ASKED = new HashSet<String>();
    /** The newest message seen in each conversation, by page id. */
    private static final Map<String, Long> NEWEST = new HashMap<String, Long>();

    private ClientChatContextHistory() {}

    /**
     * Remembers the newest message a conversation has been shown, so a
     * later request asks only for what came after it.
     */
    static synchronized void remember(ConversationPage page, long messageId) {
        if (page == null || !ChatMessageIds.isServerId(messageId)
                || !isScoped(page)) {
            return;
        }
        Long newest = NEWEST.get(page.id());
        if (newest == null || messageId > newest.longValue()) {
            NEWEST.put(page.id(), Long.valueOf(messageId));
        }
    }

    /**
     * Asks for the conversation the page stands for, unless this session
     * already has.
     */
    static synchronized void request(ConversationPage page, String scopeValue) {
        if (page == null || !isScoped(page) || scopeValue == null
                || scopeValue.length() == 0 || !ASKED.add(page.id())) {
            return;
        }
        Long newest = NEWEST.get(page.id());
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesChatContextHistoryPacket(page.getChannel(), scopeValue,
                        newest == null ? ChatMessageIds.NONE : newest.longValue()));
    }

    /** Whether the page names one conversation of a channel that has several. */
    private static boolean isScoped(ConversationPage page) {
        return page != null && !page.isWhisper() && page.getChannel() != null
                && page.getChannel().isScoped()
                && page.getOwnerKey().length() > 0;
    }

    /** The conversation is asked about again in the next world. */
    static synchronized void clear() {
        ASKED.clear();
        NEWEST.clear();
    }

    /**
     * The conversation the page stands for; empty when it stands for
     * none, which is what a request must not be made for. A scoped
     * page is named by its conversation, so the page is the answer.
     */
    static String scopeOf(ConversationPage page) {
        return isScoped(page) ? page.getOwnerKey() : "";
    }
}
