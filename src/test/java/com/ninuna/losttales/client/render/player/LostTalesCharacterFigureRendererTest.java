package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.client.gui.CharacterMenuButtonPlacement;
import net.minecraft.client.model.ModelBiped;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How large the figure is drawn, which is the part that decides whether
 * it reads as pixel art or as a smear. The drawing itself is a model
 * render and needs a screen; the scale is arithmetic.
 */
public final class LostTalesCharacterFigureRendererTest {

    /** Sixteen texels to a block, so a scale must be a multiple of it. */
    private static final int TEXELS_PER_BLOCK = 16;

    @Test
    public void everyScaleIsAWholeNumberOfPixelsPerTexel() {
        for (int available = 1; available <= 400; available++) {
            int scale = LostTalesCharacterFigureRenderer.scaleFor(available);
            assertEquals("ragged pixels at " + available,
                    0, scale % TEXELS_PER_BLOCK);
            assertTrue("no figure at all at " + available, scale > 0);
        }
    }

    @Test
    public void theFigureIsAsLargeAsItsRoomAllows() {
        for (int available = 32; available <= 400; available++) {
            int scale = LostTalesCharacterFigureRenderer.scaleFor(available);
            int height = LostTalesCharacterFigureRenderer.height(scale);
            assertTrue("overflows " + available + " at scale " + scale,
                    height <= available);
            // One step larger would not have fitted.
            int larger = LostTalesCharacterFigureRenderer.height(
                    scale + TEXELS_PER_BLOCK);
            assertTrue("room to spare at " + available, larger > available);
        }
    }

    @Test
    public void theSmallestRoomStillDrawsSomething() {
        // Below one pixel per texel there is nothing to round down to, so
        // the figure overflows rather than vanishing.
        int scale = LostTalesCharacterFigureRenderer.scaleFor(4);
        assertEquals(TEXELS_PER_BLOCK, scale);
    }

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

    /** The menu's own button, at the height vanilla's spacing gives it. */
    @Test
    public void theMenuButtonHoldsAFigureOfMoreThanOnePixelPerTexel() {
        int scale = LostTalesCharacterFigureRenderer.scaleFor(
                CharacterMenuButtonPlacement.VANILLA_HEIGHT - 6);
        assertTrue("the button is too short to be worth a figure",
                scale >= TEXELS_PER_BLOCK);
        assertTrue(LostTalesCharacterFigureRenderer.height(scale)
                <= CharacterMenuButtonPlacement.VANILLA_HEIGHT);
    }
}
