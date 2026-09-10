package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who reacted to one kept message, and with what: each emoji in the
 * order it was first used, and under it every reactor in the order
 * they reacted, with the name they reacted as.
 *
 * <p>A reactor is an account, or a Discord member by the sender id the
 * bridge signs them with; one reaction per reactor per emoji, as on
 * Discord. Bounded by {@link #MAX_KINDS} distinct emoji and
 * {@link #MAX_REACTORS} reactions in all, past which a new one is
 * refused rather than an old one dropped. Owned by {@link ChatHistory}
 * and touched only under its lock.</p>
 */
public final class ChatReactions {
    public static final int MAX_KINDS = ChatReactionSummary.MAX_KINDS;
    /** Reactions on one message in all, every emoji together. */
    public static final int MAX_REACTORS = 100;

    private final LinkedHashMap<String, LinkedHashMap<UUID, String>> byEmoji =
            new LinkedHashMap<String, LinkedHashMap<UUID, String>>();

    /**
     * Adds or takes back one reaction. Answers whether anything changed:
     * a reaction already there, one not there to take back, an emoji the
     * registry does not know, or one past the bounds changes nothing.
     */
    public boolean set(String emoji, UUID reactor, String name, boolean add) {
        if (emoji == null || reactor == null
                || ChatEmoji.fromName(emoji) == null) {
            return false;
        }
        LinkedHashMap<UUID, String> reactors = this.byEmoji.get(emoji);
        if (!add) {
            if (reactors == null || reactors.remove(reactor) == null) {
                return false;
            }
            if (reactors.isEmpty()) {
                this.byEmoji.remove(emoji);
            }
            return true;
        }
        if (reactors != null && reactors.containsKey(reactor)) {
            return false;
        }
        if (total() >= MAX_REACTORS
                || (reactors == null && this.byEmoji.size() >= MAX_KINDS)) {
            return false;
        }
        if (reactors == null) {
            reactors = new LinkedHashMap<UUID, String>();
            this.byEmoji.put(emoji, reactors);
        }
        reactors.put(reactor, shown(name));
        return true;
    }

    /**
     * Takes back every Discord member's reaction — with one emoji, or
     * with every emoji when {@code emoji} is null — as Discord does when
     * a moderator clears them there. A player's own reactions stay.
     * Answers whether anything changed.
     */
    public boolean clearDiscord(String emoji) {
        boolean changed = false;
        Iterator<Map.Entry<String, LinkedHashMap<UUID, String>>> kinds =
                this.byEmoji.entrySet().iterator();
        while (kinds.hasNext()) {
            Map.Entry<String, LinkedHashMap<UUID, String>> kind = kinds.next();
            if (emoji != null && !emoji.equals(kind.getKey())) {
                continue;
            }
            Iterator<UUID> reactors = kind.getValue().keySet().iterator();
            while (reactors.hasNext()) {
                if (LostTalesChatMessagePacket.isDiscordSender(reactors.next())) {
                    reactors.remove();
                    changed = true;
                }
            }
            if (kind.getValue().isEmpty()) {
                kinds.remove();
            }
        }
        return changed;
    }

    /**
     * How many players — not Discord members — reacted with the emoji:
     * what the bridge's own reaction on Discord stands for there.
     */
    public int gameCount(String emoji) {
        LinkedHashMap<UUID, String> reactors = emoji == null ? null
                : this.byEmoji.get(emoji);
        if (reactors == null) {
            return 0;
        }
        int count = 0;
        for (UUID reactor : reactors.keySet()) {
            if (!LostTalesChatMessagePacket.isDiscordSender(reactor)) {
                count++;
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return this.byEmoji.isEmpty();
    }

    /** Reactions in all, every emoji together. */
    public int total() {
        int total = 0;
        for (LinkedHashMap<UUID, String> reactors : this.byEmoji.values()) {
            total += reactors.size();
        }
        return total;
    }

    /** The reactions as {@code viewer} is shown them. */
    public ChatReactionSummary summaryFor(UUID viewer) {
        if (this.byEmoji.isEmpty()) {
            return ChatReactionSummary.EMPTY;
        }
        List<ChatReactionSummary.Reaction> reactions =
                new ArrayList<ChatReactionSummary.Reaction>(this.byEmoji.size());
        for (Map.Entry<String, LinkedHashMap<UUID, String>> kind
                : this.byEmoji.entrySet()) {
            List<String> names = new ArrayList<String>();
            for (String name : kind.getValue().values()) {
                if (names.size() >= ChatReactionSummary.MAX_NAMES) {
                    break;
                }
                if (name.length() > 0) {
                    names.add(name);
                }
            }
            reactions.add(new ChatReactionSummary.Reaction(kind.getKey(),
                    kind.getValue().size(),
                    viewer != null && kind.getValue().containsKey(viewer),
                    names));
        }
        return new ChatReactionSummary(reactions);
    }

    /** Every emoji and its reactors in order, for the save. */
    public Map<String, Map<UUID, String>> snapshot() {
        Map<String, Map<UUID, String>> copy =
                new LinkedHashMap<String, Map<UUID, String>>();
        for (Map.Entry<String, LinkedHashMap<UUID, String>> kind
                : this.byEmoji.entrySet()) {
            copy.put(kind.getKey(), Collections.unmodifiableMap(
                    new LinkedHashMap<UUID, String>(kind.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** A name as it is kept and shown: trimmed and cut to a glance. */
    static String shown(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() <= ChatReactionSummary.MAX_NAME_CHARS
                ? trimmed
                : trimmed.substring(0, ChatReactionSummary.MAX_NAME_CHARS);
    }
}
