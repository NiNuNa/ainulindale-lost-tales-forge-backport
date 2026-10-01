package com.ninuna.losttales.chat.server;

import java.util.Arrays;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The game's and LOTR's ways to speak are found by name or alias, and
 * their words are read as the game joins them.
 */
public final class ChatSpeechGateTest {

    @Test
    public void theGamesAndLotrsSpeakingCommandsAreFound() {
        for (String name : Arrays.asList("me", "say", "tell", "msg", "w",
                "fmsg", "fchat", "ME", " Tell ")) {
            assertTrue(name, ChatSpeechGate.isSpeaking(name));
        }
        for (String name : Arrays.asList("give", "tp", "losttales", "", null)) {
            assertFalse(String.valueOf(name), ChatSpeechGate.isSpeaking(name));
        }
    }

    @Test
    public void aCommandSpeaksByItsNameOrAnAlias() {
        assertTrue(ChatSpeechGate.speaks(command("tell")));
        assertTrue(ChatSpeechGate.speaks(command("whisper", "w")));
        assertFalse(ChatSpeechGate.speaks(command("give", "item")));
    }

    @Test
    public void theWordsAreJoinedAsTheGameJoinsThem() {
        assertEquals("waves at Sam",
                ChatSpeechGate.joined(new String[] {"waves", "at", "Sam"}));
        assertEquals("a b", ChatSpeechGate.joined(new String[] {"a", "", null, "b"}));
        assertEquals("", ChatSpeechGate.joined(null));
        assertEquals("", ChatSpeechGate.joined(new String[0]));
    }

    private static ICommand command(final String name, final String... aliases) {
        return new CommandBase() {
            @Override
            public String getCommandName() {
                return name;
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "";
            }

            @Override
            public List getCommandAliases() {
                return Arrays.asList(aliases);
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {}
        };
    }
}
