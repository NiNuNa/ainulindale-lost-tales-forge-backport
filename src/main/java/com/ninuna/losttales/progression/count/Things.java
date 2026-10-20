package com.ninuna.losttales.progression.count;

import cpw.mods.fml.common.registry.GameData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Items, blocks and creatures as the skill files' tables name them. */
public final class Things {
    /** What every player is named in a table. */
    public static final String PLAYER = "player";

    private Things() {}

    public static Thing of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return Thing.NONE;
        }
        Object registered = GameData.getItemRegistry().getNameForObject(stack.getItem());
        String name = registered == null ? "" : registered.toString();
        List<String> keys = new ArrayList<String>();
        if (name.length() > 0) {
            keys.add(name + "@" + stack.getItemDamage());
            keys.add(name);
        }
        addOreNames(keys, stack);
        int food = stack.getItem() instanceof ItemFood
                ? ((ItemFood) stack.getItem()).func_150905_g(stack) : 0;
        return new Thing(keys, food, keys.isEmpty() ? "" : keys.get(0));
    }

    public static Thing of(Block block, int meta) {
        if (block == null) {
            return Thing.NONE;
        }
        Object registered = GameData.getBlockRegistry().getNameForObject(block);
        String name = registered == null ? "" : registered.toString();
        List<String> keys = new ArrayList<String>();
        if (name.length() > 0) {
            keys.add(name + "@" + meta);
            keys.add(name);
        }
        Item item = Item.getItemFromBlock(block);
        if (item != null) {
            addOreNames(keys, new ItemStack(item, 1, meta));
        }
        return new Thing(keys, 0, keys.isEmpty() ? "" : keys.get(0));
    }

    /** A creature by its entity name ({@code lotr.Troll}); every player is {@link #PLAYER}. */
    public static Thing of(Entity entity) {
        return Thing.named(nameOf(entity));
    }

    public static String nameOf(Entity entity) {
        if (entity == null) {
            return "";
        }
        if (entity instanceof EntityPlayer) {
            return PLAYER;
        }
        String name = EntityList.getEntityString(entity);
        return name == null ? entity.getClass().getSimpleName() : name;
    }

    private static void addOreNames(List<String> keys, ItemStack stack) {
        for (int id : OreDictionary.getOreIDs(stack)) {
            keys.add("ore:" + OreDictionary.getOreName(id));
        }
    }
}
