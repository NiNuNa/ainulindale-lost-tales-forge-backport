package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class LostTalesQuickLootContainerSyncPacket implements IMessage {
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
        this.items = items;
    }

    public static LostTalesQuickLootContainerSyncPacket fromInventory(int x, int y, int z, IInventory inventory) {
        ItemStack[] items = new ItemStack[inventory.getSizeInventory()];
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
        int size = buf.readShort();
        this.items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            this.items[i] = ByteBufUtils.readItemStack(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        ByteBufUtils.writeUTF8String(buf, this.title == null ? "Container" : this.title);
        buf.writeBoolean(this.sealed);
        buf.writeShort(this.items == null ? 0 : this.items.length);
        if (this.items != null) {
            for (ItemStack item : this.items) {
                ByteBufUtils.writeItemStack(buf, item);
            }
        }
    }

    public int getX() { return this.x; }

    public int getY() { return this.y; }

    public int getZ() { return this.z; }

    public String getTitle() { return this.title; }

    public boolean isSealed() { return this.sealed; }

    public ItemStack[] getItems() { return this.items; }

}
