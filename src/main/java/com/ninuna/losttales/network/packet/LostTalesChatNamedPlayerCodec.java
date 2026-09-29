package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatNamedPlayer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The wire layout of the players a line names, shared by the line and
 * the edit that changes its words: a count of at most
 * {@link ChatNamedPlayer#MAX_PER_LINE}, then for each the player's id,
 * account, character id, the name they were named by and their skin.
 * An id is a presence flag and both halves, so every entry is the same
 * size. Every part is bounded; a list that breaks a bound does not decode.
 */
final class LostTalesChatNamedPlayerCodec {
    /** One optional id: its flag and its two halves. */
    private static final int ID_BYTES = 1 + 16;
    /** The most a list takes on the wire. */
    static final int MAX_BYTES = 1 + ChatNamedPlayer.MAX_PER_LINE * (ID_BYTES
            + 4 + ChatNamedPlayer.MAX_ACCOUNT_BYTES
            + ID_BYTES
            + 4 + ChatNamedPlayer.MAX_IDENTITY_BYTES
            + 4 + ChatNamedPlayer.MAX_SKIN_ID_BYTES);

    private LostTalesChatNamedPlayerCodec() {}

    static void write(ByteBuf buffer, List<ChatNamedPlayer> players) {
        List<ChatNamedPlayer> named = players == null
                ? Collections.<ChatNamedPlayer>emptyList() : players;
        LostTalesPacketCodec.writeCount(buffer, named.size(),
                ChatNamedPlayer.MAX_PER_LINE, "named players");
        for (ChatNamedPlayer player : named) {
            writeId(buffer, player.getPlayerId());
            LostTalesPacketCodec.writeUtf8String(buffer, player.getAccount(),
                    ChatNamedPlayer.MAX_ACCOUNT_BYTES);
            writeId(buffer, player.getCharacterId());
            LostTalesPacketCodec.writeUtf8String(buffer,
                    player.getIdentityName(),
                    ChatNamedPlayer.MAX_IDENTITY_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, player.getSkinId(),
                    ChatNamedPlayer.MAX_SKIN_ID_BYTES);
        }
    }

    static List<ChatNamedPlayer> read(ByteBuf buffer) {
        int count = LostTalesPacketCodec.readCount(buffer,
                ChatNamedPlayer.MAX_PER_LINE, "named players");
        List<ChatNamedPlayer> players = new ArrayList<ChatNamedPlayer>(count);
        for (int index = 0; index < count; index++) {
            UUID playerId = readId(buffer);
            String account = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatNamedPlayer.MAX_ACCOUNT_BYTES);
            UUID characterId = readId(buffer);
            String identity = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatNamedPlayer.MAX_IDENTITY_BYTES);
            String skin = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatNamedPlayer.MAX_SKIN_ID_BYTES);
            ChatNamedPlayer player = new ChatNamedPlayer(playerId, account,
                    characterId, identity, skin);
            if (!player.isValid()) {
                throw new LostTalesPacketCodec.DecodeException(
                        "a named player without an account");
            }
            players.add(player);
        }
        return players.isEmpty() ? Collections.<ChatNamedPlayer>emptyList()
                : Collections.unmodifiableList(players);
    }

    private static void writeId(ByteBuf buffer, UUID value) {
        buffer.writeBoolean(value != null);
        buffer.writeLong(value == null ? 0L : value.getMostSignificantBits());
        buffer.writeLong(value == null ? 0L : value.getLeastSignificantBits());
    }

    private static UUID readId(ByteBuf buffer) {
        boolean present = buffer.readBoolean();
        long most = buffer.readLong();
        long least = buffer.readLong();
        return present ? new UUID(most, least) : null;
    }
}
