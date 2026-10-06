package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.server.ChatServerStatus;
import com.ninuna.losttales.network.packet.LostTalesServerStatusPacket;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.ArrayList;
import java.util.List;

/**
 * The server's status as the server last told it: what the Server's status
 * line in every member list and its card show. The time up is counted on
 * here from when it was told, so nothing is sent as it passes. Forgotten as
 * the player leaves the server.
 */
public final class ClientServerStatus {
    /** What the line parts its parts with: a dot the game's own font draws. */
    static final String SEPARATOR = " · ";

    private static boolean known;
    private static int players;
    private static int maxPlayers;
    private static String address = "";
    private static int ticksPerSecond;
    private static long upMillis;
    private static long receivedMillis;

    private ClientServerStatus() {}

    /** What the server says of itself now. */
    public static synchronized void accept(LostTalesServerStatusPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        known = true;
        players = packet.getPlayers();
        maxPlayers = packet.getMaxPlayers();
        address = packet.getAddress();
        ticksPerSecond = packet.getTicksPerSecond();
        upMillis = packet.getUpMillis();
        receivedMillis = System.currentTimeMillis();
    }

    /** Whether the server has said how it stands. */
    public static synchronized boolean isKnown() {
        return known;
    }

    /** The address the server names; empty for none. */
    public static synchronized String address() {
        return address;
    }

    /** The players shown against the cap: {@code 3/20 players}; empty before the server has said. */
    public static synchronized String players() {
        return !known ? "" : ChatServerStatus.playersPart(LostTalesWords.LANG,
                players, maxPlayers);
    }

    /** The players shown against the cap as figures: {@code 3/20}; empty before the server has said. */
    public static synchronized String count() {
        return !known ? "" : maxPlayers > 0 ? players + "/" + maxPlayers
                : String.valueOf(players);
    }

    /** The ticks a second: {@code 20 TPS}; empty before the server has said. */
    public static synchronized String speed() {
        return !known ? "" : ChatServerStatus.speedPart(LostTalesWords.LANG,
                ticksPerSecond);
    }

    /** How long the server has taken players, counted on to now: {@code 2h 15m}; empty before. */
    public static synchronized String uptime() {
        return !known || upMillis <= 0L ? "" : ChatServerStatus.uptime(
                LostTalesWords.LANG, upMillis + Math.max(0L,
                        System.currentTimeMillis() - receivedMillis));
    }

    /**
     * The status's parts, in this client's language: the players shown
     * against the cap, the address once named, the ticks a second, and the
     * time up once the server has taken players. Empty before the server
     * has said anything.
     */
    public static synchronized List<String> parts() {
        if (!known) {
            return new ArrayList<String>(0);
        }
        return ChatServerStatus.partsOf(LostTalesWords.LANG, players,
                maxPlayers, address, ticksPerSecond, upMillis <= 0L ? 0L
                        : upMillis + Math.max(0L,
                                System.currentTimeMillis() - receivedMillis));
    }

    /** The Server's status line: its parts, a dot between each two. */
    public static String line() {
        StringBuilder line = new StringBuilder();
        for (String part : parts()) {
            if (line.length() > 0) {
                line.append(SEPARATOR);
            }
            line.append(part);
        }
        return line.toString();
    }

    public static synchronized void clear() {
        known = false;
        players = 0;
        maxPlayers = 0;
        address = "";
        ticksPerSecond = 0;
        upMillis = 0L;
        receivedMillis = 0L;
    }
}
