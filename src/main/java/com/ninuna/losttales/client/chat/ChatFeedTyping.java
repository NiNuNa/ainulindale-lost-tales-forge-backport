package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The closed feed's typing row: who is typing into the conversations the
 * feed carries, each conversation named as the feed names a line's
 * channel ("Global: Aldric is typing"), in the feed's own order. While
 * anyone types, the feed's lines rise a row and the typing row comes up
 * under them from the feed's bottom edge; once nobody does, the lines
 * settle back over it and it goes down with them. Cleared on
 * disconnect.
 */
final class ChatFeedTyping {
    /** One conversation and who is typing into it, oldest first. */
    static final class Segment {
        final ChatTab tab;
        final List<String> names;

        Segment(ChatTab tab, List<String> names) {
            this.tab = tab;
            this.names = Collections.unmodifiableList(
                    new ArrayList<String>(names));
        }
    }

    private static final MotionTransition RISE =
            new MotionTransition(MotionIds.CHAT_FEED_TYPING, true);
    /** What the row says: the typing now, or the last while it goes down. */
    private static List<Segment> shown = Collections.emptyList();

    private ChatFeedTyping() {}

    /** The conversations of {@code tabs} someone is typing into, in their order. */
    static List<Segment> segments(List<ChatTab> tabs) {
        List<Segment> segments = new ArrayList<Segment>();
        if (!LostTalesConfig.showChatTypingIndicators) {
            return segments;
        }
        for (ChatTab tab : tabs) {
            List<String> names = ClientChatTypingState.namesTyping(tab);
            if (!names.isEmpty()) {
                segments.add(new Segment(tab, names));
            }
        }
        return segments;
    }

    /**
     * Steps the rise toward a whole row while {@code typing} holds
     * anyone and back to none after, and answers the share of a row the
     * lines stand raised by, 0 to 1.
     */
    static float advance(List<Segment> typing, long nowNanos) {
        if (!typing.isEmpty()) {
            shown = typing;
        }
        float share = RISE.advance(nowNanos, !typing.isEmpty());
        if (share <= 0.0F) {
            shown = Collections.emptyList();
        }
        return share;
    }

    /** What the row says, empty while it is down. */
    static List<Segment> shown() {
        return shown;
    }

    static void clear() {
        RISE.settle(false);
        shown = Collections.emptyList();
    }
}
