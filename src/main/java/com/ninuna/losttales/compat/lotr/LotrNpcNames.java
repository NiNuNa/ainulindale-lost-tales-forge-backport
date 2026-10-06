package com.ninuna.losttales.compat.lotr;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;

/**
 * A LOTR NPC's own name, kept apart from the name of its kind. LOTR names
 * an NPC {@code Hamfast the Hobbit}, the kind in the server's language;
 * the name alone is the person's and reads the same in every language.
 */
public final class LotrNpcNames {
    private LotrNpcNames() {}

    /**
     * The NPC's personal name where LOTR gave it one; empty for any other
     * entity and for an NPC known only by its kind.
     */
    public static String personalName(Entity entity) {
        if (!(entity instanceof LOTREntityNPC)) {
            return "";
        }
        LOTREntityNPC npc = (LOTREntityNPC)entity;
        try {
            String name = npc.getNPCName();
            String kind = npc.getEntityClassName();
            if (name == null || name.trim().length() == 0
                    || name.equals(kind)) {
                return "";
            }
            return name.trim();
        } catch (RuntimeException unnamed) {
            // An NPC another mod adds may fail to say its name; its kind stands in.
            return "";
        }
    }
}
