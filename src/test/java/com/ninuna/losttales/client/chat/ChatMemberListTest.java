package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A window's member list: a heading over each group of those here, the
 * absent under their own heading at the end — counting those an answer
 * too long to list leaves out — every row a fixed height, an absent
 * member's head reading Offline and the member drawn fainter until the
 * pointer lights them; an NPC in its conversation's list beside the
 * player; the list stands where the words keep their room; and it asks
 * the server again on a clock of its own.
 */
public final class ChatMemberListTest {

    private static LostTalesChatMembersPacket.Member member(String name,
                                                           String group,
                                                           boolean online) {
        return new LostTalesChatMembersPacket.Member(UUID.randomUUID(),
                name.toLowerCase(), UUID.randomUUID(), name, 0xFFFFFF,
                "human/male/1", "", 0xFFFFFF, group, group, 0, online);
    }

    @Test
    public void eachGroupStandsUnderItsOwnHeading() {
        List<ChatMemberList.Row> rows = ChatMemberList.layOut(Arrays.asList(
                member("Aldric", "gondor", true),
                member("Beren", "gondor", true),
                member("Gorbag", "mordor", true),
                member("Sam", "", false)), 0);
        assertEquals(7, rows.size());
        assertTrue(rows.get(0).heading != null);
        assertNull(rows.get(0).member);
        assertEquals("Aldric", rows.get(1).member.getName());
        assertEquals("Beren", rows.get(2).member.getName());
        assertTrue(rows.get(3).heading != null);
        assertEquals("Gorbag", rows.get(4).member.getName());
        assertTrue(rows.get(5).heading != null);
        assertEquals("Sam", rows.get(6).member.getName());
        assertEquals(ChatMemberList.PAD, rows.get(0).top);
        assertEquals(ChatMemberList.PAD + ChatMemberList.HEADER_HEIGHT,
                rows.get(1).top);
        assertEquals(rows.get(2).top + ChatMemberList.ROW_HEIGHT
                + ChatMemberList.GROUP_GAP, rows.get(3).top);
        assertEquals(rows.get(6).top + ChatMemberList.ROW_HEIGHT
                + ChatMemberList.PAD, ChatMemberList.heightOf(rows));
        assertTrue(ChatMemberList.layOut(null, 0).isEmpty());
    }

    @Test
    public void anAnswerTooLongToListCountsTheRestOnALineOfItsOwn() {
        List<ChatMemberList.Row> rows = ChatMemberList.layOut(Arrays.asList(
                member("Aldric", "gondor", true),
                member("Sam", "", false)), 5);
        // Gondor, Aldric, Offline, Sam, and the line counting the rest.
        assertEquals(5, rows.size());
        assertNull(rows.get(4).member);
        assertEquals(rows.get(3).top + ChatMemberList.ROW_HEIGHT,
                rows.get(4).top);
        // Nobody absent listed at all: the Offline heading still stands
        // over the line that counts them.
        rows = ChatMemberList.layOut(Arrays.asList(
                member("Aldric", "gondor", true)), 3);
        assertEquals(4, rows.size());
        assertNull(rows.get(2).member);
        assertNull(rows.get(3).member);
        assertEquals(rows.get(1).top + ChatMemberList.ROW_HEIGHT
                + ChatMemberList.GROUP_GAP, rows.get(2).top);
    }

    @Test
    public void anAbsentMembersHeadReadsOffline() {
        LostTalesChatMembersPacket.Member here = member("Aldric", "gondor", true);
        LostTalesChatMembersPacket.Member away = member("Sam", "", false);
        assertEquals(here.getCharacterId(),
                ChatMemberList.headOf(here).characterId);
        assertNull(ChatMemberList.headOf(away).characterId);
        assertEquals(ChatPresence.OFFLINE,
                ChatPresenceMark.presenceOf(ChatMemberList.headOf(away)));
    }

    @Test
    public void theListStandsWhereTheWordsKeepTheirRoom() {
        assertTrue(ChatMemberList.fits(ChatMemberList.WIDTH
                + ChatMemberList.MIN_MESSAGE_WIDTH));
        assertFalse(ChatMemberList.fits(ChatMemberList.WIDTH
                + ChatMemberList.MIN_MESSAGE_WIDTH - 1));
    }

    @Test
    public void theAbsentAreFainterUntilThePointerLightsThem() {
        LostTalesChatMembersPacket.Member here = member("Aldric", "gondor", true);
        LostTalesChatMembersPacket.Member away = member("Sam", "", false);
        assertEquals(200, ChatMemberList.rowAlpha(here, 200, 0.0F));
        assertEquals(Math.round(200 * ChatMemberList.OFFLINE_OPACITY),
                ChatMemberList.rowAlpha(away, 200, 0.0F));
        assertEquals(200, ChatMemberList.rowAlpha(away, 200, 1.0F));
    }

    @Test
    public void anNpcStandsInItsConversationsListBesideThePlayer() {
        ChatTab conversation = ChatTab.npc("Barliman");
        UUID npcId = UUID.randomUUID();
        try {
            ChatChannelIcons.rememberNpc(conversation, npcId, "lotr:barliman");
            ChatChannelIcons.rememberNpcFaction(npcId, "Gondor");
            LostTalesChatMembersPacket.Member me = member("Aldric", "gondor",
                    true);
            List<LostTalesChatMembersPacket.Member> members =
                    ChatMemberList.membersOf(conversation, Arrays.asList(me));
            assertEquals(2, members.size());
            assertEquals("Aldric", members.get(0).getName());
            LostTalesChatMembersPacket.Member npc = members.get(1);
            assertTrue(npc.isNpc());
            assertEquals(npcId, npc.getPlayerId());
            assertEquals("the player's group of the same name", "gondor",
                    npc.getGroupKey());
            assertTrue(ChatMemberList.headOf(npc).npcIdentity);
            // Anywhere else, the answer is the list.
            List<LostTalesChatMembersPacket.Member> answered = Arrays.asList(me);
            assertTrue(answered == ChatMemberList.membersOf(
                    ChatTab.of(ChatChannel.ALL), answered));
        } finally {
            ChatChannelIcons.forgetPortraits();
        }
    }

    @Test
    public void theListAsksAgainOnItsOwnClock() {
        long now = 100000L;
        assertTrue("never asked", ClientChatMembers.isDue(now, null, null, true));
        assertFalse("asked a moment ago", ClientChatMembers.isDue(now,
                Long.valueOf(now - 500L), null, true));
        assertTrue("asked, never answered", ClientChatMembers.isDue(now,
                Long.valueOf(now - 1500L), null, true));
        assertFalse("answered lately", ClientChatMembers.isDue(now,
                Long.valueOf(now - 2000L), Long.valueOf(now - 1900L), true));
        assertTrue("answered long ago", ClientChatMembers.isDue(now,
                Long.valueOf(now - 5000L), Long.valueOf(now
                        - ClientChatMembers.REFRESH_MILLIS), true));
        assertTrue("the chat is read as someone else now",
                ClientChatMembers.isDue(now, Long.valueOf(now - 2000L),
                        Long.valueOf(now - 1900L), false));
    }
}
