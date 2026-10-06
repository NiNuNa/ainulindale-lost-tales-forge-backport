package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Only an in-character line naming a player online who is still taking
 * their character's name waits; everything else goes out at once.
 */
public final class ChatLinesAwaitingNamesTest {

    @After
    public void clear() {
        ChatLinesAwaitingNames.clear();
    }

    @Test
    public void outOfCharacterLinesNeverWait() {
        assertFalse(ChatLinesAwaitingNames.hold(achievement(), ChatChannel.OOC));
        assertFalse(ChatLinesAwaitingNames.hold(achievement(), null));
        assertFalse(ChatLinesAwaitingNames.hold(null, ChatChannel.GLOBAL));
    }

    @Test
    public void aLineNamingNobodyOnlineGoesOut() {
        // No server runs here, so nobody it names is online.
        assertFalse(ChatLinesAwaitingNames.hold(achievement(), ChatChannel.GLOBAL));
    }

    private static ChatComponentTranslation achievement() {
        ChatComponentText name = new ChatComponentText("Steve");
        name.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, "/msg Steve "));
        return new ChatComponentTranslation("chat.lotr.achievement", name,
                new ChatComponentText("First Steps"));
    }
}
