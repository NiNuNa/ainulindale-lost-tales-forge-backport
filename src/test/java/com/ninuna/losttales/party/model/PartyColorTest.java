package com.ninuna.losttales.party.model;

import com.ninuna.losttales.gui.style.LostTalesColors;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A party colour is one colour. The HUD, the chat and the party screens
 * all read it from here, so a member cannot look one colour in one place
 * and another somewhere else.
 */
public final class PartyColorTest {

    /** Every colour is drawn in a different one. */
    @Test
    public void theFourColoursAreFourColours() {
        Set<Integer> drawn = new HashSet<Integer>();
        for (PartyColor color : PartyColor.values()) {
            assertTrue(color.getId() + " has a colour of its own",
                    drawn.add(Integer.valueOf(color.getRgb())));
        }
        assertEquals(PartyColor.values().length, drawn.size());
    }

    /** Each is a palette entry and not a colour invented at a draw site. */
    @Test
    public void everyColourComesFromThePalette() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN),
                PartyColor.GREEN.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.HONEY),
                PartyColor.YELLOW.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.ORCHID),
                PartyColor.PURPLE.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.SEAFOAM),
                PartyColor.BLUE.getRgb());
    }

    /** It is a colour and nothing else: no alpha rides along with it. */
    @Test
    public void aColourCarriesNoAlphaOfItsOwn() {
        for (PartyColor color : PartyColor.values()) {
            assertEquals(color.getId() + " is three bytes",
                    color.getRgb() & 0xFFFFFF, color.getRgb());
        }
    }

    /** The wire and the config still name them as they always did. */
    @Test
    public void theWireAndConfigNamesAreUnchanged() {
        for (PartyColor color : PartyColor.values()) {
            assertEquals(color, PartyColor.fromNetworkId(color.getNetworkId()));
            assertEquals(color, PartyColor.fromId(color.getId()));
        }
    }
}
