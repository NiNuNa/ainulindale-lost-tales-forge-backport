package com.ninuna.losttales.item.armor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

public class LostTalesItemArmorBase extends ItemArmor {
    private final String armorTextureName;
    private final String credits;
    private final ELostTalesItemMaterial material;
    private final ELostTalesItem.Type itemType;

    private AttributeModifier knockBack;
    private AttributeModifier moveSpeed;

    public LostTalesItemArmorBase(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int slotType, String armorTextureName, String credits) {
        super((itemType.equals(ELostTalesItem.Type.ARMOR_LIGHT)) ? material.getMaterial().getLightArmorMaterial() : material.getMaterial().getHeavyArmorMaterial(), 0, slotType);
        this.material = material;
        this.itemType = itemType;
        this.armorTextureName = armorTextureName;
        this.credits = credits;
        this.setAttributeModifier();
    }

    public LostTalesItemArmorBase(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int slotType, String armorTextureName) {
        super((itemType.equals(ELostTalesItem.Type.ARMOR_LIGHT)) ? material.getMaterial().getLightArmorMaterial() : material.getMaterial().getHeavyArmorMaterial(), 0, slotType);
        this.material = material;
        this.itemType = itemType;
        this.armorTextureName = armorTextureName;
        this.credits = null;
        this.setAttributeModifier();
    }

    private void setAttributeModifier() {
        if (this.itemType == ELostTalesItem.Type.ARMOR_HEAVY) {
            if (this.armorType == 0 || this.armorType == 3) {
                this.knockBack = new AttributeModifier("Knockback Modifier", 0.05, 1);
                this.moveSpeed = new AttributeModifier("Movespeed Modifier", -0.025, 1);
            } else {
                this.knockBack = new AttributeModifier("Knockback Modifier", 0.075, 1);
                this.moveSpeed = new AttributeModifier("Movespeed Modifier", -0.05, 1);
            }
        }
    }

    @Override
    public Multimap getItemAttributeModifiers() {
        Multimap multimap = HashMultimap.create();
        if (this.itemType == ELostTalesItem.Type.ARMOR_HEAVY) {
            multimap.put(SharedMonsterAttributes.knockbackResistance.getAttributeUnlocalizedName(), this.knockBack);
            multimap.put(SharedMonsterAttributes.movementSpeed.getAttributeUnlocalizedName(), this.moveSpeed);
        }
        return multimap;
    }

    @Override
    public String getArmorTexture(ItemStack itemstack, Entity entity, int slot, String type) {
        if (this.armorType == 2){
            if (itemstack.getUnlocalizedName().startsWith("item.community")) {
                return LostTalesMetaData.MOD_ID + ":textures/armor/community/" + armorTextureName + "_layer_2.png";
            } else {
                return LostTalesMetaData.MOD_ID + ":textures/armor/" + armorTextureName + "_layer_2.png";
            }
        } else {
            if (itemstack.getUnlocalizedName().startsWith("item.community")) {
                return LostTalesMetaData.MOD_ID + ":textures/armor/community/" + armorTextureName + "_layer_1.png";
            } else {
                return LostTalesMetaData.MOD_ID + ":textures/armor/" + armorTextureName + "_layer_1.png";
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesClientUtil.addItemInformation(list, itemStack, this.material, this.credits, player, this.itemType);
    }

    public ELostTalesItem.Type getItemType() {
        return itemType;
    }

}