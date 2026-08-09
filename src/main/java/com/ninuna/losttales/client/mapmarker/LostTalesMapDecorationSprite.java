package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;

/**
 * The artwork a map decoration is drawn from.
 *
 * <p>One entry per kind, describing a sheet of frames laid out in a row.
 * Everything about the artwork lives here — how many frames it has, how large
 * they are, whether the frames are an animation or a set of variants, and how
 * large the thing is — so replacing the placeholder drawings with finished ones
 * is a new sheet and the numbers beside it, and touches neither where
 * decorations are placed nor how map coordinates work.</p>
 *
 * <p>The drawn size is in <em>map</em> pixels, not screen pixels. A tree stands
 * on a patch of Middle-earth of a certain size and keeps it: pushing the map
 * closer makes it larger on screen exactly as it makes the ground under it
 * larger, and pulling the map out makes it smaller until it is not worth
 * drawing and is culled. Sizing it in screen pixels instead — which is what a
 * map marker legitimately does — is what makes decorations read as stickers on
 * the glass rather than as things standing on the map.</p>
 */
@SideOnly(Side.CLIENT)
enum LostTalesMapDecorationSprite {
    /** Placeholder crest, four frames of sixteen by eight. */
    WAVE("decoration_wave.png", 4, 16, 8, 6, true,
            4.6F, 0.0F, 1.8F, 1),
    /**
     * Two drawings of a tree, used as variants rather than as an animation:
     * trees on a map do not sway, and a forest where every tree moved in step
     * read as the paper rippling.
     */
    TREE("decoration_tree.png", 2, 16, 16, 0, false,
            4.0F, 0.5F, 1.35F, 1),
    MOUNTAIN("decoration_mountain.png", 1, 16, 16, 0, false,
            5.6F, 1.0F, 0.9F, 1),
    SHIP("decoration_ship.png", 2, 16, 16, 11, true,
            4.4F, 0.15F, 1.2F, 2);

    private final ResourceLocation texture;
    private final int frames;
    private final int frameWidth;
    private final int frameHeight;
    /** Client ticks a frame is held for, where the frames are an animation. */
    private final int ticksPerFrame;
    private final boolean animated;
    /** How wide the sprite is drawn, in map-image pixels. */
    private final float worldWidth;
    /**
     * How much of what this kind draws is standing up off the ground.
     *
     * <p>A mountain is all height, a wave is none of it, and a tree is between
     * them. It is what decides how much taller the sprite is drawn as the map
     * tips, and it is a property of what the artwork depicts rather than of
     * the artwork itself.</p>
     */
    private final float standing;
    /** Smallest projected width at which this artwork is still readable. */
    private final float minimumReadableWidth;
    /** Transparent pixel rows below the visible foot in every frame. */
    private final int bottomPaddingPixels;

    LostTalesMapDecorationSprite(
            String textureName, int frames, int frameWidth, int frameHeight,
            int ticksPerFrame, boolean animated, float worldWidth,
            float standing, float minimumReadableWidth,
            int bottomPaddingPixels) {
        this.texture = new ResourceLocation(LostTalesMetaData.MOD_ID,
                "textures/gui/map/" + textureName);
        this.frames = Math.max(1, frames);
        this.frameWidth = Math.max(1, frameWidth);
        this.frameHeight = Math.max(1, frameHeight);
        this.ticksPerFrame = Math.max(1, ticksPerFrame);
        this.animated = animated && frames > 1;
        this.worldWidth = Math.max(0.01F, worldWidth);
        this.standing = Math.max(0.0F, Math.min(1.0F, standing));
        this.minimumReadableWidth = Math.max(
                0.01F, minimumReadableWidth);
        this.bottomPaddingPixels = Math.max(
                0, Math.min(frameHeight - 1, bottomPaddingPixels));
    }

    ResourceLocation getTexture() {
        return this.texture;
    }

    int getFrames() {
        return this.frames;
    }

    boolean isAnimated() {
        return this.animated;
    }

    /** How many drawings of this kind there are to choose between. */
    int getVariants() {
        return this.animated ? 1 : this.frames;
    }

    float getWorldWidth() {
        return this.worldWidth;
    }

    float getWorldHeight() {
        return this.worldWidth * this.frameHeight / this.frameWidth;
    }

    float getStanding() {
        return this.standing;
    }

    /** Screen-space offset that places visible ink, not transparent padding, on the ground. */
    float footOffset(float drawnHeight) {
        return drawnHeight * this.bottomPaddingPixels
                / (float)this.frameHeight;
    }

    float visibilityAlpha(float projectedWidth) {
        return LostTalesMapProjectedVisibility.alpha(
                projectedWidth, this.minimumReadableWidth);
    }

    /**
     * Which frame is showing.
     *
     * <p>Off world time, with a phase of the decoration's own, so a shoreline
     * ripples rather than beating in unison. Both are whole numbers, so this
     * says the same thing on every client without anything being sent. A kind
     * whose frames are variants rather than an animation never moves.</p>
     */
    int frameAt(long worldTime, int phase) {
        if (!this.animated) {
            return 0;
        }
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
