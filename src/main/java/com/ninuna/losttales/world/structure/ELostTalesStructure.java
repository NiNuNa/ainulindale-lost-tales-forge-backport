package com.ninuna.losttales.world.structure;

import com.ninuna.losttales.world.structure.odane.LostTalesWorldGenOdaneGlowstoneHouse;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Method;
import lotr.common.LOTRConfig;
import lotr.common.world.structure.LOTRStructures;
import lotr.common.world.structure2.LOTRStructureTimelapse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public final class ELostTalesStructure {

    public static final int ODANE_GLOWSTONE_HOUSE_ID = 7000;

    private ELostTalesStructure() {}

    public static void initAndRegisterStructures() {
        registerStructureSpawner(
                ODANE_GLOWSTONE_HOUSE_ID,
                new OdaneGlowstoneHouseProvider(),
                "OdaneGlowstoneHouse",
                0x8A5A38,
                0xE3B04B,
                false
        );
    }

    private static void registerStructureSpawner(int id, LOTRStructures.IStructureProvider provider, String name, int colorBackground, int colorForeground, boolean hidden) {
        if (LOTRStructures.structureItemSpawners.containsKey(Integer.valueOf(id))) {
            return;
        }

        try {
            Method registerStructure = LOTRStructures.class.getDeclaredMethod(
                    "registerStructure",
                    Integer.TYPE,
                    LOTRStructures.IStructureProvider.class,
                    String.class,
                    Integer.TYPE,
                    Integer.TYPE,
                    Boolean.TYPE
            );
            registerStructure.setAccessible(true);
            registerStructure.invoke(null, Integer.valueOf(id), provider, name, Integer.valueOf(colorBackground), Integer.valueOf(colorForeground), Boolean.valueOf(hidden));
        } catch (Exception e) {
            FMLLog.warning("Failed to register Lost Tales structure spawner %s with ID %d", name, Integer.valueOf(id));
            e.printStackTrace();
        }
    }

    private static class OdaneGlowstoneHouseProvider implements LOTRStructures.IStructureProvider {

        @Override
        public boolean generateStructure(World world, EntityPlayer player, int x, int y, int z) {
            LostTalesWorldGenOdaneGlowstoneHouse generator = new LostTalesWorldGenOdaneGlowstoneHouse(true);
            generator.restrictions = false;
            generator.usingPlayer = player;

            if (LOTRConfig.strTimelapse) {
                LOTRStructureTimelapse.start(generator, world, x, y, z);
                return true;
            }

            return generator.generateWithSetRotation(world, world.rand, x, y, z, generator.usingPlayerRotation());
        }

        @Override
        public boolean isVillage() {
            return false;
        }
    }
}
