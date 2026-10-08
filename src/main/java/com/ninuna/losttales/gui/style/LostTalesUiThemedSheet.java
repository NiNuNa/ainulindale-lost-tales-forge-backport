package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * The window sheet repainted for the accent the player picked: a copy of
 * the painted sheet, its honey glyphs repainted by
 * {@link LostTalesUiAccentInk}, kept as a texture of its own. Honey, the
 * accent the sheet is painted in, draws the sheet itself. The copy is made
 * again when the accent changes or a resource pack reloads the sheet;
 * whatever fails, the painted sheet is drawn instead.
 */
public final class LostTalesUiThemedSheet {
    private static final ResourceLocation THEMED =
            new ResourceLocation("losttales", "themed/window");
    /** The accent the copy was made for; null while there is none. */
    private static String madeFor;
    /** The accent a copy could not be made for, so it is not tried every frame. */
    private static String failedFor;

    private LostTalesUiThemedSheet() {}

    /** What to bind for {@code painted}: the copy for the accent, or the sheet as painted. */
    static ResourceLocation location(Minecraft minecraft, ResourceLocation painted) {
        String accent = LostTalesUiTheme.accentName();
        if (minecraft == null || accent == null || "HONEY".equals(accent)
                || accent.equals(failedFor)) {
            return painted;
        }
        if (!accent.equals(madeFor) && !make(minecraft, painted, accent)) {
            return painted;
        }
        return THEMED;
    }

    private static boolean make(Minecraft minecraft, ResourceLocation painted,
                                String accent) {
        InputStream stream = null;
        try {
            stream = minecraft.getResourceManager().getResource(painted)
                    .getInputStream();
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new java.io.IOException("not an image");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            LostTalesUiAccentInk.repaintSheet(pixels, width, accent);
            BufferedImage repainted = new BufferedImage(width, height,
                    BufferedImage.TYPE_INT_ARGB);
            repainted.setRGB(0, 0, width, height, pixels, 0, width);
            forget(minecraft);
            minecraft.getTextureManager().loadTexture(THEMED,
                    new DynamicTexture(repainted));
            madeFor = accent;
            failedFor = null;
            return true;
        } catch (Exception failure) {
            failedFor = accent;
            FMLLog.warning("[%s] The window sheet could not be repainted for "
                    + "the accent %s; it is drawn as painted: %s",
                    LostTalesMetaData.MOD_ID, accent, failure.toString());
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (java.io.IOException ignored) {
                    // Nothing more to read from it either way.
                }
            }
        }
    }

    /**
     * Lets the copy go, so the next draw makes it afresh from the sheet
     * as it now loads: after a resource pack reload, and before a copy for
     * another accent is made.
     */
    public static void forget(Minecraft minecraft) {
        if (madeFor != null && minecraft != null) {
            minecraft.getTextureManager().deleteTexture(THEMED);
        }
        madeFor = null;
        failedFor = null;
    }
}
