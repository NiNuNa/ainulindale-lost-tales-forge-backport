package com.ninuna.losttales.client.render.player;

import net.minecraft.client.model.ModelBiped;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The figure is a model render and needs a screen; what it relies on of
 * vanilla's model is checked here.
 */
public final class LostTalesCharacterFigureRendererTest {

    /**
     * A model is a child until a renderer says otherwise, and the only
     * thing that ever says so is RendererLivingEntity, from the entity it
     * is drawing. A figure drawn without an entity has to say it itself:
     * a child model is not merely smaller, it is drawn head at three
     * quarters and body at a half, and this mod's own render() hands the
     * whole job back to vanilla when the flag is set — losing the dwarf's
     * breadth, the hobbit's limbs, the chest and the hair's place.
     */
    @Test
    public void aModelDrawnWithoutAnEntityIsNotAChild() {
        ModelBiped model = new ModelBiped();
        assertTrue("vanilla still defaults this to true", model.isChild);
    }
}
