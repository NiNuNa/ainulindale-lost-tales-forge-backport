package com.ninuna.losttales.item.armor;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.renderers.geo.GeoArmorRenderer;

public class LostTalesItemArmor3D extends LostTalesItemArmorBase implements IAnimatable {
    private final AnimationFactory factory = new AnimationFactory(this);

    public LostTalesItemArmor3D(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int slotType) {
        super(material, itemType, slotType, null);
    }

    public LostTalesItemArmor3D(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, int slotType, String credits) {
        super(material, itemType, slotType, null, credits);
    }

    @Override
    public void registerControllers(AnimationData data) {

    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack, int armorSlot) {
        Class<? extends ItemArmor> clazz = this.getClass();
        GeoArmorRenderer renderer = GeoArmorRenderer.getRenderer(clazz);
        renderer.setCurrentItem(entityLiving, itemStack, armorSlot);
        renderer.applyEntityStats(entityLiving).applySlot(armorSlot);
        return renderer;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String getArmorTexture(ItemStack itemstack, Entity entity, int slot, String type) {
        Class<? extends ItemArmor> clazz = this.getClass();
        GeoArmorRenderer renderer = GeoArmorRenderer.getRenderer(clazz);
        return renderer.getTextureLocation((ItemArmor)itemstack.getItem()).toString();
    }
}