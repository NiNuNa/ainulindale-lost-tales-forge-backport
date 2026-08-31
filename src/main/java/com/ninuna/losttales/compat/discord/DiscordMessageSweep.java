package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the bridge notices a Discord message being rewritten or taken
 * back after it was relayed: polling only ever reports <em>new</em>
 * messages, so the channel's newest page is re-read and compared with
 * what was relayed. A tracked message found with another edit stamp has
 * been edited; one missing from a page that reaches back past its place
 * has been deleted, which its id says on its own — Discord ids are
 * snowflakes, ordered by time — and one the page no longer reaches back
 * to has simply left the window the bridge watches, and is let go: the
 * bridge follows the recent conversation, not the channel's history.
 *
 * <p>Pure bookkeeping over parsed pages, owned by the worker that reads
 * them — one thread, no locks — and dying with it, exactly like the
 * poll cursor beside it. Bounded to the newest {@link #MAX_TRACKED}
 * relayed messages.</p>
 */
final class DiscordMessageSweep {
    /** Messages watched; past it the oldest is let go first. */
    private static final int MAX_TRACKED = 128;

    /** Relayed message id to the edit stamp it was last seen with. */
    private final LinkedHashMap<String, String> tracked =
            new LinkedHashMap<String, String>();

    /** What one look at the newest page found. */
    static final class Changes {
        /** Tracked messages found rewritten, oldest first. */
        final List<DiscordJson.Message> edited;
        /** Tracked messages found deleted. */
        final List<String> deletedIds;

        Changes(List<DiscordJson.Message> edited, List<String> deletedIds) {
            this.edited = edited;
            this.deletedIds = deletedIds;
        }
    }

    /** Whether anything is being watched; nothing means nothing to ask. */
    boolean isEmpty() {
        return this.tracked.isEmpty();
    }

    /** Test hook: messages currently watched. */
    int size() {
        return this.tracked.size();
    }

    /** Starts watching a message the bridge has just relayed. */
    void track(DiscordJson.Message message) {
        if (message == null || message.id == null
                || message.id.length() == 0) {
            return;
        }
        this.tracked.put(message.id, message.editedTimestamp);
        while (this.tracked.size() > MAX_TRACKED) {
            Iterator<String> oldest = this.tracked.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Compares the newest page, oldest first as
     * {@link DiscordJson#parseMessages} answers it, with what was
     * relayed. An empty page is a channel with no messages left in it:
     * everything watched has been deleted.
     */
    Changes apply(List<DiscordJson.Message> page) {
        List<DiscordJson.Message> edited =
                new ArrayList<DiscordJson.Message>();
        List<String> deletedIds = new ArrayList<String>();
        if (this.tracked.isEmpty()) {
            return new Changes(edited, deletedIds);
        }
        Map<String, DiscordJson.Message> byId =
                new HashMap<String, DiscordJson.Message>();
        for (int index = 0; index < page.size(); index++) {
            DiscordJson.Message message = page.get(index);
            if (message != null) {
                byId.put(message.id, message);
            }
        }
        long oldestId = page.isEmpty() ? 0L
                : snowflake(page.get(0).id);
        List<String> letGo = new ArrayList<String>();
        for (Map.Entry<String, String> entry : this.tracked.entrySet()) {
            DiscordJson.Message found = byId.get(entry.getKey());
            if (found != null) {
                if (!found.editedTimestamp.equals(entry.getValue())) {
                    edited.add(found);
                    entry.setValue(found.editedTimestamp);
                }
                continue;
            }
            if (page.isEmpty() || Long.compareUnsigned(
                    snowflake(entry.getKey()), oldestId) >= 0) {
                // The page reaches back past where the message stood
                // and it is not there: it has been deleted.
                deletedIds.add(entry.getKey());
            }
            // Deleted or drifted out of the watched window, the watch
            // on it ends either way.
            letGo.add(entry.getKey());
        }
        for (int index = 0; index < letGo.size(); index++) {
            this.tracked.remove(letGo.get(index));
        }
        return new Changes(Collections.unmodifiableList(edited),
                Collections.unmodifiableList(deletedIds));
    }

    /** The id as the number it is; an unreadable one sorts before all. */
    private static long snowflake(String id) {
        try {
            return Long.parseUnsignedLong(id);
        } catch (NumberFormatException malformed) {
            return 0L;
        }
    }
}
