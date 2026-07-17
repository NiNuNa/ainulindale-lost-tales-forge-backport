package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import com.ninuna.losttales.network.server.LostTalesThirdPersonEntityActionService;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Requests one camera-intent entity action. The server re-resolves reach,
 * target bounds, entity ordering, and block line of sight before acting.
 */
public final class LostTalesThirdPersonEntityActionPacket
        implements IMessage {

    public enum Action {
        INTERACT(0),
        ATTACK(1);

        private final int wireId;

        Action(int wireId) {
            this.wireId = wireId;
        }

        static Action fromWireId(int wireId) {
            for (Action action : values()) {
                if (action.wireId == wireId) {
                    return action;
                }
            }
            return null;
        }
    }

    private Action action;
    private int entityId;
    private double hitX;
    private double hitY;
    private double hitZ;
    private boolean useItemIfInteractionDeclines;
    private boolean malformed;

    public LostTalesThirdPersonEntityActionPacket() {}

    public LostTalesThirdPersonEntityActionPacket(
            Action action, int entityId,
            double hitX, double hitY, double hitZ) {
        this(action, entityId, hitX, hitY, hitZ, false);
    }

    public LostTalesThirdPersonEntityActionPacket(
            Action action, int entityId,
            double hitX, double hitY, double hitZ,
            boolean useItemIfInteractionDeclines) {
        this.action = action;
        this.entityId = entityId;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.useItemIfInteractionDeclines =
                useItemIfInteractionDeclines;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.action = Action.fromWireId(buffer.readUnsignedByte());
            this.entityId = buffer.readInt();
            this.hitX = buffer.readDouble();
            this.hitY = buffer.readDouble();
            this.hitZ = buffer.readDouble();
            this.useItemIfInteractionDeclines = buffer.readBoolean();
            LostTalesPacketCodec.requireFinished(buffer);
            if (this.action == null || this.entityId < 0
                    || !isFinite(this.hitX)
                    || !isFinite(this.hitY)
                    || !isFinite(this.hitZ)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid third-person entity action");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (this.action == null) {
            throw new IllegalStateException("entity action is required");
        }
        buffer.writeByte(this.action.wireId);
        buffer.writeInt(this.entityId);
        buffer.writeDouble(this.hitX);
        buffer.writeDouble(this.hitY);
        buffer.writeDouble(this.hitZ);
        buffer.writeBoolean(this.useItemIfInteractionDeclines);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    Action getActionForTest() {
        return this.action;
    }

    int getEntityIdForTest() {
        return this.entityId;
    }

    boolean isMalformedForTest() {
        return this.malformed;
    }

    boolean shouldUseItemIfInteractionDeclinesForTest() {
        return this.useItemIfInteractionDeclines;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesThirdPersonEntityActionPacket, IMessage> {

        @Override
        public IMessage onMessage(
                final LostTalesThirdPersonEntityActionPacket message,
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
            final Action action = message.action;
            final int entityId = message.entityId;
            final double hitX = message.hitX;
            final double hitY = message.hitY;
            final double hitZ = message.hitZ;
            final boolean useItemIfInteractionDeclines =
                    message.useItemIfInteractionDeclines;
            final int expectedHotbarSlot =
                    player.inventory.currentItem;
            final ItemStack expectedHeldItem = ItemStack.copyItemStack(
                    player.inventory.getCurrentItem());
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType
                            .THIRD_PERSON_ENTITY_ACTION,
                    message.malformed,
                    "LostTalesThirdPersonEntityActionPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesThirdPersonEntityActionService.execute(
                                    livePlayer, expectedDimension,
                                    action, entityId,
                                    hitX, hitY, hitZ,
                                    useItemIfInteractionDeclines,
                                    expectedHotbarSlot,
                                    expectedHeldItem);
                        }
                    });
            return null;
        }
    }
}
