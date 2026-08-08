package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;

/**
 * The animated artwork a map decoration is drawn from.
 *
 * <p>One entry per kind, describing a sprite sheet of frames laid out in a row.
 * Everything about the artwork lives here — how many frames it has, how large
 * they are, how long each is held, and how large the thing is drawn — so
 * replacing the placeholder drawings with finished ones is a new sheet and the
 * numbers beside it, and touches neither where decorations are placed nor how
 * map coordinates work.</p>
 *
 * <p>The drawn size is in <em>screen</em> pixels and does not change with zoom,
 * the same way a map marker does not. These are symbols standing on the map
 * saying "forest here", not scale drawings of trees; a tree that grew with the
 * zoom would be a mile across at one end of the range and invisible at the
 * other.</p>
 */
@SideOnly(Side.CLIENT)
enum LostTalesMapDecorationSprite {
    /** Placeholder crest, four frames of sixteen by eight. */
    WAVE("decoration_wave.png", 4, 16, 8, 6, 16.0F),
    TREE("decoration_tree.png", 2, 16, 16, 14, 14.0F),
    MOUNTAIN("decoration_mountain.png", 1, 16, 16, 20, 16.0F),
    SHIP("decoration_ship.png", 2, 16, 16, 11, 14.0F),
    TRAVELLER("decoration_traveller.png", 4, 12, 12, 5, 10.0F);

    private final ResourceLocation texture;
    private final int frames;
    private final int frameWidth;
    private final int frameHeight;
    /** Client ticks a frame is held for. */
    private final int ticksPerFrame;
    /** How wide the sprite is drawn, in screen pixels, at every zoom. */
    private final float screenWidth;

    LostTalesMapDecorationSprite(
            String textureName, int frames, int frameWidth, int frameHeight,
            int ticksPerFrame, float screenWidth) {
        this.texture = new ResourceLocation(LostTalesMetaData.MOD_ID,
                "textures/gui/map/" + textureName);
        this.frames = Math.max(1, frames);
        this.frameWidth = Math.max(1, frameWidth);
        this.frameHeight = Math.max(1, frameHeight);
        this.ticksPerFrame = Math.max(1, ticksPerFrame);
        this.screenWidth = Math.max(1.0F, screenWidth);
    }

    ResourceLocation getTexture() {
        return this.texture;
    }

    int getFrames() {
        return this.frames;
    }

    float getScreenWidth() {
        return this.screenWidth;
    }

    float getScreenHeight() {
        return this.screenWidth * this.frameHeight / this.frameWidth;
    }

    /**
     * Which frame is showing.
     *
     * <p>Off world time, with a phase of the decoration's own, so a shoreline
     * ripples rather than beating in unison. Both are whole numbers, so this
     * says the same thing on every client without anything being sent.</p>
     */
    int frameAt(long worldTime, int phase) {
        long frame = Math.floorDiv(worldTime, this.ticksPerFrame)
                + Math.abs(phase);
        return (int)Math.floorMod(frame, this.frames);
    }

    /** Left edge of a frame within the sheet, as a texture coordinate. */
    double frameUMin(int frame) {
        return clampFrame(frame) / (double)this.frames;
    }

    double frameUMax(int frame) {
        return (clampFrame(frame) + 1.0D) / this.frames;
    }

    private int clampFrame(int frame) {
        return Math.max(0, Math.min(this.frames - 1, frame));
    }
}
