package com.ninuna.losttales.world.biome.gen;

import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.world.biome.LostTalesBiomeBase;
import lotr.common.LOTRAchievement;
import lotr.common.entity.animal.LOTREntityBird;
import lotr.common.entity.animal.LOTREntityDeer;
import lotr.common.entity.animal.LOTREntityElk;
import lotr.common.entity.animal.LOTREntityRabbit;
import lotr.common.world.biome.LOTRMusicRegion;
import lotr.common.world.biome.variant.LOTRBiomeVariant;
import lotr.common.world.feature.LOTRTreeType;
import lotr.common.world.map.LOTRWaypoint;
import lotr.common.world.spawning.LOTRBiomeSpawnList;
import lotr.common.world.spawning.LOTREventSpawner;
import lotr.common.world.spawning.LOTRSpawnList;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Naldossë Vale, the hidden refuge of the Ithildhrim in the far north: a
 * sheltered vale under snow, with groves of birch and aspen among firs and
 * open glades between them, under a cold, misty sky. Snow falls here, never
 * rain. Deer and elk graze, and a lone snow troll may wander in. The Vale
 * has no people of its own yet.
 */
public class LostTalesBiomeGenMoonElfVale extends LostTalesBiomeBase {

    /** The Vale's subregion of LOTR's Forodwaith music. */
    static final String MUSIC = "moonElfVale";
    /** LOTR's spawn chance for the snow trolls: the higher, the rarer. */
    static final int SNOW_TROLL_RARITY = 100000;

    @SuppressWarnings("unchecked")
    public LostTalesBiomeGenMoonElfVale(int id, boolean major) {
        super(id, major);
        this.topBlock = Blocks.grass;
        this.fillerBlock = Blocks.dirt;
        this.setEnableSnow();

        this.spawnableCreatureList.clear();
        this.spawnableCreatureList.add(new BiomeGenBase.SpawnListEntry(LOTREntityDeer.class, 10, 2, 4));
        this.spawnableCreatureList.add(new BiomeGenBase.SpawnListEntry(LOTREntityElk.class, 5, 2, 3));
        this.spawnableLOTRAmbientList.clear();
        this.spawnableLOTRAmbientList.add(new BiomeGenBase.SpawnListEntry(LOTREntityRabbit.class, 8, 1, 3));
        this.spawnableLOTRAmbientList.add(new BiomeGenBase.SpawnListEntry(LOTREntityBird.class, 10, 1, 2));

        LOTRBiomeSpawnList.FactionContainer trolls = this.npcSpawnList.newFactionList(100);
        trolls.add(LOTRBiomeSpawnList.entry(LOTRSpawnList.SNOW_TROLLS, 10).setSpawnChance(SNOW_TROLL_RARITY));
        this.setBanditChance(LOTREventSpawner.EventChance.NEVER);

        this.clearBiomeVariants();
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_BIRCH, 0.6F);
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_ASPEN, 0.5F);
        this.addBiomeVariant(LOTRBiomeVariant.CLEARING, 0.4F);
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_LARCH, 0.3F);
        this.addBiomeVariant(LOTRBiomeVariant.HILLS, 0.3F);

        this.decorator.clearTrees();
        this.decorator.setTreeCluster(5, 16);
        this.decorator.treesPerChunk = 2;
        this.decorator.grassPerChunk = 5;
        this.decorator.doubleGrassPerChunk = 1;
        this.decorator.flowersPerChunk = 1;
        this.decorator.doubleFlowersPerChunk = 0;
        this.decorator.enableFern = true;

        this.decorator.addTree(LOTRTreeType.BIRCH, 300);
        this.decorator.addTree(LOTRTreeType.FIR, 300);
        this.decorator.addTree(LOTRTreeType.ASPEN, 250);
        this.decorator.addTree(LOTRTreeType.BIRCH_TALL, 150);
        this.decorator.addTree(LOTRTreeType.LARCH, 150);
        this.decorator.addTree(LOTRTreeType.SPRUCE_THIN, 100);

        this.registerTaigaFlowers();
        this.biomeColors.setGrass(LostTalesColors.rgb(LostTalesColors.FERN_GREEN));
        this.biomeColors.setFoliage(LostTalesColors.rgb(LostTalesColors.SEA_GREEN));
        this.biomeColors.setSky(LostTalesColors.rgb(LostTalesColors.STEEL_BLUE));
        this.biomeColors.setClouds(LostTalesColors.rgb(LostTalesColors.SEAFOAM));
        this.biomeColors.setFog(LostTalesColors.rgb(LostTalesColors.SEAFOAM));
        this.biomeColors.setFoggy(true);
    }

    @Override
    public LOTRAchievement getBiomeAchievement() {
        return ELostTalesAchievement.ENTER_MOON_ELF_BIOME.getAchievement();
    }

    /** Naldossë's two waypoints stand in LOTR's Forodwaith region. */
    @Override
    public LOTRWaypoint.Region getBiomeWaypoints() {
        return LOTRWaypoint.Region.FORODWAITH;
    }

    /**
     * Forodwaith's tracks for all its subregions play here, and a music
     * pack may name this subregion for tracks of the Vale's own.
     */
    @Override
    public LOTRMusicRegion.Sub getBiomeMusic() {
        return LOTRMusicRegion.FORODWAITH.getSubregion(MUSIC);
    }
}
