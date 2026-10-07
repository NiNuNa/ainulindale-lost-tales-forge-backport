package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatStatusLine;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesServerStatusPacket;
import com.ninuna.losttales.util.LostTalesWords;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * What the server says of itself: how many players show as online against
 * its cap, the address it is joined by once an operator has written one,
 * how many ticks it runs a second, and how long it has taken players. One
 * status, said everywhere: the Server's status line and card in the game
 * ({@link LostTalesServerStatusPacket}, which each client writes in its own
 * words and whose time up it counts on itself), the topic of every linked
 * Discord channel, and Discord's {@code /server}. Looked at once a second;
 * the players are told only when what they show changes.
 *
 * <p>An Invisible player is not counted, as Discord never counts one; the
 * address is never read off the machine, only from {@code serverAddress},
 * since a server's own bind address is often a private one.</p>
 */
public final class ChatServerStatus {
    /** How often the status is looked at: once a second. */
    private static final int LOOK_TICKS = 20;
    /** Whether the players have been told the status yet. */
    private static boolean told;
    private static int players;
    private static int maxPlayers;
    private static String address = "";
    private static int ticksPerSecond;
    /** The status in Discord's words, as it was last asked for; empty before the first look. */
    private static List<String> parts = Collections.emptyList();
    /** When the server began to take players; 0 before. */
    private static long startedMillis;
    private static int ticks;

    /** Registered on the event bus for the once-a-second look. */
    public ChatServerStatus() {}

    /** Once a second the server's status is looked at again. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < LOOK_TICKS) {
            return;
        }
        ticks = 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        look(ChatPresenceService.countShownOnline(
                        server.getConfigurationManager().playerEntityList),
                server.getMaxPlayers(), LostTalesConfig.serverAddress,
                ticksPerSecond(server.tickTimeArray));
    }

    /**
     * The status as it is now: the players told when what they show
     * changed, Discord's topics asked for again when their words did.
     */
    private static synchronized void look(int playersNow, int maxNow,
                                          String addressNow, int ticksNow) {
        int shown = Math.max(0, Math.min(LostTalesServerStatusPacket.MAX_PLAYERS,
                playersNow));
        int cap = Math.max(0, Math.min(LostTalesServerStatusPacket.MAX_PLAYERS,
                maxNow));
        String named = ChatStatusLine.clean(addressNow);
        int speed = Math.max(0, Math.min(
                LostTalesServerStatusPacket.MAX_TICKS_PER_SECOND, ticksNow));
        if (!told || shown != players || cap != maxPlayers
                || !named.equals(address) || speed != ticksPerSecond) {
            told = true;
            players = shown;
            maxPlayers = cap;
            address = named;
            ticksPerSecond = speed;
            LostTalesNetworkHandler.CHANNEL.sendToAll(packet());
        }
        List<String> words = partsOf(LostTalesWords.LANG, shown, cap, named, speed,
                upMillis());
        if (!words.equals(parts)) {
            parts = Collections.unmodifiableList(words);
            LostTalesDiscordBridge.getInstance().requestStatusRefresh();
        }
    }

    /** The status to a player who has just joined; nothing before the first look. */
    public static synchronized void sendTo(EntityPlayerMP joiner) {
        if (joiner != null && told) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet(), joiner);
        }
    }

    private static LostTalesServerStatusPacket packet() {
        return new LostTalesServerStatusPacket(players, maxPlayers, address,
                ticksPerSecond, upMillis());
    }

    /** How long the server has taken players; 0 before it has. */
    private static long upMillis() {
        return startedMillis <= 0L ? 0L
                : Math.max(1L, System.currentTimeMillis() - startedMillis);
    }

    /** The status in Discord's words, as it was last asked for; empty before the first look. */
    public static synchronized List<String> parts() {
        return parts;
    }

    /**
     * The status's parts, worded from the lang file: the game's own words
     * in each game and the server's for Discord. The players shown against
     * the cap ({@code 3/20 players}, or {@code 3 players} with none), the
     * address once one is written, the ticks a second ({@code 20 TPS}),
     * and how long the server has taken players ({@code up 2h 15m}) once
     * it has.
     */
    public static List<String> partsOf(LostTalesWords words, int players,
                                       int maxPlayers, String address,
                                       int ticksPerSecond, long upMillis) {
        List<String> parts = new ArrayList<String>(4);
        parts.add(playersPart(words, players, maxPlayers));
        String shown = ChatStatusLine.clean(address);
        if (shown.length() > 0) {
            parts.add(shown);
        }
        parts.add(speedPart(words, ticksPerSecond));
        if (upMillis > 0L) {
            parts.add(words.format("chat.losttales.server.status.up",
                    uptime(words, upMillis)));
        }
        return parts;
    }

    /** The players shown against the cap: {@code 3/20 players}, {@code 3 players} or {@code 1 player} with none. */
    public static String playersPart(LostTalesWords words, int players, int maxPlayers) {
        int online = Math.max(0, players);
        if (maxPlayers > 0) {
            return words.format("chat.losttales.server.status.players",
                    online, maxPlayers);
        }
        return words.format(online == 1
                ? "chat.losttales.server.status.player"
                : "chat.losttales.server.status.players_uncapped", online);
    }

    /** The ticks a second: {@code 20 TPS}. */
    public static String speedPart(LostTalesWords words, int ticksPerSecond) {
        return words.format("chat.losttales.server.status.tps",
                Math.max(0, ticksPerSecond));
    }

    /**
     * How long, to the minute: {@code 2d 3h}, {@code 2h 15m}, {@code 15m},
     * and {@code <1m} for less than a minute, as the lang file words them.
     */
    public static String uptime(LostTalesWords words, long millis) {
        long[] parts = uptimeParts(millis);
        if (parts[0] > 0) {
            return words.format("chat.losttales.server.status.uptime.days",
                    parts[0], parts[1]);
        }
        if (parts[1] > 0) {
            return words.format("chat.losttales.server.status.uptime.hours",
                    parts[1], parts[2]);
        }
        return parts[2] > 0
                ? words.format("chat.losttales.server.status.uptime.minutes",
                        parts[2])
                : words.format("chat.losttales.server.status.uptime.moment");
    }

    /** How long, as whole days, the hours past them and the minutes past those. */
    static long[] uptimeParts(long millis) {
        long minutes = Math.max(0L, millis) / 60000L;
        return new long[] {minutes / 1440L, (minutes % 1440L) / 60L,
                minutes % 60L};
    }

    /**
     * How many ticks the server runs a second, from the nanoseconds its
     * last ticks took: twenty at most, as the game never runs faster.
     */
    static int ticksPerSecond(long[] tickNanos) {
        if (tickNanos == null || tickNanos.length == 0) {
            return 20;
        }
        long total = 0L;
        int counted = 0;
        for (long nanos : tickNanos) {
            if (nanos > 0L) {
                total += nanos;
                counted++;
            }
        }
        if (counted == 0) {
            return 20;
        }
        double millis = total / (double)counted / 1.0E6D;
        return millis <= 50.0D ? 20 : (int)Math.round(1000.0D / millis);
    }

    /** The server takes players from now: what the time up is counted from. */
    public static synchronized void started() {
        startedMillis = System.currentTimeMillis();
    }

    public static synchronized void clear() {
        told = false;
        players = 0;
        maxPlayers = 0;
        address = "";
        ticksPerSecond = 0;
        parts = Collections.emptyList();
        startedMillis = 0L;
        ticks = 0;
    }
}
