package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class LostTalesQuickLootContainerSyncPacket implements IMessage {
    static final int MAX_ITEM_SLOTS = 512;
    static final int MAX_TITLE_BYTES = 512;
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    private int x;
    private int y;
    private int z;
    private String title;
    private boolean sealed;
    private ItemStack[] items;
    private boolean malformed;

    public LostTalesQuickLootContainerSyncPacket() {}

    public LostTalesQuickLootContainerSyncPacket(int x, int y, int z, String title, boolean sealed, ItemStack[] items) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.title = title == null ? "Container" : title;
        this.sealed = sealed;
        this.items = copyItems(items, MAX_ITEM_SLOTS);
        validate();
    }

    public static LostTalesQuickLootContainerSyncPacket fromInventory(int x, int y, int z, IInventory inventory) {
        int size = Math.min(Math.max(0, inventory.getSizeInventory()), MAX_ITEM_SLOTS);
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < items.length; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            items[i] = stack == null ? null : stack.copy();
        }
        boolean sealed = inventory instanceof LostTalesTileEntityUrn && ((LostTalesTileEntityUrn) inventory).isSealed();
        return new LostTalesQuickLootContainerSyncPacket(x, y, z, inventory.getInventoryName(), sealed, items);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid quick-loot packet size");
            }
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.title = LostTalesPacketCodec.readUtf8String(
                    buf, MAX_TITLE_BYTES);
            this.sealed = buf.readBoolean();
            int size = buf.readUnsignedShort();
            if (size > MAX_ITEM_SLOTS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many quick-loot slots");
            }
            this.items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                this.items[i] = ByteBufUtils.readItemStack(buf);
            }
            LostTalesPacketCodec.requireFinished(buf);
            validate();
        } catch (RuntimeException exception) {
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.title = "";
            this.sealed = false;
            this.items = new ItemStack[0];
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        validate();
        int startIndex = buf.writerIndex();
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        LostTalesPacketCodec.writeUtf8String(
                buf, this.title, MAX_TITLE_BYTES);
        buf.writeBoolean(this.sealed);
        int size = this.items == null ? 0 : Math.min(this.items.length, MAX_ITEM_SLOTS);
        buf.writeShort(size);
        if (this.items != null) {
            for (int i = 0; i < size; i++) {
                ByteBufUtils.writeItemStack(buf, this.items[i]);
            }
        }
        if (buf.writerIndex() - startIndex > MAX_PACKET_BYTES) {
            throw new IllegalStateException(
                    "quick-loot snapshot exceeds packet limit");
        }
    }

    public int getX() { return this.x; }

    public int getY() { return this.y; }

    public int getZ() { return this.z; }

    public String getTitle() { return this.title; }

    public boolean isSealed() { return this.sealed; }

    public ItemStack[] getItems() { return copyItems(this.items, MAX_ITEM_SLOTS); }

    public boolean isMalformed() { return this.malformed; }

    private void validate() {
        if (!LostTalesPacketCodec.isValidBlockPosition(
                this.x, this.y, this.z)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                this.title, MAX_TITLE_BYTES)
                || this.items == null || this.items.length > MAX_ITEM_SLOTS) {
            throw new IllegalArgumentException("invalid quick-loot snapshot");
        }
        for (ItemStack stack : this.items) {
            if (stack != null && stack.stackSize <= 0) {
                throw new IllegalArgumentException(
                        "invalid quick-loot item stack");
            }
        }
    }

    private static ItemStack[] copyItems(ItemStack[] source, int maxItems) {
        if (source == null || source.length == 0) {
            return new ItemStack[0];
        }
        int size = Math.min(source.length, Math.max(0, maxItems));
        ItemStack[] copy = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            copy[i] = source[i] == null ? null : source[i].copy();
        }
        return copy;
    }

    /** Common-safe clientbound handler; real client work is delegated to the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesQuickLootContainerSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesQuickLootContainerSyncPacket message,
                MessageContext ctx) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleQuickLootContainerSync(
                                    message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
