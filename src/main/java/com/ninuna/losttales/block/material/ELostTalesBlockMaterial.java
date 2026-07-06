package com.ninuna.losttales.block.material;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;

public enum ELostTalesBlockMaterial {
    CLAY(new Material(MapColor.clayColor)),
    CERAMIC(new Material(MapColor.diamondColor));

    private final Material material;

    ELostTalesBlockMaterial(Material material) {
        this.material = material;
    }

    public Material getMaterial() {
        return material;
    }
}