package com.ninuna.losttales.armour;

/**
 * The numbers a weight's pieces bring before any perk (PROG-18,
 * CONTENT-06), server settings sent to each client with the armour
 * files: the speed four light pieces give, the chance four heavy pieces
 * give that a blow does not push you, and how much each heavy helmet or
 * pair of boots, and each heavy chestplate or pair of leggings, slows.
 */
public final class ArmourSettings {
    /** The numbers as shipped: 5% speed, a 20% chance, 2.5% and 5% slower. */
    public static final ArmourSettings SHIPPED = new ArmourSettings(0.05D, 0.2D, 0.025D, 0.05D);

    private final double lightSpeed;
    private final double heavyKnockback;
    private final double slowSmall;
    private final double slowLarge;

    public ArmourSettings(double lightSpeed, double heavyKnockback, double slowSmall,
                          double slowLarge) {
        this.lightSpeed = share(lightSpeed);
        this.heavyKnockback = share(heavyKnockback);
        this.slowSmall = share(slowSmall);
        this.slowLarge = share(slowLarge);
    }

    /** From percentages, as the server settings write them. */
    public static ArmourSettings ofPercent(double lightSpeed, double heavyKnockback,
                                           double slowSmall, double slowLarge) {
        return new ArmourSettings(lightSpeed / 100.0D, heavyKnockback / 100.0D,
                slowSmall / 100.0D, slowLarge / 100.0D);
    }

    private static double share(double value) {
        return value > 0.0D ? Math.min(1.0D, value) : 0.0D;
    }

    public double getLightSpeed() {
        return this.lightSpeed;
    }

    public double getHeavyKnockback() {
        return this.heavyKnockback;
    }

    /** How much a heavy helmet or pair of boots slows. */
    public double getSlowSmall() {
        return this.slowSmall;
    }

    /** How much a heavy chestplate or pair of leggings slows. */
    public double getSlowLarge() {
        return this.slowLarge;
    }

    /** How much a heavy piece in {@code slot} slows. */
    public double slowOf(ArmourSlot slot) {
        return slot.isSmall() ? this.slowSmall : this.slowLarge;
    }
}
