package com.ninuna.losttales.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The commands a mute holds besides the game's own ({@code
 * chat.mutedCommands}, CHAT-63): read as names, a login or password command
 * never among them, matched by any name a command answers to.
 */
public final class ChatMutedCommandsTest {

    @Test
    public void theListIsReadAsNamesAndNeverTakesALogin() {
        List<String> warnings = new ArrayList<String>();
        List<String> names = ChatCommandText.parseMutedNames(new String[] {
                "/PM", "reply", "pm", "login", "two words", ""}, warnings);
        assertEquals(Arrays.asList("pm", "reply"), names);
        assertEquals(2, warnings.size());
    }

    @Test
    public void theListHoldsAtMostItsBound() {
        String[] lines = new String[ChatCommandText.MAX_MUTED_NAMES + 3];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = "cmd" + index;
        }
        List<String> warnings = new ArrayList<String>();
        assertEquals(ChatCommandText.MAX_MUTED_NAMES,
                ChatCommandText.parseMutedNames(lines, warnings).size());
        assertEquals(3, warnings.size());
    }

    @Test
    public void aCommandIsHeldByAnyNameItAnswersToButALoginNever() {
        List<String> muted = Arrays.asList("pm", "mail");
        assertTrue(ChatCommandText.isMutedCommand("PM", null, muted));
        assertTrue(ChatCommandText.isMutedCommand("privatemessage",
                Arrays.asList("pm"), muted));
        assertFalse(ChatCommandText.isMutedCommand("say", null, muted));
        assertFalse(ChatCommandText.isMutedCommand("pm", null,
                Collections.<String>emptyList()));
        // A login answering to a listed name still lets its player in.
        assertFalse(ChatCommandText.isMutedCommand("login",
                Arrays.asList("pm"), muted));
    }
}
