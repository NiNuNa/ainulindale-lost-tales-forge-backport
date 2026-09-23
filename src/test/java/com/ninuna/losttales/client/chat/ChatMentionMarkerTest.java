package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.util.UUID;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * A mention carries whom it reaches, and the player as the line recorded
 * them, so its card opens after they have gone.
 */
public final class ChatMentionMarkerTest {

    @Test
    public void aMentionCarriesTheRecordedPlayerForItsCard() {
        UUID account = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        ChatNamedPlayer recorded = new ChatNamedPlayer(account, "Steve",
                character, "Aldric, the Gondor Farmer", "human/male/2");
        ChatMentionMarker.Data data = ChatMentionMarker.decode(
                ChatMentionMarker.apply(new ChatComponentText("@Aldric"),
                        0xAB597D, "Steve", recorded));
        assertEquals(0xAB597D, data.color);
        assertEquals("Steve", data.account);
        assertNull(data.role());
        assertEquals(account, data.recorded.getPlayerId());
        assertEquals(character, data.recorded.getCharacterId());
        assertEquals("human/male/2", data.recorded.getSkinId());
        assertEquals("Aldric, the Gondor Farmer",
                data.recorded.getIdentityName());
    }

    @Test
    public void anAccountRecordAndNoRecordBothReadBack() {
        UUID account = UUID.randomUUID();
        ChatMentionMarker.Data asAccount = ChatMentionMarker.decode(
                ChatMentionMarker.apply(new ChatComponentText("@Steve"),
                        0x123456, "Steve", new ChatNamedPlayer(account,
                                "Steve", null, "", "")));
        assertEquals("Steve", asAccount.account);
        assertNull(asAccount.recorded.getCharacterId());
        assertEquals("Steve", asAccount.recorded.getIdentityName());
        ChatMentionMarker.Data bare = ChatMentionMarker.decode(
                ChatMentionMarker.apply(new ChatComponentText("@Steve"),
                        0x123456, "Steve", null));
        assertEquals("Steve", bare.account);
        assertNull(bare.recorded);
    }

    /**
     * A Discord member's nickname may hold anything, the characters the
     * marker parts its fields with and a role's own prefix among them, and
     * still reads back as the player it names.
     */
    @Test
    public void aNameHoldingAnythingReadsBackWhole() {
        UUID member = UUID.randomUUID();
        String nickname = "role:Aragorn | King: of Gondor";
        ChatMentionMarker.Data data = ChatMentionMarker.decode(
                ChatMentionMarker.apply(new ChatComponentText("@" + nickname),
                        0x123456, nickname, ChatNamedPlayer.account(member,
                                nickname)));
        assertEquals(nickname, data.account);
        assertFalse(data.isRole());
        assertNull(data.role());
        assertEquals(member, data.recorded.getPlayerId());
        assertEquals(nickname, data.recorded.getIdentityName());
    }
}
