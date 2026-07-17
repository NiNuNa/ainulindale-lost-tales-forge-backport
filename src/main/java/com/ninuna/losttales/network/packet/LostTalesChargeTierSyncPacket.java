package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server-authoritative charge-tier transition sent to nearby clients. */
public final class LostTalesChargeTierSyncPacket implements IMessage {
    private static final int ENCODED_BYTES = 11;
    private static final float MINIMUM_VELOCITY_MULTIPLIER = 0.5F;
    private static final float MAXIMUM_VELOCITY_MULTIPLIER = 3.0F;

    private int entityId;
    private boolean active;
    private boolean released;
    private int tier;
    private float velocityMultiplier = 1.0F;
    private boolean malformed;

    public LostTalesChargeTierSyncPacket() {}

    public LostTalesChargeTierSyncPacket(
            int entityId, boolean active, boolean released, int tier,
            double velocityMultiplier) {
        if (!isValid(entityId, active, released,
                tier, velocityMultiplier)) {
            throw new IllegalArgumentException(
                    "invalid charge-tier state");
        }
        this.entityId = entityId;
        this.active = active;
        this.released = released;
        this.tier = tier;
        this.velocityMultiplier = (float)velocityMultiplier;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() != ENCODED_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid charge-tier packet length");
            }
            entityId = buffer.readInt();
            active = buffer.readBoolean();
            released = buffer.readBoolean();
            tier = buffer.readUnsignedByte();
            velocityMultiplier = buffer.readFloat();
            if (!isValid(entityId,
                    active, released, tier, velocityMultiplier)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid charge-tier packet state");
            }
        } catch (RuntimeException exception) {
            malformed = true;
            entityId = -1;
            active = false;
            released = false;
            tier = 0;
            velocityMultiplier = 1.0F;
            if (buffer != null && buffer.readableBytes() > 0) {
                buffer.skipBytes(buffer.readableBytes());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeBoolean(active);
        buffer.writeBoolean(released);
        buffer.writeByte(tier);
        buffer.writeFloat(velocityMultiplier);
    }

    public int getEntityId() {
        return entityId;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isReleased() {
        return released;
    }

    public int getTier() {
        return tier;
    }

    public double getVelocityMultiplier() {
        return velocityMultiplier;
    }

    public boolean isMalformed() {
        return malformed;
    }

    private static boolean isValid(
            int entityId, boolean active, boolean released, int tier,
            double velocityMultiplier) {
        if (entityId < 0 || active && released
                || tier < 0 || tier > 3
                || !isFinite(velocityMultiplier)
                || velocityMultiplier < MINIMUM_VELOCITY_MULTIPLIER
                || velocityMultiplier > MAXIMUM_VELOCITY_MULTIPLIER) {
            return false;
        }
        return active || released || tier == 0;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** Common-safe client handler; client work is routed by the sided proxy. */
    public static final class Handler implements IMessageHandler<
            LostTalesChargeTierSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesChargeTierSyncPacket message,
                MessageContext context) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleChargeTierSync(
                                    message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
