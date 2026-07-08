package com.ninuna.losttales.world.biome.gen;

import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import com.ninuna.losttales.world.structure.odane.LostTalesWorldGenOdaneGlowstoneHouse;
import java.util.Random;
import lotr.common.world.biome.variant.LOTRBiomeVariant;
import lotr.common.world.feature.LOTRTreeType;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class LostTalesBiomeGenOdaneMountains extends LostTalesBiomeGenOdaneIsland {

    public LostTalesBiomeGenOdaneMountains(int i, boolean major) {
        super(i, major);

        this.clearBiomeVariants();
        this.addBiomeVariant(LOTRBiomeVariant.HILLS, 0.8F);
        this.addBiomeVariant(LOTRBiomeVariant.HILLS_FOREST, 0.7F);
        this.addBiomeVariant(LOTRBiomeVariant.MOUNTAIN, 0.5F);
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_MAPLE, 0.45F);
        this.addBiomeVariant(LOTRBiomeVariant.FLOWERS, 0.25F);

        this.decorator.clearTrees();
        this.decorator.clearRandomStructures();
        this.decorator.setTreeCluster(4, 14);
        this.decorator.treesPerChunk = 4;
        this.decorator.flowersPerChunk = 4;
        this.decorator.doubleFlowersPerChunk = 1;
        this.decorator.grassPerChunk = 8;
        this.decorator.doubleGrassPerChunk = 2;
        this.decorator.willowPerChunk = 0;
        this.decorator.reedPerChunk = 0;
        this.decorator.canePerChunk = 0;

        this.decorator.addTree(LOTRTreeType.CHERRY, 220);
        this.decorator.addTree(LOTRTreeType.PLUM, 120);
        this.decorator.addTree(LOTRTreeType.MAPLE, 280);
        this.decorator.addTree(LOTRTreeType.MAPLE_LARGE, 90);
        this.decorator.addTree(LOTRTreeType.PINE, 160);
        this.decorator.addTree(LOTRTreeType.FIR, 120);

        this.decorator.addRandomStructure(new LostTalesWorldGenOdaneGlowstoneHouse(false), 1100);

        this.biomeColors.setGrass(0x607F47);
        this.biomeColors.setFoliage(0x526E3B);
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
