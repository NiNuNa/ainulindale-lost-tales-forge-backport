package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.HashMap;
import java.util.Map;

/**
 * The map's kinds of marker as buttons on the map window's tool strip:
 * a small pattern for each kind, in its marker's colour. They stand in
 * until their artwork is painted.
 */
@SideOnly(Side.CLIENT)
final class LostTalesMapLegendGlyphs {
    private static final String[] HOUSE = {
            "..#..",
            ".###.",
            "#####",
            ".#.#.",
            ".###."};
    private static final String[] PIN = {
            ".###.",
            "#####",
            ".###.",
            "..#..",
            "..#.."};
    private static final String[] TOWER = {
            "#.#.#",
            "#####",
            ".#.#.",
            ".###.",
            "#####"};
    private static final String[] MARK = {
            "..#..",
            "..#..",
            "..#..",
            ".....",
            "..#.."};
    private static final String[] PEOPLE = {
            ".#.#.",
            "#####",
            "#####",
            ".#.#."};
    private static final String[] LETTER = {
            ".###.",
            "#...#",
            "#####",
            "#...#",
            "#...#"};

    /** Each kind's glyph, made once: the strip asks for them every frame. */
    private static final Map<String, OptionGlyph> MADE =
            new HashMap<String, OptionGlyph>();

    private LostTalesMapLegendGlyphs() {}

    /** The button of a kind of marker, in its colour. */
    static synchronized OptionGlyph of(LostTalesMapLegendCategory category) {
        OptionGlyph glyph = MADE.get(category.getId());
        if (glyph == null) {
            glyph = OptionGlyph.pattern(rgbOf(category.getColorName()),
                    rowsOf(category.getId()));
            MADE.put(category.getId(), glyph);
        }
        return glyph;
    }

    private static String[] rowsOf(String categoryId) {
        if (LostTalesMapLegendRegistry.LOCATIONS.equals(categoryId)) {
            return HOUSE;
        }
        if (LostTalesMapLegendRegistry.PLAYER_WAYSTONES.equals(categoryId)) {
            return TOWER;
        }
        if (LostTalesMapLegendRegistry.QUESTS.equals(categoryId)) {
            return MARK;
        }
        if (LostTalesMapLegendRegistry.PARTY.equals(categoryId)) {
            return PEOPLE;
        }
        if (LostTalesMapLegendRegistry.LABELS.equals(categoryId)) {
            return LETTER;
        }
        return PIN;
    }

    /** A marker colour's name as the compass reads it, as one colour. */
    private static int rgbOf(String colorName) {
        float[] color = LostTalesCompassMarker.parseColor(colorName);
        int red = Math.round(Math.max(0.0F, Math.min(1.0F, color[0])) * 255.0F);
        int green = Math.round(Math.max(0.0F, Math.min(1.0F, color[1])) * 255.0F);
        int blue = Math.round(Math.max(0.0F, Math.min(1.0F, color[2])) * 255.0F);
        return (red << 16) | (green << 8) | blue;
    }
}
