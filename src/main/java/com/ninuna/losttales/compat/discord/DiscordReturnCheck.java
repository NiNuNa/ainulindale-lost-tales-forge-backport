package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the bridge looks at once the live connection is back after being
 * up this server run: no reader hears a reaction while it is down, and a
 * session Discord could not resume says nothing of what it missed. Each
 * Discord channel read is read once, its newest page of
 * {@link DiscordJson#PAGE_SIZE} messages: what was said or changed there
 * since the last read goes in as a read would take it, and every bridged
 * message on the page whose reactions Discord counts otherwise than the
 * game holds them is put right ({@link Fix}). A fix reads the members
 * Discord lists for that emoji, at most {@link DiscordJson#MAX_REACTORS_READ},
 * or, where Discord holds none of it any more, reads nothing and takes
 * the game's away.
 *
 * <p>Bounded so a return stays within Discord's rate limits: one page a
 * channel, and at most {@link #MAX_FIXES} emoji put right, the newest
 * messages first; the worker sends one of these requests a pass. Owned
 * by the worker; one thread.</p>
 */
final class DiscordReturnCheck {
    /** Emoji put right after one return, across every channel. */
    static final int MAX_FIXES = 20;

    /** One emoji of one bridged message to put right in the game. */
    static final class Fix {
        final String channelId;
        final String messageId;
        /** The chat's key for the emoji; for a custom emoji with no key, empty. */
        final String key;
        /** A custom emoji's id; empty for a Unicode one. */
        final String customId;
        /** The emoji as Discord's reactions path takes it; empty for a fix that reads nothing. */
        final String pathForm;
        /** Whether Discord counts super reactions with it, listed apart. */
        final boolean burst;
        /**
         * Whether the emoji is the only one of the message's held under its
         * key, so members Discord no longer lists may be taken away. A
         * custom emoji named as one of the chat's own shares its key with
         * that emoji, and only adds.
         */
        final boolean alone;
        /**
         * Where the reactions heard live stood as the page this fix comes
         * from was read ({@link DiscordLiveReactions#mark}): a fix that
         * reads nothing gives way to what was heard since.
         */
        final long heardMark;

        Fix(String channelId, String messageId, String key, String customId,
            String pathForm, boolean burst, boolean alone, long heardMark) {
            this.channelId = channelId;
            this.messageId = messageId;
            this.key = key == null ? "" : key;
            this.customId = customId == null ? "" : customId;
            this.pathForm = pathForm == null ? "" : pathForm;
            this.burst = burst;
            this.alone = alone;
            this.heardMark = heardMark;
        }

        /** Whether Discord's members are to be read; otherwise Discord holds none of them. */
        boolean reads() {
            return this.pathForm.length() > 0;
        }
    }

    private final Deque<String> channels;
    private final Deque<Fix> fixes = new ArrayDeque<Fix>();
    private int offered;

    DiscordReturnCheck(Collection<String> channelIds) {
        this.channels = new ArrayDeque<String>(channelIds == null
                ? Collections.<String>emptyList() : channelIds);
    }

    /** The next channel whose page is to be read, or null when every one has been. */
    String nextChannel() {
        return this.channels.poll();
    }

    /** A channel whose read Discord asked to wait: read first once the pause is over. */
    void readAgain(String channelId) {
        if (channelId != null) {
            this.channels.addFirst(channelId);
        }
    }

    /** Takes a fix while the return has room for one; false once it has none. */
    boolean offer(Fix fix) {
        if (fix == null || this.offered >= MAX_FIXES) {
            return false;
        }
        this.offered++;
        this.fixes.add(fix);
        return true;
    }

    /** The fix to make next, left in place; null for none. */
    Fix peekFix() {
        return this.fixes.peek();
    }

    /** The fix made, or given up. */
    Fix pollFix() {
        return this.fixes.poll();
    }

    /** Whether nothing is left to read or to put right. */
    boolean isDone() {
        return this.fixes.isEmpty() && this.channels.isEmpty();
    }

    /**
     * The emoji of one bridged message whose reactions from its Discord
     * channel the game holds otherwise than Discord counts them.
     * {@code onDiscord} is what the page counts, the bot left out;
     * {@code inGame} the game's count of Discord members' reactions from
     * that channel by the chat's key. A custom emoji is matched by its id
     * among the game's keys, whatever it is called now. An emoji Discord
     * counts and names no way the chat can key is left alone. Two of
     * Discord's emoji the game holds under one key (a custom emoji named
     * as one of the chat's own, beside that emoji) are counted together,
     * and their fixes only add. {@code heardMark} is where the reactions
     * heard live stood as the page was read.
     */
    static List<Fix> fixesFor(String channelId, String messageId,
                              List<DiscordJson.ReactionCount> onDiscord,
                              Map<String, Integer> inGame, long heardMark) {
        List<DiscordJson.ReactionCount> counted = onDiscord == null
                ? Collections.<DiscordJson.ReactionCount>emptyList() : onDiscord;
        List<String> keyOf = new ArrayList<String>(counted.size());
        List<List<String>> heldUnder = new ArrayList<List<String>>(counted.size());
        Map<String, Integer> claims = new HashMap<String, Integer>();
        for (DiscordJson.ReactionCount count : counted) {
            String key = count.reactionKey();
            List<String> keys = keysOf(count.emojiId, key, inGame);
            keyOf.add(key);
            heldUnder.add(keys);
            Set<String> claimed = new HashSet<String>(keys);
            if (keys.isEmpty() && key != null) {
                claimed.add(key);
            }
            for (String each : claimed) {
                Integer before = claims.get(each);
                claims.put(each, Integer.valueOf(before == null ? 1 : before.intValue() + 1));
            }
        }
        List<Fix> fixes = new ArrayList<Fix>();
        for (int index = 0; index < counted.size(); index++) {
            DiscordJson.ReactionCount count = counted.get(index);
            String key = keyOf.get(index);
            List<String> keys = heldUnder.get(index);
            if (key == null && keys.isEmpty()) {
                continue;
            }
            boolean alone = true;
            for (String each : keys.isEmpty() ? Collections.singletonList(key) : keys) {
                if (claims.get(each).intValue() > 1) {
                    alone = false;
                }
            }
            int held = 0;
            for (String each : keys) {
                held += countOf(inGame, each);
            }
            int members = alone ? count.members : sharedMembers(counted, heldUnder,
                    keyOf, keys.isEmpty() ? Collections.singletonList(key) : keys);
            if (held == members) {
                continue;
            }
            String form = count.members > 0 ? count.pathForm() : "";
            if (form.length() == 0 && (count.members > 0 || !alone)) {
                continue;
            }
            fixes.add(new Fix(channelId, messageId,
                    key != null ? key : keys.get(0), count.emojiId, form,
                    count.burst, alone, heardMark));
        }
        if (inGame != null) {
            for (Map.Entry<String, Integer> kind : inGame.entrySet()) {
                if (!claims.containsKey(kind.getKey()) && kind.getValue() != null
                        && kind.getValue().intValue() > 0) {
                    fixes.add(new Fix(channelId, messageId, kind.getKey(),
                            customIdOf(kind.getKey()), "", false, true, heardMark));
                }
            }
        }
        return fixes;
    }

    /** The members of every one of Discord's emoji the game holds under any of {@code keys}. */
    private static int sharedMembers(List<DiscordJson.ReactionCount> counted,
                                     List<List<String>> heldUnder, List<String> keyOf,
                                     List<String> keys) {
        int members = 0;
        for (int index = 0; index < counted.size(); index++) {
            List<String> under = heldUnder.get(index).isEmpty() && keyOf.get(index) != null
                    ? Collections.singletonList(keyOf.get(index)) : heldUnder.get(index);
            for (String each : under) {
                if (keys.contains(each)) {
                    members += counted.get(index).members;
                    break;
                }
            }
        }
        return members;
    }

    /**
     * The game's keys a Discord emoji is held under: a custom emoji's
     * keys with its id, else the key its name makes.
     */
    private static List<String> keysOf(String customId, String key,
                                       Map<String, Integer> inGame) {
        List<String> keys = new ArrayList<String>(1);
        if (inGame != null && ChatForeignEmoji.isCustomId(customId)) {
            for (String held : inGame.keySet()) {
                if (customId.equals(customIdOf(held))) {
                    keys.add(held);
                }
            }
        }
        if (keys.isEmpty() && key != null && inGame != null && inGame.containsKey(key)) {
            keys.add(key);
        }
        return keys;
    }

    private static int countOf(Map<String, Integer> inGame, String key) {
        Integer count = inGame == null ? null : inGame.get(key);
        return count == null ? 0 : count.intValue();
    }

    /** A custom key's id, or empty for any other key. */
    private static String customIdOf(String key) {
        return ChatForeignEmoji.isCustom(key)
                ? key.substring(key.indexOf(':') + 1) : "";
    }
}
