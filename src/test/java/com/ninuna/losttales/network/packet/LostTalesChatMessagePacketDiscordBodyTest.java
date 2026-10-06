package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A Discord member's line may carry its words with the marks the server
 * wrote in them as words each game translates; a player's line never
 * carries a component, on the wire or off it.
 */
public final class LostTalesChatMessagePacketDiscordBodyTest {
    private static final String BODY = "{\"text\":\"\",\"extra\":[{\"text\":\"look \"},"
            + "{\"translate\":\"chat.losttales.words.discord_sticker\",\"with\":[\"Wave\"]}]}";
    private static final UUID SAM =
            LostTalesChatMessagePacket.discordSenderId("123456789012345678");
    private static final UUID STEVE =
            UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    public void aDiscordMembersLineCarriesItsWordsToTranslate() {
        List<ChatNamedPlayer> named = Collections.singletonList(new ChatNamedPlayer(
                UUID.randomUUID(), "bob", null, "Beren", ""));
        LostTalesChatMessagePacket line = discordLine(SAM).withServerBody(BODY, named);
        assertEquals(BODY, line.getBodyJson());
        LostTalesChatMessagePacket decoded = roundTrip(line);
        assertFalse(decoded.isMalformed());
        assertEquals(BODY, decoded.getBodyJson());
        assertEquals("look *[Sticker: Wave]*", decoded.getMessage());
        assertEquals("Beren", decoded.getNamedPlayers().get(0).getIdentityName());
        assertEquals(BODY, decoded.withNameColor(0x123456).getBodyJson());
        // New words never keep the component the old ones came with.
        assertEquals("", decoded.withMessage("changed").getBodyJson());
        // The bridge's own id is a Discord sender too.
        assertEquals(BODY, discordLine(LostTalesChatMessagePacket.DISCORD_SENDER_ID)
                .withServerBody(BODY, null).getBodyJson());
    }

    @Test
    public void aComponentTooLargeIsLeftBehind() {
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= LostTalesChatMessagePacket.MAX_BODY_BYTES) {
            huge.append("{\"text\":\"x\"},");
        }
        assertEquals("", discordLine(SAM).withServerBody(huge.toString(), null)
                .getBodyJson());
    }

    @Test
    public void aPlayersLineStillMayNotCarryOne() {
        assertFalse(LostTalesChatMessagePacket.mayCarryBody(STEVE));
        assertTrue(LostTalesChatMessagePacket.mayCarryBody(SAM));
        assertTrue(LostTalesChatMessagePacket.mayCarryBody(
                LostTalesChatMessagePacket.SERVER_SENDER_ID));
        LostTalesChatMessagePacket player = ChatPacketFixtures.line(ChatChannel.GLOBAL,
                "Beren", "Steve", "hello").build();
        assertEquals("", player.withServerBody(BODY, null).getBodyJson());

        // The very bytes of a Discord line with a component, a player's
        // id in the sender's place: refused on decode.
        ByteBuf withBody = Unpooled.buffer();
        discordLine(SAM).withServerBody(BODY, null).toBytes(withBody);
        asSentBy(withBody, STEVE);
        LostTalesChatMessagePacket refused = new LostTalesChatMessagePacket();
        refused.fromBytes(withBody);
        assertTrue(refused.isMalformed());
        assertEquals("", refused.getBodyJson());

        // Without the component the same line is a player's like any other.
        ByteBuf plain = Unpooled.buffer();
        discordLine(SAM).toBytes(plain);
        asSentBy(plain, STEVE);
        LostTalesChatMessagePacket accepted = new LostTalesChatMessagePacket();
        accepted.fromBytes(plain);
        assertFalse(accepted.isMalformed());
        assertEquals(STEVE, accepted.getSenderId());
    }

    private static LostTalesChatMessagePacket discordLine(UUID sender) {
        return new LostTalesChatMessagePacket(ChatChannel.GLOBAL, sender, "Sam", "Sam",
                "The Shire", 0xFFFFFF, 0xFFFFFF, "look *[Sticker: Wave]*", 1L, "", null,
                "", "", 0, true, ChatMessageIdAllocator.next(), ChatReplyReference.NONE);
    }

    private static LostTalesChatMessagePacket roundTrip(LostTalesChatMessagePacket line) {
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    /** Writes {@code sender} over the encoded sender id, which follows the channel's id. */
    private static void asSentBy(ByteBuf buffer, UUID sender) {
        int at = 1 + ChatChannel.GLOBAL.getId().getBytes(Charset.forName("UTF-8")).length;
        buffer.setLong(at, sender.getMostSignificantBits());
        buffer.setLong(at + 8, sender.getLeastSignificantBits());
    }
}
