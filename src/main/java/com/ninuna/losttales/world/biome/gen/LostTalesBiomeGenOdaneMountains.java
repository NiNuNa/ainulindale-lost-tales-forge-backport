package com.ninuna.losttales.world.biome.gen;

import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import java.util.Random;
import lotr.common.world.biome.variant.LOTRBiomeVariant;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class LostTalesBiomeGenOdaneMountains extends LostTalesBiomeGenOdaneIsland {

    public LostTalesBiomeGenOdaneMountains(int i, boolean major) {
        super(i, major);
        this.decorator.treesPerChunk = 3;
        this.decorator.flowersPerChunk = 2;
        this.decorator.grassPerChunk = 6;
        this.decorator.doubleGrassPerChunk = 1;
    }

    @Override
    public LOTRWaypoint.Region getBiomeWaypoints() {
        return ELostTalesWaypoint.Region.ODANE.getRegion();
    }

    @Override
    public float getTreeIncreaseChance() {
        return 0.05F;
    }

    @Override
    protected void generateMountainTerrain(World world, Random random, Block[] blocks, byte[] meta, int i, int k, int xzIndex, int ySize, int height, int rockDepth, LOTRBiomeVariant variant) {
        int stoneHeight = 78 - rockDepth;

        for (int j = ySize - 1; j >= stoneHeight; --j) {
            int index = xzIndex * ySize + j;
            Block block = blocks[index];
            if (block == this.topBlock || block == this.fillerBlock) {
                blocks[index] = Blocks.stone;
                meta[index] = 0;
            }
        }
    }
}
