package com.ninuna.losttales.compat.discord;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The relay turns the server's own broadcast lines into notices by
 * their translation keys, and finds the account a line is about from
 * the click event vanilla puts on every announced player name. No
 * server runs here, so no head is ever resolved; that path is exercised
 * only in a launched server.
 */
public final class DiscordGameEventRelayTest {

    /** A player name as {@code EntityPlayer.func_145748_c_} builds it. */
    private static IChatComponent announcedName(String shown,
                                                String account) {
        ChatComponentText name = new ChatComponentText(shown);
        name.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, "/msg " + account + " "));
        return name;
    }

    @Test
    public void deathsBecomeDeathNoticesWithTheGamesOwnWords() {
        DiscordNotice notice = DiscordGameEventRelay.noticeFor(
                new ChatComponentTranslation("death.attack.mob",
                        announcedName("Aragorn", "Steve"),
                        new ChatComponentText("Mordor Orc")));
        assertEquals(DiscordNotice.Kind.PLAYER_DIED, notice.getKind());
        // Vanilla's own wording, rendered by the server.
        assertEquals("💀 Aragorn was slain by Mordor Orc", notice.getText());
        assertEquals("", notice.getIconUrl());
    }

    @Test
    public void achievementsBecomeAchievementNotices() {
        DiscordNotice vanilla = DiscordGameEventRelay.noticeFor(
                new ChatComponentTranslation("chat.type.achievement",
                        announcedName("Steve", "Steve"),
                        new ChatComponentText("[Taking Inventory]")));
        assertEquals(DiscordNotice.Kind.ACHIEVEMENT, vanilla.getKind());
        assertEquals("🏆 Steve has just earned the achievement "
                + "[Taking Inventory]", vanilla.getText());

        // LOTR's key is classified the same way; its wording is LOTR's
        // own and is not on this test classpath, so only the kind and
        // the presence of text are checked.
        DiscordNotice lotr = DiscordGameEventRelay.noticeFor(
                new ChatComponentTranslation("chat.lotr.achievement",
                        announcedName("Aragorn", "Steve"),
                        new ChatComponentText("Middle-earth"),
                        new ChatComponentText("[Orc Slayer]")));
        assertEquals(DiscordNotice.Kind.ACHIEVEMENT, lotr.getKind());
        assertTrue(lotr.getText().startsWith("🏆 "));
    }

    @Test
    public void everythingElseIsNoNotice() {
        assertNull(DiscordGameEventRelay.noticeFor(
                new ChatComponentTranslation("multiplayer.player.joined",
                        announcedName("Steve", "Steve"))));
        assertNull(DiscordGameEventRelay.noticeFor(
                new ChatComponentTranslation("chat.type.announcement",
                        "Server", "hello")));
        assertNull(DiscordGameEventRelay.noticeFor(
                new ChatComponentTranslation("commands.gamemode.success.self",
                        "Creative")));
        assertNull(DiscordGameEventRelay.noticeFor(
                new ChatComponentText("Steve fell from a high place")));
        assertNull(DiscordGameEventRelay.noticeFor(null));
    }

    @Test
    public void theSubjectIsTheAccountBehindTheShownName() {
        assertEquals("Steve", DiscordGameEventRelay.subjectAccountName(
                new ChatComponentTranslation("death.fell.accident.generic",
                        announcedName("Aragorn", "Steve"))));
        // A name a mod built by hand carries no account.
        assertEquals("", DiscordGameEventRelay.subjectAccountName(
                new ChatComponentTranslation("death.attack.mob",
                        new ChatComponentText("Aragorn"),
                        new ChatComponentText("Orc"))));
        assertEquals("", DiscordGameEventRelay.subjectAccountName(
                new ChatComponentTranslation("death.attack.mob", "Aragorn",
                        "Orc")));
        assertEquals("", DiscordGameEventRelay.subjectAccountName(
                new ChatComponentTranslation("death.attack.generic")));
        assertEquals("", DiscordGameEventRelay.subjectAccountName(
                new ChatComponentText("plain")));
        assertEquals("", DiscordGameEventRelay.subjectAccountName(null));
        // Only the whisper suggestion names an account.
        ChatComponentText linked = new ChatComponentText("Aragorn");
        linked.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.OPEN_URL, "https://example.invalid"));
        assertEquals("", DiscordGameEventRelay.subjectAccountName(
                new ChatComponentTranslation("death.attack.mob", linked,
                        "Orc")));
    }
}
