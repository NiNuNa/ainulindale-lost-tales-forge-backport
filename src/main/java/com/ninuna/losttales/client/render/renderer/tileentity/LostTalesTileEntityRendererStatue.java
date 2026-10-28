package com.ninuna.losttales.client.render.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.client.render.model.tileentity.LostTalesTileEntityModelStatue;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

/** Draws the watch stone's model from its lower half; the upper half draws nothing. */
public class LostTalesTileEntityRendererStatue extends GeoBlockRenderer<LostTalesTileEntityStatue> {

    public LostTalesTileEntityRendererStatue() {
        super(new LostTalesTileEntityModelStatue());
    }

    @Override
    public void render(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (tileEntity.getBlockMetadata() != 4) {
            super.render(tileEntity, x, y, z, partialTicks);
        }
    }
}
