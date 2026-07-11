package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public active-character appearance synchronization.
 *
 * A full packet replaces the client cache. Incremental packets update or remove
 * individual online players. No private roster data is included.
 */
public final class CharacterAppearanceSyncPacket implements IMessage {

    private static final int MAX_APPEARANCES = 512;

    private boolean replaceAll;
    private List<CharacterAppearance> appearances = Collections.emptyList();
    private boolean malformed;

    public CharacterAppearanceSyncPacket() {}

    public CharacterAppearanceSyncPacket(boolean replaceAll,
                                         List<CharacterAppearance> appearances) {
        this.replaceAll = replaceAll;
        if (appearances == null || appearances.isEmpty()) {
            this.appearances = Collections.emptyList();
        } else {
            if (appearances.size() > MAX_APPEARANCES) {
                throw new IllegalArgumentException("too many appearance entries");
            }
            this.appearances = Collections.unmodifiableList(
                    new ArrayList<CharacterAppearance>(appearances));
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.replaceAll = buffer.readBoolean();
            int count = buffer.readUnsignedShort();
            if (count > MAX_APPEARANCES) {
                throw new CharacterPacketCodec.DecodeException("too many appearance entries");
            }

            ArrayList<CharacterAppearance> decoded =
                    new ArrayList<CharacterAppearance>(count);
            Set<UUID> playerIds = new HashSet<UUID>();
            for (int index = 0; index < count; index++) {
                UUID playerId = CharacterPacketCodec.readUuid(buffer);
                String raceId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String genderId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String skinId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                if (!playerIds.add(playerId)) {
                    throw new CharacterPacketCodec.DecodeException(
                            "duplicate appearance player UUID");
                }
                decoded.add(new CharacterAppearance(
                        playerId, raceId, genderId, skinId));
            }
            CharacterPacketCodec.requireFinished(buffer);
            this.appearances = Collections.unmodifiableList(decoded);
        } catch (RuntimeException exception) {
            this.appearances = Collections.emptyList();
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (this.appearances.size() > MAX_APPEARANCES) {
            throw new IllegalStateException("too many appearance entries");
        }
        buffer.writeBoolean(this.replaceAll);
        buffer.writeShort(this.appearances.size());
        for (CharacterAppearance appearance : this.appearances) {
            CharacterPacketCodec.writeUuid(buffer, appearance.getPlayerId());
            CharacterPacketCodec.writeString(
                    buffer, appearance.getRaceId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, appearance.getGenderId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, appearance.getSkinId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        }
    }

    public boolean isReplaceAll() {
        return this.replaceAll;
    }

    public List<CharacterAppearance> getAppearances() {
        return this.appearances;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterAppearanceSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterAppearanceSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleCharacterAppearanceSync(message);
                }
            });
            return null;
        }
    }
}
