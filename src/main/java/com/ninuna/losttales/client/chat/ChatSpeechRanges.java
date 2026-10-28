package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatSpeechRange;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * The range each copy of a conversation heard nearby speaks at (CHAT-37):
 * whisper, talk or shout, picked on the range button beside the identity
 * button. A copy that never picked one speaks at the range the client's
 * Speech Range setting names. Each copy keeps its own for the session, by
 * the id of the row entry its window holds, as its voice is kept; a
 * duplicate takes its source's, and a copy opening anew starts at the
 * setting's. Cleared with the other session caches.
 */
final class ChatSpeechRanges {
    private static final Map<String, ChatSpeechRange> PICKED =
            new HashMap<String, ChatSpeechRange>();

    private ChatSpeechRanges() {}

    /** What a copy's range is kept by: the id of the row entry its window holds. */
    private static String keyOf(ConversationPage copy) {
        ConversationPage row = ConversationPage.row(copy);
        return row == null ? null : row.id();
    }

    /** Whether the copy is of a conversation heard nearby, which has a range. */
    static boolean hasRange(ConversationPage copy) {
        return copy != null && !copy.isNpc() && !copy.isWhisper()
                && ChatSpeechRange.appliesTo(copy.getChannel());
    }

    /** The range the client's setting names: what a copy speaks at until it picks one. */
    static ChatSpeechRange settingRange() {
        return ChatSpeechRange.of(LostTalesConfig.chatSpeechRange,
                ChatSpeechRange.TALK);
    }

    /** The range the copy speaks at: its pick, else the setting's; talk for a copy not heard nearby. */
    static synchronized ChatSpeechRange of(ConversationPage copy) {
        if (!hasRange(copy)) {
            return ChatSpeechRange.TALK;
        }
        String key = keyOf(copy);
        ChatSpeechRange picked = key == null ? null : PICKED.get(key);
        return picked != null ? picked : settingRange();
    }

    /** The range a line of {@code channel} typed in {@code copy} is said at. */
    static ChatSpeechRange forLine(ConversationPage copy, ChatChannel channel) {
        return ChatSpeechRange.forChannel(channel, of(copy));
    }

    /**
     * Steps the copy's range: a click out (whisper, talk, shout, round to
     * whisper), a right-click back in. Answers the range it now speaks at.
     */
    static synchronized ChatSpeechRange step(ConversationPage copy,
                                             boolean back) {
        if (!hasRange(copy)) {
            return ChatSpeechRange.TALK;
        }
        ChatSpeechRange now = of(copy);
        ChatSpeechRange next = back ? now.previous() : now.next();
        String key = keyOf(copy);
        if (key != null) {
            PICKED.put(key, next);
        }
        return next;
    }

    /** A duplicated copy speaks at the range of the copy it was made from. */
    static synchronized void inherit(ConversationPage source,
                                     ConversationPage copy) {
        String from = keyOf(source);
        String to = keyOf(copy);
        ChatSpeechRange picked = from == null ? null : PICKED.get(from);
        if (to != null && picked != null) {
            PICKED.put(to, picked);
        }
    }

    /** A copy opening anew speaks at the setting's range, whatever an earlier copy of its number picked. */
    static synchronized void forget(ConversationPage copy) {
        String key = keyOf(copy);
        if (key != null) {
            PICKED.remove(key);
        }
    }

    /** Forgotten with the rest of the session. */
    static synchronized void clear() {
        PICKED.clear();
    }
}
