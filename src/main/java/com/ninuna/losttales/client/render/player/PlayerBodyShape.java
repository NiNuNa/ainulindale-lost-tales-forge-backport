package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;

/**
 * The race geometry {@link LostTalesPlayerModel} adds on top of the vanilla
 * biped. Each entry matches the look of the LOTR Legacy body the race's
 * catalogue skins were painted for; the skin layout is chosen separately.
 *
 * Pivot fields describe where a shape keeps its parts away from vanilla's
 * pivots, which the biped rewrites every frame: the head and legs are moved
 * by a constant, the arms hang at a fixed height and reach out from the
 * torso centre by a fixed distance, and the torso sits at a fixed height.
 */
enum PlayerBodyShape {

    /** A plain human: no race features. */
    PLAYER(0, false, false, false, false, false,
            0.0F, 0.0F, 0.0F, Float.NaN, 5.0F, 0.0F),
    /** Hanging hair and beard cuboid, 16 rows tall. */
    HUMAN(16, false, false, false, false, false,
            0.0F, 0.0F, 0.0F, Float.NaN, 5.0F, 0.0F),
    /** Human plus pointed ears on the head. */
    ELF(16, true, false, false, false, false,
            0.0F, 0.0F, 0.0F, Float.NaN, 5.0F, 0.0F),
    /** Shorter beard cuboid; torso and legs a quarter wider, arms pushed out. */
    DWARF(12, false, true, false, false, false,
            0.0F, 0.0F, 0.0F, Float.NaN, 5.0F, 0.0F),
    /** Shorter beard cuboid; head and limbs lowered, legs shortened, bare feet. */
    HOBBIT(12, false, false, true, false, false,
            4.0F, 4.8F, 4.8F, Float.NaN, 5.0F, 4.8F),
    /** Classic biped with a nose and swept-back ears. */
    ORC(0, false, false, false, true, false,
            0.0F, 0.0F, 0.0F, Float.NaN, 5.0F, 0.0F),
    /**
     * LOTR's half-troll: a ten-pixel head with muzzle, nose, tusks, ears,
     * mohawk and horns on a twelve-pixel torso, broad two-piece arms and
     * thick legs, eight pixels taller than a biped.
     */
    HALF_TROLL(0, false, false, false, false, true,
            -8.0F, -8.0F, 0.0F, -6.0F, 8.5F, -4.0F);

    /**
     * How much wider than a biped a dwarf's torso, arms and legs are
     * drawn. Owned here rather than by either drawing: the body model
     * scales its parts by it and the cape has to be scaled to match, and
     * a proportion changed in one of those places and not the other
     * detaches the cape from the back it hangs on.
     */
    static final float DWARF_WIDTH_SCALE = 1.25F;
    /** How much shorter a hobbit's limbs are: ten rows where twelve fit. */
    static final float HOBBIT_VERTICAL_SCALE = 10.0F / 12.0F;
    /**
     * The half-troll's torso against a biped's, in both directions: its
     * box is twelve wide and sixteen long where a biped's is eight and
     * twelve, and a cape hangs on the box rather than on the biped.
     */
    static final float HALF_TROLL_WIDTH_SCALE = 12.0F / 8.0F;
    static final float HALF_TROLL_LENGTH_SCALE = 16.0F / 12.0F;
    /** How far out of the torso a dwarf's arms are pushed. */
    static final float DWARF_ARM_SHIFT = 1.0F;
    /** And its legs, which are widened rather than only moved. */
    static final float DWARF_LEG_SHIFT = 0.25F;

    private final int lotrHeadwearHeight;
    private final boolean elfEars;
    private final boolean dwarfProportions;
    private final boolean hobbitProportions;
    private final boolean orcFeatures;
    private final boolean halfTroll;
    private final float headDrop;
    private final float bodyPivotY;
    private final float armDrop;
    private final float armPivotOverride;
    private final float armReach;
    private final float legDrop;

    PlayerBodyShape(int lotrHeadwearHeight, boolean elfEars, boolean dwarfProportions,
                    boolean hobbitProportions, boolean orcFeatures, boolean halfTroll,
                    float headDrop, float bodyPivotY, float armDrop,
                    float armPivotOverride, float armReach, float legDrop) {
        this.lotrHeadwearHeight = lotrHeadwearHeight;
        this.elfEars = elfEars;
        this.dwarfProportions = dwarfProportions;
        this.hobbitProportions = hobbitProportions;
        this.orcFeatures = orcFeatures;
        this.halfTroll = halfTroll;
        this.headDrop = headDrop;
        this.bodyPivotY = bodyPivotY;
        this.armDrop = armDrop;
        this.armPivotOverride = armPivotOverride;
        this.armReach = armReach;
        this.legDrop = legDrop;
    }

    /** Rows of the (0,32) hair and beard cuboid; zero when the shape has none. */
    int getLotrHeadwearHeight() {
        return this.lotrHeadwearHeight;
    }

    boolean hasElfEars() {
        return this.elfEars;
    }

    boolean hasDwarfProportions() {
        return this.dwarfProportions;
    }

    boolean hasHobbitProportions() {
        return this.hobbitProportions;
    }

    boolean hasOrcFeatures() {
        return this.orcFeatures;
    }

    boolean isHalfTroll() {
        return this.halfTroll;
    }

    /** True when any pivot differs from vanilla's and must be restored after a pose. */
    boolean hasOwnPivots() {
        return this.headDrop != 0.0F || this.bodyPivotY != 0.0F || this.armDrop != 0.0F
                || !Float.isNaN(this.armPivotOverride) || this.armReach != 5.0F
                || this.legDrop != 0.0F;
    }

    float getHeadDrop() {
        return this.headDrop;
    }

    float getBodyPivotY() {
        return this.bodyPivotY;
    }

    /** The arm height: the biped's own (wide or slim) plus the drop, or the override. */
    float armPivotY(float bipedArmPivotY) {
        return Float.isNaN(this.armPivotOverride)
                ? bipedArmPivotY + this.armDrop : this.armPivotOverride;
    }

    /** Distance of the shoulder pivots from the torso centre; vanilla's is 5. */
    float getArmReach() {
        return this.armReach;
    }

    float getLegDrop() {
        return this.legDrop;
    }

    /**
     * The shape behind a body model identifier, and the plain player body
     * for anything that names none.
     *
     * <p>What a renderer asks. A blank race, a model a later build knew
     * and this one does not, a template nobody has filled in yet — none
     * of those is a reason to stop drawing, and a biped is what every
     * shape here is a change to.</p>
     */
    static PlayerBodyShape forModelIdOrDefault(String modelId) {
        PlayerBodyShape shape = forModelId(modelId);
        return shape == null ? PLAYER : shape;
    }

    /** The shape behind a body model identifier, or null for an unknown one. */
    static PlayerBodyShape forModelId(String modelId) {
        if (CharacterBodyModelRegistry.LOSTTALES_PLAYER.equals(modelId)) {
            return PLAYER;
        }
        if (CharacterBodyModelRegistry.LOTR_HUMAN.equals(modelId)) {
            return HUMAN;
        }
        if (CharacterBodyModelRegistry.LOTR_ELF.equals(modelId)) {
            return ELF;
        }
        if (CharacterBodyModelRegistry.LOTR_DWARF.equals(modelId)) {
            return DWARF;
        }
        if (CharacterBodyModelRegistry.LOTR_HOBBIT.equals(modelId)) {
            return HOBBIT;
        }
        if (CharacterBodyModelRegistry.LOTR_ORC.equals(modelId)
                || CharacterBodyModelRegistry.LOTR_URUK.equals(modelId)) {
            return ORC;
        }
        if (CharacterBodyModelRegistry.LOTR_HALF_TROLL.equals(modelId)) {
            return HALF_TROLL;
        }
        return null;
    }
}
