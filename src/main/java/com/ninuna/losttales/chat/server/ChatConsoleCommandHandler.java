package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.CommandEvent;

/**
 * Puts every command a person runs into the shared operator console:
 * one entry naming who ran what, with the words the console must not
 * repeat left out ({@link ChatConsoleStream#describeCommand}). Only
 * people count — a player, or whoever is at the server's own console;
 * a command block runs on a clock and would drown the stream, and it
 * is world data staff can read for themselves. Recorded as the command
 * is issued, whether or not it then succeeds: the attempt is the fact.
 */
public final class ChatConsoleCommandHandler {

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (event == null || event.isCanceled() || event.command == null
                || event.sender == null) {
            return;
        }
        ICommandSender sender = event.sender;
        String actor;
        if (sender instanceof EntityPlayerMP) {
            actor = sender.getCommandSenderName();
        } else if (sender instanceof MinecraftServer) {
            actor = "Server";
        } else {
            return;
        }
        LostTalesChatService.console(ChatConsoleEvent.Kind.COMMAND,
                ChatConsoleEvent.Severity.INFO, actor,
                ChatConsoleStream.describeCommand(event.command.getCommandName(),
                        event.parameters));
    }
}
