package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Full private roster snapshot sent only to the owning player: its
 * characters, the account character's capes, and the deleted characters
 * the server still keeps for it.
 */
public final class CharacterRosterSyncPacket implements IMessage {

    private int requestId;
    private CharacterRosterSnapshot snapshot;
    private boolean malformed;

    public CharacterRosterSyncPacket() {}

    public CharacterRosterSyncPacket(int requestId, CharacterRosterSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        this.requestId = requestId;
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            UUID ownerId = LostTalesPacketCodec.readUuid(buffer);
            int unlockedSlotCount = buffer.readUnsignedByte();
            UUID activeCharacterId = LostTalesPacketCodec.readNullableUuid(buffer);
            long revision = buffer.readLong();
            int characterCount = buffer.readUnsignedByte();

            if (unlockedSlotCount < CharacterRoster.INITIAL_UNLOCKED_SLOTS
                    || unlockedSlotCount > CharacterRoster.MAX_SLOTS
                    || revision < 0L
                    || characterCount > CharacterPacketCodec.MAX_CHARACTERS) {
                throw new CharacterPacketCodec.DecodeException("invalid roster header");
            }

            List<CharacterSummary> characters = new ArrayList<CharacterSummary>(characterCount);
            Set<UUID> ids = new HashSet<UUID>();
            Set<Integer> slots = new HashSet<Integer>();
            for (int index = 0; index < characterCount; index++) {
                UUID characterId = LostTalesPacketCodec.readUuid(buffer);
                // Signed: the account character sits in slot -1, and the
                // nine a player fills encode the same either way.
                int slotIndex = buffer.readByte();
                String name = LostTalesPacketCodec.readUtf8String(buffer, CharacterPacketCodec.MAX_NAME_BYTES);
                String raceId = LostTalesPacketCodec.readUtf8String(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String genderId = LostTalesPacketCodec.readUtf8String(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String skinId = LostTalesPacketCodec.readUtf8String(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                boolean showMinecraftCape = buffer.readBoolean();
                int cosmeticCapeId = buffer.readUnsignedShort();
                int age = buffer.readInt();
                String factionId = LostTalesPacketCodec.readUtf8String(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String bodyTypeId = LostTalesPacketCodec.readUtf8String(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String chestTypeId = LostTalesPacketCodec.readUtf8String(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);

                if (!CharacterRoster.isValidSlotIndex(slotIndex)
                        || !ids.add(characterId)
                        || !slots.add(Integer.valueOf(slotIndex))
                        || !CharacterCapeCatalog.isValidSelection(cosmeticCapeId)) {
                    throw new CharacterPacketCodec.DecodeException("invalid character summary");
                }
                characters.add(new CharacterSummary(
                        characterId,
                        slotIndex,
                        name,
                        raceId,
                        genderId,
                        skinId,
                        showMinecraftCape,
                        cosmeticCapeId,
                        age,
                        factionId,
                        bodyTypeId,
                        chestTypeId
                ));
            }
            // After every character: the cape the account wears when
            // played as itself, then whether this world has taken the
            // account character's look.
            boolean accountShowMinecraftCape = buffer.readBoolean();
            int accountCosmeticCapeId = buffer.readUnsignedShort();
            if (!CharacterCapeCatalog.isValidSelection(accountCosmeticCapeId)) {
                throw new CharacterPacketCodec.DecodeException("invalid account cape");
            }
            boolean templateTaken = buffer.readBoolean();
            // Last, the deleted characters the server still keeps.
            int deletedCount = buffer.readUnsignedByte();
            if (deletedCount > CharacterRosterSnapshot.MAX_DELETED) {
                throw new CharacterPacketCodec.DecodeException(
                        "too many deleted characters");
            }
            List<DeletedCharacterSummary> deleted =
                    new ArrayList<DeletedCharacterSummary>(deletedCount);
            for (int index = 0; index < deletedCount; index++) {
                UUID characterId = LostTalesPacketCodec.readUuid(buffer);
                String name = LostTalesPacketCodec.readUtf8String(
                        buffer, CharacterPacketCodec.MAX_NAME_BYTES);
                String raceId = LostTalesPacketCodec.readUtf8String(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String skinId = LostTalesPacketCodec.readUtf8String(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                int daysLeft = buffer.readUnsignedShort();
                if (!ids.add(characterId)) {
                    throw new CharacterPacketCodec.DecodeException(
                            "a deleted character named twice");
                }
                deleted.add(new DeletedCharacterSummary(characterId, name,
                        raceId, skinId, daysLeft));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.snapshot = new CharacterRosterSnapshot(
                    ownerId,
                    unlockedSlotCount,
                    activeCharacterId,
                    revision,
                    characters,
                    accountShowMinecraftCape,
                    accountCosmeticCapeId,
                    templateTaken,
                    deleted
            );
            if (activeCharacterId != null && this.snapshot.getActiveCharacterId() == null) {
                throw new CharacterPacketCodec.DecodeException("invalid active character reference");
            }
        } catch (RuntimeException exception) {
            this.snapshot = null;
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (this.snapshot == null) {
            throw new IllegalStateException("snapshot must not be null");
        }
        buffer.writeInt(this.requestId);
        LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getOwnerId());
        buffer.writeByte(this.snapshot.getUnlockedSlotCount());
        LostTalesPacketCodec.writeNullableUuid(buffer, this.snapshot.getActiveCharacterId());
        buffer.writeLong(this.snapshot.getRevision());
        buffer.writeByte(this.snapshot.getCharacterCount());
        for (CharacterSummary character : this.snapshot.getCharacters()) {
            LostTalesPacketCodec.writeUuid(buffer, character.getCharacterId());
            buffer.writeByte(character.getSlotIndex());
            LostTalesPacketCodec.writeUtf8String(buffer, character.getName(), CharacterPacketCodec.MAX_NAME_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, character.getRaceId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            LostTalesPacketCodec.writeUtf8String(
                    buffer, character.getGenderId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            LostTalesPacketCodec.writeUtf8String(
                    buffer, character.getSkinId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            buffer.writeBoolean(character.isMinecraftCapeVisible());
            buffer.writeShort(character.getCosmeticCapeId());
            buffer.writeInt(character.getAge());
            LostTalesPacketCodec.writeUtf8String(buffer, character.getFactionId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            // The arm width and chest type chosen for the character.
            LostTalesPacketCodec.writeUtf8String(
                    buffer, character.getBodyTypeId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            LostTalesPacketCodec.writeUtf8String(
                    buffer, character.getChestTypeId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        }
        buffer.writeBoolean(this.snapshot.isAccountMinecraftCapeVisible());
        buffer.writeShort(this.snapshot.getAccountCosmeticCapeId());
        buffer.writeBoolean(this.snapshot.isTemplateTaken());
        buffer.writeByte(this.snapshot.getDeleted().size());
        for (DeletedCharacterSummary deleted : this.snapshot.getDeleted()) {
            LostTalesPacketCodec.writeUuid(buffer, deleted.getCharacterId());
            LostTalesPacketCodec.writeUtf8String(buffer, deleted.getName(),
                    CharacterPacketCodec.MAX_NAME_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, deleted.getRaceId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, deleted.getSkinId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            buffer.writeShort(Math.min(0xFFFF, deleted.getDaysLeft()));
        }
    }

    public int getRequestId() {
        return this.requestId;
    }

    public CharacterRosterSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<CharacterRosterSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterRosterSyncPacket message, MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleCharacterRosterSync(message);
                }
            });
            return null;
        }
    }
}
