package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.network.server.LostTalesThirdPersonBlockActionService;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Requests one server-validated offset-camera block interaction. */
public final class LostTalesThirdPersonBlockActionPacket
        implements IMessage {
    private static final int OFFSET_UNITS = 16;

    private int x;
    private int y;
    private int z;
    private int side;
    private int hitOffsetX;
    private int hitOffsetY;
    private int hitOffsetZ;
    private boolean malformed;

    public LostTalesThirdPersonBlockActionPacket() {}

    public LostTalesThirdPersonBlockActionPacket(
            int x, int y, int z, int side,
            float hitOffsetX, float hitOffsetY, float hitOffsetZ) {
        if (!LostTalesPacketCodec.isValidBlockPosition(x, y, z)
                || !isValidSide(side)
                || !isUnitOffset(hitOffsetX)
                || !isUnitOffset(hitOffsetY)
                || !isUnitOffset(hitOffsetZ)) {
            throw new IllegalArgumentException(
                    "invalid third-person block action");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.hitOffsetX = quantizeOffset(hitOffsetX);
        this.hitOffsetY = quantizeOffset(hitOffsetY);
        this.hitOffsetZ = quantizeOffset(hitOffsetZ);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.x = buffer.readInt();
            this.y = buffer.readUnsignedByte();
            this.z = buffer.readInt();
            this.side = buffer.readUnsignedByte();
            this.hitOffsetX = buffer.readUnsignedByte();
            this.hitOffsetY = buffer.readUnsignedByte();
            this.hitOffsetZ = buffer.readUnsignedByte();
            LostTalesPacketCodec.requireFinished(buffer);
            if (!LostTalesPacketCodec.isValidBlockPosition(
                    this.x, this.y, this.z)
                    || !isValidSide(this.side)
                    || !isValidOffset(this.hitOffsetX)
                    || !isValidOffset(this.hitOffsetY)
                    || !isValidOffset(this.hitOffsetZ)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid third-person block action");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!LostTalesPacketCodec.isValidBlockPosition(
                this.x, this.y, this.z)
                || !isValidSide(this.side)
                || !isValidOffset(this.hitOffsetX)
                || !isValidOffset(this.hitOffsetY)
                || !isValidOffset(this.hitOffsetZ)) {
            throw new IllegalStateException(
                    "invalid third-person block action");
        }
        buffer.writeInt(this.x);
        buffer.writeByte(this.y);
        buffer.writeInt(this.z);
        buffer.writeByte(this.side);
        buffer.writeByte(this.hitOffsetX);
        buffer.writeByte(this.hitOffsetY);
        buffer.writeByte(this.hitOffsetZ);
    }

    private static int quantizeOffset(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return (int)(clamped * (float)OFFSET_UNITS);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean isUnitOffset(float value) {
        return isFinite(value) && value >= 0.0F && value <= 1.0F;
    }

    private static boolean isValidSide(int side) {
        return side >= 0 && side <= 5;
    }

    private static boolean isValidOffset(int value) {
        return value >= 0 && value <= OFFSET_UNITS;
    }

    int getXForTest() {
        return this.x;
    }

    int getYForTest() {
        return this.y;
    }

    int getZForTest() {
        return this.z;
    }

    int getSideForTest() {
        return this.side;
    }

    float getHitOffsetXForTest() {
        return (float)this.hitOffsetX / (float)OFFSET_UNITS;
    }

    float getHitOffsetYForTest() {
        return (float)this.hitOffsetY / (float)OFFSET_UNITS;
    }

    float getHitOffsetZForTest() {
        return (float)this.hitOffsetZ / (float)OFFSET_UNITS;
    }

    boolean isMalformedForTest() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesThirdPersonBlockActionPacket, IMessage> {

        @Override
        public IMessage onMessage(
                final LostTalesThirdPersonBlockActionPacket message,
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
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            final int side = message.side;
            final float hitOffsetX =
                    (float)message.hitOffsetX / (float)OFFSET_UNITS;
            final float hitOffsetY =
                    (float)message.hitOffsetY / (float)OFFSET_UNITS;
            final float hitOffsetZ =
                    (float)message.hitOffsetZ / (float)OFFSET_UNITS;
            final int expectedHotbarSlot =
                    player.inventory.currentItem;
            final ItemStack expectedHeldItem = ItemStack.copyItemStack(
                    player.inventory.getCurrentItem());
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType
                            .THIRD_PERSON_BLOCK_ACTION,
                    message.malformed,
                    "LostTalesThirdPersonBlockActionPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesThirdPersonBlockActionService.execute(
                                    livePlayer, expectedDimension,
                                    x, y, z, side,
                                    hitOffsetX, hitOffsetY, hitOffsetZ,
                                    expectedHotbarSlot,
                                    expectedHeldItem);
                        }
                    });
            return null;
        }
    }
}
