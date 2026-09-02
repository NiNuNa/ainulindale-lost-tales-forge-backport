package com.ninuna.losttales.character.registry;

/**
 * One body model a player can be drawn with.
 *
 * This is common-side metadata only: the identifier, the skin layout its
 * catalogue skins follow, and the render-relevant capabilities the client
 * needs to know before it builds the model. The client keeps the model
 * geometry itself.
 */
public final class CharacterBodyModelDefinition {

    private final String id;
    private final CharacterSkinLayout layout;
    private final boolean chestVariant;
    private final boolean bodyTypes;
    private final boolean vanillaArmPivots;

    CharacterBodyModelDefinition(String id, CharacterSkinLayout layout,
                                 boolean chestVariant, boolean bodyTypes,
                                 boolean vanillaArmPivots) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("body model id must not be blank");
        }
        if (layout == null) {
            throw new IllegalArgumentException("body model layout must not be null");
        }
        this.id = id;
        this.layout = layout;
        this.chestVariant = chestVariant;
        this.bodyTypes = bodyTypes;
        this.vanillaArmPivots = vanillaArmPivots;
    }

    public String getId() {
        return this.id;
    }

    /** The layout of the skins painted for this body; an account skin brings its own. */
    public CharacterSkinLayout getLayout() {
        return this.layout;
    }

    /** True when the model can be built with the feminine chest cuboid. */
    public boolean hasChestVariant() {
        return this.chestVariant;
    }

    /** True when the model is built once per {@link CharacterBodyTypeRegistry} entry. */
    public boolean supportsBodyTypes() {
        return this.bodyTypes;
    }

    /**
     * True when the model keeps vanilla's arm pivots, so the first-person
     * arm is drawn where it sits instead of being moved back to them.
     */
    public boolean hasVanillaArmPivots() {
        return this.vanillaArmPivots;
    }
}
