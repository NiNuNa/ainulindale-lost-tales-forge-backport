package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>An emoji is a reaction key ({@link ChatForeignEmoji}): a registry
 * name, or the foreign key of an emoji the registry lacks. Only a
 * Discord member can bring a foreign emoji to a message; a player may
 * add theirs only to one the message already carries. A custom emoji is
 * one emoji by its Discord id whatever it is called now, so a Discord
 * member's reaction finds its key by the id ({@link #discordKeyOf}),
 * while a player's names the key they were shown.</p>
 */
public final class ChatReactions {
    public static final int MAX_KINDS = ChatReactionSummary.MAX_KINDS;
    /** Reactions on one message in all, every emoji together. */
    public static final int MAX_REACTORS = 100;

    private final LinkedHashMap<String, LinkedHashMap<UUID, String>> byEmoji =
            new LinkedHashMap<String, LinkedHashMap<UUID, String>>();
    /** The keys a restore filed a saved key under by another name; null while none. */
    private Set<String> renamedOnRestore;

    /**
     * Adds or takes back one reaction. Answers whether anything changed:
     * a reaction already there, one not there to take back, anything
     * that is not a reaction key, a player's reaction with a foreign
     * emoji nobody on the message reacted with, or one past the bounds
     * changes nothing.
     */
    public boolean set(String emoji, UUID reactor, String name, boolean add) {
        if (emoji == null || reactor == null
                || !ChatForeignEmoji.isReactionKey(emoji)) {
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
        if (reactors == null && ChatForeignEmoji.isForeign(emoji)
                && !LostTalesChatMessagePacket.isDiscordSender(reactor)) {
            return false;
        }
        return put(emoji, reactors, reactor, name);
    }

    /**
     * One reaction read back from the save. Any reactor may hold a
     * foreign emoji here, since the Discord member who brought it may
     * have taken theirs back while a player's stayed. A foreign key the
     * registry has come to carry since it was saved is kept under the
     * registry's name ({@link ChatForeignEmoji#restoredKey}), merged with
     * the reactions already there: a reactor found under both keys is one
     * reaction, kept once. Answers false for what {@link #set} would also
     * refuse: no key, a reactor twice under one key, or one past the
     * bounds.
     */
    boolean restore(String emoji, UUID reactor, String name) {
        String key = ChatForeignEmoji.restoredKey(emoji);
        if (key == null || reactor == null) {
            return false;
        }
        LinkedHashMap<UUID, String> reactors = this.byEmoji.get(key);
        if (!key.equals(emoji)) {
            if (this.renamedOnRestore == null) {
                this.renamedOnRestore = new HashSet<String>();
            }
            this.renamedOnRestore.add(key);
        }
        if (reactors != null && reactors.containsKey(reactor)
                && this.renamedOnRestore != null
                && this.renamedOnRestore.contains(key)) {
            return true;
        }
        return put(key, reactors, reactor, name);
    }

    /**
     * Whether a restore kept a saved key under another name, so the
     * reactions differ from what the save holds and are to be written
     * back.
     */
    boolean renamedOnRestore() {
        return this.renamedOnRestore != null;
    }

    /**
     * Every key on this message of the custom emoji whose Discord id is
     * {@code id}, in the order first used; empty for none. Discord keeps
     * a custom emoji's id through a rename, so the id is what the emoji
     * is and its name only what it was called when first seen here. A
     * message holds one key per id, two only when a save holds the emoji
     * under two names.
     */
    public List<String> customKeysOf(String id) {
        if (!ChatForeignEmoji.isCustomId(id)) {
            return Collections.emptyList();
        }
        String suffix = ":" + id;
        List<String> keys = null;
        for (String emoji : this.byEmoji.keySet()) {
            if (emoji.endsWith(suffix) && ChatForeignEmoji.isCustom(emoji)) {
                if (keys == null) {
                    keys = new ArrayList<String>(1);
                }
                keys.add(emoji);
            }
        }
        return keys == null ? Collections.<String>emptyList() : keys;
    }

    /**
     * The key a Discord member's reaction is added to or taken from, or
     * null for none. {@code emoji} is the key Discord's name for the
     * emoji makes, null when it sent none (a deleted custom emoji);
     * {@code customId} is a custom emoji's id, empty for a Unicode one.
     *
     * <p>An addition goes to the key the message already holds for that
     * id: the one the member holds, else the first. A renamed emoji so
     * stays one chip under the name first seen. With no key for the id it
     * starts one under {@code emoji}. A removal takes the key with that
     * id that holds the member. With none it falls to {@code emoji},
     * which reaches a reaction only where that is a registry name: a
     * custom emoji named as one of the registry's names is kept under
     * that name, without its id.</p>
     */
    public String discordKeyOf(String emoji, String customId, UUID reactor,
                               boolean add) {
        String first = null;
        for (String key : customKeysOf(customId)) {
            if (reactor != null && this.byEmoji.get(key).containsKey(reactor)) {
                return key;
            }
            if (first == null) {
                first = key;
            }
        }
        return add && first != null ? first : emoji;
    }

    /** A custom key's id, or null for any other key. */
    private static String customIdOf(String key) {
        return ChatForeignEmoji.isCustom(key)
                ? key.substring(key.indexOf(':') + 1) : null;
    }

    private boolean put(String emoji, LinkedHashMap<UUID, String> reactors,
                        UUID reactor, String name) {
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
     * Takes back every Discord member's reaction with one emoji, as
     * Discord does when a moderator clears that emoji there: every key
     * of the custom emoji whose id is {@code customId}, whatever name
     * each is kept under, and with no key for that id the key
     * {@code emoji}. With no emoji and no id it takes back every Discord
     * member's reaction, as {@link #clearDiscord(String)} with null does.
     * A player's own reactions stay. Answers whether anything changed.
     */
    public boolean clearDiscord(String emoji, String customId) {
        List<String> keys = customKeysOf(customId);
        if (keys.isEmpty()) {
            if (emoji == null && ChatForeignEmoji.isCustomId(customId)) {
                return false;
            }
            return clearDiscord(emoji);
        }
        boolean changed = false;
        for (String key : keys) {
            if (clearDiscord(key)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * How many players — not Discord members — reacted with the emoji:
     * what the bridge's own reaction on Discord stands for there. A
     * custom emoji counts every key of its id, since the bot's one
     * reaction there is by the id.
     */
    public int gameCount(String emoji) {
        if (emoji == null) {
            return 0;
        }
        String customId = customIdOf(emoji);
        List<String> keys = customId == null
                ? Collections.singletonList(emoji) : customKeysOf(customId);
        int count = 0;
        for (String key : keys) {
            LinkedHashMap<UUID, String> reactors = this.byEmoji.get(key);
            if (reactors == null) {
                continue;
            }
            for (UUID reactor : reactors.keySet()) {
                if (!LostTalesChatMessagePacket.isDiscordSender(reactor)) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return this.byEmoji.isEmpty();
    }

    /** Whether any emoji reacted with is a foreign one. */
    public boolean hasForeign() {
        for (String emoji : this.byEmoji.keySet()) {
            if (ChatForeignEmoji.isForeign(emoji)) {
                return true;
            }
        }
        return false;
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
