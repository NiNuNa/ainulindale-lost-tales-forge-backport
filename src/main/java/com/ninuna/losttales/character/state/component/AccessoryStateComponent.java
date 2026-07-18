package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.effect.AccessoryEffectService;
import com.ninuna.losttales.accessory.AccessorySlotType;
import com.ninuna.losttales.accessory.player.AccessoryInventory;
import com.ninuna.losttales.accessory.player.AccessoryInventorySyncManager;
import com.ninuna.losttales.accessory.player.AccessoryPlayerData;
import com.ninuna.losttales.character.state.CharacterStateApplyPhase;
import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/** The character-owned ring slot plus any loss-prevention quarantine entry. */
public final class AccessoryStateComponent implements CharacterStateComponent {

    public static final String ID = "losttales_accessory";
    private static final int VERSION = 1;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_EQUIPPED = "Equipped";
    private static final String TAG_REJECTED = "Rejected";

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
        AccessoryPlayerData data = requireData(player);
        NBTTagCompound state = createDefault();
        ItemStack equipped = data.getInventory().getStackInSlot(
                AccessoryInventory.RING_SLOT);
        if (equipped != null) {
            NBTTagCompound encoded = new NBTTagCompound();
            equipped.writeToNBT(encoded);
            state.setTag(TAG_EQUIPPED, encoded);
        }
        List<NBTTagCompound> rejectedEntries = data.getRejectedEntries();
        if (!rejectedEntries.isEmpty()) {
            NBTTagList rejected = new NBTTagList();
            for (NBTTagCompound entry : rejectedEntries) {
                rejected.appendTag(entry.copy());
            }
            state.setTag(TAG_REJECTED, rejected);
        }
        validate(state);
        return state;
    }

    @Override
    public NBTTagCompound createDefault() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger(TAG_VERSION, VERSION);
        return state;
    }

    @Override
    public void validate(NBTTagCompound state)
            throws CharacterStateValidationException {
        if (state == null || !state.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)) {
            throw new CharacterStateValidationException(
                    "Accessory component version is missing");
        }
        if (state.getInteger(TAG_VERSION) != VERSION) {
            throw new CharacterStateValidationException(
                    "Unsupported accessory component version "
                            + state.getInteger(TAG_VERSION));
        }
        if (state.hasKey(TAG_EQUIPPED)
                && !state.hasKey(TAG_EQUIPPED, Constants.NBT.TAG_COMPOUND)) {
            throw new CharacterStateValidationException(
                    "Accessory equipped entry has an invalid type");
        }
        if (state.hasKey(TAG_REJECTED)
                && !state.hasKey(TAG_REJECTED, Constants.NBT.TAG_LIST)) {
            throw new CharacterStateValidationException(
                    "Accessory rejected entry has an invalid type");
        }
        if (state.hasKey(TAG_REJECTED, Constants.NBT.TAG_LIST)) {
            NBTTagList rejected = state.getTagList(
                    TAG_REJECTED, Constants.NBT.TAG_COMPOUND);
            if (rejected.tagCount() > 0
                    && rejected.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
                throw new CharacterStateValidationException(
                        "Accessory rejected list has an invalid element type");
            }
            if (rejected.tagCount()
                    > AccessoryPlayerData.MAX_REJECTED_ENTRIES) {
                throw new CharacterStateValidationException(
                        "Accessory rejected list contains too many entries");
            }
        }
        if (state.hasKey(TAG_EQUIPPED, Constants.NBT.TAG_COMPOUND)) {
            ItemStack equipped = decode(
                    state.getCompoundTag(TAG_EQUIPPED), "equipped");
            if (equipped.stackSize != 1
                    || !AccessoryCompatibilityRegistry.getInstance()
                    .isCompatible(AccessorySlotType.RING, equipped)) {
                throw new CharacterStateValidationException(
                        "Accessory equipped entry is incompatible or overstacked");
            }
        }
    }

    @Override
    public void apply(EntityPlayerMP player, NBTTagCompound state)
            throws CharacterStateValidationException {
        validate(state);
        AccessoryPlayerData data = requireData(player);
        ItemStack equipped = state.hasKey(
                TAG_EQUIPPED, Constants.NBT.TAG_COMPOUND)
                ? decode(state.getCompoundTag(TAG_EQUIPPED), "equipped")
                : null;
        ArrayList<NBTTagCompound> rejectedEntries =
                new ArrayList<NBTTagCompound>();
        if (state.hasKey(TAG_REJECTED, Constants.NBT.TAG_LIST)) {
            NBTTagList rejected = state.getTagList(
                    TAG_REJECTED, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < rejected.tagCount(); index++) {
                rejectedEntries.add((NBTTagCompound)
                        rejected.getCompoundTagAt(index).copy());
            }
        }
        try {
            data.replaceState(equipped, rejectedEntries);
        } catch (IllegalArgumentException exception) {
            throw new CharacterStateValidationException(
                    "Accessory state cannot be applied safely", exception);
        }
    }

    @Override
    public void synchronize(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        player.inventoryContainer.detectAndSendChanges();
        player.sendContainerToPlayer(player.inventoryContainer);
        AccessoryInventorySyncManager.send(player);
        AccessoryEffectService.refresh(player);
    }

    private static AccessoryPlayerData requireData(EntityPlayerMP player)
            throws CharacterStateValidationException {
        AccessoryPlayerData data = AccessoryPlayerData.getOrCreate(player);
        if (data == null || data.getInventory() == null) {
            throw new CharacterStateValidationException(
                    "Player accessory inventory is unavailable");
        }
        return data;
    }

    private static ItemStack decode(NBTTagCompound encoded, String label)
            throws CharacterStateValidationException {
        try {
            ItemStack stack = ItemStack.loadItemStackFromNBT(encoded);
            if (stack == null) {
                throw new CharacterStateValidationException(
                        "Accessory " + label + " entry is unknown");
            }
            return stack;
        } catch (CharacterStateValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CharacterStateValidationException(
                    "Accessory " + label + " entry could not be decoded",
                    exception);
        }
    }
}
