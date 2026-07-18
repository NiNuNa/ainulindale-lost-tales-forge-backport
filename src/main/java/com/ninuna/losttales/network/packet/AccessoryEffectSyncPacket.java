package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Server-authoritative public/owner effect state for one tracked player. */
public final class AccessoryEffectSyncPacket implements IMessage {

    public static final int MAX_DEFINITION_ID_BYTES = 128;
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_PACKET_BYTES = 192;

    private UUID playerId;
    private int entityId;
    private long sequence;
    private String definitionId = "";
    private boolean malformed;

    public AccessoryEffectSyncPacket() {}

    public AccessoryEffectSyncPacket(
            UUID playerId, int entityId, long sequence,
            String definitionId) {
        String normalized = definitionId == null ? "" : definitionId;
        if (!isValid(playerId, entityId, sequence, normalized)) {
            throw new IllegalArgumentException("invalid accessory effect state");
        }
        this.playerId = playerId;
        this.entityId = entityId;
        this.sequence = sequence;
        this.definitionId = normalized;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() < 30
                    || buffer.readableBytes() > MAX_PACKET_BYTES
                    || buffer.readUnsignedByte() != PROTOCOL_VERSION) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid accessory-effect packet framing");
            }
            this.playerId = new UUID(buffer.readLong(), buffer.readLong());
            this.entityId = buffer.readInt();
            this.sequence = buffer.readLong();
            this.definitionId = ByteBufUtils.readUTF8String(buffer);
            if (buffer.readableBytes() != 0
                    || !isValid(this.playerId, this.entityId,
                    this.sequence, this.definitionId)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid accessory-effect packet state");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.playerId = null;
            this.entityId = -1;
            this.sequence = -1L;
            this.definitionId = "";
            if (buffer != null && buffer.readableBytes() > 0) {
                buffer.skipBytes(buffer.readableBytes());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(PROTOCOL_VERSION);
        buffer.writeLong(this.playerId.getMostSignificantBits());
        buffer.writeLong(this.playerId.getLeastSignificantBits());
        buffer.writeInt(this.entityId);
        buffer.writeLong(this.sequence);
        ByteBufUtils.writeUTF8String(buffer, this.definitionId);
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public long getSequence() {
        return this.sequence;
    }

    public String getDefinitionId() {
        return this.definitionId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private static boolean isValid(
            UUID playerId, int entityId, long sequence,
            String definitionId) {
        if (playerId == null || entityId < 0 || sequence <= 0L
                || definitionId == null
                || definitionId.getBytes(StandardCharsets.UTF_8).length
                > MAX_DEFINITION_ID_BYTES) {
            return false;
        }
        return definitionId.length() == 0
                || AccessoryCompatibilityRegistry.getInstance()
                .getDefinition(definitionId) != null;
    }

    public static final class Handler implements IMessageHandler<
            AccessoryEffectSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final AccessoryEffectSyncPacket message,
                MessageContext context) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleAccessoryEffectSync(
                                    message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
