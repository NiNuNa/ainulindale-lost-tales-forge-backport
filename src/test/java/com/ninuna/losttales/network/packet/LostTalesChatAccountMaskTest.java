package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * A player not shown which account plays a character is sent a
 * character's line without the account, a whisper filed under the
 * character, and the characters a line names without theirs; their own
 * account, and an account's own line, stay.
 */
public final class LostTalesChatAccountMaskTest {
    private static final UUID NILS = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID ALDRIC = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BEREN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    public void aCharactersLineNamesNoAccountButToItsSpeaker() {
        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, NILS, "Aldric", "Nils", "", 0, 0, "@Beren hail",
                1000000L, "skin", null, "", "", 0, false, 42L,
                ChatReplyReference.NONE, "", 0L, ALDRIC)
                .withNamedPlayers(Arrays.asList(
                        new ChatNamedPlayer(SAM, "Sam", BEREN, "Beren", "bskin"),
                        ChatNamedPlayer.account(NILS, "Nils")));

        LostTalesChatMessagePacket shown = roundTrip(line.withoutAccountsFor(SAM));
        assertFalse(shown.isMalformed());
        assertEquals("", shown.getAccountName());
        assertEquals("Aldric", shown.getIdentityName());
        assertEquals("the reader's own account stays", "Sam",
                shown.getNamedPlayers().get(0).getAccount());
        assertEquals("an account named as the account stays", "Nils",
                shown.getNamedPlayers().get(1).getAccount());

        LostTalesChatMessagePacket other = line.withoutAccountsFor(UUID.randomUUID());
        assertEquals("", other.getNamedPlayers().get(0).getAccount());
        assertEquals(BEREN, other.getNamedPlayers().get(0).getCharacterId());

        assertEquals("the speaker sees their own", "Nils",
                line.withoutAccountsFor(NILS).getAccountName());
    }

    @Test
    public void anAccountsLineStaysAsItIs() {
        LostTalesChatMessagePacket ooc = new LostTalesChatMessagePacket(
                ChatChannel.OOC, NILS, "Nils", "Nils", "", 0, 0, "hello",
                1000000L, "", null, "", "", 0, true, 43L,
                ChatReplyReference.NONE, "", 0L, null);
        assertSame(ooc, ooc.withoutAccountsFor(SAM));
    }

    @Test
    public void aWhisperWithACharacterIsFiledUnderTheCharacter() {
        LostTalesChatMessagePacket received = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, NILS, "Aldric", "Nils", "", 0, 0, "psst",
                1000000L, "skin", null, "", "Nils", 0, false, 44L,
                ChatReplyReference.NONE, "Aldric", 0L, ALDRIC)
                .withConversation(BEREN, ALDRIC);
        LostTalesChatMessagePacket shown = roundTrip(received.withoutAccountsFor(SAM));
        assertFalse(shown.isMalformed());
        assertEquals("Aldric", shown.getPartner());
        assertEquals("", shown.getAccountName());
        assertEquals(ALDRIC, shown.getPartnerCharacterId());
    }

    @Test
    public void aNamedCharacterWithoutItsAccountIsStillSomebody() {
        ChatNamedPlayer beren = new ChatNamedPlayer(SAM, "Sam", BEREN, "Beren", "s");
        ChatNamedPlayer shown = beren.withoutAccount();
        assertEquals("", shown.getAccount());
        assertEquals("Beren", shown.getIdentityName());
        assertEquals(true, shown.isValid());
        ChatNamedPlayer account = ChatNamedPlayer.account(SAM, "Sam");
        assertSame(account, account.withoutAccount());
        assertFalse(new ChatNamedPlayer(SAM, "", null, "", "").isValid());
    }

    @Test
    public void aMemberRowOfACharacterLosesItsAccount() {
        LostTalesChatMembersPacket.Member row = new LostTalesChatMembersPacket.Member(
                SAM, "Sam", BEREN, "Beren", 0, "s", "", 0, "", "", 0, true);
        assertEquals("", row.withoutAccount().getAccount());
        assertEquals(BEREN, row.withoutAccount().getCharacterId());
        LostTalesChatMembersPacket.Member account = new LostTalesChatMembersPacket.Member(
                SAM, "Sam", null, "Sam", 0, "", "", 0, "", "", 0, true);
        assertSame(account, account.withoutAccount());
        LostTalesChatMembersPacket packet = new LostTalesChatMembersPacket(ChatChannel.GLOBAL,
                "global", Collections.singletonList(row.withoutAccount()), 0);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatMembersPacket decoded = new LostTalesChatMembersPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("Beren", decoded.getMembers().get(0).getName());
    }

    private static LostTalesChatMessagePacket roundTrip(LostTalesChatMessagePacket line) {
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        return decoded;
    }
}
