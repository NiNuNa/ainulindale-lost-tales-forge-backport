package com.ninuna.losttales.item.weapon;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;

public class LostTalesItemPike extends LostTalesItemPoleArm{

    public LostTalesItemPike(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        super(material, itemType);
    }

    public LostTalesItemPike(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, String credits) {
        super(material, itemType, credits);
    }
}