package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What each speaker has just said, for the words drawn over their head.
 *
 * <p>Only lines spoken <em>in character</em> are kept: the mod already
 * marks which channels those are — {@link ChatIdentityType#CHARACTER},
 * which is Global, Proximity, Party and Faction — so a channel added
 * later needs nothing here, and the out-of-character ones (OOC, the
 * operator channel, whispers, the console, the Discord bridge) never
 * reach the world. Party, Faction and Global carry no distance of their
 * own, but a speaker has to be rendered in front of you for the words to
 * be drawn at all, so what shows over a head is always someone present.</p>
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
    /** How long it takes to fade out after that. */
    static final long FADE_NANOS = 1200000000L;
    /** Longest line kept; anything past this is cut with an ellipsis. */
    private static final int MAX_CHARACTERS = 160;

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
        if (channel == null || speaker == null
                || channel.getIdentityType() != ChatIdentityType.CHARACTER) {
            return;
        }
        // The name and its colour are the chat's own, so a hobbit is the
        // same green over their head as in the log.
        file(speaker, packet.getIdentityName(), packet.getNameColor(),
                packet.getMessage());
    }

    /**
     * An NPC's floating speech, filed the same way a player's line is:
     * LOTR hands the words to the chat and to the world at once, and
     * this is the copy the world draws. The name and its colour are the
     * ones the conversation tab shows, so an NPC reads the same in both
     * places.
     */
    public static synchronized void receiveNpc(UUID speaker, String name,
                                               int nameColor, String body) {
        file(speaker, name, nameColor, body);
    }

    private static void file(UUID speaker, String name, int nameColor,
                             String body) {
        if (speaker == null || body == null) {
            return;
        }
        String spoken = LostTalesChatVisualStyle.removeColorCodes(body).trim();
        if (spoken.length() == 0) {
            return;
        }
        if (spoken.length() > MAX_CHARACTERS) {
            spoken = spoken.substring(0, MAX_CHARACTERS - 1) + "…";
        }
        Speech speech = SPOKEN.remove(speaker);
        if (speech == null) {
            speech = new Speech();
        }
        speech.name = name == null ? "" : name;
        speech.nameColor = nameColor & 0xFFFFFF;
        speech.lines.add(new Line(spoken, System.nanoTime()));
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
        while (!speech.lines.isEmpty()
                && nowNanos - speech.lines.get(0).spokenNanos > HOLD_NANOS
                        + FADE_NANOS) {
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

    /** One thing said, and when. */
    static final class Line {
        final String text;
        final long spokenNanos;

        private Line(String text, long spokenNanos) {
            this.text = text;
            this.spokenNanos = spokenNanos;
        }

        /** Full strength while it is held, then out over the fade. */
        float opacity(long nowNanos) {
            long age = nowNanos - this.spokenNanos;
            if (age <= HOLD_NANOS) {
                return 1.0F;
            }
            float faded = (float)(age - HOLD_NANOS) / (float)FADE_NANOS;
            return Math.max(0.0F, Math.min(1.0F, 1.0F - faded));
        }
    }
}
