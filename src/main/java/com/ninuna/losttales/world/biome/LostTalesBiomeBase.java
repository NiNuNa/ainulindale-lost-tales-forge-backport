package com.ninuna.losttales.world.biome;

import lotr.common.LOTRDimension;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.biome.LOTRMusicRegion;

public abstract class LostTalesBiomeBase extends LOTRBiome {

    public LostTalesBiomeBase(int i, boolean major) {
        super(i, major);
    }

    public LostTalesBiomeBase(int i, boolean major, LOTRDimension dim) {
        super(i, major, dim);
    }

    @Override
    public LOTRMusicRegion.Sub getBiomeMusic() {
        return null;
    }
}