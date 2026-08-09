package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Where every decoration of a kind stands, worked out once for the world.
 *
 * <p>The map image is fixed for the session, so which cells of a lattice carry
 * a tree is fixed too. Answering that per frame was what forced the map to
 * thin its decorations out as it was pulled back: the lattice is fine and
 * Middle-earth is three thousand map pixels across, so a zoomed-out frame had
 * a hundred thousand cells to ask about and could not afford them. Asking once
 * and keeping the answers turns the per-frame cost into the number of
 * decorations that exist — some thousands for the whole map — which is small
 * enough that no zoom needs any of them removed.</p>
 *
 * <p>That is the whole reason this class exists, and it is what lets a
 * decoration behave the way the map says it does: it is somewhere in
 * Middle-earth, it is drawn while it is large enough to see, and pulling the
 * map out and pushing it back in gives the same one back rather than a
 * different scatter that faded in on the way.</p>
 *
 * <p>Built a slice at a time. Walking the whole map at once means reading the
 * biome image a few million times, which is a visible stall on the frame the
 * map is opened; spread over a second of frames it is not noticed, and the
 * only cost is that a region the player has pulled straight out to may fill in
 * over the next few frames.</p>
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapDecorationSites {
    /**
     * Cells resolved per frame, per kind.
     *
     * <p>Each one is a handful of hashes, and the few that carry a decoration
     * are fifty reads of the biome image on top. Low enough not to be felt on
     * the frame the map opens, high enough that the map is complete within
     * about a second of it.</p>
     */
    private static final int CELLS_PER_FRAME = 4500;
    private static final int INITIAL_CAPACITY = 2048;

    /** Where each site is, in map pixels, as {@code x, y} pairs. */
    private float[] positions = new float[INITIAL_CAPACITY * 2];
    /** And which lattice cell it came from, which is its seed for everything
     * else about it. */
    private int[] cells = new int[INITIAL_CAPACITY * 2];
    private int count;

    /** How far the walk has got, as a cell on the lattice. */
    private int cursorX;
    private int cursorY;
    private boolean complete;
    /** The map image the sites were worked out for. */
    private int builtForWidth;
    private int builtForHeight;
    private final float[] positionScratch = new float[2];

    /** What a kind needs to answer "is there one here". */
    interface SiteRule {
        /** Whether the lattice puts a site in this cell at all. */
        boolean hasSite(int cellX, int cellY);

        /** Where in the cell it sits, written into {@code result}. */
        void position(int cellX, int cellY, float[] result);

        /** Whether the ground under that position will carry it. */
        boolean isGround(float mapX, float mapY);
    }

    int getCount() {
        return this.count;
    }

    float getX(int index) {
        return this.positions[index * 2];
    }

    float getY(int index) {
        return this.positions[index * 2 + 1];
    }

    int getCellX(int index) {
        return this.cells[index * 2];
    }

    int getCellY(int index) {
        return this.cells[index * 2 + 1];
    }

    boolean isComplete() {
        return this.complete;
    }

    /**
     * Carries the walk on, and starts it again if the map has changed under
     * it.
     *
     * @param cell the lattice spacing, in map pixels
     */
    void advance(SiteRule rule, float cell, int imageWidth, int imageHeight) {
        if (rule == null || !(cell > 0.0F)
                || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        if (imageWidth != this.builtForWidth
                || imageHeight != this.builtForHeight) {
            reset(imageWidth, imageHeight);
        }
        if (this.complete) {
            return;
        }
        int lastX = (int)Math.ceil(imageWidth / cell);
        int lastY = (int)Math.ceil(imageHeight / cell);
        for (int visited = 0; visited < CELLS_PER_FRAME; visited++) {
            if (this.cursorY > lastY) {
                this.complete = true;
                return;
            }
            resolve(rule, this.cursorX, this.cursorY, this.positionScratch);
            this.cursorX++;
            if (this.cursorX > lastX) {
                this.cursorX = 0;
                this.cursorY++;
            }
        }
    }

    private void resolve(
            SiteRule rule, int cellX, int cellY, float[] position) {
        if (!rule.hasSite(cellX, cellY)) {
            return;
        }
        rule.position(cellX, cellY, position);
        if (!rule.isGround(position[0], position[1])) {
            return;
        }
        if (this.count * 2 >= this.cells.length) {
            grow();
        }
        this.positions[this.count * 2] = position[0];
        this.positions[this.count * 2 + 1] = position[1];
        this.cells[this.count * 2] = cellX;
        this.cells[this.count * 2 + 1] = cellY;
        this.count++;
    }

    private void grow() {
        int capacity = this.cells.length;
        float[] grownPositions = new float[capacity * 2];
        System.arraycopy(this.positions, 0, grownPositions, 0, capacity);
        this.positions = grownPositions;
        int[] grownCells = new int[capacity * 2];
        System.arraycopy(this.cells, 0, grownCells, 0, capacity);
        this.cells = grownCells;
    }

    private void reset(int imageWidth, int imageHeight) {
        this.count = 0;
        this.cursorX = 0;
        this.cursorY = 0;
        this.complete = false;
        this.builtForWidth = imageWidth;
        this.builtForHeight = imageHeight;
    }

    /** Forgets a world's decorations on the way out of it. */
    void clear() {
        this.positions = new float[INITIAL_CAPACITY * 2];
        this.cells = new int[INITIAL_CAPACITY * 2];
        reset(0, 0);
    }
}
