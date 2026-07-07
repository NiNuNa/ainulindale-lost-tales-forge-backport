package com.ninuna.losttales.client.render.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
import com.ninuna.losttales.client.render.model.tileentity.LostTalesTileEntityModelLamp;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

public class LostTalesTileEntityRendererLamp extends GeoBlockRenderer<LostTalesTileEntityLamp> {

    public LostTalesTileEntityRendererLamp() {
        super(new LostTalesTileEntityModelLamp());
    }

    @Override
    public void render(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (tile.getBlockMetadata() != 4) {
            super.render(tile, x, y, z, partialTicks);
        }
    }
}