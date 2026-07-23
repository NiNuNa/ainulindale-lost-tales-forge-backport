package com.ninuna.losttales.client.mapmarker;

/**
 * Short-lived client navigation context. It is only a convenience for packet
 * construction; the server independently resolves and validates the source.
 */
public final class LostTalesClientWaystoneTravelContext {
    private static final long MAX_AGE_MILLIS = 120000L;
    private static Context context;

    private LostTalesClientWaystoneTravelContext() {}

    public static synchronized void begin(
            int dimensionId, int x, int y, int z,
            String sourceMarkerId) {
        context = new Context(
                dimensionId, x, y, z, sourceMarkerId,
                System.currentTimeMillis());
    }

    public static synchronized Context get(int dimensionId) {
        if (context == null
                || context.dimensionId != dimensionId
                || System.currentTimeMillis() - context.createdAtMillis
                        > MAX_AGE_MILLIS) {
            context = null;
            return null;
        }
        return context;
    }

    public static synchronized void clear() {
        context = null;
    }

    public static final class Context {
        private final int dimensionId;
        private final int x;
        private final int y;
        private final int z;
        private final String sourceMarkerId;
        private final long createdAtMillis;

        private Context(
                int dimensionId, int x, int y, int z,
                String sourceMarkerId, long createdAtMillis) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sourceMarkerId = sourceMarkerId == null
                    ? "" : sourceMarkerId;
            this.createdAtMillis = createdAtMillis;
        }

        public int getDimensionId() { return this.dimensionId; }
        public int getX() { return this.x; }
        public int getY() { return this.y; }
        public int getZ() { return this.z; }
        public String getSourceMarkerId() {
            return this.sourceMarkerId;
        }
    }
}
