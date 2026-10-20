package com.ninuna.losttales.block.tileentity;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.tileentity.TileEntity;

/**
 * The mod's tile entities and the ids a world saves them under. They
 * register from the common proxy, so a dedicated server knows them too.
 */
public enum ELostTalesTileEntity {
    URN(LostTalesTileEntityUrn.class, "losttales_pot"),
    STATUE(LostTalesTileEntityStatue.class, "losttales_statue"),
    LAMP(LostTalesTileEntityLamp.class, "losttales_lamp"),
    PLUSHIE(LostTalesTileEntityPlushie.class, "losttales_plushie"),
    MISSIVE_BOARD(LostTalesTileEntityMissiveBoard.class, "losttales_missive_board"),
    WAYSTONE(LostTalesTileEntityWaystone.class, "losttales_waystone");

    private final Class<? extends TileEntity> tileEntityClass;
    private final String id;

    ELostTalesTileEntity(Class<? extends TileEntity> tileEntityClass, String id) {
        this.tileEntityClass = tileEntityClass;
        this.id = id;
    }

    public static void initAndRegisterTileEntities() {
        for (ELostTalesTileEntity t : ELostTalesTileEntity.values()) {
            GameRegistry.registerTileEntity(t.tileEntityClass, t.getId());
        }
    }

    /** The id the tile entity saves under. */
    public String getId() {
        return this.id;
    }
}
