package com.ninuna.losttales.gui;

import com.ninuna.losttales.util.LostTalesClientUtil;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRMapLabels;

@SideOnly(Side.CLIENT)
public enum ELostTalesMapLabels {
    MOON_ELVES(LostTalesClientUtil.addMapLabel("MOON_ELVES", "MOON_ELVES", 1577, 533, 3.5F, 15, -2.5F, 1.5F)),
    ODANE(LostTalesClientUtil.addMapLabel("ODANE", "ODANE", 3100, 790, 2.2F, -18, -2.5F, 1.5F));

    private final LOTRMapLabels mapLabel;

    ELostTalesMapLabels(LOTRMapLabels mapLabel) {
        this.mapLabel = mapLabel;
    }

    public static void initAndRegisterMapLabels() {
        // Invoking this method initializes and registers the enum constants.
    }

    public LOTRMapLabels getMapLabel() {
        return mapLabel;
    }
}
