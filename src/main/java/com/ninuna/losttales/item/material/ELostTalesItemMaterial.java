package com.ninuna.losttales.item.material;

import com.ninuna.losttales.faction.ELostTalesFaction;
import lotr.common.LOTRMod;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public enum ELostTalesItemMaterial {
    ARNOR(new LostTalesItemMaterialBase("ARNOR", 450, 2, 6.0F, 2.5F, 10, new int[]{2, 4, 3, 1}, new ItemStack(Items.iron_ingot))),
    LOSSOTH(new LostTalesItemMaterialBase("LOSSOTH", 625, 2, 6.0F, 1.5F, 15, new int[]{2, 3, 2, 2}, new ItemStack(Items.iron_ingot))),
    MORIA_GOBLINS(new LostTalesItemMaterialBase("MORIA_GOBLINS", 350, 2, 5.5F, 2.5F, 6, new int[]{2, 4, 2, 2}, new ItemStack(LOTRMod.orcSteel))),
    OROCARNI(new LostTalesItemMaterialBase("OROCARNI", 675, 3, 6.5F, 3.0F, 9, new int[]{2, 4, 3, 2}, new ItemStack(LOTRMod.bronze))),
    LOTHLORIEN(new LostTalesItemMaterialBase("LOTHLORIEN", 675, 3, 6.5F, 3.0F, 9, new int[]{2, 4, 3, 2}, new ItemStack(LOTRMod.elfSteel))),
    NEUTRAL(new LostTalesItemMaterialBase("NEUTRAL", 675, 3, 6.5F, 3.0F, 9, new int[]{2, 4, 3, 2}, new ItemStack(LOTRMod.bronze))),
    THARBAD(new LostTalesItemMaterialBase("THARBAD", 675, 3, 6.5F, 3.0F, 9, new int[]{2, 4, 3, 2}, new ItemStack(LOTRMod.bronze))),
    MOON_ELVES(new LostTalesItemMaterialBase("MOON_ELVES", 675, 3, 6.5F, 3.0F, 9, new int[]{2, 4, 3, 2}, new ItemStack(LOTRMod.elfSteel))),
    SUN_ELVES(new LostTalesItemMaterialBase("SUN_ELVES", 675, 3, 6.5F, 3.0F, 9, new int[]{2, 4, 3, 2}, new ItemStack(LOTRMod.elfSteel))),
    BLUE_GOBLINS(new LostTalesItemMaterialBase("BLUE_GOBLINS", 350, 2, 5.5F, 2.5F, 6, new int[]{2, 4, 2, 2}, new ItemStack(LOTRMod.orcSteel)));

    private final LostTalesItemMaterialBase material;
    private final ELostTalesFaction faction;

    ELostTalesItemMaterial(LostTalesItemMaterialBase material) {
        this.material = material;
        this.faction = ELostTalesFaction.valueOf(this.name());
    }

    public LostTalesItemMaterialBase getMaterial() {
        return material;
    }

    public ELostTalesFaction getFaction() {
        return faction;
    }

    public enum BlockMaterial {
        ;

        private final Material material;

        BlockMaterial(Material material) {
            this.material = material;
        }
        
        public Material getMaterial() {
            return material;
        }
    }
}