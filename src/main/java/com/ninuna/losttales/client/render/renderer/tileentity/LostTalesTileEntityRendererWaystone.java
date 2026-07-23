package com.ninuna.losttales.client.render.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.client.render.model.tileentity.LostTalesTileEntityModelWaystone;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

public final class LostTalesTileEntityRendererWaystone
        extends GeoBlockRenderer<LostTalesTileEntityWaystone> {
    public LostTalesTileEntityRendererWaystone() {
        super(new LostTalesTileEntityModelWaystone());
    }
}
