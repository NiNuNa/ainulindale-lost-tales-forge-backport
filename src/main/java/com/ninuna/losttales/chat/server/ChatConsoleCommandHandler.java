package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.CommandEvent;

/**
 * Puts every command a person runs into the Server Console:
 * one entry naming who ran what, and for a player where — the tab
 * their client reported just ahead of the command
 * ({@link ChatCommandContexts}) — with the words the console must not
 * repeat left out ({@link ChatConsoleStream#describeCommand}). Only
 * people count — a player, or whoever is at the server's own console;
 * a command block runs on a clock and would drown the stream, and it
 * is world data staff can read for themselves. Recorded as the command
 * is issued, whether or not it then succeeds: the attempt is the fact.
 */
public final class ChatConsoleCommandHandler {
    /**
     * How long after a command is recorded vanilla's notice to operators
     * about it counts as about that command: the two happen on one tick.
     */
    private static final long NOTICE_MILLIS = 1000L;
    /** Who ran the command recorded last, and when. */
    private static String lastActor = "";
    private static long lastRecordedMillis;

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (event == null || event.isCanceled() || event.command == null
                || event.sender == null) {
            return;
        }
        ICommandSender sender = event.sender;
        String actor;
        String context = "";
        if (sender instanceof EntityPlayerMP) {
            actor = sender.getCommandSenderName();
            context = ChatCommandContexts.beginCommand(
                    ((EntityPlayerMP)sender).getUniqueID(),
                    System.currentTimeMillis());
        } else if (sender instanceof MinecraftServer) {
            actor = "Server";
        } else {
            return;
        }
        LostTalesChatService.console(ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, actor,
                ChatConsoleStream.describeCommand(event.command.getCommandName(),
                        event.parameters), context);
        noteRecorded(actor, System.currentTimeMillis());
        // A command may make somebody an operator, or no longer one, or
        // change who reads the console: the watcher looks this tick.
        LostTalesChatRoleRosterWatcher.checkSoon();
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

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        lastActor = "";
        lastRecordedMillis = 0L;
    }
}
