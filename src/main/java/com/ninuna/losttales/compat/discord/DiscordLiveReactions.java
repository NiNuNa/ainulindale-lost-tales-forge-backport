package com.ninuna.losttales.compat.discord;

import java.util.ArrayDeque;
import java.util.Map;

/**
 * The reactions the live connection reported lately, in the order they
 * came, so that what a return's read of one emoji's members finds
 * ({@link DiscordReturnCheck}) gives way to what was heard after the read
 * began: Discord's answer may be older than a reaction heard meanwhile.
 * The gateway thread notes each reaction and queues it while holding this
 * object's lock, and the worker holds it while it puts what was heard
 * onto a read and queues the read's result, so every reaction heard is
 * either in that result or queued after it. Bounded to the newest
 * {@link #MAX_HEARD}; a read that began before the oldest of them is
 * left undone. Cleared as the server starts and stops.
 */
final class DiscordLiveReactions {
    /** Reactions remembered, the oldest going first. */
    static final int MAX_HEARD = 256;

    /** What a reaction heard did. */
    enum Change { ADDED, REMOVED, EMOJI_CLEARED, ALL_CLEARED }

    private static final class Heard {
        final long number;
        final Change change;
        final String messageId;
        final String key;
        final String customId;
        final String userId;
        final String name;

        Heard(long number, Change change, String messageId, String key,
              String customId, String userId, String name) {
            this.number = number;
            this.change = change;
            this.messageId = messageId == null ? "" : messageId;
            this.key = key == null ? "" : key;
            this.customId = customId == null ? "" : customId;
            this.userId = userId == null ? "" : userId;
            this.name = name == null ? "" : name;
        }
    }

    private final ArrayDeque<Heard> heard = new ArrayDeque<Heard>();
    /** How many reactions were ever heard: each one's number. */
    private long count;

    /** Where the record stands now: what a read about to begin is compared with. */
    synchronized long mark() {
        return this.count;
    }

    /**
     * Notes a reaction heard on {@code messageId}: by the chat's key for
     * its emoji, or a custom emoji's id; nothing of either for a clear of
     * every emoji.
     */
    synchronized void heard(Change change, String messageId, String key,
                            String customId, String userId, String name) {
        if (change == null) {
            return;
        }
        this.count++;
        this.heard.addLast(new Heard(this.count, change, messageId, key, customId,
                userId, name));
        while (this.heard.size() > MAX_HEARD) {
            this.heard.removeFirst();
        }
    }

    /**
     * Puts every reaction heard since {@code mark} on that emoji of
     * {@code messageId} onto the members a read listed, in the order they
     * came: a member who reacted is added, one who took it back taken
     * away, and a clear takes everyone listed before it away. A custom
     * emoji is matched by its id, any other by its key. Answers false
     * when reactions heard since the mark have already gone from the
     * record, so what the read found cannot be vouched for.
     */
    synchronized boolean overlay(long mark, String messageId, String key,
                                 String customId, Map<String, String> listed) {
        long oldest = this.heard.isEmpty() ? this.count + 1L
                : this.heard.peekFirst().number;
        if (mark < this.count && mark + 1L < oldest) {
            return false;
        }
        String wantedKey = key == null ? "" : key;
        String wantedId = customId == null ? "" : customId;
        for (Heard reaction : this.heard) {
            if (reaction.number <= mark || !reaction.messageId.equals(messageId)) {
                continue;
            }
            if (reaction.change == Change.ALL_CLEARED) {
                listed.clear();
                continue;
            }
            boolean same = wantedId.length() > 0 ? wantedId.equals(reaction.customId)
                    : wantedKey.length() > 0 && wantedKey.equals(reaction.key);
            if (!same) {
                continue;
            }
            if (reaction.change == Change.EMOJI_CLEARED) {
                listed.clear();
            } else if (reaction.change == Change.ADDED) {
                listed.put(reaction.userId, reaction.name);
            } else {
                listed.remove(reaction.userId);
            }
        }
        return true;
    }

    /** Forgets every reaction heard; the numbering goes on, so no read begun before vouches for after. */
    synchronized void clear() {
        this.heard.clear();
    }
}
