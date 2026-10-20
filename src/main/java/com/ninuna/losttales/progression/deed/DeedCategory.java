package com.ninuna.losttales.progression.deed;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Locale;

/**
 * The Lost Tales categories a bundled deed's achievement stands in among
 * LOTR's own (PROG-11), each by its word in a deed file and its colour on
 * LOTR's achievement screen.
 */
public enum DeedCategory {
    GROWTH(LostTalesColors.HONEY),
    JOURNEYS(LostTalesColors.FERN_GREEN),
    FOES(LostTalesColors.CRIMSON),
    TALES(LostTalesColors.STEEL_BLUE);

    private final int colour;

    DeedCategory(int colour) {
        this.colour = colour;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The name LOTR's category enum knows it by, which its lang line follows. */
    public String lotrName() {
        return "LOSTTALES_" + name();
    }

    /** Its colour, without alpha, as LOTR's category takes it. */
    public int colour() {
        return this.colour & 0xFFFFFF;
    }

    public static DeedCategory fromWord(String word) {
        for (DeedCategory category : values()) {
            if (category.word().equals(word)) {
                return category;
            }
        }
        return null;
    }
}
