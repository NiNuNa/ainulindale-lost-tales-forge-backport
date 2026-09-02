package com.ninuna.losttales.character.registry;

/** One chest type: its shape and the size the client draws that shape at. */
public final class CharacterChestTypeDefinition {

    private final String id;
    private final CharacterChestTypeRegistry.Shape shape;
    private final float size;

    CharacterChestTypeDefinition(String id, CharacterChestTypeRegistry.Shape shape, float size) {
        if (id == null || id.length() == 0 || shape == null || size < 0.0F) {
            throw new IllegalArgumentException("chest type fields must be valid");
        }
        this.id = id;
        this.shape = shape;
        this.size = size;
    }

    public String getId() {
        return this.id;
    }

    public CharacterChestTypeRegistry.Shape getShape() {
        return this.shape;
    }

    /**
     * Shape-specific size: a uniform scale for the rounded shape, a fullness
     * level from 0 to 1 for the full shape, unused for the others.
     */
    public float getSize() {
        return this.size;
    }

    public boolean isNone() {
        return this.shape == CharacterChestTypeRegistry.Shape.NONE;
    }
}
