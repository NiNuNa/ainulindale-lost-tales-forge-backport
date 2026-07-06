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
    private static final int MAX_ITEM_SLOTS = 512;
    private int x;
    private int y;
    private int z;
    private String title;
    private boolean sealed;
    private ItemStack[] items;

    public LostTalesQuickLootContainerSyncPacket() {}

    public LostTalesQuickLootContainerSyncPacket(int x, int y, int z, String title, boolean sealed, ItemStack[] items) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.title = title;
        this.sealed = sealed;
        this.items = copyItems(items, MAX_ITEM_SLOTS);
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
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.title = ByteBufUtils.readUTF8String(buf);
        this.sealed = buf.readBoolean();
        int size = buf.readUnsignedShort();
        int storedSize = Math.min(size, MAX_ITEM_SLOTS);
        this.items = new ItemStack[storedSize];
        for (int i = 0; i < size; i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buf);
            if (i < storedSize) {
                this.items[i] = stack;
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        ByteBufUtils.writeUTF8String(buf, this.title == null ? "Container" : this.title);
        buf.writeBoolean(this.sealed);
        int size = this.items == null ? 0 : Math.min(this.items.length, MAX_ITEM_SLOTS);
        buf.writeShort(size);
        if (this.items != null) {
            for (int i = 0; i < size; i++) {
                ByteBufUtils.writeItemStack(buf, this.items[i]);
            }
        }
    }

    public int getX() { return this.x; }

    public int getY() { return this.y; }

    public int getZ() { return this.z; }

    public String getTitle() { return this.title; }

    public boolean isSealed() { return this.sealed; }

    public ItemStack[] getItems() { return copyItems(this.items, MAX_ITEM_SLOTS); }

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
        public IMessage onMessage(LostTalesQuickLootContainerSyncPacket message, MessageContext ctx) {
            if (LostTalesMod.proxy != null) {
                LostTalesMod.proxy.handleQuickLootContainerSync(message);
            }
            return null;
        }
    }
}

