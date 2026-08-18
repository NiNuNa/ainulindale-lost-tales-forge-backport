package com.ninuna.losttales.client.gui.tooltip;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;

/**
 * Key icons inside tooltip lines.
 *
 * <p>A tooltip is a list of strings and nothing else, so an icon has to travel
 * inside one. A marked span carries the key it stands for and the plain text it
 * replaces: {@link LostTalesTooltipHooks} draws the icon and skips the text,
 * and anything that renders the line without knowing about the markers still
 * shows the readable label it always did.</p>
 *
 * <p>The markers are only ever written when the tooltip transformer is in
 * place. Without it nothing would strip them, so {@link #key} hands back the
 * plain label instead and the tooltip reads exactly as it did before.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesTooltipIcons {
    /**
     * Set by the coremod once the tooltip renderer is patched. Nothing writes
     * a marker until it is, because nothing else would strip one.
     */
    public static final String ACTIVE_PROPERTY =
            "losttales.tooltipIconTransformer.active";
    /** Opens a marked span; the next character carries the key code. */
    static final char SPAN_START = '\uE000';
    /** Closes a marked span. */
    static final char SPAN_END = '\uE001';
    /** Key codes are shifted into a private-use range of their own. */
    private static final int CODE_BASE = 0xE100;
    private static final int CODE_BIAS = 256;
    private static final int MIN_CODE = -CODE_BIAS;
    private static final int MAX_CODE = 255;

    private LostTalesTooltipIcons() {}

    /**
     * A key icon for {@code keyCode}, or {@code fallbackLabel} unchanged when
     * icons cannot be drawn in tooltips on this installation.
     *
     * @param fallbackLabel the text the icon replaces, formatting codes and
     *                      all; it is what a foreign tooltip renderer shows
     */
    public static String key(int keyCode, String fallbackLabel) {
        String label = fallbackLabel == null ? "" : fallbackLabel;
        if (!isAvailable() || keyCode < MIN_CODE || keyCode > MAX_CODE) {
            return label;
        }
        return SPAN_START
                + String.valueOf((char)(CODE_BASE + CODE_BIAS + keyCode))
                + label + SPAN_END;
    }

    /** The same, for a binding the player can rebind. */
    public static String key(KeyBinding keyBinding, String fallbackLabel) {
        if (keyBinding == null) {
            return fallbackLabel == null ? "" : fallbackLabel;
        }
        return key(keyBinding.getKeyCode(), fallbackLabel);
    }

    /**
     * Whether tooltips can draw icons at all.
     *
     * <p>False until the coremod has patched the tooltip renderer, because
     * only that renderer knows what the markers mean.</p>
     */
    public static boolean isAvailable() {
        return Boolean.getBoolean(ACTIVE_PROPERTY);
    }

    static boolean hasIcon(String line) {
        return line != null && line.indexOf(SPAN_START) >= 0;
    }

    static int decodeKeyCode(char marker) {
        return marker - CODE_BASE - CODE_BIAS;
    }

    /** The drawn width of one icon, in the tooltip's own pixels. */
    static int measureIcon(Minecraft minecraft, int keyCode) {
        return LostTalesInputIconRenderer.measureInput(minecraft,
                LostTalesInputBinding.getType(keyCode), keyCode, 1.0F);
    }

    static int drawIcon(
            Minecraft minecraft, int keyCode, float x, float y) {
        return LostTalesInputIconRenderer.drawInput(minecraft,
                LostTalesInputBinding.getType(keyCode), keyCode,
                x, y, 1.0F);
    }

    /** Width of a line with its icons drawn and their labels left out. */
    static int measureLine(
            Minecraft minecraft, FontRenderer font, String line) {
        if (line == null) {
            return 0;
        }
        if (!hasIcon(line)) {
            return font.getStringWidth(line);
        }
        int width = 0;
        int index = 0;
        // The same segments the renderer draws, measured the same way: each
        // run carries the formatting in force where the icon interrupted it,
        // and bold text is a pixel per character wider for it.
        String format = "";
        while (index < line.length()) {
            int start = line.indexOf(SPAN_START, index);
            if (start < 0) {
                width += font.getStringWidth(
                        format + line.substring(index));
                break;
            }
            String before = line.substring(index, start);
            width += font.getStringWidth(format + before);
            format = carryFormat(format, before);
            int end = line.indexOf(SPAN_END, start);
            if (end < 0 || start + 1 >= line.length()) {
                // A span that never closes is text like any other.
                width += font.getStringWidth(
                        format + line.substring(start));
                break;
            }
            width += measureIcon(minecraft,
                    decodeKeyCode(line.charAt(start + 1)));
            index = end + 1;
        }
        return width;
    }

    /**
     * Formatting carries across a span the way it carries across plain text:
     * the codes seen so far are re-applied to what follows the icon, since
     * each drawn segment starts a fresh string.
     */
    static String carryFormat(String active, String segment) {
        String carried = active == null ? "" : active;
        for (int index = 0; index + 1 < segment.length(); index++) {
            if (segment.charAt(index) != '§') {
                continue;
            }
            char code = Character.toLowerCase(segment.charAt(index + 1));
            if (code == 'r') {
                carried = "";
            } else if (isColorCode(code)) {
                // A colour clears the styles riding on the previous one.
                carried = "§" + code;
            } else if (isStyleCode(code)) {
                carried = carried + "§" + code;
            }
        }
        return carried;
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f');
    }

    private static boolean isStyleCode(char code) {
        return code >= 'k' && code <= 'o';
    }
}
