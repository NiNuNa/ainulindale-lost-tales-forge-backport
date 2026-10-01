package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatMessageValidator;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;

/**
 * Holds the ways to speak that bypass the chat's own sending to the
 * chat's rules: the game's {@code /me}, {@code /say} and private
 * messages, LOTR's fellowship messages, and plain chat a client sends
 * the game's way. A muted account is refused, and so are words the chat
 * would refuse (a hidden break, a direction mark). The chat's own lines
 * are checked where they are sent ({@link LostTalesChatService#send}).
 */
public final class ChatSpeechGate {
    /** The commands, by name or alias, that put a player's words before others. */
    static final Set<String> SPEAKING_COMMANDS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "me", "say", "tell", "msg", "w", "fmsg", "fchat")));

    /** After the Server Console has recorded the attempt, which stands either way. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onCommand(CommandEvent event) {
        if (event == null || event.isCanceled() || event.command == null
                || !(event.sender instanceof EntityPlayerMP)
                || !speaks(event.command)) {
            return;
        }
        String words = joined(event.parameters);
        if (words.length() > 0 && !ChatMessageValidator.isValid(words)) {
            event.setCanceled(true);
            return;
        }
        if (LostTalesChatService.refuseIfMuted((EntityPlayerMP)event.sender)) {
            event.setCanceled(true);
        }
    }

    /** Before anything decorates the line. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerChat(ServerChatEvent event) {
        if (event == null || event.isCanceled() || event.player == null) {
            return;
        }
        if (!ChatMessageValidator.isValid(event.message)
                || LostTalesChatService.refuseIfMuted(event.player)) {
            event.setCanceled(true);
        }
    }

    /** Whether the command is one of {@link #SPEAKING_COMMANDS}, by its name or an alias. */
    static boolean speaks(ICommand command) {
        if (isSpeaking(command.getCommandName())) {
            return true;
        }
        List<?> aliases = command.getCommandAliases();
        if (aliases != null) {
            for (Object alias : aliases) {
                if (alias instanceof String && isSpeaking((String)alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isSpeaking(String name) {
        return name != null && SPEAKING_COMMANDS.contains(
                name.trim().toLowerCase(Locale.ROOT));
    }

    /** The command's words as one line, as the game joins them. */
    static String joined(String[] parameters) {
        if (parameters == null) {
            return "";
        }
        StringBuilder line = new StringBuilder();
        for (String parameter : parameters) {
            if (parameter == null || parameter.length() == 0) {
                continue;
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(parameter);
        }
        return line.toString();
    }
}
