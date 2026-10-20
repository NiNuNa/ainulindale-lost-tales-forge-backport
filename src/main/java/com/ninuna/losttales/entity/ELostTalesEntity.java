package com.ninuna.losttales.entity;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.entity.npc.LostTalesEntityBlueGoblinSoldier;
import com.ninuna.losttales.entity.npc.LostTalesEntityNia;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneGuard;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneMan;
import cpw.mods.fml.common.registry.EntityRegistry;
import net.minecraft.entity.Entity;

/**
 * The Lost Tales creatures: each registered with Forge under its name and
 * id, with the two colours of its spawn egg (CONTENT-04), the shell first
 * and the spots second.
 */
public enum ELostTalesEntity {
    BLUE_GOBLIN_SOLDIER(LostTalesEntityBlueGoblinSoldier.class, "BlueGoblinSoldier", 7000, 0xCE50FF, 0x05B153),
    NIA(LostTalesEntityNia.class, "Nia", 7001, 0x0D0A4E, 0x5D0A4E),
    ODANE_MAN(LostTalesEntityOdaneMan.class, "OdaneMan", 7002, 0x8A5A38, 0xB45A32),
    ODANE_GUARD(LostTalesEntityOdaneGuard.class, "OdaneGuard", 7003, 0x4F5A33, 0xC89B5A);

    private final Class<? extends Entity> entityClass;
    private final String name;
    private final int id;
    private final int eggColor;
    private final int eggSpotColor;

    ELostTalesEntity(Class<? extends Entity> entityClass, String name, int id,
            int eggColor, int eggSpotColor) {
        this.entityClass = entityClass;
        this.name = name;
        this.id = id;
        this.eggColor = eggColor;
        this.eggSpotColor = eggSpotColor;
    }

    public static void initAndRegisterEntities() {
        for (ELostTalesEntity e : ELostTalesEntity.values()) {
            EntityRegistry.registerModEntity(e.getEntityClass(), e.getName(), e.getId(), LostTalesMod.instance, 80, 3, true);
        }
    }

    /** The creature registered under {@code id}, or null for any other id. */
    public static ELostTalesEntity byId(int id) {
        for (ELostTalesEntity creature : values()) {
            if (creature.id == id) {
                return creature;
            }
        }
        return null;
    }

    /**
     * The creature an entity of exactly this class is, or null: a class
     * another mod derives from one of ours is not ours.
     */
    public static ELostTalesEntity byClass(Class<?> entityClass) {
        for (ELostTalesEntity creature : values()) {
            if (creature.entityClass == entityClass) {
                return creature;
            }
        }
        return null;
    }

    public Class<? extends Entity> getEntityClass() {
        return entityClass;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** The lang key of the creature's name, the one the game reads for it. */
    public String getNameKey() {
        return "entity." + LostTalesMetaData.MOD_ID + "." + name + ".name";
    }

    /** The colour of the spawn egg's shell, as 0xRRGGBB. */
    public int getEggColor() {
        return eggColor;
    }

    /** The colour of the spawn egg's spots, as 0xRRGGBB. */
    public int getEggSpotColor() {
        return eggSpotColor;
    }
}
