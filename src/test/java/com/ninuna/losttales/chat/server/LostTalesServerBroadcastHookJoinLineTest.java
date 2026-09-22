package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatMessageIds;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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

    /**
     * Who comes and goes is an account: the game writes the character's
     * name into a join or a leave, and the line goes out naming the
     * account its name's click suggests whispering to, in the line's own
     * style, the click kept.
     */
    @Test
    public void aJoinOrALeaveGoesOutNamingTheAccount() {
        ChatComponentTranslation joined = new ChatComponentTranslation(
                "multiplayer.player.joined", characterName("Aragorn", "Steve"));
        joined.getChatStyle().setColor(EnumChatFormatting.YELLOW);
        IChatComponent out = LostTalesServerBroadcastHook.namingTheAccount(joined);
        assertEquals("multiplayer.player.joined",
                ((ChatComponentTranslation)out).getKey());
        IChatComponent name = (IChatComponent)
                ((ChatComponentTranslation)out).getFormatArgs()[0];
        assertEquals("Steve", name.getUnformattedText());
        assertEquals("/msg Steve ",
                name.getChatStyle().getChatClickEvent().getValue());
        assertEquals(EnumChatFormatting.YELLOW, out.getChatStyle().getColor());
        assertEquals(EnumChatFormatting.YELLOW, name.getChatStyle().getColor());

        IChatComponent left = LostTalesServerBroadcastHook.namingTheAccount(
                new ChatComponentTranslation("multiplayer.player.left",
                        characterName("Aragorn", "Steve")));
        assertEquals("Steve", ((IChatComponent)((ChatComponentTranslation)left)
                .getFormatArgs()[0]).getUnformattedText());

        // A renamed account keeps the name it had beside it.
        IChatComponent renamed = LostTalesServerBroadcastHook.namingTheAccount(
                new ChatComponentTranslation("multiplayer.player.joined.renamed",
                        characterName("Aragorn", "Steve"), "Stephen"));
        assertEquals("Stephen", ((ChatComponentTranslation)renamed).getFormatArgs()[1]);
    }

    /**
     * Anything but a join or a leave goes out as it came, and so does a
     * join whose name carries no whisper click: there is no account to
     * name it by.
     */
    @Test
    public void otherLinesGoOutAsTheyCame() {
        ChatComponentTranslation death = new ChatComponentTranslation(
                "death.attack.mob", characterName("Aragorn", "Steve"), "Zombie");
        assertSame(death, LostTalesServerBroadcastHook.namingTheAccount(death));
        ChatComponentTranslation clickless = new ChatComponentTranslation(
                "multiplayer.player.joined", new ChatComponentText("Aragorn"));
        assertSame(clickless, LostTalesServerBroadcastHook.namingTheAccount(clickless));
        ChatComponentText plain = new ChatComponentText("Steve joined the game");
        assertSame(plain, LostTalesServerBroadcastHook.namingTheAccount(plain));
    }

    /** The game's display name of a player playing {@code character}. */
    private static ChatComponentText characterName(String character, String account) {
        ChatComponentText name = new ChatComponentText(character);
        name.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, "/msg " + account + " "));
        return name;
    }
}
