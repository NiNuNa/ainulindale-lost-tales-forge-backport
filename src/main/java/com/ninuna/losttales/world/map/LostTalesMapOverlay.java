package com.ninuna.losttales.world.map;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.util.LostTalesUtil;
import com.ninuna.losttales.world.biome.ELostTalesBiome;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;

/**
 * Keeps Lost Tales map edits as a transparent overlay instead of shipping a
 * republished copy of the LOTR Legacy map texture.
 */
public final class LostTalesMapOverlay {
    public static final ResourceLocation LOTR_BASE_MAP = new ResourceLocation("lotr", "map/map.png");
    public static final ResourceLocation LOST_TALES_MAP_OVERLAY = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/map/map_overlay.png");

    public static final int ODANE_ISLAND_OVERLAY_COLOR = 0xB8563F;
    public static final int ODANE_MOUNTAIN_OVERLAY_COLOR = 0x7C2F35;

    private LostTalesMapOverlay() {}

    public static void applyWorldGenerationMap() {
        LostTalesUtil.setWorldGenMapImageWithOverlayBiomes(
                LOTR_BASE_MAP,
                LOST_TALES_MAP_OVERLAY,
                new int[]{ODANE_ISLAND_OVERLAY_COLOR, ODANE_MOUNTAIN_OVERLAY_COLOR},
                new lotr.common.world.biome.LOTRBiome[]{ELostTalesBiome.ODANE_ISLAND.getBiome(), ELostTalesBiome.ODANE_MOUNTAINS.getBiome()},
                ELostTalesBiome.ODANE_ISLAND.getBiome());
    }

    @SideOnly(Side.CLIENT)
    public static void applyClientMap() {
        LostTalesUtil.setClientMapImageWithOverlay(LOTR_BASE_MAP, LOST_TALES_MAP_OVERLAY);
    }
}
