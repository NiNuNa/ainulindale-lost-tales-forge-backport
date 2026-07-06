package com.ninuna.losttales.item.weapon;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

public class LostTalesItemWarhammer extends LostTalesItemSword implements IAnimatable {
    private final AnimationFactory factory = new AnimationFactory(this);

    public LostTalesItemWarhammer(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        super(material, itemType);
        this.lotrWeaponDamage += 2.0F;
    }

    public LostTalesItemWarhammer(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, String credits) {
        super(material, itemType, credits);
        this.lotrWeaponDamage += 2.0F;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack itemstack) {
        return EnumAction.none;
    }

    @Override
    public void registerControllers(AnimationData data) {
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }
}