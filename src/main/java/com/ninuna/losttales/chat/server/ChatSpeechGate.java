package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatCommandText;
import com.ninuna.losttales.chat.ChatMessageValidator;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayList;
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
 * messages, and plain chat a client sends the game's way. A muted account
 * is refused, and so are words the chat would refuse (a hidden break, a
 * direction mark). Other mods' commands the server lists in
 * {@code chat.mutedCommands} are held under a mute too, never a login or
 * password command. The chat's own lines are checked where they are sent
 * ({@link LostTalesChatService#send}).
 */
public final class ChatSpeechGate {
    /** The game's emote command. */
    static final String EMOTE = "me";

    /**
     * The commands, by name or alias, that put a player's words before
     * others: the game's {@code /me}, {@code /say} and private message.
     */
    static final Set<String> SPEAKING_COMMANDS = speakingCommands();

    /** After the Server Log has recorded the attempt, which stands either way. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onCommand(CommandEvent event) {
        if (event == null || event.isCanceled() || event.command == null
                || !(event.sender instanceof EntityPlayerMP)) {
            return;
        }
        if (!speaks(event.command)) {
            // Another mod's way to speak the server lists
            // (chat.mutedCommands): held under a mute, never read.
            if (isMutedCommand(event.command)
                    && LostTalesChatService.refuseIfMuted(
                            (EntityPlayerMP)event.sender)) {
                event.setCanceled(true);
            }
            return;
        }
        String words = joined(event.parameters);
        if (words.length() > 0 && !ChatMessageValidator.isValid(words)) {
            event.setCanceled(true);
            return;
        }
        if (isEmote(event.command)) {
            // The game's /me is said as the chat's own action in OOC Chat,
            // under the account, and never in Global (CHAT-37): the chat's
            // send holds it to every rule a typed line passes.
            event.setCanceled(true);
            if (words.length() > 0) {
                LostTalesChatService.actOutOfCharacter((EntityPlayerMP)event.sender,
                        words);
            }
            return;
        }
        if (LostTalesChatService.refuseIfMuted((EntityPlayerMP)event.sender)) {
            event.setCanceled(true);
        }
    }

    /** Whether the command is the game's {@code /me}, by its name or an alias. */
    static boolean isEmote(ICommand command) {
        if (EMOTE.equals(command.getCommandName())) {
            return true;
        }
        List<?> aliases = command.getCommandAliases();
        return aliases != null && aliases.contains(EMOTE);
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

    /**
     * Whether the command stands on the server's list of commands a mute
     * holds ({@code chat.mutedCommands}), by its name or an alias; a
     * login or password command never does.
     */
    static boolean isMutedCommand(ICommand command) {
        List<String> aliases = new ArrayList<String>();
        List<?> raw = command.getCommandAliases();
        if (raw != null) {
            for (Object alias : raw) {
                if (alias instanceof String) {
                    aliases.add((String)alias);
                }
            }
        }
        return ChatCommandText.isMutedCommand(command.getCommandName(), aliases,
                ChatCommandText.mutedNames());
    }

    private static Set<String> speakingCommands() {
        Set<String> names = new HashSet<String>(ChatCommandText.PRIVATE_MESSAGE_NAMES);
        names.add(EMOTE);
        names.add("say");
        return Collections.unmodifiableSet(names);
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
