package com.ninuna.losttales.world.biome.gen;

import lotr.common.world.biome.variant.LOTRBiomeVariant;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.Random;

public class LostTalesBiomeGenMoonElfMountains extends LostTalesBiomeGenMoonElfVale {

    public LostTalesBiomeGenMoonElfMountains(int i, boolean major) {
        super(i, major);
    }

    @Override
    protected void generateMountainTerrain(World world, Random random, Block[] blocks, byte[] meta, int i, int k, int xzIndex, int ySize, int height, int rockDepth, LOTRBiomeVariant variant) {
        int snowHeight = 100 - rockDepth;
        int stoneHeight = snowHeight - 30;

        for (int j = ySize - 1; j >= stoneHeight; --j) {
            int index = xzIndex * ySize + j;
            Block block = blocks[index];
            if (j >= snowHeight && block == this.topBlock) {
                blocks[index] = Blocks.snow;
                meta[index] = 0;
            } else if (block == this.topBlock || block == this.fillerBlock) {
                blocks[index] = Blocks.stone;
                meta[index] = 0;
            }
        }
    }
}