package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A player's line can ping on Discord only the members the server said it
 * names, who can see the channel it goes to, and never with a code the
 * player typed.
 */
public final class DiscordMentionsTest {
    private static final String ARWEN = "123456789012345678";
    private static final String ELROND = "223456789012345678";
    private static final char B = DiscordMentions.BREAK;

    private static ChatNamedPlayer member(String userId, String nickname) {
        return ChatNamedPlayer.account(
                LostTalesChatMessagePacket.discordSenderId(userId), nickname);
    }

    @Test
    public void everyCodeAPlayerTypesIsBroken() {
        assertEquals("hi <" + B + "@123> and <" + B + "@!123> and <" + B
                + "@&55> in <" + B + "#9>",
                DiscordMentions.defused("hi <@123> and <@!123> and <@&55> in <#9>"));
        assertEquals("@" + B + "everyone @" + B + "here @" + B + "Here",
                DiscordMentions.defused("@everyone @here @Here"));
        // Ordinary text, an address and a custom emoji stay as they are.
        assertEquals("mail@example.org <:elf:77> a < b", DiscordMentions
                .defused("mail@example.org <:elf:77> a < b"));
    }

    @Test
    public void aNamedMemberWhoCanSeeTheChannelIsPinged() {
        List<ChatNamedPlayer> named = Arrays.asList(member(ARWEN, "Arwen Undomiel"),
                member(ELROND, "Elrond"));
        Set<String> visible = new HashSet<String>(Collections.singletonList(ARWEN));
        DiscordMentions.Post post = DiscordMentions.rewrite(
                "@Arwen Undomiel meet @Elrond at @everyone's feast", named, visible);
        // Elrond cannot see this channel: plain text, no ping.
        assertEquals("<@" + ARWEN + "> meet @Elrond at @" + B
                + "everyone's feast", post.content);
        assertEquals(Collections.singletonList(ARWEN), post.pinged);
    }

    @Test
    public void aPlayerOrAMemberNobodyNamedIsNeverPinged() {
        UUID player = UUID.randomUUID();
        List<ChatNamedPlayer> named = Collections.singletonList(
                ChatNamedPlayer.account(player, "Nils"));
        Set<String> visible = new HashSet<String>(Arrays.asList(ARWEN, ELROND));
        DiscordMentions.Post post = DiscordMentions.rewrite(
                "@Nils and @Arwen", named, visible);
        assertEquals("@Nils and @Arwen", post.content);
        assertTrue(post.pinged.isEmpty());
    }

    @Test
    public void aPostPingsAtMostFiveMembers() {
        List<ChatNamedPlayer> named = new ArrayList<ChatNamedPlayer>();
        Set<String> visible = new HashSet<String>();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 7; index++) {
            String id = "30000000000000000" + index;
            named.add(member(id, "Member" + index));
            visible.add(id);
            text.append("@Member").append(index).append(' ');
        }
        DiscordMentions.Post post = DiscordMentions.rewrite(text.toString(),
                named, visible);
        assertEquals(DiscordMentions.MOST_PINGS, post.pinged.size());
        assertTrue(post.content.contains("@Member6"));
        assertFalse(post.content.contains("<@300000000000000006>"));
    }
}
