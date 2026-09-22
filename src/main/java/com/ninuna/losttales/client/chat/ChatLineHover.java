package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.Motion;
import com.ninuna.losttales.client.motion.MotionBeat;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionPart;
import com.ninuna.losttales.client.motion.MotionPlayer;
import com.ninuna.losttales.client.motion.Motions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The motion a message plays while the pointer rests on it
 * ({@link MotionIds#CHAT_LINE_HOVER}): its chevron lights toward a lighter
 * shade of its speaker's colour and steps right, and its words follow
 * further, one after another, the row stretching by its gaps while it
 * travels. The name row, a reply's quote and the reactions stay where
 * they are. One player for the message the pointer is on and one for
 * each it has just left, until that one is back at rest.
 */
final class ChatLineHover {
    /** The chevron a message's words open with. */
    static final String CHEVRON = "chevron";
    /** A message's words, each an item of the part. */
    static final String WORDS = "words";
    /** The most messages kept moving at once; a sweep across many drops the oldest. */
    private static final int MAX_PLAYERS = 16;

    private static final Map<Integer, MotionPlayer> PLAYERS =
            new HashMap<Integer, MotionPlayer>();
    private static int onLineId;

    private ChatLineHover() {}

    /**
     * Once a frame: the message the pointer is on sets out on its way on,
     * the one it left on its way off, and a message back at rest lets its
     * player go.
     */
    static void advance(int hoveredLineId, long nowNanos) {
        if (hoveredLineId != onLineId) {
            MotionPlayer leaving = PLAYERS.get(Integer.valueOf(onLineId));
            if (leaving != null) {
                leaving.play(Motion.OFF, nowNanos);
            }
            if (hoveredLineId != 0) {
                Integer key = Integer.valueOf(hoveredLineId);
                MotionPlayer arriving = PLAYERS.get(key);
                if (arriving == null) {
                    arriving = new MotionPlayer(MotionIds.CHAT_LINE_HOVER);
                    arriving.settle(Motion.REST);
                    PLAYERS.put(key, arriving);
                }
                arriving.play(Motion.ON, nowNanos);
            }
            onLineId = hoveredLineId;
        }
        float lastWord = lastStaggerIndex();
        Iterator<Map.Entry<Integer, MotionPlayer>> players =
                PLAYERS.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<Integer, MotionPlayer> entry = players.next();
            if (entry.getKey().intValue() != onLineId
                    && (entry.getValue().isSettled(nowNanos, lastWord)
                            || PLAYERS.size() > MAX_PLAYERS)) {
                players.remove();
            }
        }
    }

    /**
     * How a body row of the message moves this frame, or null for a
     * message at rest, which is drawn as it always is.
     */
    static ChatRowMotion rowMotion(int chatLineId, long nowNanos) {
        MotionPlayer player = PLAYERS.get(Integer.valueOf(chatLineId));
        if (player == null || !Motions.enabled()) {
            return null;
        }
        return new ChatRowMotion(player, nowNanos, staggerMillis(),
                Motions.param(MotionIds.CHAT_LINE_HOVER, "stagger_span", 0.0F));
    }

    static void clear() {
        PLAYERS.clear();
        onLineId = 0;
    }

    /** The words' stagger on their way on, in milliseconds. */
    private static int staggerMillis() {
        MotionPart words = Motions.get(MotionIds.CHAT_LINE_HOVER).part(WORDS);
        MotionBeat on = words == null ? null : words.beat(Motion.ON);
        return on == null ? 0 : on.staggerMillis();
    }

    /** The stagger index the last word of the longest row stands at. */
    private static float lastStaggerIndex() {
        int stagger = staggerMillis();
        return stagger <= 0 ? 0.0F
                : Motions.param(MotionIds.CHAT_LINE_HOVER, "stagger_span",
                        0.0F) / stagger;
    }
}
