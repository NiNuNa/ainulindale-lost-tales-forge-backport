package com.ninuna.losttales.world.biome;

import com.ninuna.losttales.world.biome.gen.LostTalesBiomeGenMoonElfMountains;
import com.ninuna.losttales.world.biome.gen.LostTalesBiomeGenMoonElfVale;
import lotr.common.world.biome.LOTRBiome;

public enum ELostTalesBiome {
    MOON_ELF_VALE(new LostTalesBiomeGenMoonElfVale(200, true).setTemperatureRainfall(0.0F, 0.2F).setMinMaxHeight(0.1F, 0.1F).setColor(13718492).setBiomeName("moonElfVale")),
    MOON_ELF_MOUNTAINS(new LostTalesBiomeGenMoonElfMountains(201, true).setTemperatureRainfall(0.0F, 0.2F).setMinMaxHeight(2.0F, 2.0F).setColor(13140953).setBiomeName("moonElfMountains"));

    private final LOTRBiome biome;

    ELostTalesBiome(LOTRBiome biome) {
        this.biome = biome;
    }

    public static void initAndRegisterBiomes() {
        for (ELostTalesBiome b : ELostTalesBiome.values()) {}
    }

    public LOTRBiome getBiome() {
        return biome;
    }
}