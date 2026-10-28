package com.ninuna.losttales.world.biome;

import com.ninuna.losttales.world.biome.gen.LostTalesBiomeGenMoonElfMountains;
import com.ninuna.losttales.world.biome.gen.LostTalesBiomeGenMoonElfVale;
import com.ninuna.losttales.world.biome.gen.LostTalesBiomeGenOdaneIsland;
import com.ninuna.losttales.world.biome.gen.LostTalesBiomeGenOdaneMountains;
import lotr.common.world.biome.LOTRBiome;

public enum ELostTalesBiome {
    MOON_ELF_VALE(new LostTalesBiomeGenMoonElfVale(200, true).setTemperatureRainfall(0.0F, 0.2F).setMinMaxHeight(0.1F, 0.1F).setColor(0xD153DC).setBiomeName("moonElfVale")),
    MOON_ELF_MOUNTAINS(new LostTalesBiomeGenMoonElfMountains(201, true).setTemperatureRainfall(0.0F, 0.2F).setMinMaxHeight(2.0F, 2.0F).setColor(0xC883D9).setBiomeName("moonElfMountains")),
    ODANE_ISLAND(new LostTalesBiomeGenOdaneIsland(202, true).setTemperatureRainfall(0.8F, 0.75F).setMinMaxHeight(0.15F, 0.25F).setColor(0x78A85A).setBiomeName("odaneIsland")),
    ODANE_MOUNTAINS(new LostTalesBiomeGenOdaneMountains(203, true).setTemperatureRainfall(0.65F, 0.55F).setMinMaxHeight(1.25F, 1.85F).setColor(0x627E4B).setBiomeName("odaneMountains"));

    private final LOTRBiome biome;

    ELostTalesBiome(LOTRBiome biome) {
        this.biome = biome;
    }

    /**
     * Builds every biome: calling this loads the enum, and LOTR's biome
     * constructor registers each biome under its id.
     */
    public static void initAndRegisterBiomes() {
    }

    public LOTRBiome getBiome() {
        return biome;
    }
}