package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;

/**
 * A line as one reader is shown it, live or replayed. Two things differ
 * from reader to reader. A line an unseen voice said names its speaker
 * only to the speaker and to staff holding {@code chat.moderate}, and so
 * does a quote or a forward of such a line (CHAT-84). A reply's quote of
 * a line heard nearby goes only to the readers who heard that line; the
 * others read the reply alone (CHAT-43).
 */
final class ChatReaderView {
    private ChatReaderView() {}

    /**
     * {@code line} as the account {@code reader} is shown it.
     * {@code moderates} says whether the reader holds
     * {@code chat.moderate}, and {@code heardQuoted} whether they heard
     * the line the reply quotes; a forward's quote is never left off,
     * since a forward goes only where its readers could read the line.
     */
    static LostTalesChatMessagePacket forReader(LostTalesChatMessagePacket line,
                                                UUID reader, boolean moderates,
                                                boolean heardQuoted) {
        if (line == null) {
            return null;
        }
        LostTalesChatMessagePacket shown = line;
        if (line.isUnseen()
                && !knowsSpeaker(reader, moderates, line.getSenderId())) {
            shown = shown.unseenForOthers();
        }
        ChatReplyReference reply = shown.getReply();
        if (reply.isAnchored() && !reply.isForward() && !heardQuoted) {
            return shown.withReply(ChatReplyReference.NONE);
        }
        if (reply.isUnseen()
                && !knowsSpeaker(reader, moderates, reply.getSenderId())) {
            shown = shown.withReply(reply.unseenVoice());
        }
        return shown;
    }

    /**
     * Whether a reader may know who an unseen voice was: the speaker
     * themself, or staff.
     */
    static boolean knowsSpeaker(UUID reader, boolean moderates, UUID speaker) {
        return moderates || (reader != null && reader.equals(speaker));
    }
}
