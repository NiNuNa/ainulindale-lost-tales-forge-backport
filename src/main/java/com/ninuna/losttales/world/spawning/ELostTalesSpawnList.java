package com.ninuna.losttales.world.spawning;

import com.ninuna.losttales.entity.npc.LostTalesEntityBlueGoblinSoldier;
import com.ninuna.losttales.util.LostTalesUtil;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.spawning.LOTRBiomeSpawnList;
import lotr.common.world.spawning.LOTRSpawnEntry;
import lotr.common.world.spawning.LOTRSpawnList;

public enum ELostTalesSpawnList {
    BLUE_GOBLINS(LostTalesUtil.newLOTRSpawnList(new LOTRSpawnEntry(LostTalesEntityBlueGoblinSoldier.class, 10, 4, 6)), LOTRBiome.blueMountains, 25);

    private final LOTRSpawnList spawnList;
    private final LOTRBiome biome;
    private final int weight;

    ELostTalesSpawnList(LOTRSpawnList spawnList, LOTRBiome biome, int weight) {
        this.spawnList = spawnList;
        this.biome = biome;
        this.weight = weight;
    }

    public static void initAndRegisterSpawnLists() {
        for (ELostTalesSpawnList spawnLists : ELostTalesSpawnList.values()) {
            LOTRBiomeSpawnList.FactionContainer factionList = spawnLists.getBiome().npcSpawnList.newFactionList(spawnLists.getWeight());
            factionList.add(LOTRBiomeSpawnList.entry(spawnLists.getSpawnList()));
        }
    }

    public LOTRSpawnList getSpawnList() {
        return spawnList;
    }

    public LOTRBiome getBiome() {
        return biome;
    }

    public int getWeight() {
        return weight;
    }
}