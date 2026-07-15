package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/** The player's 27-slot ender chest, isolated per roleplaying character. */
public final class VanillaEnderChestStateComponent implements CharacterStateComponent {

    public static final String ID = "vanilla_ender_chest";

    private static final int VERSION = 1;
    private static final int SLOT_COUNT = 27;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_ITEMS = "Items";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public CharacterStateApplyPhase getApplyPhase() {
        return CharacterStateApplyPhase.BEFORE_ATTRIBUTES;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        InventoryEnderChest inventory = requireInventory(player);
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_ITEMS, inventory.saveInventoryToNBT());
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_ITEMS, new NBTTagList());
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException(
                    "Ender-chest component version is missing");
        }
        if (state.getInteger(TAG_VERSION) != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported ender-chest component version "
                            + state.getInteger(TAG_VERSION));
        }
        if (!state.hasKey(TAG_ITEMS, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException(
                    "Ender-chest item list is missing");
        }
        NBTTagList items = state.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        if (items.tagCount() > 0
                && items.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new CharacterStateValidationException(
                    "Ender-chest item list has an invalid element type");
        }
        if (items.tagCount() > SLOT_COUNT) {
            throw new CharacterStateValidationException(
                    "Ender chest contains too many serialized slots");
        }

        boolean[] occupied = new boolean[SLOT_COUNT];
        for (int index = 0; index < items.tagCount(); index++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(index);
            if (!itemTag.hasKey("Slot", Constants.NBT.TAG_BYTE)) {
                throw new CharacterStateValidationException(
                        "Ender-chest entry " + index + " has no slot");
            }
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < 0 || slot >= SLOT_COUNT) {
                throw new CharacterStateValidationException(
                        "Ender-chest entry uses unsupported slot " + slot);
            }
            if (occupied[slot]) {
                throw new CharacterStateValidationException(
                        "Ender chest contains duplicate slot " + slot);
            }
            occupied[slot] = true;
            try {
                ItemStack stack = ItemStack.loadItemStackFromNBT(itemTag);
                if (stack == null || stack.stackSize <= 0
                        || stack.stackSize > Math.max(1, stack.getMaxStackSize())) {
                    throw new CharacterStateValidationException(
                            "Ender-chest entry " + index
                                    + " is invalid or overstacked");
                }
            } catch (CharacterStateValidationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new CharacterStateValidationException(
                        "Ender-chest entry " + index + " could not be decoded",
                        exception);
            }
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        InventoryEnderChest inventory = requireInventory(player);
        validate(state);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setInventorySlotContents(slot, null);
        }
        inventory.loadInventoryFromNBT(state.getTagList(
                TAG_ITEMS, Constants.NBT.TAG_COMPOUND));
        inventory.markDirty();
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        if (player != null && player.getInventoryEnderChest() != null) {
            player.getInventoryEnderChest().markDirty();
        }
    }

    private static InventoryEnderChest requireInventory(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.getInventoryEnderChest() == null) {
            throw new CharacterStateValidationException(
                    "Player ender chest is unavailable");
        }
        return player.getInventoryEnderChest();
    }
}
