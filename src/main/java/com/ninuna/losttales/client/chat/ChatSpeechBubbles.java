package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.client.motion.Motions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What each speaker has just said, for the words drawn over their head.
 *
 * <p>Only lines spoken <em>in character</em> are kept: the channel says
 * which those are ({@link ChatRolePresentation#isInCharacter}) — Global,
 * Proximity, Party, Faction and whispers — so a channel added later needs
 * nothing here, and the out-of-character ones (OOC, the operator channel,
 * the console, the Discord bridge) never reach the world. Party, Faction,
 * whispers and Global carry no distance of their
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
    /**
     * How long it takes to fade out after that, at speed 1; the motion
     * settings shorten it, and with the Animations switch off a line goes
     * at once.
     */
    static final int FADE_MILLIS = 1200;
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
        // The Narrator tells; nothing is said over a head.
        if (speaker == null || !ChatRolePresentation.isInCharacter(channel)
                || packet.isNarrator() || !spokenAsPlayed(packet)
                || !ClientChatChannelState.isAvailable(LostTalesChatPresentation.fileUnder(packet))) {
            return;
        }
        // The name and its colour are the chat's own, so a hobbit is the
        // same green over their head as in the log. An action floats as
        // the sentence it is in the chat.
        file(speaker, packet.getIdentityName(), packet.getNameColor(),
                packet.getMessage(), packet.isAction());
    }

    /**
     * Whether the line wears the character its speaker is playing: words
     * spoken as another of their characters stay in the chat, since the
     * body they would float over is somebody else (Nils, 2026-09-28, C3 a).
     * A speaker whose look is not known yet is taken at their word.
     */
    private static boolean spokenAsPlayed(LostTalesChatMessagePacket packet) {
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(packet.getSenderId());
        if (appearance == null) {
            return true;
        }
        UUID played = appearance.getCharacterId() == null
                ? packet.getSenderId() : appearance.getCharacterId();
        UUID spoken = packet.getIdentityCharacterId() == null
                ? packet.getSenderId() : packet.getIdentityCharacterId();
        return played.equals(spoken);
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
        file(speaker, name, nameColor, body, false);
    }

    private static void file(UUID speaker, String name, int nameColor,
                             String body, boolean action) {
        if (speaker == null || body == null) {
            return;
        }
        // The words over a head read as the words in the log do, their
        // paragraphs run together on the bubble's lines.
        String spoken = ClientChatProfanity.filterMessage(
                LostTalesChatVisualStyle.removeColorCodes(
                        ChatMessageValidator.oneLine(body)).trim());
        if (spoken.length() == 0) {
            return;
        }
        if (spoken.length() > MAX_CHARACTERS) {
            spoken = spoken.substring(0, MAX_CHARACTERS - 3) + "...";
        }
        Speech speech = SPOKEN.remove(speaker);
        if (speech == null) {
            speech = new Speech();
        }
        speech.name = name == null ? "" : name;
        speech.nameColor = nameColor & 0xFFFFFF;
        speech.lines.add(new Line(spoken, System.nanoTime(), action));
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

        private Line(String text, long spokenNanos, boolean action) {
            this.text = text;
            this.spokenNanos = spokenNanos;
            this.action = action;
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
