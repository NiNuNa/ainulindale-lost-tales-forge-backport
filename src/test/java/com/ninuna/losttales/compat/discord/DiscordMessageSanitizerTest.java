package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageValidator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DiscordMessageSanitizerTest {

    @Test
    public void discordMarkupIsSpelledOutAndLinesAreFlattened() {
        Map<String, String> names = new HashMap<String, String>();
        names.put("1234", "Frodo");
        assertEquals("hey @Frodo and @user, see #channel :smile: @role",
                DiscordMessageSanitizer.inbound(
                        "hey <@1234> and <@!99>,\nsee <#55> <a:smile:7> <@&8>",
                        names));
        assertEquals("no codes here",
                DiscordMessageSanitizer.inbound("no §ccodes here",
                        Collections.<String, String>emptyMap()));
        assertEquals("", DiscordMessageSanitizer.inbound("  \n\t ", null));
        assertEquals("", DiscordMessageSanitizer.inbound(null, null));
    }

    @Test
    public void longMessagesAreCutToTheChatLimit() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            text.append("word ");
        }
        String cut = DiscordMessageSanitizer.inbound(text.toString(), null);
        assertTrue(cut.length() <= ChatMessageValidator.MAX_CHARACTERS);
        assertTrue(cut.endsWith("..."));
        assertTrue(ChatMessageValidator.isValid(cut));
    }

    @Test
    public void namesAreBoundedAndCleaned() {
        assertEquals("Sam Gamgee",
                DiscordMessageSanitizer.inboundName(" Sam\n§lGamgee "));
        assertEquals("", DiscordMessageSanitizer.inboundName(null));
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 50; index++) {
            name.append('x');
        }
        assertEquals(32, DiscordMessageSanitizer.inboundName(
                name.toString()).length());
    }
}
