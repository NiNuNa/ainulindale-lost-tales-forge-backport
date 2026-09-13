package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatMessageIds;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A join line is kept for the login replay of exactly the player it
 * announces, found by the account the game's own display name suggests
 * whispering to, and taken once.
 */
public final class LostTalesServerBroadcastHookJoinLineTest {

    @After
    public void clear() {
        LostTalesServerBroadcastHook.clear();
    }

    @Test
    public void aJoinLineNamesTheAccountItsNameSuggestsWhispering() {
        // The game's display name: a team may colour or prefix it, the
        // click still names the account.
        ChatComponentText name = new ChatComponentText("[Gondor] Elf");
        name.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, "/msg Elf "));
        assertEquals("Elf", LostTalesServerBroadcastHook.joinerAccount(
                new ChatComponentTranslation("multiplayer.player.joined", name)));
        // Without the click, the name's own words stand in.
        assertEquals("Plain", LostTalesServerBroadcastHook.joinerAccount(
                new ChatComponentTranslation("multiplayer.player.joined",
                        new ChatComponentText(" Plain "))));
        assertEquals("Renamed", LostTalesServerBroadcastHook.joinerAccount(
                new ChatComponentTranslation(
                        "multiplayer.player.joined.renamed", "Renamed", "Old")));
        assertNull(LostTalesServerBroadcastHook.joinerAccount(
                new ChatComponentText("Elf joined the game")));
        assertNull(LostTalesServerBroadcastHook.joinerAccount(
                new ChatComponentTranslation("multiplayer.player.joined")));
    }

    @Test
    public void aLoginReplayTakesItsOwnJoinLineOnce() {
        LostTalesServerBroadcastHook.noteJoinLine("Elf", 42L);
        LostTalesServerBroadcastHook.noteJoinLine("Dwarf", 43L);
        assertEquals(42L, LostTalesServerBroadcastHook.takeJoinLine("elf"));
        assertEquals(ChatMessageIds.NONE,
                LostTalesServerBroadcastHook.takeJoinLine("Elf"));
        assertEquals(ChatMessageIds.NONE,
                LostTalesServerBroadcastHook.takeJoinLine("Hobbit"));
        assertEquals(ChatMessageIds.NONE,
                LostTalesServerBroadcastHook.takeJoinLine(null));
        // A later join replaces the one that was still waiting.
        LostTalesServerBroadcastHook.noteJoinLine("Dwarf", 44L);
        assertEquals(44L, LostTalesServerBroadcastHook.takeJoinLine("Dwarf"));
    }
}
