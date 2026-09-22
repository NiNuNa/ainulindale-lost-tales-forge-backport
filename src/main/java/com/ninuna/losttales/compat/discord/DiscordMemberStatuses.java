package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatPresence;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What each player's client has been told of Discord members' statuses,
 * and what it is told next. A player hears only about members who can see
 * a Discord channel linked to a conversation the player can read, so
 * nobody learns anything about a Discord channel whose lines they never
 * see. Once a second the bridge passes in each player's linked channels
 * and the members whose status changed, and gets back what to send each
 * player: each member's status with their custom status as its line. A
 * player is sent at most {@link #MAX_PER_STEP} members per step, so a
 * large server reaches a player who has just joined over a few seconds
 * instead of in one burst. Server thread.
 */
final class DiscordMemberStatuses {
    static final int MAX_PER_STEP = 512;

    /** Where the statuses come from. */
    interface Source {
        /** Everyone who can see any of the channels, by Discord id. */
        Set<String> usersSeeing(List<String> channelIds);

        /** What a member is doing; null for offline. */
        ChatPresence statusOf(String userId);

        /** An online member's custom status as a status line; empty for none. */
        String lineOf(String userId);
    }

    /** What a member shows a player: their status and the line under it. */
    static final class Shown {
        final ChatPresence status;
        final String line;

        Shown(ChatPresence status, String line) {
            this.status = status;
            this.line = line == null ? "" : line;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Shown)) {
                return false;
            }
            Shown shown = (Shown)other;
            return this.status == shown.status && this.line.equals(shown.line);
        }

        @Override
        public int hashCode() {
            return 31 * (this.status == null ? 0 : this.status.hashCode())
                    + this.line.hashCode();
        }
    }

    /** What one player's client holds. */
    private static final class Told {
        List<String> channels;
        final Map<String, Shown> members = new HashMap<String, Shown>();
        /** Whether the client still waits for part of a whole telling. */
        boolean behind = true;
    }

    private final Map<UUID, Told> told = new HashMap<UUID, Told>();

    /**
     * One step. {@code players} maps each player online to the Discord
     * channels linked to what they can read, and {@code changed} names the
     * members whose status changed since the last step. Answers, for each
     * player to be sent anything, what to send of each member; null means
     * the member is now offline to them. Everything a player should hold
     * is checked when their channels changed, when they have just joined,
     * and for everyone when {@code seeingChanged} says who can see a
     * channel may have changed.
     */
    Map<UUID, Map<String, Shown>> step(Map<UUID, List<String>> players,
                                       Collection<String> changed,
                                       boolean seeingChanged, Source source) {
        this.told.keySet().retainAll(players.keySet());
        if (seeingChanged) {
            for (Told state : this.told.values()) {
                state.behind = true;
            }
        }
        Map<List<String>, Set<String>> seeingByChannels =
                new HashMap<List<String>, Set<String>>();
        Map<UUID, Map<String, Shown>> sends = new LinkedHashMap<UUID, Map<String, Shown>>();
        for (Map.Entry<UUID, List<String>> player : players.entrySet()) {
            Told state = this.told.get(player.getKey());
            if (state == null) {
                state = new Told();
                this.told.put(player.getKey(), state);
            }
            if (!player.getValue().equals(state.channels)) {
                state.channels = new ArrayList<String>(player.getValue());
                state.behind = true;
            }
            Set<String> seeing = seeingByChannels.get(state.channels);
            if (seeing == null) {
                seeing = source.usersSeeing(state.channels);
                seeingByChannels.put(state.channels, seeing);
            }
            Map<String, Shown> send = state.behind
                    ? everything(state, seeing, source)
                    : changes(state, seeing, changed, source);
            for (Map.Entry<String, Shown> entry : send.entrySet()) {
                if (entry.getValue() == null) {
                    state.members.remove(entry.getKey());
                } else {
                    state.members.put(entry.getKey(), entry.getValue());
                }
            }
            if (!send.isEmpty()) {
                sends.put(player.getKey(), send);
            }
        }
        return sends;
    }

    /** A player who left: if they come back, their client is told everything again. */
    void forget(UUID playerId) {
        this.told.remove(playerId);
    }

    void clear() {
        this.told.clear();
    }

    /** What a member shows a player who may see them now; null while offline. */
    private static Shown shownOf(String userId, Source source) {
        ChatPresence status = source.statusOf(userId);
        return status == null ? null : new Shown(status, source.lineOf(userId));
    }

    /**
     * Whatever the client holds that differs from what it should: every
     * member it may see who is online now, and every member it was told
     * of who is offline now or is no longer one it may see.
     */
    private static Map<String, Shown> everything(Told state, Set<String> seeing,
                                                 Source source) {
        Map<String, Shown> send = new LinkedHashMap<String, Shown>();
        for (String userId : seeing) {
            Shown shown = shownOf(userId, source);
            if (shown != null && !shown.equals(state.members.get(userId))
                    && !full(send, state)) {
                send.put(userId, shown);
            }
        }
        for (String userId : state.members.keySet()) {
            if ((!seeing.contains(userId) || source.statusOf(userId) == null)
                    && !full(send, state)) {
                send.put(userId, null);
            }
        }
        if (send.size() < MAX_PER_STEP) {
            state.behind = false;
        }
        return send;
    }

    /** The changed members the client may see, or was told of, whom it holds wrong. */
    private static Map<String, Shown> changes(Told state, Set<String> seeing,
                                              Collection<String> changed,
                                              Source source) {
        Map<String, Shown> send = new LinkedHashMap<String, Shown>();
        for (String userId : changed) {
            Shown shown = seeing.contains(userId) ? shownOf(userId, source) : null;
            Shown held = state.members.get(userId);
            boolean same = shown == null ? held == null : shown.equals(held);
            if (!same && !full(send, state)) {
                send.put(userId, shown);
            }
        }
        return send;
    }

    /**
     * Whether a step's sends are as many as a player takes in one; the
     * rest then waits for a whole telling at the next step.
     */
    private static boolean full(Map<String, Shown> send, Told state) {
        if (send.size() < MAX_PER_STEP) {
            return false;
        }
        state.behind = true;
        return true;
    }
}
