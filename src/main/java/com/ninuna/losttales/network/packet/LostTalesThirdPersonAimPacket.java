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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

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
        if (active && (!isFinite(directionX)
                || !isFinite(directionY) || !isFinite(directionZ))) {
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
                    && (!isFinite(this.directionX)
                    || !isFinite(this.directionY)
                    || !isFinite(this.directionZ))) {
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

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
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
            if (player == null || player.worldObj == null
                    || player.worldObj.provider == null
                    || message == null) {
                return null;
            }
            final int expectedDimension =
                    player.worldObj.provider.dimensionId;
            final boolean active = message.active;
            final double directionX = message.directionX;
            final double directionY = message.directionY;
            final double directionZ = message.directionZ;
            final int expectedHotbarSlot = player.inventory.currentItem;
            ItemStack held = player.inventory.getCurrentItem();
            final Item expectedItem = held == null
                    ? null : held.getItem();
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
                                    livePlayer, expectedDimension,
                                    active, directionX, directionY,
                                    directionZ, expectedHotbarSlot,
                                    expectedItem);
                        }
                    });
            return null;
        }
    }
}
