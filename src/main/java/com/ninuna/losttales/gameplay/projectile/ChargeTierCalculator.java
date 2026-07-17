package com.ninuna.losttales.gameplay.projectile;

/** Pure server/client-independent charge-tier threshold calculation. */
public final class ChargeTierCalculator {
    private ChargeTierCalculator() {}

    public static int resolveTier(
            int useTicks, int fullDrawTicks,
            int tierOneTicks, int tierTwoTicks, int tierThreeTicks) {
        if (useTicks < 0 || fullDrawTicks <= 0
                || tierOneTicks <= 0
                || tierTwoTicks <= tierOneTicks
                || tierThreeTicks <= tierTwoTicks) {
            throw new IllegalArgumentException(
                    "charge ticks must be positive and ordered");
        }
        int overchargeTicks = useTicks - fullDrawTicks;
        if (overchargeTicks < tierOneTicks) {
            return 0;
        }
        if (overchargeTicks < tierTwoTicks) {
            return 1;
        }
        if (overchargeTicks < tierThreeTicks) {
            return 2;
        }
        return 3;
    }
}
