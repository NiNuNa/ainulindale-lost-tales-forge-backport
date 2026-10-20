package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.network.server.LostTalesThirdPersonAimService;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Updates or clears a short-lived server-validated projectile aim vector. */
public final class LostTalesThirdPersonAimPacket implements IMessage {
    private boolean active;
    private float directionX;
    private float directionY;
    private float directionZ;
    private boolean malformed;

    public LostTalesThirdPersonAimPacket() {}

    public LostTalesThirdPersonAimPacket(
            boolean active, float directionX,
            float directionY, float directionZ) {
        if (active && (!Float.isFinite(directionX)
                || !Float.isFinite(directionY) || !Float.isFinite(directionZ))) {
            throw new IllegalArgumentException("aim direction must be finite");
        }
        this.active = active;
        this.directionX = directionX;
        this.directionY = directionY;
        this.directionZ = directionZ;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            int activeValue = buffer.readUnsignedByte();
            this.active = activeValue == 1;
            this.directionX = buffer.readFloat();
            this.directionY = buffer.readFloat();
            this.directionZ = buffer.readFloat();
            LostTalesPacketCodec.requireFinished(buffer);
            if (activeValue > 1 || this.active
                    && (!Float.isFinite(this.directionX)
                    || !Float.isFinite(this.directionY)
                    || !Float.isFinite(this.directionZ))) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid third-person aim packet");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(this.active ? 1 : 0);
        buffer.writeFloat(this.directionX);
        buffer.writeFloat(this.directionY);
        buffer.writeFloat(this.directionZ);
    }

    boolean isActiveForTest() {
        return this.active;
    }

    boolean isMalformedForTest() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesThirdPersonAimPacket, IMessage> {

        @Override
        public IMessage onMessage(
                final LostTalesThirdPersonAimPacket message,
                MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            // The player's world and hand are read on the server thread,
            // in the task, never here.
            final boolean active = message.active;
            final double directionX = message.directionX;
            final double directionY = message.directionY;
            final double directionZ = message.directionZ;
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType
                            .THIRD_PERSON_AIM,
                    message.malformed,
                    "LostTalesThirdPersonAimPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesThirdPersonAimService.execute(
                                    livePlayer, active, directionX,
                                    directionY, directionZ);
                        }
                    });
            return null;
        }
    }
}
