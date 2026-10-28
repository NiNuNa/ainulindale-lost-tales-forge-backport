package com.ninuna.losttales.character.state;

import com.ninuna.losttales.accessory.player.AccessoryPlayerData;
import com.ninuna.losttales.character.state.component.AccessoryStateComponent;
import com.ninuna.losttales.character.state.component.VanillaEnderChestStateComponent;
import com.ninuna.losttales.character.state.component.VanillaInventoryStateComponent;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a saved life holds that the game no longer takes, taken out as the
 * life is read (CHAR-46): a stack of an item the game no longer has, or
 * one past its item's stack size, in the inventory, the ender chest or the
 * ring slot. Each goes into the list handed in, to be set aside in the
 * player-state quarantine; the rest of the life is kept as it was. A worn
 * ring in a stack of more than one goes among the rings kept aside, which
 * hand it back to the inventory. Shapes the components themselves refuse
 * (a missing slot, a duplicate one) are left for them to refuse.
 */
public final class SavedLifeItems {

    /** The item is not in the game. */
    public static final String WHY_UNKNOWN = "unknown_item";
    /** The stack holds more than its item stacks to, or nothing. */
    public static final String WHY_OVERSTACKED = "overstacked";

    public static final String TAG_CHARACTER = "CharacterUUID";
    public static final String TAG_GENERATION = "Generation";
    public static final String TAG_COMPONENT = "Component";
    public static final String TAG_WHY = "Why";
    public static final String TAG_ITEM = "Item";

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_EQUIPPED = "Equipped";
    private static final String TAG_REJECTED = "Rejected";

    /** Whether the game takes one saved stack as it is. */
    public interface Check {
        /** Null when the game takes the stack, else why not ({@link #WHY_UNKNOWN}, {@link #WHY_OVERSTACKED}). */
        String refusal(NBTTagCompound itemTag);
    }

    /** The game's own answer: the item is in the game and the stack fits its size. */
    public static final Check GAME = new Check() {
        @Override
        public String refusal(NBTTagCompound itemTag) {
            ItemStack stack;
            try {
                stack = ItemStack.loadItemStackFromNBT(itemTag);
            } catch (RuntimeException unreadable) {
                return WHY_UNKNOWN;
            }
            if (stack == null || stack.getItem() == null) {
                return WHY_UNKNOWN;
            }
            if (stack.stackSize <= 0
                    || stack.stackSize > Math.max(1, stack.getMaxStackSize())) {
                return WHY_OVERSTACKED;
            }
            return null;
        }
    };

    private SavedLifeItems() {}

    /**
     * The record with both its generations cleaned; the same record when
     * neither holds anything the game does not take.
     */
    public static CharacterPlayerStateRecord clean(CharacterPlayerStateRecord record,
                                                   Check check,
                                                   List<NBTTagCompound> setAside) {
        CharacterPlayerStateSnapshot current = clean(record.getCurrent(), check, setAside);
        CharacterPlayerStateSnapshot previous = record.getPrevious() == null
                ? null : clean(record.getPrevious(), check, setAside);
        if (current == record.getCurrent() && previous == record.getPrevious()) {
            return record;
        }
        return new CharacterPlayerStateRecord(record.getCharacterId(), current, previous);
    }

    /**
     * The snapshot without what the game does not take, each stack taken
     * out added to {@code setAside}; the same snapshot when it holds none.
     */
    public static CharacterPlayerStateSnapshot clean(CharacterPlayerStateSnapshot snapshot,
                                                     Check check,
                                                     List<NBTTagCompound> setAside) {
        if (snapshot == null) {
            return null;
        }
        Map<String, NBTTagCompound> components = snapshot.copyComponents();
        List<NBTTagCompound> taken = new ArrayList<NBTTagCompound>();
        boolean changed = cleanItems(components.get(VanillaInventoryStateComponent.ID),
                VanillaInventoryStateComponent.ID, check, taken);
        changed |= cleanItems(components.get(VanillaEnderChestStateComponent.ID),
                VanillaEnderChestStateComponent.ID, check, taken);
        changed |= cleanRing(components.get(AccessoryStateComponent.ID), check, taken);
        if (!changed) {
            return snapshot;
        }
        for (NBTTagCompound item : taken) {
            NbtTags.writeUuid(item, TAG_CHARACTER, snapshot.getCharacterId());
            item.setLong(TAG_GENERATION, snapshot.getGeneration());
            setAside.add(item);
        }
        return new CharacterPlayerStateSnapshot(snapshot.getCharacterId(),
                snapshot.getGeneration(), snapshot.getCapturedAt(),
                snapshot.getDataVersion(), new LinkedHashMap<String, NBTTagCompound>(components));
    }

    /** Takes the stacks the game does not take out of a component's item list. */
    private static boolean cleanItems(NBTTagCompound component, String componentId,
                                      Check check, List<NBTTagCompound> taken) {
        if (component == null || !component.hasKey(TAG_ITEMS, Constants.NBT.TAG_LIST)) {
            return false;
        }
        NBTTagList items = component.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        if (items.tagCount() == 0 || items.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            return false;
        }
        NBTTagList kept = new NBTTagList();
        boolean changed = false;
        for (int index = 0; index < items.tagCount(); index++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(index);
            String why = check.refusal(itemTag);
            if (why == null) {
                kept.appendTag(itemTag.copy());
            } else {
                taken.add(entry(componentId, why, itemTag));
                changed = true;
            }
        }
        if (changed) {
            component.setTag(TAG_ITEMS, kept);
        }
        return changed;
    }

    /**
     * The worn ring: one the game does not take is taken out; a stack of
     * more than one goes among the rings kept aside while they have room
     * under their safety bound, else it is taken out too.
     */
    private static boolean cleanRing(NBTTagCompound component, Check check,
                                     List<NBTTagCompound> taken) {
        if (component == null || !component.hasKey(TAG_EQUIPPED, Constants.NBT.TAG_COMPOUND)) {
            return false;
        }
        NBTTagCompound worn = component.getCompoundTag(TAG_EQUIPPED);
        String why = check.refusal(worn);
        if (why != null) {
            taken.add(entry(AccessoryStateComponent.ID, why, worn));
            component.removeTag(TAG_EQUIPPED);
            return true;
        }
        if (worn.getByte("Count") == 1) {
            return false;
        }
        NBTTagList rejected = component.hasKey(TAG_REJECTED, Constants.NBT.TAG_LIST)
                ? component.getTagList(TAG_REJECTED, Constants.NBT.TAG_COMPOUND)
                : new NBTTagList();
        if (rejected.tagCount() < AccessoryPlayerData.MAX_KEPT_ENTRIES) {
            rejected.appendTag(worn.copy());
            component.setTag(TAG_REJECTED, rejected);
        } else {
            taken.add(entry(AccessoryStateComponent.ID, WHY_OVERSTACKED, worn));
        }
        component.removeTag(TAG_EQUIPPED);
        return true;
    }

    private static NBTTagCompound entry(String componentId, String why,
                                        NBTTagCompound itemTag) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_COMPONENT, componentId);
        entry.setString(TAG_WHY, why);
        entry.setTag(TAG_ITEM, itemTag.copy());
        return entry;
    }
}
