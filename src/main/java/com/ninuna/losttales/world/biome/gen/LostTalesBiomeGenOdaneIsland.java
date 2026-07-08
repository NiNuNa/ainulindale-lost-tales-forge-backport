package com.ninuna.losttales.world.biome.gen;

import com.ninuna.losttales.world.biome.LostTalesBiomeBase;
import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import java.util.Random;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class LostTalesBiomeGenOdaneIsland extends LostTalesBiomeBase {

    public LostTalesBiomeGenOdaneIsland(int i, boolean major) {
        super(i, major);
        this.topBlock = Blocks.grass;
        this.fillerBlock = Blocks.dirt;
        this.spawnableMonsterList.clear();
        this.decorator.treesPerChunk = 8;
        this.decorator.flowersPerChunk = 4;
        this.decorator.grassPerChunk = 12;
        this.decorator.doubleGrassPerChunk = 4;
        this.biomeColors.setSky(9283839);
    }

    @Override
    public LOTRWaypoint.Region getBiomeWaypoints() {
        return ELostTalesWaypoint.Region.ODANE.getRegion();
    }

    @Override
    public float getTreeIncreaseChance() {
        return 0.25F;
    }

    @Override
    public void decorate(World world, Random random, int i, int k) {
        super.decorate(world, random, i, k);
    }
}
