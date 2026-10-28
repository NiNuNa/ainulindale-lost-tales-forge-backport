package com.ninuna.losttales.armour;

import java.util.Random;

/**
 * One roll for one blow. Forge 1.7.10 posts a player's attack event twice
 * for the same blow (once in EntityPlayer, once in EntityLivingBase), so
 * Deft Movement and Elusive would roll twice; the second asking of the same
 * blow gets the first answer.
 */
final class BlowRoll {
    private Object target;
    private Object source;
    private boolean result;

    /** Whether the blow misses, at {@code chance}; the same answer for the same blow. */
    synchronized boolean misses(Object target, Object source, double chance, Random random) {
        if (target != null && target == this.target && source == this.source) {
            return this.result;
        }
        this.target = target;
        this.source = source;
        this.result = chance > 0.0D && random.nextDouble() < chance;
        return this.result;
    }

    synchronized void clear() {
        this.target = null;
        this.source = null;
        this.result = false;
    }
}
