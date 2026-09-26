package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Full private roster snapshot sent only to the owning player. */
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
            UUID ownerId = CharacterPacketCodec.readUuid(buffer);
            int unlockedSlotCount = buffer.readUnsignedByte();
            UUID activeCharacterId = CharacterPacketCodec.readNullableUuid(buffer);
            long revision = buffer.readLong();
            int dataVersion = buffer.readInt();
            int characterCount = buffer.readUnsignedByte();

            if (unlockedSlotCount < CharacterRoster.INITIAL_UNLOCKED_SLOTS
                    || unlockedSlotCount > CharacterRoster.MAX_SLOTS
                    || dataVersion <= 0
                    || characterCount > CharacterPacketCodec.MAX_CHARACTERS) {
                throw new CharacterPacketCodec.DecodeException("invalid roster header");
            }

            List<CharacterSummary> characters = new ArrayList<CharacterSummary>(characterCount);
            Set<UUID> ids = new HashSet<UUID>();
            Set<Integer> slots = new HashSet<Integer>();
            for (int index = 0; index < characterCount; index++) {
                UUID characterId = CharacterPacketCodec.readUuid(buffer);
                // Signed: the default character sits in slot -1, and the
                // nine a player fills encode the same either way.
                int slotIndex = buffer.readByte();
                String name = CharacterPacketCodec.readString(buffer, CharacterPacketCodec.MAX_NAME_BYTES);
                String raceId = CharacterPacketCodec.readString(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String genderId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String skinId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                boolean showMinecraftCape = buffer.readBoolean();
                int cosmeticCapeId = buffer.readUnsignedShort();
                int age = buffer.readInt();
                String factionId = CharacterPacketCodec.readString(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                int roleplayLevel = buffer.readInt();
                long experiencePoints = buffer.readLong();
                long creationTimestamp = buffer.readLong();
                int characterDataVersion = buffer.readInt();
                String bodyTypeId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String chestTypeId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);

                if (!CharacterRoster.isValidSlotIndex(slotIndex)
                        || !ids.add(characterId)
                        || !slots.add(Integer.valueOf(slotIndex))
                        || !CharacterCapeCatalog.isValidSelection(cosmeticCapeId)
                        || roleplayLevel < 1
                        || experiencePoints < 0L
                        || creationTimestamp < 0L
                        || characterDataVersion <= 0) {
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
                        roleplayLevel,
                        experiencePoints,
                        creationTimestamp,
                        characterDataVersion,
                        bodyTypeId,
                        chestTypeId
                ));
            }
            // After every character: the cape the account wears when
            // played as itself, then whether this world has taken the
            // account's template.
            boolean accountShowMinecraftCape = buffer.readBoolean();
            int accountCosmeticCapeId = buffer.readUnsignedShort();
            if (!CharacterCapeCatalog.isValidSelection(accountCosmeticCapeId)) {
                throw new CharacterPacketCodec.DecodeException("invalid account cape");
            }
            boolean templateTaken = buffer.readBoolean();
            CharacterPacketCodec.requireFinished(buffer);
            this.snapshot = new CharacterRosterSnapshot(
                    ownerId,
                    unlockedSlotCount,
                    activeCharacterId,
                    revision,
                    dataVersion,
                    characters,
                    accountShowMinecraftCape,
                    accountCosmeticCapeId,
                    templateTaken
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
        CharacterPacketCodec.writeUuid(buffer, this.snapshot.getOwnerId());
        buffer.writeByte(this.snapshot.getUnlockedSlotCount());
        CharacterPacketCodec.writeNullableUuid(buffer, this.snapshot.getActiveCharacterId());
        buffer.writeLong(this.snapshot.getRevision());
        buffer.writeInt(this.snapshot.getDataVersion());
        buffer.writeByte(this.snapshot.getCharacterCount());
        for (CharacterSummary character : this.snapshot.getCharacters()) {
            CharacterPacketCodec.writeUuid(buffer, character.getCharacterId());
            buffer.writeByte(character.getSlotIndex());
            CharacterPacketCodec.writeString(buffer, character.getName(), CharacterPacketCodec.MAX_NAME_BYTES);
            CharacterPacketCodec.writeString(buffer, character.getRaceId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, character.getGenderId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, character.getSkinId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            buffer.writeBoolean(character.isMinecraftCapeVisible());
            buffer.writeShort(character.getCosmeticCapeId());
            buffer.writeInt(character.getAge());
            CharacterPacketCodec.writeString(buffer, character.getStartingFactionId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            buffer.writeInt(character.getRoleplayLevel());
            buffer.writeLong(character.getExperiencePoints());
            buffer.writeLong(character.getCreationTimestamp());
            buffer.writeInt(character.getDataVersion());
            // The arm width and chest type chosen for the character.
            CharacterPacketCodec.writeString(
                    buffer, character.getBodyTypeId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            CharacterPacketCodec.writeString(
                    buffer, character.getChestTypeId(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        }
        buffer.writeBoolean(this.snapshot.isAccountMinecraftCapeVisible());
        buffer.writeShort(this.snapshot.getAccountCosmeticCapeId());
        buffer.writeBoolean(this.snapshot.isTemplateTaken());
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
