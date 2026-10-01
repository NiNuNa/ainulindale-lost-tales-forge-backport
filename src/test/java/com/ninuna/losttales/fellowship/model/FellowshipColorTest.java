package com.ninuna.losttales.fellowship.model;

import com.ninuna.losttales.gui.style.LostTalesColors;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A fellowship colour is one colour. The HUD, the chat and the fellowship screens
 * all read it from here, so a member cannot look one colour in one place
 * and another somewhere else.
 */
public final class FellowshipColorTest {

    /** Every colour is drawn in a different one. */
    @Test
    public void theEightColoursAreEightColours() {
        Set<Integer> drawn = new HashSet<Integer>();
        for (FellowshipColor color : FellowshipColor.values()) {
            assertTrue(color.getId() + " has a colour of its own",
                    drawn.add(Integer.valueOf(color.getRgb())));
        }
        assertEquals(FellowshipColor.values().length, drawn.size());
    }

    /** Each is a palette entry and not a colour invented at a draw site. */
    @Test
    public void everyColourComesFromThePalette() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN),
                FellowshipColor.GREEN.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.HONEY),
                FellowshipColor.YELLOW.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.ORCHID),
                FellowshipColor.PURPLE.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.SEAFOAM),
                FellowshipColor.BLUE.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.APRICOT),
                FellowshipColor.ORANGE.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.SALMON),
                FellowshipColor.RED.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.TEAL),
                FellowshipColor.TEAL.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE),
                FellowshipColor.ROSE.getRgb());
    }

    /** The wire names the colours 0 to 7, in the order they are given out. */
    @Test
    public void theNetworkIdsRunFromZeroToSeven() {
        for (FellowshipColor color : FellowshipColor.values()) {
            assertEquals(color.ordinal(), color.getNetworkId());
        }
    }

    /** The compass and the map are told the member's own colour. */
    @Test
    public void theTintIsTheColour() {
        for (FellowshipColor color : FellowshipColor.values()) {
            assertEquals(color.getRgb(), Integer.parseInt(
                    color.getTint().substring(1), 16));
        }
    }

    /** It is a colour and nothing else: no alpha rides along with it. */
    @Test
    public void aColourCarriesNoAlphaOfItsOwn() {
        for (FellowshipColor color : FellowshipColor.values()) {
            assertEquals(color.getId() + " is three bytes",
                    color.getRgb() & 0xFFFFFF, color.getRgb());
        }
    }

    /** The wire and the config still name them as they always did. */
    @Test
    public void theWireAndConfigNamesAreUnchanged() {
        for (FellowshipColor color : FellowshipColor.values()) {
            assertEquals(color, FellowshipColor.fromNetworkId(color.getNetworkId()));
            assertEquals(color, FellowshipColor.fromId(color.getId()));
        }
    }
}
