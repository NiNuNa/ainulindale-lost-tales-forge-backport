package com.ninuna.losttales.entity;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.entity.npc.LostTalesEntityBlueGoblinSoldier;
import com.ninuna.losttales.entity.npc.LostTalesEntityNia;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneGuard;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneMan;
import cpw.mods.fml.common.registry.EntityRegistry;
import lotr.common.entity.LOTREntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import java.util.*;

public enum ELostTalesEntity {
    TEST_PERSON(LostTalesEntityBlueGoblinSoldier.class, "TestPerson", 7000, 13521151, 373075),
    NIA(LostTalesEntityNia.class, "Nia", 7001, 854606, 6097486),
    ODANE_MAN(LostTalesEntityOdaneMan.class, "OdaneMan", 7002, 0x8A5A38, 0xB45A32),
    ODANE_GUARD(LostTalesEntityOdaneGuard.class, "OdaneGuard", 7003, 0x4F5A33, 0xC89B5A);

    private final Class<? extends Entity> entityClass;
    private final String name;
    private final int id;
    private final int eggBackground;
    private final int eggSpots;

    public static final Map<String, Integer> stringToIDMapping = new HashMap<>();
    public static final HashMap<Integer, LOTREntities.SpawnEggInfo> spawnEggs = new LinkedHashMap<>();

    ELostTalesEntity(Class<? extends Entity> entityClass, String name, int id, int eggBackground, int eggSpots) {
        this.entityClass = entityClass;
        this.name = name;
        this.id = id;
        this.eggBackground = eggBackground;
        this.eggSpots = eggSpots;
    }

    public static void initAndRegisterEntities() {
        for (ELostTalesEntity e : ELostTalesEntity.values()) {
            EntityRegistry.registerModEntity(e.getEntityClass(), e.getName(), e.getId(), LostTalesMod.instance, 80, 3, true);
            String fullName = (String) EntityList.classToStringMapping.get(e.getEntityClass());
            spawnEggs.put(e.getId(), new LOTREntities.SpawnEggInfo(e.getId(), e.getEggBackground(), e.getEggSpots()));
            stringToIDMapping.put(fullName, e.getId());
        }
    }

    public int getEggBackground() {
        return eggBackground;
    }

    public int getEggSpots() {
        return eggSpots;
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

    public static Set<String> getAllEntityNames() {
        return Collections.unmodifiableSet(stringToIDMapping.keySet());
    }
}