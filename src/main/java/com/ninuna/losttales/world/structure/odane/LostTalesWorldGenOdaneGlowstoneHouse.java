package com.ninuna.losttales.world.structure.odane;

import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneMan;
import com.ninuna.losttales.world.waystone.LostTalesStructureScan;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHouse;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneHousePlacer;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneStructurePlacer;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneStructureRegistry;
import java.util.Random;
import lotr.common.world.structure2.LOTRWorldGenStructureBase2;
import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * The Odane glowstone house, built from the house files' {@code glowstone_house}
 * as a generated waystone's house is (CONTENT-10, WAY-17): the bundled one, or
 * a server's own of that name in its place. With no such house loaded, nothing
 * is built.
 */
public class LostTalesWorldGenOdaneGlowstoneHouse extends LOTRWorldGenStructureBase2 {

    /** The houses that loaded, bundled and the server's own. */
    private static final OdaneHousePlan.Houses LOADED = new OdaneHousePlan.Houses() {
        @Override
        public LostTalesWaystoneHouse get(String id) {
            LostTalesWaystoneStructurePlacer placer = LostTalesWaystoneStructureRegistry.get(id);
            return placer instanceof LostTalesWaystoneHousePlacer
                    ? ((LostTalesWaystoneHousePlacer) placer).getHouse() : null;
        }
    };

    public LostTalesWorldGenOdaneGlowstoneHouse(boolean notify) {
        super(notify);
    }

    @Override
    public boolean generateWithSetRotation(World world, Random random, int i, int j, int k, int rotation) {
        OdaneHousePlan plan = OdaneHousePlan.from(LOADED);
        if (plan == null) {
            return false;
        }
        this.setOriginAndRotation(world, i, j, k, rotation, 0);

        if (this.restrictions && !this.isGroundSuitable(world, plan)) {
            return false;
        }

        this.clearHouseSpace(world, plan);
        for (LostTalesStructureScan.Block block : plan.getBlocks()) {
            Block placed = Block.getBlockFromName(block.name);
            if (placed != null) {
                this.setBlockAndMetadata(world, block.x, block.y, block.z, placed, block.metadata);
            }
        }

        int[] resident = plan.getResident();
        LostTalesEntityOdaneMan npc = new LostTalesEntityOdaneMan(world);
        this.spawnNPCAndSetHome(npc, world, resident[0], resident[1], resident[2], 16);

        return true;
    }

    private boolean isGroundSuitable(World world, OdaneHousePlan plan) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = plan.getMinX(); x <= plan.getMaxX(); x++) {
            for (int z = plan.getMinZ(); z <= plan.getMaxZ(); z++) {
                int y = this.getTopBlock(world, x, z) - 1;
                if (!this.isSurface(world, x, y, z)) {
                    return false;
                }
                if (y < minY) {
                    minY = y;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }

        return OdaneHousePlan.isLevelEnough(minY, maxY);
    }

    private void clearHouseSpace(World world, OdaneHousePlan plan) {
        for (int x = plan.getMinX(); x <= plan.getMaxX(); x++) {
            for (int z = plan.getMinZ(); z <= plan.getMaxZ(); z++) {
                for (int y = 1; y <= plan.getClearance(); y++) {
                    this.setAir(world, x, y, z);
                }
            }
        }
    }
}
