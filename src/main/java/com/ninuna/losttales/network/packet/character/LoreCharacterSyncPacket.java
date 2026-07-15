package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Bounded server snapshot of definitions and world ownership availability. */
public final class LoreCharacterSyncPacket implements IMessage {

    private static final int MAX_LORE_CHARACTERS = 1024;
    private LoreCharacterSnapshot snapshot;
    private boolean malformed;

    public LoreCharacterSyncPacket() {}
    public LoreCharacterSyncPacket(LoreCharacterSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot required");
        this.snapshot = snapshot;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        try {
            boolean ownershipReadOnly = buffer.readBoolean();
            boolean transferReadOnly = buffer.readBoolean();
            int count = buffer.readUnsignedShort();
            if (count > MAX_LORE_CHARACTERS) {
                throw new CharacterPacketCodec.DecodeException("too many lore characters");
            }
            List<LoreCharacterSummary> summaries =
                    new ArrayList<LoreCharacterSummary>(count);
            for (int index = 0; index < count; index++) {
                String id = string(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String name = string(buffer, CharacterPacketCodec.MAX_NAME_BYTES);
                String description = string(buffer, CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
                String race = string(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String gender = string(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String model = string(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                String skin = string(buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                boolean configured = buffer.readBoolean();
                boolean claimed = buffer.readBoolean();
                boolean owned = buffer.readBoolean();
                boolean transferring = buffer.readBoolean();
                String owner = string(buffer, CharacterPacketCodec.MAX_NAME_BYTES);
                java.util.UUID ownedCharacterId =
                        CharacterPacketCodec.readNullableUuid(buffer);
                long revision = buffer.readLong();
                if (revision < 0L) throw new CharacterPacketCodec.DecodeException("negative revision");
                summaries.add(new LoreCharacterSummary(
                        id, name, description, race, gender, model, skin,
                        configured, claimed, owned, transferring,
                        owner, ownedCharacterId, revision));
            }
            CharacterPacketCodec.requireFinished(buffer);
            this.snapshot = new LoreCharacterSnapshot(
                    summaries, ownershipReadOnly, transferReadOnly);
        } catch (RuntimeException exception) {
            this.snapshot = null; this.malformed = true;
        }
    }

    @Override public void toBytes(ByteBuf buffer) {
        if (this.snapshot == null) throw new IllegalStateException("snapshot missing");
        List<LoreCharacterSummary> summaries = this.snapshot.getCharacters();
        if (summaries.size() > MAX_LORE_CHARACTERS) {
            throw new IllegalStateException("too many lore characters");
        }
        buffer.writeBoolean(this.snapshot.isOwnershipReadOnly());
        buffer.writeBoolean(this.snapshot.isTransferReadOnly());
        buffer.writeShort(summaries.size());
        for (LoreCharacterSummary summary : summaries) {
            write(buffer, summary.getId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            write(buffer, summary.getName(), CharacterPacketCodec.MAX_NAME_BYTES);
            write(buffer, summary.getDescription(), CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
            write(buffer, summary.getRaceId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            write(buffer, summary.getGenderId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            write(buffer, summary.getModelId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            write(buffer, summary.getSkinId(), CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            buffer.writeBoolean(summary.isConfigured());
            buffer.writeBoolean(summary.isClaimed());
            buffer.writeBoolean(summary.isOwnedByViewer());
            buffer.writeBoolean(summary.isTransferInProgress());
            write(buffer, summary.getOwnerName(), CharacterPacketCodec.MAX_NAME_BYTES);
            CharacterPacketCodec.writeNullableUuid(
                    buffer, summary.getOwnedCharacterId());
            buffer.writeLong(summary.getOwnershipRevision());
        }
    }

    public LoreCharacterSnapshot getSnapshot() { return this.snapshot; }
    public boolean isMalformed() { return this.malformed; }
    private static String string(ByteBuf buffer, int max) {
        return CharacterPacketCodec.readString(buffer, max);
    }
    private static void write(ByteBuf buffer, String value, int max) {
        CharacterPacketCodec.writeString(buffer, value, max);
    }
    public static final class Handler implements IMessageHandler<
            LoreCharacterSyncPacket, IMessage> {
        @Override public IMessage onMessage(
                final LoreCharacterSyncPacket message, MessageContext context) {
            if (message != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override public void run() {
                        LostTalesMod.proxy.handleLoreCharacterSync(message);
                    }
                });
            }
            return null;
        }
    }
}
