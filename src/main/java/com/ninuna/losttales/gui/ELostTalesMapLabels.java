package com.ninuna.losttales.gui;

import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public enum ELostTalesMapLabels {
    MOON_ELVES(1577, 533, 3.5F, 15, -2.5F, 1.5F),
    ODANE(3100, 790, 2.2F, -18, -2.5F, 1.5F);

    private final int x;
    private final int y;
    private final float scale;
    private final int angle;
    private final float zoomMin;
    private final float zoomMax;

    ELostTalesMapLabels(int x, int y, float scale, int angle,
                        float zoomMin, float zoomMax) {
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.angle = angle;
        this.zoomMin = zoomMin;
        this.zoomMax = zoomMax;
    }

    /** Adds every label to LOTR's map under its own name; called once, at client start. */
    public static void initAndRegisterMapLabels() {
        for (ELostTalesMapLabels label : values()) {
            LostTalesClientUtil.addMapLabel(label.name(), label.name(),
                    label.x, label.y, label.scale, label.angle,
                    label.zoomMin, label.zoomMax);
        }
    }
}
