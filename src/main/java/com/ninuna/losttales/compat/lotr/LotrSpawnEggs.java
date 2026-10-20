package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.util.LostTalesLog;
import lotr.common.LOTRCreativeTabs;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;

/**
 * Where the Lost Tales spawn eggs stand and what a creature hatched from
 * one is told (CONTENT-04): the eggs sit in LOTR's spawn-egg tab beside
 * LOTR's own, and an NPC spawned from one is marked as LOTR marks its
 * egg-spawned NPCs, so it stays in the world.
 */
public final class LotrSpawnEggs {
    private static volatile boolean tabMissingLogged;
    private static volatile boolean markFailedLogged;

    private LotrSpawnEggs() {}

    /**
     * LOTR's spawn-egg tab, or {@code fallback} when it cannot be read; the
     * first miss is logged.
     */
    public static CreativeTabs tabOr(CreativeTabs fallback) {
        try {
            CreativeTabs tab = LOTRCreativeTabs.tabSpawn;
            if (tab != null) {
                return tab;
            }
            logTabMissing("it is not set up");
        } catch (RuntimeException failure) {
            logTabMissing(failure.toString());
        } catch (LinkageError incompatible) {
            logTabMissing(incompatible.toString());
        }
        return fallback;
    }

    /**
     * Marks an NPC spawned from an egg as LOTR marks its own: it never
     * despawns, and a trader comes back. Any other entity is left alone.
     */
    public static void markEggSpawned(Entity entity) {
        if (!(entity instanceof LOTREntityNPC)) {
            return;
        }
        LOTREntityNPC npc = (LOTREntityNPC)entity;
        try {
            npc.setPersistentAndTraderShouldRespawn();
            npc.onArtificalSpawn();
        } catch (RuntimeException failure) {
            logMarkFailed(failure);
        } catch (LinkageError incompatible) {
            logMarkFailed(incompatible);
        }
    }

    private static void logTabMissing(String reason) {
        if (tabMissingLogged) {
            return;
        }
        tabMissingLogged = true;
        LostTalesLog.warning("LOTR's spawn-egg tab cannot be found (%s); "
                + "the Lost Tales spawn eggs stand in the mod's Misc tab", reason);
    }

    private static void logMarkFailed(Throwable failure) {
        if (markFailedLogged) {
            return;
        }
        markFailedLogged = true;
        LostTalesLog.warning("An NPC spawned from a Lost Tales egg could not be "
                + "marked as LOTR marks egg-spawned NPCs; it may despawn: %s",
                failure.toString());
    }
}
