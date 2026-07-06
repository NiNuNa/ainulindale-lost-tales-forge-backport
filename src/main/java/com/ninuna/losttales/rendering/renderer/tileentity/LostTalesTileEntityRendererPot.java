package com.ninuna.losttales.rendering.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPot;
import com.ninuna.losttales.rendering.model.tileentity.LostTalesTileEntityModelPot;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

public class LostTalesTileEntityRendererPot extends GeoBlockRenderer<LostTalesTileEntityPot> {

    public LostTalesTileEntityRendererPot() {
        super(new LostTalesTileEntityModelPot());
    }

    @Override
    public void render(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (tile.getBlockMetadata() != 4) {
            super.render(tile, x, y, z, partialTicks);
        }
    }
}