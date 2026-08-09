package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;

/**
 * One ivory for everything written on the map.
 *
 * <p>The mod's own labels — markers, their names, road names — are drawn in
 * the same off-white the rest of its interface uses. LOTR writes its region
 * names in plain white, which next to that reads as a different map, so the
 * transformer sends its label pass through here and the one colour is
 * substituted on the way past.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesLotrMapLabelStyle {
    /** The interface's own white, without its alpha. */
    static final int LABEL_RGB =
            LostTalesSkyrimUiStyle.rgb(LostTalesSkyrimUiStyle.TEXT);
    /** What LOTR writes its region names in. */
    private static final int PLAIN_WHITE = 0x00FFFFFF;

    private LostTalesLotrMapLabelStyle() {
    }

    /**
     * Called in place of {@code FontRenderer.drawString} from LOTR's own map
     * label pass, with the arguments it was about to use.
     */
    public static int drawMapLabel(FontRenderer font, String text,
                                   int x, int y, int colour) {
        if (font == null) {
            return 0;
        }
        return font.drawString(text, x, y, restyle(colour));
    }

    /** Transformer hook for biome and coordinate text in the map strip. */
    public static int restyleMapSubtitle(int colour) {
        return restyle(colour);
    }

    /**
     * Substitutes the interface's ivory for plain white, and leaves anything
     * else exactly as it was.
     *
     * <p>Only white is touched on purpose. The same pass draws the label's
     * drop shadow and, on the old-school map, an amber; recolouring those
     * would flatten a shadow into the text or lose a deliberate highlight.
     * Whatever alpha the caller asked for is kept, since that is how the
     * labels fade in and out with the zoom.</p>
     */
    static int restyle(int colour) {
        return (colour & 0x00FFFFFF) == PLAIN_WHITE
                ? (colour & 0xFF000000) | LABEL_RGB
                : colour;
    }
}
