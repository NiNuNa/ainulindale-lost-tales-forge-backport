package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.ninuna.losttales.chat.ChatChannel.CONSOLE;

public final class ChatSystemLineClassifierTest {

    @Test
    public void serverVisibleLinesGoToGlobal() {
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.achievement",
                        "Steve", new ChatComponentText("Taking Inventory"))));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.achievement.taken",
                        "Steve", "x")));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.lotr.achievement",
                        "Steve", "Middle-earth",
                        new ChatComponentText("[First Steps]"))));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("death.attack.mob", "Steve",
                        "Zombie")));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("multiplayer.player.joined",
                        "Steve")));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("multiplayer.player.left",
                        "Steve")));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.announcement",
                        "Server", "hello")));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.emote",
                        "Steve", "waves")));
        assertEquals(ChatChannel.ALL, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.type.text",
                        "Steve", "vanilla chat")));
    }

    @Test
    public void privateAndUnknownLinesGoToTheConsole() {
        // Command output, whispers, LOTR notices, other mods, plain text.
        assertEquals(CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("commands.gamemode.success.self",
                        "Creative")));
        assertEquals(CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation(
                        "commands.message.display.incoming", "Steve", "psst")));
        assertEquals(CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("lotr.fastTravel.wait", "5")));
        assertEquals(CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("chat.losttales.waystone.saved")));
        assertEquals(CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentText("Steve has made the achievement")));
        assertEquals(CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentTranslation("")));
        assertNull(ChatSystemLineClassifier.classify(null));
        // The key decides, never the rendered text.
        assertEquals(CONSOLE, ChatSystemLineClassifier.classify(
                new ChatComponentText("death.attack.mob")));
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
}
