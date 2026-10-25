package com.ninuna.losttales.block.vessel;

/**
 * How a vessel (amphora, urn, loutrophoros) gives up what it holds
 * (CONTENT-09): a sealed one only when broken, its items falling out; an
 * unsealed one only through quick loot, and broken, it drops as a vessel
 * with its items still inside, as a shulker box does. Nothing else takes
 * an item out: no right-click, no hopper.
 */
public final class VesselRules {
    /** How many empty vessels one stack holds. */
    public static final int EMPTY_STACK_LIMIT = 16;
    /** How many vessels holding items one stack holds: one, standing alone. */
    public static final int FILLED_STACK_LIMIT = 1;

    /** The ways something might take an item out of a vessel. */
    public enum Way {
        QUICK_LOOT,
        BREAKING,
        RIGHT_CLICK,
        HOPPER
    }

    /** What breaking a vessel drops. */
    public enum Break {
        /** What it held falls out, and an empty vessel drops. */
        SPILL_AND_DROP_EMPTY,
        /** What it held falls out, and no vessel drops. */
        SPILL,
        /** One vessel drops, its items inside. */
        DROP_FILLED,
        /** An empty vessel drops. */
        DROP_EMPTY,
        /** Nothing drops. */
        NOTHING;

        public boolean spills() {
            return this == SPILL_AND_DROP_EMPTY || this == SPILL;
        }

        public boolean dropsEmptyVessel() {
            return this == SPILL_AND_DROP_EMPTY || this == DROP_EMPTY;
        }
    }

    private VesselRules() {}

    /** Whether a vessel, sealed or not, gives up what it holds {@code way}. */
    public static boolean givesUp(Way way, boolean sealed) {
        if (way == null) {
            return false;
        }
        return sealed ? way == Way.BREAKING : way == Way.QUICK_LOOT;
    }

    /**
     * What breaking a vessel drops. A creative player's break drops no
     * empty vessel, as no block drops for them, but a vessel still holding
     * items drops so they are not lost. Where blocks drop nothing
     * ({@code doTileDrops} off), the items fall out as a chest's do.
     */
    public static Break onBreak(boolean sealed, boolean holdsItems,
                                boolean byCreativePlayer, boolean blocksDrop) {
        boolean vesselDrops = blocksDrop && !byCreativePlayer;
        if (!holdsItems) {
            return vesselDrops ? Break.DROP_EMPTY : Break.NOTHING;
        }
        if (sealed) {
            return vesselDrops ? Break.SPILL_AND_DROP_EMPTY : Break.SPILL;
        }
        return blocksDrop ? Break.DROP_FILLED : Break.SPILL;
    }

    /** How many vessels one stack holds: empty ones stack, one holding items stands alone. */
    public static int stackLimit(boolean holdsItems) {
        return holdsItems ? FILLED_STACK_LIMIT : EMPTY_STACK_LIMIT;
    }

    /** Whether a vessel may take an item in: never a vessel holding items of its own. */
    public static boolean mayHold(boolean isVessel, boolean vesselHoldsItems) {
        return !(isVessel && vesselHoldsItems);
    }
}
