package com.ninuna.losttales.inventory.container;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Chest-style server container for the missive board inventory.
 *
 * Missives are now normal board notices: players may take a letter, read it,
 * accept it from the letter screen, or place it back on the board if they do
 * not want the work. Quest acceptance is still handled by a dedicated
 * server-validated packet and never by trusting a client-side GUI action.
 */
public class LostTalesContainerMissiveBoard extends Container {
    public static final int BOARD_SLOT_COUNT = LostTalesTileEntityMissiveBoard.INVENTORY_SIZE;

    private final LostTalesTileEntityMissiveBoard board;

    public LostTalesContainerMissiveBoard(IInventory playerInventory, LostTalesTileEntityMissiveBoard board) {
        this.board = board;
        board.openInventory();

        this.addBoardSlots(board);
        this.addPlayerInventorySlots(playerInventory);
    }

    private void addBoardSlots(LostTalesTileEntityMissiveBoard board) {
        int startX = 8;
        int y = 18;
        for (int slot = 0; slot < BOARD_SLOT_COUNT; slot++) {
            this.addSlotToContainer(new SlotMissiveBoard(board, slot, startX + slot * 18, y));
        }
    }

    private void addPlayerInventorySlots(IInventory playerInventory) {
        int playerInventoryY = 50;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlotToContainer(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, playerInventoryY + row * 18));
            }
        }

        int hotbarY = 108;
        for (int column = 0; column < 9; column++) {
            this.addSlotToContainer(new Slot(playerInventory, column, 8 + column * 18, hotbarY));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return this.board.isUseableByPlayer(player);
    }

    public LostTalesTileEntityMissiveBoard getBoard() {
        return this.board;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = null;
        Slot slot = index >= 0 && index < this.inventorySlots.size() ? (Slot) this.inventorySlots.get(index) : null;
        if (slot == null || !slot.getHasStack()) {
            return null;
        }

        ItemStack stack = slot.getStack();
        result = stack.copy();

        if (index < BOARD_SLOT_COUNT) {
            if (!this.mergeItemStack(stack, BOARD_SLOT_COUNT, this.inventorySlots.size(), true)) {
                return null;
            }
        } else {
            if (!this.mergeItemStack(stack, 0, BOARD_SLOT_COUNT, false)) {
                return null;
            }
        }

        if (stack.stackSize <= 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (stack.stackSize == result.stackSize) {
            return null;
        }
        slot.onPickupFromSlot(player, stack);
        return result;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        this.board.closeInventory();
    }

    private static class SlotMissiveBoard extends Slot {
        public SlotMissiveBoard(IInventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return this.inventory.isItemValidForSlot(this.getSlotIndex(), stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
