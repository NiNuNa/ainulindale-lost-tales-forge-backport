package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.AccessorySlotType;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;

/** Owner-only server snapshot for the custom player-container slot. */
public final class AccessoryInventorySyncPacket implements IMessage {

    private static final int PROTOCOL_VERSION = 1;
    private static final int MAXIMUM_PACKET_BYTES = 32768;

    private long revision;
    private ItemStack equipped;
    private boolean rejectedEntryPresent;
    private boolean malformed;

    public AccessoryInventorySyncPacket() {}

    public AccessoryInventorySyncPacket(
            long revision, ItemStack equipped, boolean rejectedEntryPresent) {
        if (revision < 0L || !isValidStack(equipped)) {
            throw new IllegalArgumentException("invalid accessory snapshot");
        }
        this.revision = revision;
        this.equipped = equipped == null ? null : equipped.copy();
        this.rejectedEntryPresent = rejectedEntryPresent;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() < 11
                    || buffer.readableBytes() > MAXIMUM_PACKET_BYTES
                    || buffer.readUnsignedByte() != PROTOCOL_VERSION) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid accessory packet framing");
            }
            this.revision = buffer.readLong();
            this.rejectedEntryPresent = buffer.readBoolean();
            this.equipped = ByteBufUtils.readItemStack(buffer);
            if (buffer.readableBytes() != 0 || this.revision < 0L
                    || !isValidStack(this.equipped)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid accessory packet state");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.revision = -1L;
            this.equipped = null;
            this.rejectedEntryPresent = false;
            if (buffer != null && buffer.readableBytes() > 0) {
                buffer.skipBytes(buffer.readableBytes());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(PROTOCOL_VERSION);
        buffer.writeLong(this.revision);
        buffer.writeBoolean(this.rejectedEntryPresent);
        ByteBufUtils.writeItemStack(buffer, this.equipped);
    }

    public long getRevision() {
        return this.revision;
    }

    public ItemStack getEquipped() {
        return this.equipped == null ? null : this.equipped.copy();
    }

    public boolean hasRejectedEntry() {
        return this.rejectedEntryPresent;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private static boolean isValidStack(ItemStack stack) {
        return stack == null || stack.stackSize == 1
                && AccessoryCompatibilityRegistry.getInstance().isCompatible(
                AccessorySlotType.RING, stack);
    }

    /** Common-safe handler; all client access remains behind the sided proxy. */
    public static final class Handler implements IMessageHandler<
            AccessoryInventorySyncPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final AccessoryInventorySyncPacket message,
                MessageContext context) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleAccessoryInventorySync(
                                    message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
