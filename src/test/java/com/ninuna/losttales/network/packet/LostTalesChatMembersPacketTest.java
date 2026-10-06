package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The member list's two packets: the request names a channel this build
 * has, the client's key for the conversation, a whisper's other party
 * and the fingerprint of the answer the client holds; the answer carries
 * the key back and each member whole, or is refused whole — or is only the
 * word that the answer held still stands. An NPC the client lists itself
 * never goes on the wire.
 */
public final class LostTalesChatMembersPacketTest {

    private static LostTalesChatMembersPacket.Member account(String name,
                                                            boolean online) {
        return new LostTalesChatMembersPacket.Member(UUID.randomUUID(), name,
                null, name, 0, "", "", 0, "", "", 0, online);
    }

    /** Each Discord server's members stand after all of the game's groups, the plain one too. */
    @Test
    public void discordServersStandAfterTheGamesGroups() {
        LostTalesChatMembersPacket.Member discord = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "Sam", null, "Sam", 0, "", "", 0,
                LostTalesChatMembersPacket.DISCORD_GROUP_PREFIX + "100", "Arda", 0, true);
        LostTalesChatMembersPacket.Member moderator = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "Mod", null, "Mod", 0, "", "", 0, "moderator",
                "Moderator", 2, true);
        LostTalesChatMembersPacket.Member plain = account("Alex", true);
        LostTalesChatMembersPacket.Member absent = account("Zed", false);
        List<LostTalesChatMembersPacket.Member> members =
                new ArrayList<LostTalesChatMembersPacket.Member>(
                        Arrays.asList(absent, discord, plain, moderator));
        Collections.sort(members, LostTalesChatMembersPacket.ORDER);
        assertEquals(Arrays.asList(moderator, plain, discord, absent), members);
        assertTrue(LostTalesChatMembersPacket.isDiscordGroup(discord.getGroupKey()));
        assertFalse(LostTalesChatMembersPacket.isDiscordGroup("moderator"));
    }

    @Test
    public void membersRoundTripWhole() {
        UUID aldric = UUID.randomUUID();
        UUID steve = UUID.randomUUID();
        LostTalesChatMembersPacket sent = new LostTalesChatMembersPacket(
                ChatChannel.GLOBAL, "global", Arrays.asList(
                        new LostTalesChatMembersPacket.Member(steve, "Steve",
                                aldric, "Aldric", 0x4A90D9, "human/male/2",
                                "lotr.title.farmer", 0x4A90D9, "lotr:gondor",
                                "", 0, true),
                        new LostTalesChatMembersPacket.Member(UUID.randomUUID(),
                                "Alex", null, "Alex", 0xFFFFFF, "ignored", "",
                                0xFFFFFF, "", "", 0, false)), 7);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesChatMembersPacket read = new LostTalesChatMembersPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertFalse(read.isUnchanged());
        assertEquals(ChatChannel.GLOBAL, read.getChannel());
        assertEquals("global", read.getConversationKey());
        assertEquals(sent.getFingerprint(), read.getFingerprint());
        assertEquals(7, read.getUnlisted());
        assertEquals(2, read.getMembers().size());
        LostTalesChatMembersPacket.Member first = read.getMembers().get(0);
        assertEquals(steve, first.getPlayerId());
        assertEquals(aldric, first.getCharacterId());
        assertEquals("Aldric", first.getName());
        assertEquals("lotr.title.farmer", first.getTitle());
        assertEquals("lotr:gondor", first.getGroupKey());
        assertEquals("", first.getGroupName());
        assertTrue(first.isOnline());
        LostTalesChatMembersPacket.Member second = read.getMembers().get(1);
        assertNull(second.getCharacterId());
        assertEquals("an account wears its own skin", "", second.getSkinId());
        assertFalse(second.isOnline());
    }

    @Test
    public void aTruncatedOrOverlongAnswerIsRefusedWhole() {
        LostTalesChatMembersPacket sent = new LostTalesChatMembersPacket(
                ChatChannel.OOC, "ooc", Arrays.asList(account("Steve", true)), 0);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        ByteBuf truncated = buffer.copy(0, buffer.readableBytes() - 3);
        LostTalesChatMembersPacket read = new LostTalesChatMembersPacket();
        read.fromBytes(truncated);
        assertTrue(read.isMalformed());
        assertTrue(read.getMembers().isEmpty());

        List<LostTalesChatMembersPacket.Member> many =
                new ArrayList<LostTalesChatMembersPacket.Member>();
        for (int index = 0; index < LostTalesChatMembersPacket.MAX_MEMBERS + 5;
             index++) {
            many.add(account("p" + index, true));
        }
        assertEquals(LostTalesChatMembersPacket.MAX_MEMBERS,
                new LostTalesChatMembersPacket(ChatChannel.OOC, "ooc", many, 0)
                        .getMembers().size());
    }

    /**
     * A group travels by its key alone, for each game to name in its own
     * language: a member whose faction or role group carries the server's
     * words is never sent, and an answer carrying one is refused whole.
     * Only a Discord server's group carries the name Discord gives it.
     */
    @Test
    public void aGroupTravelsByItsKeyAloneButADiscordServersByItsName() {
        LostTalesChatMembersPacket.Member worded = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "Steve", null, "Steve", 0, "", "", 0,
                "lotr:gondor", "Gondor", 0, true);
        LostTalesChatMembersPacket.Member discord = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "Sam", null, "Sam", 0, "", "", 0,
                LostTalesChatMembersPacket.DISCORD_GROUP_PREFIX + "100", "Arda", 0, true);
        LostTalesChatMembersPacket sent = new LostTalesChatMembersPacket(
                ChatChannel.GLOBAL, "global", Arrays.asList(worded, discord), 0);
        assertEquals(Arrays.asList(discord), sent.getMembers());
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesChatMembersPacket read = new LostTalesChatMembersPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertEquals("Arda", read.getMembers().get(0).getGroupName());

        assertTrue(decodeOneMember("operator", "Operator").isMalformed());
        assertTrue(decodeOneMember("lotr:gondor", "Gondor").isMalformed());
        assertTrue(decodeOneMember(LostTalesChatMembersPacket.SERVER_GROUP,
                "Server").isMalformed());
        assertFalse(decodeOneMember("lotr:gondor", "").isMalformed());
        assertFalse(decodeOneMember(LostTalesChatMembersPacket.DISCORD_GROUP_PREFIX
                + "7", "Arda").isMalformed());
    }

    /**
     * Factions stand in the order of the names a game gives them, Unaligned
     * last; the server, which names no group, stands them by their keys.
     */
    @Test
    public void factionsStandInTheOrderOfTheNamesTheyAreGiven() {
        LostTalesChatMembersPacket.Member gondor = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "Aldric", null, "Aldric", 0, "", "", 0,
                "lotr:gondor", "", 0, true);
        LostTalesChatMembersPacket.Member mordor = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "Gorbag", null, "Gorbag", 0, "", "", 0,
                "lotr:mordor", "", 0, true);
        LostTalesChatMembersPacket.Member unaligned = new LostTalesChatMembersPacket.Member(
                UUID.randomUUID(), "Bob", null, "Bob", 0, "", "", 0,
                "lotr:unaligned", "", 0, true);
        List<LostTalesChatMembersPacket.Member> members =
                new ArrayList<LostTalesChatMembersPacket.Member>(
                        Arrays.asList(unaligned, gondor, mordor));
        Collections.sort(members, LostTalesChatMembersPacket.ORDER);
        assertEquals(Arrays.asList(gondor, mordor, unaligned), members);
        Collections.sort(members, LostTalesChatMembersPacket.order(
                new LostTalesChatMembersPacket.GroupNames() {
                    @Override
                    public String of(LostTalesChatMembersPacket.Member member) {
                        return "lotr:gondor".equals(member.getGroupKey())
                                ? "Zirakzigil" : "Arnor";
                    }
                }));
        assertEquals(Arrays.asList(mordor, gondor, unaligned), members);
    }

    /** An answer of one member here, in the group {@code groupKey} named {@code groupName}. */
    private static LostTalesChatMembersPacket decodeOneMember(String groupKey,
                                                             String groupName) {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(buffer, ChatChannel.GLOBAL.getId(), 64);
        LostTalesPacketCodec.writeUtf8String(buffer, "global", 256);
        buffer.writeLong(77L);
        buffer.writeBoolean(false);
        buffer.writeInt(0);
        buffer.writeShort(1);
        LostTalesPacketCodec.writeUuid(buffer, UUID.randomUUID());
        LostTalesPacketCodec.writeUtf8String(buffer, "Steve", 64);
        LostTalesPacketCodec.writeNullableUuid(buffer, null);
        LostTalesPacketCodec.writeUtf8String(buffer, "Steve", 256);
        buffer.writeInt(0);
        LostTalesPacketCodec.writeUtf8String(buffer, "", 128);
        LostTalesPacketCodec.writeUtf8String(buffer, "", 256);
        buffer.writeInt(0);
        LostTalesPacketCodec.writeUtf8String(buffer, groupKey, 128);
        LostTalesPacketCodec.writeUtf8String(buffer, groupName, 128);
        buffer.writeShort(0);
        buffer.writeBoolean(true);
        LostTalesChatMembersPacket read = new LostTalesChatMembersPacket();
        read.fromBytes(buffer);
        return read;
    }

    @Test
    public void theFingerprintFollowsWhatTheAnswerSays() {
        UUID steve = UUID.randomUUID();
        LostTalesChatMembersPacket.Member here = new LostTalesChatMembersPacket
                .Member(steve, "Steve", null, "Steve", 0, "", "", 0, "", "", 0, true);
        LostTalesChatMembersPacket.Member gone = new LostTalesChatMembersPacket
                .Member(steve, "Steve", null, "Steve", 0, "", "", 0, "", "", 0, false);
        long first = new LostTalesChatMembersPacket(ChatChannel.OOC, "ooc",
                Arrays.asList(here), 0).getFingerprint();
        assertEquals(first, new LostTalesChatMembersPacket(ChatChannel.OOC,
                "ooc", Arrays.asList(here), 0).getFingerprint());
        assertTrue(first != new LostTalesChatMembersPacket(ChatChannel.OOC,
                "ooc", Arrays.asList(gone), 0).getFingerprint());
        assertTrue(first != new LostTalesChatMembersPacket(ChatChannel.OOC,
                "ooc", Arrays.asList(here), 1).getFingerprint());
        assertTrue(new LostTalesChatMembersPacket(ChatChannel.OOC, "ooc",
                Collections.<LostTalesChatMembersPacket.Member>emptyList(), 0)
                .getFingerprint() != 0L);
    }

    @Test
    public void anUnchangedAnswerCarriesOnlyItsKeyAndFingerprint() {
        LostTalesChatMembersPacket sent = LostTalesChatMembersPacket.unchanged(
                ChatChannel.WHISPER, "whisper:Steve", 1234L);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesChatMembersPacket read = new LostTalesChatMembersPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertTrue(read.isUnchanged());
        assertEquals("whisper:Steve", read.getConversationKey());
        assertEquals(1234L, read.getFingerprint());
        assertTrue(read.getMembers().isEmpty());

        // No fingerprint is no answer.
        ByteBuf blank = Unpooled.buffer();
        LostTalesChatMembersPacket.unchanged(ChatChannel.OOC, "ooc", 0L)
                .toBytes(blank);
        LostTalesChatMembersPacket refused = new LostTalesChatMembersPacket();
        refused.fromBytes(blank);
        assertTrue(refused.isMalformed());
    }

    @Test
    public void anNpcTheClientListsIsNeverSent() {
        LostTalesChatMembersPacket.Member npc = LostTalesChatMembersPacket.Member
                .npc(UUID.randomUUID(), "Barliman", 0xFFAA00, "lotr:portrait",
                        "", "", 0);
        assertTrue(npc.isNpc());
        assertEquals("the portrait is its head", "lotr:portrait",
                npc.getSkinId());
        assertTrue(new LostTalesChatMembersPacket(ChatChannel.WHISPER, "npc:B",
                Arrays.asList(npc), 0).getMembers().isEmpty());
    }

    @Test
    public void aRequestNamesAChannelThisBuildHas() {
        LostTalesChatMembersRequestPacket sent =
                new LostTalesChatMembersRequestPacket(ChatChannel.FACTION,
                        "faction", "", "", null, null, 42L);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesChatMembersRequestPacket read =
                new LostTalesChatMembersRequestPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertEquals(ChatChannel.FACTION, read.getChannel());
        assertEquals("faction", read.getConversationKey());
        assertEquals(42L, read.getHeldFingerprint());

        ByteBuf unknown = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(unknown, "nowhere", 64);
        LostTalesPacketCodec.writeUtf8String(unknown, "", 256);
        LostTalesPacketCodec.writeUtf8String(unknown, "", 64);
        LostTalesPacketCodec.writeUtf8String(unknown, "", 128);
        unknown.writeBoolean(false);
        unknown.writeBoolean(false);
        unknown.writeLong(0L);
        LostTalesChatMembersRequestPacket refused =
                new LostTalesChatMembersRequestPacket();
        refused.fromBytes(unknown);
        assertTrue(refused.isMalformed());
    }

    @Test
    public void aWhisperRequestNamesItsPartnerAndOnlyAWhisperDoes() {
        UUID beren = UUID.randomUUID();
        UUID aldric = UUID.randomUUID();
        LostTalesChatMembersRequestPacket sent =
                new LostTalesChatMembersRequestPacket(ChatChannel.WHISPER,
                        "whisper:Steve", "Steve", "Beren", beren, aldric, 0L);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        LostTalesChatMembersRequestPacket read =
                new LostTalesChatMembersRequestPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertEquals("Steve", read.getPartnerAccount());
        assertEquals("Beren", read.getPartnerIdentity());
        assertEquals(beren, read.getPartnerCharacterId());
        assertEquals(aldric, read.getHeldCharacterId());

        try {
            new LostTalesChatMembersRequestPacket(ChatChannel.OOC, "ooc",
                    "Steve", "", null, null, 0L);
            throw new AssertionError("only a whisper names a partner");
        } catch (IllegalArgumentException refused) {
            // expected
        }
    }
}
