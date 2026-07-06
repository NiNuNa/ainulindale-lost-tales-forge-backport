package com.ninuna.losttales.item.weapon;

import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;

public class LostTalesItemLongSword extends LostTalesItemSword{

    public LostTalesItemLongSword(ELostTalesItemMaterial material, ELostTalesItem.Type itemType) {
        super(material, itemType);
    }

    public LostTalesItemLongSword(ELostTalesItemMaterial material, ELostTalesItem.Type itemType, String credits) {
        super(material, itemType, credits);
    }
}
