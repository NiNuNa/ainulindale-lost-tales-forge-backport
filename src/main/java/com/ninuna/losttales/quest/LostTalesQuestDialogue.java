package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a quest's giver says, and what the player may say back.
 *
 * <p>A quest's {@code dialogue} block is written as plain entries beside
 * its other blocks, so it is loaded, stored and carried over the wire
 * the same way. This reads that block by name rather than leaving every
 * caller to remember the keys, and answers with nothing rather than null
 * where a line was not written.</p>
 *
 * <p>A quest with no dialogue at all is offered the way it always was —
 * by touching its trigger — so authoring a conversation is what turns a
 * quest into one that is talked about, and nothing else changes.</p>
 */
public final class LostTalesQuestDialogue {

    /** The lines a quest may write, each optional. */
    public static final String OFFER = "offer";
    public static final String MORE = "more";
    public static final String ACCEPT = "accept";
    public static final String DECLINE = "decline";
    public static final String PROGRESS = "progress";
    public static final String HAND_IN = "handIn";
    /** What the player says to hand over what they were asked for. */
    public static final String HAND_OVER = "handOver";
    /** What the player says to end the conversation. */
    public static final String LEAVE = "leave";

    /** The longest any one line may be; a speech bubble's worth. */
    public static final int MAX_LINE = 512;

    private static final LostTalesQuestDialogue NONE =
            new LostTalesQuestDialogue(
                    Collections.<String, String>emptyMap());

    private final Map<String, String> lines;

    private LostTalesQuestDialogue(Map<String, String> lines) {
        this.lines = lines;
    }

    /** The dialogue a quest wrote; never null. */
    public static LostTalesQuestDialogue of(LostTalesQuestDefinition quest) {
        return quest == null ? NONE : of(quest.getDialogue());
    }

    /** The dialogue a block of entries stands for; never null. */
    public static LostTalesQuestDialogue of(Map<String, String> block) {
        if (block == null || block.isEmpty()) {
            return NONE;
        }
        Map<String, String> lines = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : block.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String line = entry.getValue().trim();
            if (line.length() == 0) {
                continue;
            }
            lines.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                    line.length() > MAX_LINE
                            ? line.substring(0, MAX_LINE) : line);
        }
        return lines.isEmpty() ? NONE
                : new LostTalesQuestDialogue(
                        Collections.unmodifiableMap(lines));
    }

    /** Whether the quest is talked about at all. */
    public boolean exists() {
        return !this.lines.isEmpty();
    }

    /**
     * Whether the quest is offered in conversation: without an offer
     * line there is nothing for a giver to say, so it keeps starting the
     * way it always did.
     */
    public boolean isOffered() {
        return line(OFFER).length() > 0;
    }

    /** Whether the quest is handed in in conversation. */
    public boolean isHandedIn() {
        return line(HAND_IN).length() > 0;
    }

    /** One line, or empty where it was not written. */
    public String line(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String line = this.lines.get(key);
        return line == null ? "" : line;
    }

    /** One line, or {@code fallback} where it was not written. */
    public String lineOr(String name, String fallback) {
        String line = line(name);
        return line.length() > 0 ? line : fallback == null ? "" : fallback;
    }
}
