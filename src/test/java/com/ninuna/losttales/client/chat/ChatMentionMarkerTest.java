package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import java.util.UUID;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
                character, "Aldric, the Gondor Farmer", "human/male/2", 0x4A90D9);
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
                                "Steve", null, "", "", 0)));
        assertEquals("Steve", asAccount.account);
        assertNull(asAccount.recorded.getCharacterId());
        assertEquals("Steve", asAccount.recorded.getIdentityName());
        ChatMentionMarker.Data bare = ChatMentionMarker.decode(
                ChatMentionMarker.apply(new ChatComponentText("@Steve"),
                        0x123456, "Steve", null));
        assertEquals("Steve", bare.account);
        assertNull(bare.recorded);
    }
}
