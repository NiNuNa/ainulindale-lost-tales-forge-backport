package com.ninuna.losttales.world.biome.gen;

import com.ninuna.losttales.world.biome.LostTalesBiomeBase;
import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import com.ninuna.losttales.world.structure.odane.LostTalesWorldGenOdaneGlowstoneHouse;
import java.util.Random;
import lotr.common.world.biome.variant.LOTRBiomeVariant;
import lotr.common.world.feature.LOTRTreeType;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class LostTalesBiomeGenOdaneIsland extends LostTalesBiomeBase {

    public LostTalesBiomeGenOdaneIsland(int i, boolean major) {
        super(i, major);
        this.topBlock = Blocks.grass;
        this.fillerBlock = Blocks.dirt;
        this.spawnableMonsterList.clear();

        this.clearBiomeVariants();
        this.addBiomeVariant(LOTRBiomeVariant.FLOWERS, 0.8F);
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_LIGHT, 0.7F);
        this.addBiomeVariant(LOTRBiomeVariant.FOREST_MAPLE, 0.6F);
        this.addBiomeVariant(LOTRBiomeVariant.ORCHARD_PLUM, 0.35F);
        this.addBiomeVariant(LOTRBiomeVariant.HILLS, 0.25F);

        this.decorator.clearTrees();
        this.decorator.setTreeCluster(6, 18);
        this.decorator.treesPerChunk = 7;
        this.decorator.flowersPerChunk = 7;
        this.decorator.doubleFlowersPerChunk = 2;
        this.decorator.grassPerChunk = 14;
        this.decorator.doubleGrassPerChunk = 4;
        this.decorator.willowPerChunk = 1;
        this.decorator.reedPerChunk = 2;
        this.decorator.canePerChunk = 1;
        this.decorator.generateAthelas = true;

        this.decorator.addTree(LOTRTreeType.CHERRY, 500);
        this.decorator.addTree(LOTRTreeType.PLUM, 220);
        this.decorator.addTree(LOTRTreeType.MAPLE, 250);
        this.decorator.addTree(LOTRTreeType.MAPLE_LARGE, 60);
        this.decorator.addTree(LOTRTreeType.OAK, 120);
        this.decorator.addTree(LOTRTreeType.OAK_LARGE, 30);
        this.decorator.addTree(LOTRTreeType.WILLOW, 60);

        this.decorator.addRandomStructure(new LostTalesWorldGenOdaneGlowstoneHouse(false), 700);

        this.registerRhunForestFlowers();
        this.biomeColors.setGrass(0x6FA557);
        this.biomeColors.setFoliage(0x5C8F46);
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
