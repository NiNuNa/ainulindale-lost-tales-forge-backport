package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Where the map's small decorations belong.
 *
 * <p>Placement is a hash of the map position and nothing else, so a coastline
 * has the same waves on it every time the map is opened, on every client, with
 * nothing sent about it and nothing stored. No entities and no objects that
 * outlive a frame: a decoration is a position worked out on the way past.</p>
 *
 * <p>The one real decision here is what counts as a stretch of ground broad
 * enough to be worth decorating. It is answered by sampling — the site itself,
 * then two rings around it — rather than by flood-filling anything, which keeps
 * it to a fixed handful of array lookups per site and gives ponds, single tiles
 * and the thin line of a river the same short answer.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapDecorationPlacement {
    /** Samples taken around each ring. */
    static final int PROBE_SAMPLES = 16;
    /**
     * How many of a ring's samples have to match.
     *
     * <p>Ten of sixteen is ground that continues in most directions rather
     * than ground that merely happens to be under the middle of the ring.</p>
     */
    static final int MIN_MATCHING_SAMPLES = 10;

    private LostTalesMapDecorationPlacement() {}

    /** What the placement rule needs to know about the ground. */
    interface GroundSampler {
        boolean matches(int mapX, int mapY);
    }

    /**
     * Whether a map position sits in a broad stretch of the sampled kind.
     *
     * <p>Deliberately not a flood fill. The question is "is there a lot of
     * this here", not "how large is it", and rings of samples answer that at a
     * fixed cost that does not grow with the size of the sea.</p>
     *
     * <p>Two rings rather than one, because one is not enough to tell a sea
     * from a river. A channel three pixels across fills most of a small ring —
     * it is water in every direction that ring reaches — and only a wider ring
     * shows that it is water in one direction and land in the other. Both have
     * to agree, so ground qualifies by being broad rather than merely by
     * matching where it was asked.</p>
     */
    static boolean isBroadSite(
            GroundSampler sampler, int mapX, int mapY, int radius) {
        if (sampler == null || radius <= 0
                || !sampler.matches(mapX, mapY)) {
            return false;
        }
        return isMostly(sampler, mapX, mapY, radius)
                && isMostly(sampler, mapX, mapY, radius * 2);
    }

    /**
     * Whether a map position sits on water with a shore in sight.
     *
     * <p>The opposite question to {@link #isBroadSite}, and it exists because
     * a ship in the middle of Belegaer is not a landmark, it is a speck a
     * thousand miles from anywhere. So a ship wants water that has land
     * somewhere around it: enough of the ring wet to float on, and enough of
     * it dry to be a coast. A river satisfies both at once — it is thin, so
     * most of its ring is bank — which is why the same test puts ships on
     * rivers without a rule of its own.</p>
     */
    static boolean isShoreSite(
            GroundSampler sampler, int mapX, int mapY, int radius) {
        if (sampler == null || radius <= 0
                || !sampler.matches(mapX, mapY)) {
            return false;
        }
        int water = countRing(sampler, mapX, mapY, radius);
        return water >= MIN_SHORE_WATER && water <= MAX_SHORE_WATER;
    }

    /** Least of a ring that has to be water for a hull to sit in it. */
    static final int MIN_SHORE_WATER = 4;
    /** And the most, past which this is open sea rather than a coast. */
    static final int MAX_SHORE_WATER = 13;

    private static boolean isMostly(
            GroundSampler sampler, int mapX, int mapY, int radius) {
        return countRing(sampler, mapX, mapY, radius)
                >= MIN_MATCHING_SAMPLES;
    }

    private static int countRing(
            GroundSampler sampler, int mapX, int mapY, int radius) {
        int matching = 0;
        for (int sample = 0; sample < PROBE_SAMPLES; sample++) {
            double angle = Math.PI * 2.0D * sample / PROBE_SAMPLES;
            int probeX = mapX + (int)Math.round(Math.cos(angle) * radius);
            int probeY = mapY + (int)Math.round(Math.sin(angle) * radius);
            if (sampler.matches(probeX, probeY)) {
                matching++;
            }
        }
        return matching;
    }

    /**
     * Whether a cell has a decoration in it at all.
     *
     * <p>Thinning the lattice is what stops a coast reading as a row of
     * identical stamps a fixed distance apart.</p>
     */
    static boolean hasSite(int cellX, int cellY, int channel, float density) {
        return LostTalesLotrMapAtmosphere.cellNoise(
                cellX, cellY, channel) < density;
    }

    /**
     * The same question, asked of a lattice that clumps.
     *
     * <p>One number per cell gives an even scatter: every part of a forest
     * grows the same number of trees, every stretch of sea carries the same
     * number of waves, and the eye reads the regularity long before it reads
     * the trees. So the density a cell is judged against is itself a slowly
     * varying field of the map position — dense cores, loose edges, stretches
     * with nothing in them at all — and the per-cell number then decides
     * within it.</p>
     *
     * <p>Both are hashes of position, so this stays what it was: the same
     * decorations in the same places on every client, at no cost in storage
     * and with nothing sent about it.</p>
     *
     * @param cell        the lattice's spacing, in map pixels
     * @param clusterSize how broad a clump is, in map pixels
     */
    static boolean hasClusteredSite(int cellX, int cellY, int channel,
                                    float density, float cell,
                                    float clusterSize) {
        float weight = clusterWeight(
                (cellX + 0.5F) * cell, (cellY + 0.5F) * cell,
                channel, clusterSize);
        return hasSite(cellX, cellY, channel,
                Math.min(1.0F, density * weight));
    }

    /** Least of its ordinary density a region may be left with. */
    private static final float MIN_CLUSTER_WEIGHT = 0.04F;
    /** And the most a core of one may be given. */
    private static final float MAX_CLUSTER_WEIGHT = 2.0F;

    /**
     * How much of its ordinary density a part of the map gets.
     *
     * <p>Two octaves: a broad one that decides where the country is crowded
     * and where it is bare, and a finer one that breaks the broad one up so
     * its clumps are not all the same size. Squared on the way out, which is
     * what makes the empty stretches genuinely empty rather than merely
     * thinner — a field used straight reads as an even scatter with a slow
     * ripple through it.</p>
     */
    static float clusterWeight(
            float mapX, float mapY, int channel, float clusterSize) {
        if (!(clusterSize > 0.0F)) {
            return 1.0F;
        }
        float broad = valueNoise(
                mapX / clusterSize, mapY / clusterSize, channel + 11);
        float fine = valueNoise(
                mapX / (clusterSize * 0.35F), mapY / (clusterSize * 0.35F),
                channel + 12);
        float field = broad * 0.68F + fine * 0.32F;
        return MIN_CLUSTER_WEIGHT
                + (MAX_CLUSTER_WEIGHT - MIN_CLUSTER_WEIGHT) * field * field;
    }

    /** Cell noise smoothed between its lattice points. */
    private static float valueNoise(float x, float y, int channel) {
        int cellX = (int)Math.floor(x);
        int cellY = (int)Math.floor(y);
        float alongX = smoothstep(x - cellX);
        float alongY = smoothstep(y - cellY);
        float top = mix(
                LostTalesLotrMapAtmosphere.cellNoise(cellX, cellY, channel),
                LostTalesLotrMapAtmosphere.cellNoise(
                        cellX + 1, cellY, channel), alongX);
        float bottom = mix(
                LostTalesLotrMapAtmosphere.cellNoise(
                        cellX, cellY + 1, channel),
                LostTalesLotrMapAtmosphere.cellNoise(
                        cellX + 1, cellY + 1, channel), alongX);
        return mix(top, bottom, alongY);
    }

    private static float smoothstep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    /** Where in its cell a site sits, in map pixels. */
    static float siteX(int cellX, int cellY, int channel,
                       float cell, float jitter) {
        return (cellX + 0.5F + offset(cellX, cellY, channel + 1, jitter))
                * cell;
    }

    static float siteY(int cellX, int cellY, int channel,
                       float cell, float jitter) {
        return (cellY + 0.5F + offset(cellX, cellY, channel + 2, jitter))
                * cell;
    }

    /**
     * A site's own animation phase, so neighbouring decorations are at
     * different points of the same loop.
     */
    static int sitePhase(int cellX, int cellY, int channel) {
        return (int)(LostTalesLotrMapAtmosphere.cellNoise(
                cellX, cellY, channel + 3) * 997.0F);
    }

    /** Which artwork variant a site uses, where a sprite has several. */
    static int siteVariant(int cellX, int cellY, int channel, int variants) {
        if (variants <= 1) {
            return 0;
        }
        return (int)(LostTalesLotrMapAtmosphere.cellNoise(
                cellX, cellY, channel + 4) * variants) % variants;
    }

    /**
     * A size of a site's own, as a multiple of its kind's.
     *
     * <p>Modest on purpose. A wood drawn from two pictures still reads as two
     * pictures however they are arranged; the same two at slightly different
     * sizes, some of them turned round, read as trees.</p>
     */
    static float siteScale(
            int cellX, int cellY, int channel, float spread) {
        return 1.0F + (LostTalesLotrMapAtmosphere.cellNoise(
                cellX, cellY, channel + 5) - 0.5F) * 2.0F * spread;
    }

    /** Whether a site's artwork is drawn the other way round. */
    static boolean siteMirror(int cellX, int cellY, int channel) {
        return LostTalesLotrMapAtmosphere.cellNoise(
                cellX, cellY, channel + 6) < 0.5F;
    }

    private static float offset(
            int cellX, int cellY, int channel, float jitter) {
        return (LostTalesLotrMapAtmosphere.cellNoise(cellX, cellY, channel)
                - 0.5F) * 2.0F * jitter;
    }
}
