package com.ninuna.losttales.world.biome.gen;

import java.util.Random;
import lotr.common.world.biome.LOTRMusicRegion;
import lotr.common.world.biome.variant.LOTRBiomeVariant;
import lotr.common.world.feature.LOTRTreeType;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Naldossë Mountains, the peaks round the Vale. Firs, pines and larches climb
 * the lower slopes; higher up the rock is bare, and the summits carry snow
 * over glacier ice ({@link MoonElfMountainBands}). The Vale's creatures, sky
 * and snow troll belong here too.
 */
public class LostTalesBiomeGenMoonElfMountains extends LostTalesBiomeGenMoonElfVale {

    /** The mountains' subregion of LOTR's Forodwaith music. */
    static final String MOUNTAINS_MUSIC = "moonElfMountains";

    public LostTalesBiomeGenMoonElfMountains(int id, boolean major) {
        super(id, major);

        this.clearBiomeVariants();
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_PINE, 0.4F);
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_LARCH, 0.3F);

        this.decorator.clearTrees();
        this.decorator.setTreeCluster(4, 12);
        this.decorator.treesPerChunk = 1;
        this.decorator.grassPerChunk = 2;
        this.decorator.doubleGrassPerChunk = 0;
        this.decorator.flowersPerChunk = 0;
        this.decorator.enableFern = false;

        this.decorator.addTree(LOTRTreeType.FIR, 350);
        this.decorator.addTree(LOTRTreeType.PINE, 250);
        this.decorator.addTree(LOTRTreeType.LARCH, 200);
        this.decorator.addTree(LOTRTreeType.SPRUCE_THIN, 150);
    }

    @Override
    public LOTRMusicRegion.Sub getBiomeMusic() {
        return LOTRMusicRegion.FORODWAITH.getSubregion(MOUNTAINS_MUSIC);
    }

    @Override
    public float getTreeIncreaseChance() {
        return 0.05F;
    }

    /**
     * Turns the column's soil to stone and ice by height. Only the biome's own
     * top and filler blocks change; stone, water and air stay as they are.
     */
    @Override
    protected void generateMountainTerrain(World world, Random random, Block[] blocks, byte[] meta, int x, int z, int xzIndex, int ySize, int height, int rockDepth, LOTRBiomeVariant variant) {
        int columnStart = xzIndex * ySize;
        int lowest = MoonElfMountainBands.lowestBare(rockDepth);
        for (int y = ySize - 1; y >= lowest; y--) {
            int index = columnStart + y;
            Block block = blocks[index];
            boolean surface = block == this.topBlock;
            if (!surface && block != this.fillerBlock) {
                continue;
            }
            MoonElfMountainBands.Band band = MoonElfMountainBands.bandAt(y, rockDepth);
            if (band == MoonElfMountainBands.Band.SOIL) {
                continue;
            }
            if (band == MoonElfMountainBands.Band.ICE_CAP) {
                blocks[index] = surface ? Blocks.snow : Blocks.packed_ice;
            } else {
                blocks[index] = Blocks.stone;
            }
            meta[index] = 0;
        }
    }
}
