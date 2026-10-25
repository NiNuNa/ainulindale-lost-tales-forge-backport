package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterGear;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterGearRequestPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the player's own characters wear in their saved lives, as the
 * server said, for the stage (CHAR-42). Only a character of the roster
 * other than the one played is asked about, once as the stage first shows
 * it. A saved life changes only as the roster does (a switch, a claim, a
 * release), so each answer is kept for the roster revision it was asked
 * at, and asked again after the roster changed. Cleared when the
 * connection ends.
 */
public final class ClientCharacterGearCache {
    /** How long a question waits for its answer before the stage asks again. */
    static final long ANSWER_NANOS = 5L * 1000L * 1000L * 1000L;

    /** The answers, by character, with the roster revision each was asked at. */
    private static final Map<UUID, Answer> ANSWERS = new HashMap<UUID, Answer>();
    /** The questions waiting for an answer, by character. */
    private static final Map<UUID, Question> ASKED = new HashMap<UUID, Question>();

    private ClientCharacterGearCache() {}

    /**
     * What the character wears in its saved life, or null while that is
     * not known for the roster as it stands now: the stage calls this each
     * frame, and the server is asked when no answer and no question is
     * waiting. A character that is not the player's, or is the one played,
     * is never asked about.
     */
    public static CharacterGear want(UUID characterId) {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (characterId == null || snapshot == null
                || snapshot.getCharacter(characterId) == null
                || characterId.equals(snapshot.getActiveCharacterId())) {
            return null;
        }
        long revision = snapshot.getRevision();
        CharacterGear known = known(characterId, revision);
        if (known != null || !markAsked(characterId, revision, System.nanoTime())) {
            return known;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new CharacterGearRequestPacket(characterId));
        return null;
    }

    /** The answer kept for the character at that roster revision; null for none. */
    static synchronized CharacterGear known(UUID characterId, long revision) {
        Answer answer = ANSWERS.get(characterId);
        return answer != null && answer.revision == revision ? answer.gear : null;
    }

    /**
     * Whether to ask now, noting the question when so: not while a
     * question at this revision waits younger than {@link #ANSWER_NANOS}.
     */
    static synchronized boolean markAsked(UUID characterId, long revision, long now) {
        Question waiting = ASKED.get(characterId);
        if (waiting != null && waiting.revision == revision
                && now - waiting.askedNanos < ANSWER_NANOS) {
            return false;
        }
        ASKED.put(characterId, new Question(revision, now));
        return true;
    }

    /**
     * Keeps the server's answer for the revision the question was asked
     * at; an answer nobody asked for is dropped.
     */
    public static synchronized void accept(UUID characterId, CharacterGear gear) {
        Question question = characterId == null ? null : ASKED.remove(characterId);
        if (question == null || gear == null) {
            return;
        }
        ANSWERS.put(characterId, new Answer(question.revision, gear));
    }

    public static synchronized void clear() {
        ANSWERS.clear();
        ASKED.clear();
    }

    private static final class Answer {
        final long revision;
        final CharacterGear gear;

        Answer(long revision, CharacterGear gear) {
            this.revision = revision;
            this.gear = gear;
        }
    }

    private static final class Question {
        final long revision;
        final long askedNanos;

        Question(long revision, long askedNanos) {
            this.revision = revision;
            this.askedNanos = askedNanos;
        }
    }
}
