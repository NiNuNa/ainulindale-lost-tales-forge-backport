package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
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
                String characterName = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_NAME_BYTES);
                String raceId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String genderId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String skinId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                boolean showMinecraftCape = buffer.readBoolean();
                int cosmeticCapeId = buffer.readUnsignedShort();
                String accountName = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_NAME_BYTES);
                String startingFactionId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                int roleplayLevel = buffer.readInt();
                int age = buffer.readInt();
                String description = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
                String bodyTypeId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String chestTypeId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                if (!playerIds.add(playerId)) {
                    throw new CharacterPacketCodec.DecodeException(
                            "duplicate appearance player UUID");
                }
                if (!CharacterCapeCatalog.isValidSelection(cosmeticCapeId)) {
                    throw new CharacterPacketCodec.DecodeException(
                            "invalid cosmetic cape ID");
                }
                if (roleplayLevel < 0 || age < 0 || description.length()
                        > CharacterAppearance.MAX_DESCRIPTION_LENGTH) {
                    throw new CharacterPacketCodec.DecodeException(
                            "invalid appearance details");
                }
                decoded.add(new CharacterAppearance(
                        playerId, accountName, characterName, raceId,
                        genderId, skinId, showMinecraftCape, cosmeticCapeId,
                        startingFactionId, roleplayLevel, age, description,
                        bodyTypeId, chestTypeId));
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
                    buffer, appearance.getCharacterName(),
                    CharacterPacketCodec.MAX_NAME_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, appearance.getRaceId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, appearance.getGenderId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, appearance.getSkinId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            buffer.writeBoolean(appearance.isMinecraftCapeVisible());
            buffer.writeShort(appearance.getCosmeticCapeId());
            // Appended after the original layout; account names pair the
            // tab list with characters for mention completion.
            CharacterPacketCodec.writeString(
                    buffer, appearance.getAccountName(),
                    CharacterPacketCodec.MAX_NAME_BYTES);
            // Appended again for the chat player card: starting faction,
            // level, age, and biography are public character identity.
            CharacterPacketCodec.writeString(
                    buffer, appearance.getStartingFactionId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            buffer.writeInt(appearance.getRoleplayLevel());
            buffer.writeInt(appearance.getAge());
            CharacterPacketCodec.writeString(
                    buffer, appearance.getDescription(),
                    CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
            // Appended again: the arm width, which other clients need to
            // pick the body the character is drawn with.
            CharacterPacketCodec.writeString(
                    buffer, appearance.getBodyTypeId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            // Appended again: the chest type.
            CharacterPacketCodec.writeString(
                    buffer, appearance.getChestTypeId(),
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
