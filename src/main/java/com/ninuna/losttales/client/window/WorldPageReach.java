package com.ninuna.losttales.client.window;

/**
 * Whether the player still stands at the thing a world page is open on
 * — a waystone, a missive board — (Q8 a): in its world, within the reach
 * the server lets a player use it from, and the thing still there. The
 * page's tab closes by itself once the player does not, and says which
 * it was ({@link WorldPageWatch}); the server checks every request again
 * whatever the page thinks.
 */
public final class WorldPageReach {
    /** Why the player no longer stands at the thing; each says so in its own words. */
    public enum Leave {
        /** The player is in another world than the thing's. */
        OTHER_WORLD("world"),
        /** Further from it than its reach. */
        TOO_FAR("far"),
        /** It is no longer there. */
        GONE("gone");

        /** What the reason is called in the lang file. */
        public final String id;

        Leave(String id) {
            this.id = id;
        }

        /**
         * The notice's words for a page whose words live under
         * {@code family} — {@code gui.losttales.waystone.left.far} — a
         * format that may take the thing's name.
         */
        public String messageKey(String family) {
            return "gui.losttales." + family + ".left." + this.id;
        }
    }

    private WorldPageReach() {}

    /**
     * Why a player in {@code playerDimension}, feet at
     * {@code feetX}/{@code feetY}/{@code feetZ}, no longer stands at the
     * thing at {@code x}/{@code y}/{@code z} of {@code dimension}, which
     * {@code standing} says is still there, with {@code reachSq} the
     * server's reach squared, measured to the block's middle; null while
     * they do. Another world is said first, then the distance, since a
     * thing far away may simply be out of the world the client holds.
     */
    public static Leave check(int playerDimension, double feetX,
                              double feetY, double feetZ, int dimension,
                              int x, int y, int z, boolean standing,
                              double reachSq) {
        if (playerDimension != dimension) {
            return Leave.OTHER_WORLD;
        }
        double dx = feetX - (x + 0.5D);
        double dy = feetY - (y + 0.5D);
        double dz = feetZ - (z + 0.5D);
        if (dx * dx + dy * dy + dz * dz > reachSq) {
            return Leave.TOO_FAR;
        }
        return standing ? null : Leave.GONE;
    }
}
