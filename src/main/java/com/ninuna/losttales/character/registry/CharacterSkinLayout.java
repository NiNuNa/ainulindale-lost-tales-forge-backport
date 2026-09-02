package com.ninuna.losttales.character.registry;

/**
 * How a skin texture is laid out, which decides which body models can draw
 * it. The two 64x64 layouts overlap but disagree below row 32, so a skin
 * drawn through a model made for the other layout shows the wrong texels.
 */
public enum CharacterSkinLayout {

    /**
     * LOTR Legacy's 64x64 layout: the top half matches the classic 64x32
     * biped layout, the hanging hair and beard cuboid sits at (0,32), and
     * the feminine chest cuboid samples (24,0). The half-troll body reads
     * its own boxes from this size.
     */
    LOTR_64X64,

    /**
     * LOTR Legacy's classic 64x32 layout with the orc's nose and ears
     * painted into otherwise unused texels.
     */
    LOTR_64X32,

    /**
     * Minecraft's 64x64 player layout: the top half matches the classic
     * layout, the separate left leg and left arm sit at (16,48) and (32,48),
     * and every limb has an overlay region in rows 32 to 63.
     */
    MINECRAFT_64X64;

    /** Rows of the texture. */
    public int getHeight() {
        return this == LOTR_64X32 ? 32 : 64;
    }
}
