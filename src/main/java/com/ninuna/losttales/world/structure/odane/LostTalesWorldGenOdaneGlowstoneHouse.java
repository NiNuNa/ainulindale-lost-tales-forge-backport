package com.ninuna.losttales.world.structure.odane;

import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneMan;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;
import lotr.common.world.structure2.LOTRWorldGenStructureBase2;
import net.minecraft.block.Block;
import net.minecraft.world.World;

public class LostTalesWorldGenOdaneGlowstoneHouse extends LOTRWorldGenStructureBase2 {

    private static final String SCAN_RESOURCE = "/assets/losttales/strscan/glowstoneHouse.strscan";
    private static final int MIN_X = 0;
    private static final int MAX_X = 3;
    private static final int MIN_Z = -4;
    private static final int MAX_Z = 0;

    public LostTalesWorldGenOdaneGlowstoneHouse(boolean notify) {
        super(notify);
    }

    @Override
    public boolean generateWithSetRotation(World world, Random random, int i, int j, int k, int rotation) {
        this.setOriginAndRotation(world, i, j, k, rotation, 0);

        if (this.restrictions && !this.isGroundSuitable(world)) {
            return false;
        }

        this.clearHouseSpace(world);
        if (!this.generateGlowstoneHouseScan(world)) {
            return false;
        }

        LostTalesEntityOdaneMan npc = new LostTalesEntityOdaneMan(world);
        this.spawnNPCAndSetHome(npc, world, 1, 1, -2, 16);

        return true;
    }

    private boolean isGroundSuitable(World world) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
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

        return maxY - minY <= 2;
    }

    private void clearHouseSpace(World world) {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                for (int y = 1; y <= 5; y++) {
                    this.setAir(world, x, y, z);
                }
            }
        }
    }

    private boolean generateGlowstoneHouseScan(World world) {
        InputStream input = LostTalesWorldGenOdaneGlowstoneHouse.class.getResourceAsStream(SCAN_RESOURCE);
        if (input == null) {
            return false;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                this.generateScanLine(world, line.trim());
            }
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void generateScanLine(World world, String line) {
        if (line.length() == 0 || line.startsWith("#")) {
            return;
        }

        String[] parts = line.split("\\.");
        if (parts.length < 5) {
            return;
        }

        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String blockName = parts[3].replace("\"", "");
            int meta = Integer.parseInt(parts[4]);
            Block block = this.getBlockByScanName(blockName);

            if (block != null) {
                this.setBlockAndMetadata(world, x, y, z, block, meta);
            }
        } catch (NumberFormatException ignored) {}
    }

    private Block getBlockByScanName(String blockName) {
        Block block = Block.getBlockFromName(blockName);
        if (block == null) {
            block = (Block) Block.blockRegistry.getObject(blockName);
        }
        if (block == null && blockName.indexOf(':') < 0) {
            block = (Block) Block.blockRegistry.getObject("minecraft:" + blockName);
        }
        return block;
    }
}
