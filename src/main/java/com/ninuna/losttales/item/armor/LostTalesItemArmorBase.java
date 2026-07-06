package com.ninuna.losttales.item.armor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;

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
    public void addInformation(ItemStack itemStack, EntityPlayer player, List list, boolean advancedTooltips) {
        LostTalesUtil.addItemInformation(list, itemStack, this.material, this.credits, player, this.itemType);
    }

    public ELostTalesItem.Type getItemType() {
        return itemType;
    }

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack itemStack) {
        ELostTalesItem.Type helmetType = null;
        ELostTalesItem.Type armorType = null;
        ELostTalesItem.Type leggingsType = null;
        ELostTalesItem.Type bootsType = null;

        ItemStack helmet = player.getCurrentArmor(3);
        ItemStack armor = player.getCurrentArmor(2);
        ItemStack leggings = player.getCurrentArmor(1);
        ItemStack boots = player.getCurrentArmor(0);

        if (helmet != null && helmet.getItem() instanceof LostTalesItemArmorBase) {
            helmetType = ((LostTalesItemArmorBase) helmet.getItem()).getItemType();
        }

        if (armor != null && armor.getItem() instanceof LostTalesItemArmorBase) {
            armorType = ((LostTalesItemArmorBase) armor.getItem()).getItemType();
        }

        if (leggings != null && leggings.getItem() instanceof LostTalesItemArmorBase) {
            leggingsType = ((LostTalesItemArmorBase) leggings.getItem()).getItemType();
        }

        if (boots != null && boots.getItem() instanceof LostTalesItemArmorBase) {
            bootsType = ((LostTalesItemArmorBase) boots.getItem()).getItemType();
        }

        if (helmetType != null && armorType != null && leggingsType != null && bootsType != null) {
            // Player is wearing full light armor set.
            if (helmetType == ELostTalesItem.Type.ARMOR_LIGHT && armorType == ELostTalesItem.Type.ARMOR_LIGHT && leggingsType == ELostTalesItem.Type.ARMOR_LIGHT && bootsType == ELostTalesItem.Type.ARMOR_LIGHT) {
                //Todo...
                System.out.println("Light Set!");
            }
            // Player is wearing full heavy armor set.
            else if (helmetType == ELostTalesItem.Type.ARMOR_HEAVY && armorType == ELostTalesItem.Type.ARMOR_HEAVY && leggingsType == ELostTalesItem.Type.ARMOR_HEAVY && bootsType == ELostTalesItem.Type.ARMOR_HEAVY) {
                //Todo...
                System.out.println("Heavy Set!");
            }
            // Player is wearing a mix of heavy and light armor.
            else {
                //Todo...
                System.out.println("Medium Set!");
            }
        }
    }
}