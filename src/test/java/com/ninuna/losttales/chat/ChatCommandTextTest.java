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
        // Under the alias too: a token set with /losttales cfg never shows.
        String alias = ChatCommandText.describe("losttales", new String[] {
                "cfg", "set", "discord", "botToken", "abc.def.ghi"});
        assertEquals("/losttales cfg set discord botToken ...", alias);
        assertFalse(alias.contains("abc"));
        // An unlink keeps its game channel, never the Discord channel's id.
        String unlink = ChatCommandText.describe("losttales", new String[] {
                "discord", "unlink", "ooc", "123456789012345678"});
        assertEquals("/losttales discord unlink ooc ...", unlink);
        assertFalse(unlink.contains("1234"));
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

    /** A typed command reads as the console would write it, so its echo holds no secret. */
    @Test
    public void aTypedCommandIsDescribedAsTheConsoleWritesIt() {
        String shown = ChatCommandText.describeTyped(
                "/losttales  cfg set discord botToken a.b.c");
        assertEquals("/losttales cfg set discord botToken ...", shown);
        assertFalse(shown.contains("a.b.c"));
        assertEquals("/losttales config set discord botToken ...",
                ChatCommandText.describeTyped("/losttales config set discord botToken a.b.c"));
        assertEquals("/time query daytime",
                ChatCommandText.describeTyped(" /time query daytime "));
        assertEquals("", ChatCommandText.describeTyped("/"));
        assertEquals("", ChatCommandText.describeTyped(null));
    }

    /** Every name the config sub-command answers to is masked: none is left out. */
    @Test
    public void everyNameOfTheConfigCommandIsMasked() {
        assertEquals(com.ninuna.losttales.command.ELostTalesSubCommand.CONFIG.getNames(),
                ChatCommandText.CONFIG_NAMES);
    }
}
