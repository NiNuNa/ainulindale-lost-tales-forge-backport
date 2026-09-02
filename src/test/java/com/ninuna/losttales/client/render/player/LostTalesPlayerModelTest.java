package com.ninuna.losttales.client.render.player;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The player body must keep vanilla's proportions and pivots, differ from
 * them only in arm width, and carry one overlay per limb as a child part.
 */
public final class LostTalesPlayerModelTest {

    @Test
    public void wideBodyKeepsVanillaArms() {
        LostTalesPlayerModel model = new LostTalesPlayerModel(0.0F, false);
        assertFalse(model.isSlim());
        assertEquals(64.0F, model.textureWidth, 0.0F);
        assertEquals(64.0F, model.textureHeight, 0.0F);
        assertEquals(4.0F, width(model.bipedRightArm), 0.0F);
        assertEquals(4.0F, width(model.bipedLeftArm), 0.0F);
        assertEquals(2.0F, model.bipedRightArm.rotationPointY, 0.0F);
        assertEquals(2.0F, model.bipedLeftArm.rotationPointY, 0.0F);
        assertEquals(-5.0F, model.bipedRightArm.rotationPointX, 0.0F);
        assertEquals(5.0F, model.bipedLeftArm.rotationPointX, 0.0F);
    }

    /** Java Edition keeps slim arms level with wide ones (MC-275473). */
    @Test
    public void slimBodyNarrowsBothArmsAndKeepsTheirHeight() {
        LostTalesPlayerModel model = new LostTalesPlayerModel(0.0F, true);
        assertTrue(model.isSlim());
        assertEquals(3.0F, width(model.bipedRightArm), 0.0F);
        assertEquals(3.0F, width(model.bipedLeftArm), 0.0F);
        assertEquals(2.0F, model.bipedRightArm.rotationPointY, 0.0F);
        assertEquals(2.0F, model.bipedLeftArm.rotationPointY, 0.0F);
        // The narrow box hugs the shoulder side, as vanilla's slim model does.
        assertEquals(-2.0F, box(model.bipedRightArm).posX1, 0.0F);
        assertEquals(-1.0F, box(model.bipedLeftArm).posX1, 0.0F);
    }

    @Test
    public void leftLimbsHaveTheirOwnUnmirroredParts() {
        LostTalesPlayerModel model = new LostTalesPlayerModel(0.0F, false);
        assertFalse(model.bipedLeftArm.mirror);
        assertFalse(model.bipedLeftLeg.mirror);
        assertEquals(1.9F, model.bipedLeftLeg.rotationPointX, 0.0F);
        assertEquals(12.0F, model.bipedLeftLeg.rotationPointY, 0.0F);
        assertEquals(4.0F, width(model.bipedLeftLeg), 0.0F);
        assertEquals(8.0F, width(model.bipedHead), 0.0F);
        assertEquals(8.0F, width(model.bipedBody), 0.0F);
    }

    /**
     * ModelBox records the box before inflation, so the overlay's quarter
     * pixel is not visible here; what is checked is that each overlay is the
     * same box as its limb, sits at the limb's origin, and is a child of it.
     */
    @Test
    public void everyLimbCarriesOneOverlayChildOfItsOwnSize() {
        LostTalesPlayerModel model = new LostTalesPlayerModel(0.0F, true);
        ModelRenderer[] limbs = {
                model.bipedBody, model.bipedRightArm, model.bipedLeftArm,
                model.bipedRightLeg, model.bipedLeftLeg};
        for (ModelRenderer limb : limbs) {
            assertNotNull(limb.childModels);
            assertEquals(1, limb.childModels.size());
            ModelRenderer overlay = (ModelRenderer)limb.childModels.get(0);
            assertEquals(width(limb), width(overlay), 0.0001F);
            assertEquals(box(limb).posX1, box(overlay).posX1, 0.0001F);
            assertEquals(box(limb).posY1, box(overlay).posY1, 0.0001F);
            assertEquals(0.0F, overlay.rotationPointX, 0.0F);
            assertEquals(0.0F, overlay.rotationPointY, 0.0F);
            assertTrue(overlay.showModel);
        }
        assertSame(model.bipedHead.childModels, null);

        model.setOverlaysVisible(false);
        for (ModelRenderer limb : limbs) {
            assertFalse(((ModelRenderer)limb.childModels.get(0)).showModel);
        }
    }

    private static float width(ModelRenderer part) {
        ModelBox box = box(part);
        return box.posX2 - box.posX1;
    }

    private static ModelBox box(ModelRenderer part) {
        return (ModelBox)part.cubeList.get(0);
    }
}
