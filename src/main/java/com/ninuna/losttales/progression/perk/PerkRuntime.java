package com.ninuna.losttales.progression.perk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * What the perks remember for a moment and never save: how long a bow
 * has been drawn, when a player last loosed, blocked, struck from a
 * gallop or stood their last, and whether a cleave is striking. A game
 * runs a client and its own server in one place, so each side keeps its
 * own. Cleared as the server starts and stops and as the client leaves.
 */
public final class PerkRuntime {
    private static final Map<UUID, Moments> SERVER = new HashMap<UUID, Moments>();
    private static final Map<UUID, Moments> CLIENT = new HashMap<UUID, Moments>();
    private static boolean cleaving;

    private PerkRuntime() {}

    public static synchronized Moments of(EntityPlayer player) {
        Map<UUID, Moments> side = player.worldObj != null && player.worldObj.isRemote
                ? CLIENT : SERVER;
        Moments moments = side.get(player.getUniqueID());
        if (moments == null) {
            moments = new Moments();
            side.put(player.getUniqueID(), moments);
        }
        return moments;
    }

    public static synchronized void forget(UUID playerId) {
        SERVER.remove(playerId);
    }

    public static synchronized void clearServer() {
        SERVER.clear();
        cleaving = false;
    }

    public static synchronized void clearClient() {
        CLIENT.clear();
    }

    /** Whether a cleave is striking now, so its own blows strike nothing further. */
    static boolean isCleaving() {
        return cleaving;
    }

    static void setCleaving(boolean now) {
        cleaving = now;
    }

    /** One player's moments, in world ticks. */
    public static final class Moments {
        /** The ticks the item in use has really been drawn, and what was added to its draw. */
        public int drawnTicks;
        public int drawAdded;
        public long lastShot = Long.MIN_VALUE;
        public long lastBlock = Long.MIN_VALUE;
        public boolean blocking;
        /** Whether this gallop has struck its first blow. */
        public boolean chargeSpent;
        public long lastStandUntil = Long.MIN_VALUE;
        public long lastStandReady = Long.MIN_VALUE;
        /** Fractions of a tick Wayfarer has gathered toward the next. */
        public double travelCarry;
        /** Tools and weapons a use may have worn this tick, with the chance it costs nothing. */
        public final List<Wear> wear = new ArrayList<Wear>();
        /** Whether a thrown spear marked to take no wear may come back to this player. */
        public boolean spearsMarked;
        /** The mount whose speed and jump this player's perks raised, or -1. */
        public int raisedMount = -1;
    }

    /** An item as it stood before a use that may wear it. */
    public static final class Wear {
        public final ItemStack stack;
        public final int damageBefore;
        public final double chance;

        public Wear(ItemStack stack, double chance) {
            this.stack = stack;
            this.damageBefore = stack.getItemDamage();
            this.chance = chance;
        }
    }
}
