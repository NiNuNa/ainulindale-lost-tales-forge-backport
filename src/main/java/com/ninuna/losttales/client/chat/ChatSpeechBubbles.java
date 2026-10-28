package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatSpeechRange;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.client.motion.Motions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What each speaker has just said, for the words drawn over their head.
 *
 * <p>Only words said aloud float (CHAT-58): a line heard nearby, in
 * Proximity Chat or a server channel heard nearby
 * ({@link ChatSpeechRange#appliesTo}), so a channel added later needs
 * nothing here. A line of any other channel was never said where its
 * speaker stands. Each line keeps how far it carried, which sets its
 * words in italics for a whisper and in bold for a shout, and how far
 * its bubble is drawn.</p>
 *
 * <p>A line longer than {@link #MAX_CHARACTERS} is cut there and marked
 * cut, and its bubble's last row fades out at its end as a cut name does
 * (CHAT-86, LOOK-37), with no dots.</p>
 *
 * <p>A speaker keeps their last {@link #MAX_LINES} lines and nothing
 * older than a line's whole life, so the store holds a handful of short
 * strings at most and empties itself as people stop talking. It is
 * client-side presentation only — the message it was filled from has
 * already been through the chat — and it is cleared on disconnect like
 * every other client cache.</p>
 */
public final class ChatSpeechBubbles {
    /** Lines one speaker keeps; older ones are dropped as they arrive. */
    static final int MAX_LINES = 2;
    /** Speakers remembered at once; the least recent goes first. */
    private static final int MAX_SPEAKERS = 32;
    /** How long a line stands at full strength. */
    static final long HOLD_NANOS = 6000000000L;
    /**
     * How long it takes to fade out after that, at speed 1; the motion
     * settings shorten it, and with the Animations switch off a line goes
     * at once.
     */
    static final int FADE_MILLIS = 1200;
    /** Longest line kept; anything past this is cut, and its bubble fades at its end. */
    static final int MAX_CHARACTERS = 160;

    private static final Map<UUID, Speech> SPOKEN =
            new LinkedHashMap<UUID, Speech>();

    private ChatSpeechBubbles() {}

    /**
     * Files a message that has just arrived. Anything not spoken as a
     * character, and anything without a speaker to stand over, is
     * ignored.
     */
    public static synchronized void receive(
            LostTalesChatMessagePacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        ChatChannel channel = packet.getChannel();
        UUID speaker = packet.getSenderId();
        // Only words said aloud float (CHAT-58). The Narrator tells;
        // nothing is said over a head, nor over an unseen voice's, which
        // nobody sees.
        if (speaker == null || !ChatSpeechRange.appliesTo(channel)
                || packet.isNarrator() || packet.isNotInWorld()
                || packet.isUnseen()
                || !ClientChatChannelState.isAvailable(LostTalesChatPresentation.fileUnder(packet))) {
            return;
        }
        // The name and its colour are the chat's own, so a hobbit is the
        // same green over their head as in the log. An action floats as
        // the sentence it is in the chat.
        // Words a copy spoke as a character not in the world stay in the
        // chat: the body they would float over is somebody else (CHAT-05).
        file(speaker, packet.getIdentityName(), packet.getNameColor(),
                packet.getMessage(), packet.isAction(), packet.getNamedPlayers(),
                packet.getSpeechRange());
    }

    /**
     * An NPC's floating speech, filed the same way a player's line is:
     * LOTR hands the words to the chat and to the world at once, and
     * this is the copy the world draws. The name and its colour are the
     * ones the conversation page shows, so an NPC reads the same in both
     * places.
     */
    public static synchronized void receiveNpc(UUID speaker, String name,
                                               int nameColor, String body) {
        file(speaker, name, nameColor, body, false,
                Collections.<ChatNamedPlayer>emptyList(), ChatSpeechRange.TALK);
    }

    private static void file(UUID speaker, String name, int nameColor,
                             String body, boolean action,
                             List<ChatNamedPlayer> named,
                             ChatSpeechRange range) {
        if (speaker == null || body == null) {
            return;
        }
        // The words over a head read as the words in the log do, their
        // paragraphs run together on the bubble's lines.
        String spoken = ClientChatProfanity.filterMessage(
                LostTalesChatVisualStyle.removeColorCodes(
                        ChatMessageValidator.oneLine(body)).trim(), named);
        if (spoken.length() == 0) {
            return;
        }
        boolean cut = spoken.length() > MAX_CHARACTERS;
        if (cut) {
            spoken = spoken.substring(0, MAX_CHARACTERS);
        }
        Speech speech = SPOKEN.remove(speaker);
        if (speech == null) {
            speech = new Speech();
        }
        speech.name = name == null ? "" : name;
        speech.nameColor = nameColor & 0xFFFFFF;
        speech.lines.add(new Line(spoken, System.nanoTime(), action,
                range == null ? ChatSpeechRange.TALK : range, cut));
        while (speech.lines.size() > MAX_LINES) {
            speech.lines.remove(0);
        }
        // Re-inserting puts this speaker last, so the eviction below
        // drops whoever has been quiet longest.
        SPOKEN.put(speaker, speech);
        while (SPOKEN.size() > MAX_SPEAKERS) {
            Iterator<UUID> oldest = SPOKEN.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * What this speaker still has over their head, oldest first, or an
     * empty list. Lines that have finished fading are dropped as they
     * are asked for, which is the only cleanup the store needs.
     */
    static synchronized Speech speechOf(UUID speaker, long nowNanos) {
        if (speaker == null) {
            return null;
        }
        Speech speech = SPOKEN.get(speaker);
        if (speech == null) {
            return null;
        }
        long fadeNanos = Motions.beatNanos(FADE_MILLIS);
        while (!speech.lines.isEmpty()
                && nowNanos - speech.lines.get(0).spokenNanos > HOLD_NANOS
                        + fadeNanos) {
            speech.lines.remove(0);
        }
        if (speech.lines.isEmpty()) {
            SPOKEN.remove(speaker);
            return null;
        }
        return speech;
    }

    /** Whether anyone has anything to show, so a frame can leave early. */
    public static synchronized boolean isEmpty() {
        return SPOKEN.isEmpty();
    }

    /** Dropped on disconnect, like every other client cache. */
    public static synchronized void clear() {
        SPOKEN.clear();
    }

    /** One speaker: how the chat signs them, and what they just said. */
    static final class Speech {
        String name = "";
        int nameColor;
        final List<Line> lines = new ArrayList<Line>(MAX_LINES);
    }

    /** One thing said or done, and when. */
    static final class Line {
        final String text;
        final long spokenNanos;
        /**
         * Whether it is an action: drawn as the sentence the speaker's
         * name opens, in italics, as the chat shows it.
         */
        final boolean action;
        /** How far it carried: its words in italics for a whisper, in bold for a shout. */
        final ChatSpeechRange range;
        /** Whether it was cut at {@link #MAX_CHARACTERS}: its last row fades at its end. */
        final boolean cut;

        private Line(String text, long spokenNanos, boolean action,
                     ChatSpeechRange range, boolean cut) {
            this.text = text;
            this.spokenNanos = spokenNanos;
            this.action = action;
            this.range = range;
            this.cut = cut;
        }

        /** Full strength while it is held, then out over the fade. */
        float opacity(long nowNanos) {
            long age = nowNanos - this.spokenNanos;
            if (age <= HOLD_NANOS) {
                return 1.0F;
            }
            long fadeNanos = Motions.beatNanos(FADE_MILLIS);
            if (fadeNanos <= 0L) {
                return 0.0F;
            }
            float faded = (float)(age - HOLD_NANOS) / (float)fadeNanos;
            return Math.max(0.0F, Math.min(1.0F, 1.0F - faded));
        }
    }
}
