package com.ninuna.losttales.world.biome.gen;

import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.world.biome.LostTalesBiomeBase;
import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import java.util.Random;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRMod;
import lotr.common.world.biome.LOTRMusicRegion;
import lotr.common.world.feature.LOTRWorldGenBoulder;
import lotr.common.world.feature.LOTRWorldGenStalactites;
import lotr.common.world.map.LOTRFixedStructures;
import lotr.common.world.map.LOTRWaypoint;
import lotr.common.world.map.LOTRWorldGenUtumnoEntrance;
import lotr.common.world.spawning.LOTRBiomeSpawnList;
import lotr.common.world.spawning.LOTREventSpawner;
import lotr.common.world.spawning.LOTRSpawnList;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraft.world.gen.feature.WorldGenerator;

public class LostTalesBiomeGenMoonElfVale extends LostTalesBiomeBase {
    private WorldGenerator boulderGen;
    private LOTRWorldGenStalactites stalactiteIceGen;

    public LostTalesBiomeGenMoonElfVale(int i, boolean major) {
        super(i, major);
        this.boulderGen = new LOTRWorldGenBoulder(Blocks.stone, 0, 1, 2);
        this.stalactiteIceGen = new LOTRWorldGenStalactites(LOTRMod.stalactiteIce);
        this.setEnableSnow();
        this.topBlock = Blocks.snow;
        this.spawnableCreatureList.clear();
        this.spawnableWaterCreatureList.clear();
        this.spawnableCaveCreatureList.clear();
        this.spawnableLOTRAmbientList.clear();
        LOTRBiomeSpawnList.FactionContainer var10000 = this.npcSpawnList.newFactionList(100);
        LOTRBiomeSpawnList.SpawnListContainer[] var10001 = new LOTRBiomeSpawnList.SpawnListContainer[1];
        var10001[0] = LOTRBiomeSpawnList.entry(LOTRSpawnList.SNOW_TROLLS, 10).setSpawnChance(100000);
        var10000.add(var10001);
        this.decorator.addSoil(new WorldGenMinable(Blocks.packed_ice, 16), 40.0F, 32, 256);
        this.decorator.treesPerChunk = 0;
        this.decorator.flowersPerChunk = 0;
        this.decorator.grassPerChunk = 0;
        this.decorator.generateWater = false;
        this.biomeColors.setSky(10069160);
        this.biomeColors.setFoggy(true);
        this.setBanditChance(LOTREventSpawner.EventChance.NEVER);
    }

    @Override
    public LOTRAchievement getBiomeAchievement() {
        return ELostTalesAchievement.ENTER_MOON_ELF_BIOME.getAchievement();
    }

    @Override
    public LOTRWaypoint.Region getBiomeWaypoints() {
        return ELostTalesWaypoint.Region.MOON_ELVES.getRegion();
    }

    @Override
    public LOTRMusicRegion.Sub getBiomeMusic() {
        return LOTRMusicRegion.FORODWAITH.getSubregion("forodwaith");
    }

    @Override
    public boolean getEnableRiver() {
        return false;
    }

    @Override
    public float getTreeIncreaseChance() {
        return 0.0F;
    }

    @Override
    public void decorate(World world, Random random, int i, int k) {
        super.decorate(world, random, i, k);

        int boulders;
        int i1;
        int k1;
        int j1;

        if (random.nextInt(32) == 0) {
            boulders = 1 + random.nextInt(5);

            for (i1 = 0; i1 < boulders; ++i1) {
                k1 = i + random.nextInt(16) + 8;
                j1 = k + random.nextInt(16) + 8;
                this.boulderGen.generate(world, random, k1, world.getHeightValue(k1, j1), j1);
            }
        }

        for (boulders = 0; boulders < 2; boulders++) {
            i1 = i + random.nextInt(16) + 8;
            k1 = random.nextInt(60);
            j1 = k + random.nextInt(16) + 8;
            this.stalactiteIceGen.generate(world, random, i1, k1, j1);
        }
    }
}