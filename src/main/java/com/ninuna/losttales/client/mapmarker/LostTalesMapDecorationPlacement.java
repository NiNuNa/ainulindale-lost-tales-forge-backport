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

    /**
     * The site a coarse cell stands for, as {@code {cellX, cellY}} on the base
     * lattice.
     *
     * <p>This is what lets the map thin its decorations out instead of giving
     * up on them. Pulling the map out packs the lattice tighter on screen
     * until the artwork would be a solid carpet, so instead of drawing all of
     * it and fading the lot away, the map draws fewer of them: it walks a
     * lattice a power of two coarser and asks each coarse cell which one site
     * inside it to keep.</p>
     *
     * <p>The choice is nested, and that is the whole point. A coarse cell
     * always keeps whichever site one of its own children kept, so a site that
     * survives at one level survives at every finer level too. Zooming out can
     * therefore only ever remove decorations — never move the ones that stay,
     * never resize them, and never put different ones in their place.</p>
     */
    static void representative(
            int coarseX, int coarseY, int levels, int channel, int[] result) {
        int x = coarseX;
        int y = coarseY;
        for (int level = Math.max(0, levels); level > 0; level--) {
            int quadrant = quadrantAt(x, y, level, channel);
            x = x * 2 + (quadrant & 1);
            y = y * 2 + ((quadrant >> 1) & 1);
        }
        result[0] = x;
        result[1] = y;
    }

    /**
     * Whether a cell is the one its own parent would have kept.
     *
     * <p>A site that would still be there one level coarser is already at
     * home; one that would not is the detail this level is adding, and is
     * faded in rather than appearing whole as the map moves.</p>
     */
    static boolean survivesCoarser(
            int cellX, int cellY, int level, int channel) {
        int parentX = cellX >> 1;
        int parentY = cellY >> 1;
        int quadrant = quadrantAt(parentX, parentY, level + 1, channel);
        return cellX == parentX * 2 + (quadrant & 1)
                && cellY == parentY * 2 + ((quadrant >> 1) & 1);
    }

    private static int quadrantAt(
            int cellX, int cellY, int level, int channel) {
        return (int)(LostTalesLotrMapAtmosphere.cellNoise(
                cellX, cellY, channel + 40 + level) * 4.0F) & 3;
    }

    /** Which artwork variant a site uses, where a sprite has several. */
    static int siteVariant(int cellX, int cellY, int channel, int variants) {
        if (variants <= 1) {
            return 0;
        }
        return (int)(LostTalesLotrMapAtmosphere.cellNoise(
                cellX, cellY, channel + 4) * variants) % variants;
    }

    private static float offset(
            int cellX, int cellY, int channel, float jitter) {
        return (LostTalesLotrMapAtmosphere.cellNoise(cellX, cellY, channel)
                - 0.5F) * 2.0F * jitter;
    }
}
