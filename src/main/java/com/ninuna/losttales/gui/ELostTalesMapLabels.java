package com.ninuna.losttales.gui;

import com.ninuna.losttales.util.LostTalesUtil;
import lotr.client.gui.LOTRMapLabels;

public enum ELostTalesMapLabels {
    MOON_ELVES(LostTalesUtil.addMapLabel("MOON_ELVES", "MOON_ELVES", 800, 500, 3.5F, 15, -2.5F, 1.5F));

    private final LOTRMapLabels mapLabel;

    ELostTalesMapLabels(LOTRMapLabels mapLabel) {
        this.mapLabel = mapLabel;
    }

    public static void initAndRegisterMapLabels() {
        for (ELostTalesMapLabels l : ELostTalesMapLabels.values()) {
            //Todo...
        }
    }

    public LOTRMapLabels getMapLabel() {
        return mapLabel;
    }
}