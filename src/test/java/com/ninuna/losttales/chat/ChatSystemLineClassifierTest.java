package com.ninuna.losttales.chat;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.ninuna.losttales.chat.ChatChannel.CLIENT_CONSOLE;

public final class ChatSystemLineClassifierTest {

    /**
     * What happens to a character is roleplay news: an achievement or a
     * death goes to Global, naming the character.
     */
    @Test
    public void achievementsAndDeathsGoToGlobal() {
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.achievement",
                        "Steve", new ChatComponentText("Taking Inventory"))));
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.achievement.taken",
                        "Steve", "x")));
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.lotr.achievement",
                        "Steve", "Middle-earth",
                        new ChatComponentText("[First Steps]"))));
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("death.attack.mob", "Steve",
                        "Zombie")));
    }

    /**
     * An account coming or going is out-of-character news: a join or a
     * leave goes to OOC, naming the account.
     */
    @Test
    public void joinsAndLeavesGoToOoc() {
        assertEquals(ChatChannel.OOC, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("multiplayer.player.joined",
                        "Steve")));
        assertEquals(ChatChannel.OOC, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("multiplayer.player.joined.renamed",
                        "Steve", "Bob")));
        assertEquals(ChatChannel.OOC, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("multiplayer.player.left",
                        "Steve")));
    }

    @Test
    public void eachKindNamesItsChannel() {
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.channelOf(
                ChatSystemLineClassifier.Kind.ACHIEVEMENT));
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.channelOf(
                ChatSystemLineClassifier.Kind.DEATH));
        assertEquals(ChatChannel.OOC, ChatSystemLineClassifier.channelOf(
                ChatSystemLineClassifier.Kind.JOIN));
        assertEquals(ChatChannel.OOC, ChatSystemLineClassifier.channelOf(
                ChatSystemLineClassifier.Kind.LEAVE));
        assertNull(ChatSystemLineClassifier.channelOf(
                ChatSystemLineClassifier.Kind.OTHER));
        assertNull(ChatSystemLineClassifier.channelOf(null));
    }

    @Test
    public void otherSharedLinesGoToGlobal() {
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.announcement",
                        "Server", "hello")));
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.emote",
                        "Steve", "waves")));
        assertEquals(ChatChannel.GLOBAL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.text",
                        "Steve", "vanilla chat")));
    }

    @Test
    public void privateAndUnknownLinesGoToTheConsole() {
        // Command output, whispers, LOTR notices, other mods, plain text.
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("commands.gamemode.success.self",
                        "Creative")));
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation(
                        "commands.message.display.incoming", "Steve", "psst")));
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("lotr.fastTravel.wait", "5")));
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.losttales.waystone.saved")));
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentText("Steve has made the achievement")));
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("")));
        assertNull(ChatSystemLineClassifier.classify(null));
        // The key decides, never the rendered text.
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentText("death.attack.mob")));
    }

    @Test
    public void sharedLinesAreToldApartByKind() {
        assertEquals(ChatSystemLineClassifier.Kind.ACHIEVEMENT,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "chat.type.achievement", "Steve",
                        new ChatComponentText("Taking Inventory"))));
        assertEquals(ChatSystemLineClassifier.Kind.ACHIEVEMENT,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "chat.lotr.achievement", "Steve", "Middle-earth",
                        new ChatComponentText("[First Steps]"))));
        assertEquals(ChatSystemLineClassifier.Kind.DEATH,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "death.attack.mob", "Steve", "Zombie")));
        assertEquals(ChatSystemLineClassifier.Kind.DEATH,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "death.fell.accident.generic", "Steve")));
        assertEquals(ChatSystemLineClassifier.Kind.JOIN,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "multiplayer.player.joined", "Steve")));
        assertEquals(ChatSystemLineClassifier.Kind.JOIN,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "multiplayer.player.joined.renamed", "Steve", "Bob")));
        assertEquals(ChatSystemLineClassifier.Kind.LEAVE,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "multiplayer.player.left", "Steve")));
        // Shared but of no named kind, private, plain, or nothing.
        assertEquals(ChatSystemLineClassifier.Kind.OTHER,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "chat.type.announcement", "Server", "hello")));
        assertEquals(ChatSystemLineClassifier.Kind.OTHER,
                ChatSystemLineClassifier.kindOf(new ChatComponentTranslation(
                        "commands.gamemode.success.self", "Creative")));
        assertEquals(ChatSystemLineClassifier.Kind.OTHER,
                ChatSystemLineClassifier.kindOf(
                        new ChatComponentText("death.attack.mob")));
        assertEquals(ChatSystemLineClassifier.Kind.OTHER,
                ChatSystemLineClassifier.kindOf(null));
    }

    @Test
    public void achievementMentionsAreSilentButOthersSound() {
        // Announcements name their player without addressing them: the
        // mention highlights, the cue stays quiet.
        assertTrue(ChatSystemLineClassifier.isMentionCueSilent(
                new ChatComponentTranslation("chat.type.achievement",
                        "Steve", new ChatComponentText("Taking Inventory"))));
        assertTrue(ChatSystemLineClassifier.isMentionCueSilent(
                new ChatComponentTranslation("chat.type.achievement.taken",
                        "Steve", "x")));
        assertTrue(ChatSystemLineClassifier.isMentionCueSilent(
                new ChatComponentTranslation("chat.lotr.achievement",
                        "Steve", "Middle-earth",
                        new ChatComponentText("[First Steps]"))));
        // Everything else keeps the audible cue.
        assertFalse(ChatSystemLineClassifier.isMentionCueSilent(
                new ChatComponentTranslation("death.attack.mob", "Steve",
                        "Zombie")));
        assertFalse(ChatSystemLineClassifier.isMentionCueSilent(
                new ChatComponentTranslation("multiplayer.player.joined",
                        "Steve")));
        assertFalse(ChatSystemLineClassifier.isMentionCueSilent(
                new ChatComponentText("plain text")));
        assertFalse(ChatSystemLineClassifier.isMentionCueSilent(null));
    }

    /**
     * Vanilla's notice to operators of somebody's command is a console
     * line about who ran it, and its mention of a player stays quiet.
     */
    @Test
    public void anOperatorsNoticeIsAConsoleLineAboutWhoRanTheCommand() {
        ChatComponentTranslation opped = new ChatComponentTranslation(
                "chat.type.admin", "Nils",
                new ChatComponentTranslation("commands.op.success", "Steve"));
        assertEquals(CLIENT_CONSOLE, ChatSystemLineClassifier.classify(opped));
        assertTrue(ChatSystemLineClassifier.isAdminNotice(opped));
        assertEquals("Nils", ChatSystemLineClassifier.adminNoticeActor(opped));
        assertEquals("Server", ChatSystemLineClassifier.adminNoticeActor(
                new ChatComponentTranslation("chat.type.admin",
                        new ChatComponentText(" Server "), "x")));
        assertTrue(ChatSystemLineClassifier.isMentionCueSilent(opped));
        assertEquals("", ChatSystemLineClassifier.adminNoticeActor(
                new ChatComponentTranslation("chat.type.admin")));
        assertFalse(ChatSystemLineClassifier.isAdminNotice(
                new ChatComponentText("[Nils: Opped Steve]")));
        assertEquals("", ChatSystemLineClassifier.adminNoticeActor(
                new ChatComponentTranslation("chat.type.announcement",
                        "Nils", "hello")));
    }
}
