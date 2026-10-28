package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatCommandText;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.rcon.RConConsoleSource;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.CommandEvent;

/**
 * Puts every command a person runs into the Server Log:
 * one entry naming who ran what, and for a player where — the page
 * their client reported just ahead of the command
 * ({@link ChatCommandContexts}) — with the words the console must not
 * repeat left out ({@link ChatCommandText#describe}). Only
 * people count — a player, whoever is at the server's own console, or
 * whoever sends commands over RCON; a command block runs on a clock and
 * would drown the stream, and it
 * is world data staff can read for themselves. Recorded as the command
 * is issued, whether or not it then succeeds: the attempt is the fact.
 *
 * <p>RCON runs its commands on a thread of its own, so a command it sends
 * waits, already described, for the server thread's next tick and goes
 * into the Server Log there, under RCON's own name (DATA-13). At most
 * {@link #MAX_RCON_WAITING} wait; one past that is left out and logged.</p>
 */
public final class ChatConsoleCommandHandler {
    /**
     * How long after a command is recorded vanilla's notice to operators
     * about it counts as about that command: the two happen on one tick.
     */
    private static final long NOTICE_MILLIS = 1000L;
    /** The most RCON commands that wait for the server thread at once. */
    static final int MAX_RCON_WAITING = 64;
    /** Who ran the command recorded last, and when. */
    private static String lastActor = "";
    private static long lastRecordedMillis;
    /** RCON's commands, described, waiting for the server thread. */
    private static final Queue<String> RCON_WAITING = new ConcurrentLinkedQueue<String>();
    private static final AtomicInteger RCON_COUNT = new AtomicInteger();

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (event == null || event.isCanceled() || event.command == null
                || event.sender == null) {
            return;
        }
        ICommandSender sender = event.sender;
        if (sender instanceof RConConsoleSource) {
            waitForServerThread(ChatCommandText.describe(event.command.getCommandName(),
                    aliasesOf(event), event.parameters, ChatCommandText.serverNames()));
            return;
        }
        String actor;
        String context = "";
        if (sender instanceof EntityPlayerMP) {
            actor = sender.getCommandSenderName();
            context = ChatCommandContexts.beginCommand(
                    ((EntityPlayerMP)sender).getUniqueID(),
                    System.currentTimeMillis());
        } else if (sender instanceof MinecraftServer) {
            actor = LostTalesServerBroadcastHook.SERVER_NAME;
        } else {
            return;
        }
        LostTalesChatService.console(ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, actor,
                ChatCommandText.describe(event.command.getCommandName(),
                        aliasesOf(event), event.parameters,
                        ChatCommandText.serverNames()), context);
        noteRecorded(actor, System.currentTimeMillis());
        // A command may make somebody an operator, or no longer one, or
        // change who reads the console: the watcher looks this tick.
        LostTalesChatRoleRosterWatcher.checkSoon();
    }

    /**
     * Writes RCON's waiting commands into the Server Log, on the server
     * thread, at the start of its tick.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.START
                || RCON_COUNT.get() == 0) {
            return;
        }
        List<String> commands = takeRconWaiting();
        for (String command : commands) {
            LostTalesChatService.console(ChatConsoleEvent.Kind.COMMAND,
                    ChatConsoleEvent.Severity.INFO, RConConsoleSource.instance
                            .getCommandSenderName(), command);
        }
        if (!commands.isEmpty()) {
            LostTalesChatRoleRosterWatcher.checkSoon();
        }
    }

    /**
     * Holds an RCON command, described, for the server thread; from any
     * thread. False, and the command is logged as left out, while
     * {@link #MAX_RCON_WAITING} already wait.
     */
    static boolean waitForServerThread(String described) {
        if (described == null || described.trim().length() == 0) {
            return false;
        }
        if (RCON_COUNT.incrementAndGet() > MAX_RCON_WAITING) {
            RCON_COUNT.decrementAndGet();
            LostTalesLog.warning("An RCON command was left out of the Server Log: %s more"
                    + " wait for the server thread already (%s)",
                    Integer.valueOf(MAX_RCON_WAITING), described);
            return false;
        }
        RCON_WAITING.add(described);
        return true;
    }

    /** RCON's waiting commands, in the order they came, taken out. */
    static List<String> takeRconWaiting() {
        List<String> taken = new ArrayList<String>();
        String described;
        while ((described = RCON_WAITING.poll()) != null) {
            RCON_COUNT.decrementAndGet();
            taken.add(described);
        }
        return taken;
    }

    /** The other names the command answers to; none where it gives none. */
    private static List<String> aliasesOf(CommandEvent event) {
        List<String> names = new ArrayList<String>();
        try {
            List<?> aliases = event.command.getCommandAliases();
            if (aliases != null) {
                for (Object alias : aliases) {
                    if (alias instanceof String) {
                        names.add((String)alias);
                    }
                }
            }
        } catch (RuntimeException brokenCommand) {
            // Another mod's command that cannot list its names is matched
            // by its own name alone.
            return Collections.emptyList();
        }
        return names;
    }

    static synchronized void noteRecorded(String actor, long now) {
        lastActor = actor;
        lastRecordedMillis = now;
    }

    /**
     * Whether the console recorded a command {@code actor} ran a moment
     * ago: the command vanilla's notice to operators is then about, which
     * a reader of the console is not told twice.
     */
    static synchronized boolean recordedJustNow(String actor, long now) {
        return actor != null && actor.length() > 0
                && actor.equalsIgnoreCase(lastActor)
                && now - lastRecordedMillis <= NOTICE_MILLIS;
    }

    /** Cleared with the rest of the server's chat state, RCON's waiting commands too. */
    public static synchronized void clear() {
        lastActor = "";
        lastRecordedMillis = 0L;
        takeRconWaiting();
    }
}
