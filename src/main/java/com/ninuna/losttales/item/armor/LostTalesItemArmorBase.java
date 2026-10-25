package com.ninuna.losttales.item.armor;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

/**
 * The mod's own armour. Its weight comes from the armour weight files as
 * every make's does, and so does its slowdown (CONTENT-06); the piece
 * itself changes no attribute.
 */
public class LostTalesItemArmorBase extends ItemArmor {
    private final String armorTextureName;
    private final ELostTalesItemMaterial material;
    private final ELostTalesItem.Type itemType;

    public LostTalesItemArmorBase(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int slotType, String armorTextureName) {
        super((itemType.equals(ELostTalesItem.Type.ARMOR_LIGHT)) ? material.getMaterial().getLightArmorMaterial() : material.getMaterial().getHeavyArmorMaterial(), 0, slotType);
        this.material = material;
        this.itemType = itemType;
        this.armorTextureName = armorTextureName;
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
        LostTalesClientUtil.addItemInformation(list, itemStack, this.material, null, player, this.itemType);
    }

    public ELostTalesItem.Type getItemType() {
        return itemType;
    }

}