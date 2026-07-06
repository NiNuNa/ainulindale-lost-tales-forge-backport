package com.ninuna.losttales.item.material;

import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.EnumHelper;

public class LostTalesItemMaterialBase {

    private final String materialName;

    private int durability;
    private int harvestLevel;
    private float efficiency;
    private float damage;
    private int enchantability;
    private int[] protection;

    private Item.ToolMaterial toolMaterial;
    private ItemArmor.ArmorMaterial lightArmorMaterial;
    private ItemArmor.ArmorMaterial heavyArmorMaterial;

    private ItemStack repairItem;

    public LostTalesItemMaterialBase(String materialName, int durability, int harvestLevel, float efficiency, float damage, int enchantability, int[] protection, ItemStack repairItem) {
        this.materialName = "MATERIAL_" + materialName;
        this.durability = durability;
        this.harvestLevel = harvestLevel;
        this.efficiency = efficiency;
        this.damage = damage;
        this.enchantability = enchantability;
        this.protection = protection;
        this.repairItem = repairItem;

        this.toolMaterial = EnumHelper.addToolMaterial(this.materialName, this.harvestLevel, this.durability, this.efficiency, this.damage, this.enchantability);
        this.lightArmorMaterial = EnumHelper.addArmorMaterial(this.materialName, Math.round((float)this.durability * 0.06F), this.protection, this.enchantability);
        this.heavyArmorMaterial = EnumHelper.addArmorMaterial(this.materialName, Math.round((float)this.durability * 0.06F), new int[]{this.protection[0] + 1 , this.protection[1] + 2, this.protection[2] + 2, this.protection[3] + 1}, this.enchantability);

        this.getToolMaterial().setRepairItem(this.repairItem);
        this.getLightArmorMaterial().customCraftingMaterial = this.repairItem.getItem();
        this.getHeavyArmorMaterial().customCraftingMaterial = this.repairItem.getItem();
    }

    public Item.ToolMaterial getToolMaterial() {
        return toolMaterial;
    }
    
    public ItemArmor.ArmorMaterial getLightArmorMaterial() {
        return lightArmorMaterial;
    }

    public ItemArmor.ArmorMaterial getHeavyArmorMaterial() {
        return heavyArmorMaterial;
    }

    public ItemStack getRepairItem() {
        return repairItem;
    }
}