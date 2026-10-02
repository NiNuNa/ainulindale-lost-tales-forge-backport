package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A command is shown without what it must not repeat, in the console and in its echo alike. */
public final class ChatCommandTextTest {

    /** What a command carried is repeated only when it is safe to. */
    @Test
    public void commandsAreDescribedWithoutTheirSecrets() {
        assertEquals("/gamemode 1 Steve",
                ChatCommandText.describe("gamemode", new String[] {"1", "Steve"}));
        assertEquals("/losttales chat mute Bob 15m spam",
                ChatCommandText.describe("losttales",
                        new String[] {"chat", "mute", "Bob", "15m", "spam"}));
        // A private message keeps whom it went to, never what it said.
        assertEquals("/msg Bob ...",
                ChatCommandText.describe("msg", new String[] {"Bob", "the", "key"}));
        assertEquals("/tell Bob",
                ChatCommandText.describe("Tell", new String[] {"Bob"}));
        // A config change keeps the key, never the value.
        assertEquals("/losttales config set discord botToken ...",
                ChatCommandText.describe("losttales", new String[] {
                        "config", "set", "discord", "botToken", "abc.def.ghi"}));
        assertEquals("/losttales config get discord botToken",
                ChatCommandText.describe("losttales", new String[] {
                        "config", "get", "discord", "botToken"}));
        // A binding keeps its channel and direction, never the addresses.
        String bind = ChatCommandText.describe("losttales", new String[] {
                "discord", "bind", "ooc", "BIDIRECTIONAL",
                "channel=123456789012345678",
                "webhook=https://discord.com/api/webhooks/1/secret"});
        assertEquals("/losttales discord bind ooc BIDIRECTIONAL channel=... webhook=...", bind);
        assertFalse(bind.contains("secret"));
        assertEquals("/say", ChatCommandText.describe("say", null));
        // Cut to a line.
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            long_.append('y');
        }
        String cut = ChatCommandText.describe("say", new String[] {long_.toString()});
        assertTrue(cut.length() <= ChatCommandText.MAX_LENGTH);
        assertTrue(cut.endsWith("..."));
    }

    /**
     * A fellowship message keeps the fellowship it names, as a private
     * message keeps whom it went to, and never its words; binding and
     * letting go say nothing and are kept whole.
     */
    @Test
    public void aFellowshipMessageKeepsItsFellowshipAndNotItsWords() {
        assertEquals("/fmsg \"Grey Company\" ...", ChatCommandText.describe("fmsg",
                new String[] {"\"Grey", "Company\"", "the", "vault", "code"}));
        assertEquals("/fmsg ...", ChatCommandText.describe("fmsg",
                new String[] {"the", "vault", "code"}));
        assertEquals("/fchat ...", ChatCommandText.describe("FChat",
                new String[] {"meet", "at", "dawn"}));
        assertEquals("/fmsg bind \"Grey Company\"", ChatCommandText.describe("fmsg",
                new String[] {"bind", "\"Grey", "Company\""}));
        assertEquals("/fmsg unbind", ChatCommandText.describe("fmsg",
                new String[] {"unbind"}));
        // Words after unbind are words, said to the bound fellowship.
        assertEquals("/fmsg ...", ChatCommandText.describe("fmsg",
                new String[] {"unbind", "the", "gate"}));
        // A bind with no fellowship in quotes is words the command refuses.
        assertEquals("/fmsg bind ...", ChatCommandText.describe("fmsg",
                new String[] {"bind", "the", "gate"}));
        assertEquals("/fmsg", ChatCommandText.describe("fmsg", new String[0]));
        String typed = ChatCommandText.describeTyped("/fchat \"Rangers\" the vault code is 4417");
        assertEquals("/fchat \"Rangers\" ...", typed);
        assertFalse(typed.contains("4417"));
    }

    /** A typed command reads as the console would write it, so its echo holds no secret. */
    @Test
    public void aTypedCommandIsDescribedAsTheConsoleWritesIt() {
        String typed = "/losttales  discord bind ooc BIDIRECTIONAL "
                + "webhook=https://discord.com/api/webhooks/1/secret";
        String shown = ChatCommandText.describeTyped(typed);
        assertEquals("/losttales discord bind ooc BIDIRECTIONAL webhook=...", shown);
        assertFalse(shown.contains("secret"));
        assertEquals("/losttales config set discord botToken ...",
                ChatCommandText.describeTyped("/losttales config set discord botToken a.b.c"));
        assertEquals("/time query daytime",
                ChatCommandText.describeTyped(" /time query daytime "));
        assertEquals("", ChatCommandText.describeTyped("/"));
        assertEquals("", ChatCommandText.describeTyped(null));
    }
}
