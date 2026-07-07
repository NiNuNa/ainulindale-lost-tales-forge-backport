package com.ninuna.losttales.client.render.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.client.render.model.tileentity.LostTalesTileEntityModelUrn;
import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.core.util.Color;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

public class LostTalesTileEntityRendererUrn extends GeoBlockRenderer<LostTalesTileEntityUrn> {

    public LostTalesTileEntityRendererUrn() {
        super(new LostTalesTileEntityModelUrn());
    }

    @Override
    public void render(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (tile.getBlockMetadata() == 4 || !(tile instanceof LostTalesTileEntityUrn)) {
            return;
        }

        LostTalesTileEntityUrn urn = (LostTalesTileEntityUrn) tile;
        ResourceLocation modelLocation = this.getGeoModelProvider().getModelLocation(urn);
        if (modelLocation == null) {
            return;
        }

        GeoModel model = this.getGeoModelProvider().getModel(modelLocation);
        this.getGeoModelProvider().setLivingAnimations(urn, this.getUniqueID(urn));

        int light = 15;
        if (tile.getWorldObj() != null) {
            light = tile.getWorldObj().getLightBrightnessForSkyBlocks(tile.xCoord, tile.yCoord, tile.zCoord, 0);
        }

        int lx = light % 65536;
        int ly = light / 65536;
        if (tile.xCoord != 0 && tile.yCoord != 0 && tile.zCoord != 0) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)lx, (float)ly);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0.0F, 0.01F, 0.0F);
        GlStateManager.translate(0.5D, 0.0D, 0.5D);
        GlStateManager.rotate(urn.getRenderRotation(tile.getBlockMetadata()), 0.0F, 1.0F, 0.0F);

        Minecraft.getMinecraft().renderEngine.bindTexture(this.getTextureLocation(urn));
        Color renderColor = this.getRenderColor(urn, partialTicks);
        this.render(model, urn, partialTicks, (float)renderColor.getRed() / 255.0F, (float)renderColor.getGreen() / 255.0F, (float)renderColor.getBlue() / 255.0F, (float)renderColor.getAlpha() / 255.0F);
        GlStateManager.popMatrix();
    }
}
