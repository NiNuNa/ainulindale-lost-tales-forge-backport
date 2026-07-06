package com.ninuna.losttales.rendering.renderer.tileentity;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.rendering.model.tileentity.LostTalesTileEntityModelPlushie;
import com.ninuna.losttales.util.LostTalesBlockRotationHelper;
import net.geckominecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.util.Color;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;

public class LostTalesTileEntityRendererPlushie extends GeoBlockRenderer<LostTalesTileEntityPlushie> {

    public LostTalesTileEntityRendererPlushie() {
        super(new LostTalesTileEntityModelPlushie());
    }

    @Override
    public void render(TileEntity tile, double x, double y, double z, float partialTicks) {
        GeoModel model = this.getGeoModelProvider().getModel(this.getGeoModelProvider().getModelLocation((LostTalesTileEntityPlushie) tile));
        this.getGeoModelProvider().setLivingAnimations((LostTalesTileEntityPlushie) tile, this.getUniqueID((LostTalesTileEntityPlushie) tile));
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
        GlStateManager.translate(0.5, 0.0, 0.5);

        GlStateManager.rotate(LostTalesBlockRotationHelper.getLegacyPlushieRenderRotation(tile.getBlockMetadata()), 0.0F, 1.0F, 0.0F);

        Minecraft.getMinecraft().renderEngine.bindTexture(this.getTextureLocation((LostTalesTileEntityPlushie) tile));
        Color renderColor = this.getRenderColor((LostTalesTileEntityPlushie) tile, partialTicks);
        this.render(model, (LostTalesTileEntityPlushie) tile, partialTicks, (float)renderColor.getRed() / 255.0F, (float)renderColor.getGreen() / 255.0F, (float)renderColor.getBlue() / 255.0F, (float)renderColor.getAlpha() / 255.0F);
        GlStateManager.popMatrix();
    }
}