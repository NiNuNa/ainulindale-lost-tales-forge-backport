package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ChatWelcomeTest {
    @After
    public void forget() {
        ChatWelcome.clear();
    }

    @Test
    public void theWelcomeWaitsInOoc() {
        assertEquals(ChatChannel.OOC, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation(ChatWelcome.KEY,
                        new ChatComponentText("Read the rules"))));
    }

    @Test
    public void linesAreCleanBoundedAndNeverBlank() {
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 300; index++) {
            long_.append('a');
        }
        List<String> lines = ChatWelcome.lines(new String[] {
                " §cWelcome to the Shire! ", "", "   ", "Rules:\tbe kind",
                long_.toString(), null});
        assertEquals("Welcome to the Shire!", lines.get(0));
        assertEquals("Rules: be kind", lines.get(1));
        assertEquals(ChatMessageValidator.MAX_CHARACTERS, lines.get(2).length());
        assertEquals(3, lines.size());
        assertTrue(ChatWelcome.lines(null).isEmpty());
    }

    @Test
    public void aWelcomeHoldsEightLinesAtMost() {
        String[] many = new String[12];
        Arrays.fill(many, "line");
        assertEquals(ChatWelcome.MAX_LINES, ChatWelcome.lines(many).size());
    }

    @Test
    public void aJoinIsWelcomedAMomentLaterOnce() {
        UUID player = UUID.randomUUID();
        ChatWelcome.noteArrival(player);
        for (int tick = 1; tick < ChatWelcome.DELAY_TICKS; tick++) {
            assertTrue(ChatWelcome.due().isEmpty());
        }
        assertEquals(Collections.singletonList(player), ChatWelcome.due());
        assertTrue(ChatWelcome.due().isEmpty());
    }
}
