package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraftforge.common.util.Constants;

/** The main inventory, the hotbar, the armour and the selected hotbar slot. */
public final class VanillaInventoryStateComponent implements CharacterStateComponent {

    public static final String ID = "vanilla_inventory";
    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_CURRENT_ITEM = "CurrentItem";
    private static final int MAX_SERIALIZED_SLOTS = 40;
    /** Where the armour stands among the saved slots: boots at 100, the helmet at 103. */
    private static final int FIRST_ARMOR_SLOT = 100;
    private static final int ARMOR_SLOTS = 4;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public CharacterStateApplyPhase getApplyPhase() {
        return CharacterStateApplyPhase.BEFORE_ATTRIBUTES;
    }

    @Override
    public NBTTagCompound capture(EntityPlayerMP player)
            throws CharacterStateValidationException {
        if (player == null || player.inventory == null) {
            throw new CharacterStateValidationException("Player inventory is unavailable");
        }
        if (player.inventory.getItemStack() != null) {
            throw new CharacterStateValidationException(
                    "Cannot capture inventory while an item is held by the cursor");
        }
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_ITEMS, player.inventory.writeToNBT(new NBTTagList()));
        state.setInteger(TAG_CURRENT_ITEM, player.inventory.currentItem);
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        state.setTag(TAG_ITEMS, new NBTTagList());
        state.setInteger(TAG_CURRENT_ITEM, 0);
        return state;
    }

    /**
     * A new character's inventory holding {@code stacks} in the main
     * inventory's slots from the first hotbar slot on (CHAR-40); a stack
     * past the main inventory's 36 slots is not written.
     */
    public NBTTagCompound createDefault(java.util.List<ItemStack> stacks) {
        NBTTagCompound state = createDefault();
        NBTTagList items = new NBTTagList();
        int slot = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || slot >= 36) {
                continue;
            }
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setByte("Slot", (byte) slot++);
            stack.writeToNBT(itemTag);
            items.appendTag(itemTag);
        }
        state.setTag(TAG_ITEMS, items);
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        requireVersion(state);
        if (!state.hasKey(TAG_ITEMS, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException("Inventory item list is missing");
        }
        NBTTagList items = state.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        if (items.tagCount() > 0
                && items.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new CharacterStateValidationException(
                    "Inventory item list has an invalid element type");
        }
        if (items.tagCount() > MAX_SERIALIZED_SLOTS) {
            throw new CharacterStateValidationException(
                    "Inventory contains too many serialized slots: " + items.tagCount());
        }
        boolean[] occupied = new boolean[104];
        for (int index = 0; index < items.tagCount(); index++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(index);
            if (!itemTag.hasKey("Slot", Constants.NBT.TAG_BYTE)) {
                throw new CharacterStateValidationException(
                        "Inventory entry " + index + " has no slot");
            }
            int serializedSlot = itemTag.getByte("Slot") & 255;
            boolean mainSlot = serializedSlot >= 0 && serializedSlot < 36;
            boolean armorSlot = serializedSlot >= 100 && serializedSlot < 104;
            if (!mainSlot && !armorSlot) {
                throw new CharacterStateValidationException(
                        "Inventory entry uses unsupported slot " + serializedSlot);
            }
            if (occupied[serializedSlot]) {
                throw new CharacterStateValidationException(
                        "Inventory contains duplicate slot " + serializedSlot);
            }
            occupied[serializedSlot] = true;
            try {
                ItemStack stack = ItemStack.loadItemStackFromNBT(itemTag);
                if (stack == null || stack.stackSize <= 0
                        || stack.stackSize > Math.max(1, stack.getMaxStackSize())) {
                    throw new CharacterStateValidationException(
                            "Inventory entry " + index + " is invalid or overstacked");
                }
            } catch (CharacterStateValidationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new CharacterStateValidationException(
                        "Inventory entry " + index + " could not be decoded", exception);
            }
        }
        int current = state.getInteger(TAG_CURRENT_ITEM);
        if (current < 0 || current > 8) {
            throw new CharacterStateValidationException(
                    "Selected hotbar slot is invalid: " + current);
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        if (player == null || player.inventory == null) {
            throw new CharacterStateValidationException("Player inventory is unavailable");
        }
        validate(state);
        clear(player.inventory.mainInventory);
        clear(player.inventory.armorInventory);
        player.inventory.setItemStack(null);
        player.inventory.readFromNBT(state.getTagList(
                TAG_ITEMS, Constants.NBT.TAG_COMPOUND));
        player.inventory.currentItem = state.getInteger(TAG_CURRENT_ITEM);
        player.inventory.markDirty();
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        player.sendContainerToPlayer(player.inventoryContainer);
        if (player.playerNetServerHandler != null) {
            player.playerNetServerHandler.sendPacket(
                    new S09PacketHeldItemChange(player.inventory.currentItem));
        }
    }

    /**
     * What a saved inventory wears and holds, for the stage (CHAR-42): the
     * four armour stacks, boots first, then the stack in the selected
     * hotbar slot; null where a slot is empty. The state is checked as a
     * switch checks it first, and the saved life is only read.
     */
    public static ItemStack[] wornOf(NBTTagCompound state)
            throws CharacterStateValidationException {
        new VanillaInventoryStateComponent().validate(state);
        ItemStack[] worn = new ItemStack[ARMOR_SLOTS + 1];
        int held = state.getInteger(TAG_CURRENT_ITEM);
        NBTTagList items = state.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < items.tagCount(); index++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(index);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= FIRST_ARMOR_SLOT && slot < FIRST_ARMOR_SLOT + ARMOR_SLOTS) {
                worn[slot - FIRST_ARMOR_SLOT] = ItemStack.loadItemStackFromNBT(itemTag);
            } else if (slot == held) {
                worn[ARMOR_SLOTS] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
        return worn;
    }

    private static void clear(ItemStack[] inventory) {
        if (inventory == null) {
            return;
        }
        for (int index = 0; index < inventory.length; index++) {
            inventory[index] = null;
        }
    }

    private static void requireVersion(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException("Inventory component version is missing");
        }
        int version = state.getInteger(TAG_VERSION);
        if (version != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported inventory component version " + version);
        }
    }
}
