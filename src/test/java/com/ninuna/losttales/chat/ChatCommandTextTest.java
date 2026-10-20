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
                ChatCommandText.describe("gamemode", null, new String[] {"1", "Steve"}, null));
        assertEquals("/losttales chat mute Bob 15m spam",
                ChatCommandText.describe("losttales", null, new String[] {"chat", "mute", "Bob", "15m", "spam"}, null));
        // A private message keeps whom it went to, never what it said.
        assertEquals("/msg Bob ...",
                ChatCommandText.describe("msg", null, new String[] {"Bob", "the", "key"}, null));
        assertEquals("/tell Bob",
                ChatCommandText.describe("Tell", null, new String[] {"Bob"}, null));
        // A config change keeps the key, never the value.
        assertEquals("/losttales config set discord botToken ...",
                ChatCommandText.describe("losttales", null, new String[] {
                        "config", "set", "discord", "botToken", "abc.def.ghi"}, null));
        assertEquals("/losttales config get discord botToken",
                ChatCommandText.describe("losttales", null, new String[] {
                        "config", "get", "discord", "botToken"}, null));
        // Under the alias too: a token set with /losttales cfg never shows.
        String alias = ChatCommandText.describe("losttales", null, new String[] {
                "cfg", "set", "discord", "botToken", "abc.def.ghi"}, null);
        assertEquals("/losttales cfg set discord botToken ...", alias);
        assertFalse(alias.contains("abc"));
        // An unlink keeps its game channel, never the Discord channel's id.
        String unlink = ChatCommandText.describe("losttales", null, new String[] {
                "discord", "unlink", "ooc", "123456789012345678"}, null);
        assertEquals("/losttales discord unlink ooc ...", unlink);
        assertFalse(unlink.contains("1234"));
        assertEquals("/say", ChatCommandText.describe("say", null, null, null));
        // Cut to a line.
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            long_.append('y');
        }
        String cut = ChatCommandText.describe("say", null, new String[] {long_.toString()}, null);
        assertTrue(cut.length() <= ChatCommandText.MAX_LENGTH);
        assertTrue(cut.endsWith("..."));
    }

    /**
     * The game's private message is {@code /tell} with the aliases
     * {@code /w} and {@code /msg}; a command answering to any of them
     * keeps whom it went to, never what it said.
     */
    @Test
    public void aPrivateMessageIsKnownByEveryOneOfItsNames() {
        assertEquals("/tell Bob ...", ChatCommandText.describe("tell",
                java.util.Arrays.asList("w", "msg"),
                new String[] {"Bob", "the", "key"}, null));
        assertEquals("/whisperto Bob ...", ChatCommandText.describe("whisperto",
                java.util.Arrays.asList("W"),
                new String[] {"Bob", "the", "key"}, null));
        assertEquals("/w Bob ...",
                ChatCommandText.describeTyped("/w Bob the key", null));
        assertEquals("/tellraw Bob hello", ChatCommandText.describe("tellraw",
                null, new String[] {"Bob", "hello"}, null));
    }

    /** A typed command reads as the console would write it, so its echo holds no secret. */
    @Test
    public void aTypedCommandIsDescribedAsTheConsoleWritesIt() {
        String shown = ChatCommandText.describeTyped(
                "/losttales  cfg set discord botToken a.b.c", null);
        assertEquals("/losttales cfg set discord botToken ...", shown);
        assertFalse(shown.contains("a.b.c"));
        assertEquals("/losttales config set discord botToken ...",
                ChatCommandText.describeTyped("/losttales config set discord botToken a.b.c", null));
        assertEquals("/time query daytime",
                ChatCommandText.describeTyped(" /time query daytime ", null));
        assertEquals("", ChatCommandText.describeTyped("/", null));
        assertEquals("", ChatCommandText.describeTyped(null, null));
    }

    /**
     * Another mod's login, password and private-message commands keep only
     * their name, whichever of their names they were run by.
     */
    @Test
    public void anotherModsPasswordsAndPrivateWordsAreNeverRepeated() {
        assertEquals("/login ...", ChatCommandText.describe("login", null, new String[] {"hunter2"}, null));
        assertEquals("/register ...", ChatCommandText.describe("Register", null, new String[] {"pw", "pw"}, null));
        assertEquals("/authme ...", ChatCommandText.describe("authme",
                java.util.Arrays.asList("l", "log"), new String[] {"pw"}, null));
        assertEquals("/r ...", ChatCommandText.describe("r", null, new String[] {"see", "you", "there"}, null));
        assertEquals("/login", ChatCommandText.describe("login", null, null, null));
        assertEquals("/l ...", ChatCommandText.describeTyped("/l hunter2", null));
        assertEquals("/tp Steve Alex", ChatCommandText.describe("tp",
                java.util.Arrays.asList("teleport"),
                new String[] {"Steve", "Alex"}, null));
    }

    /**
     * A command the server lists as private keeps only its name, by any
     * of its names, in the Server Log and in its echo alike.
     */
    @Test
    public void aCommandTheServerListsKeepsOnlyItsName() {
        java.util.List<String> listed = java.util.Arrays.asList("vault", "bank");
        assertEquals("/vault ...", ChatCommandText.describe("vault", null,
                new String[] {"open", "1234"}, listed));
        assertEquals("/economy ...", ChatCommandText.describe("economy",
                java.util.Arrays.asList("bank"), new String[] {"pin", "1234"}, listed));
        assertEquals("/bank ...", ChatCommandText.describeTyped("/Bank pin 1234", listed));
        assertEquals("/vault open 1234", ChatCommandText.describe("vault", null,
                new String[] {"open", "1234"}, null));
    }

    /** The config's lines are read as names: cleaned, bounded, a bad one reported. */
    @Test
    public void theConfigsPrivateCommandsAreReadAsNames() {
        java.util.List<String> warnings = new java.util.ArrayList<String>();
        assertEquals(java.util.Arrays.asList("vault", "bank"),
                ChatCommandText.parseNames(new String[] {
                        " /Vault ", "bank", "vault", "login", "", "two words",
                        "§cred", "a23456789012345678901234567890123"}, warnings));
        assertEquals(3, warnings.size());
        String[] many = new String[ChatCommandText.MAX_LISTED_NAMES + 2];
        for (int index = 0; index < many.length; index++) {
            many[index] = "command" + index;
        }
        warnings.clear();
        assertEquals(ChatCommandText.MAX_LISTED_NAMES,
                ChatCommandText.parseNames(many, warnings).size());
        assertEquals(2, warnings.size());
    }

    /** Every name the config sub-command answers to is masked: none is left out. */
    @Test
    public void everyNameOfTheConfigCommandIsMasked() {
        assertEquals(com.ninuna.losttales.command.ELostTalesSubCommand.CONFIG.getNames(),
                ChatCommandText.CONFIG_NAMES);
    }
}
