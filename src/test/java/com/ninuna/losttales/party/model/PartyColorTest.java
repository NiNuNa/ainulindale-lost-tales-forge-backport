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

    /** There is a colour for every member a party can hold. */
    @Test
    public void everyMemberHasAColourOfTheirOwn() {
        assertEquals(Party.MAX_MEMBERS, PartyColor.values().length);
    }

    /** Every colour is drawn in a different one. */
    @Test
    public void theEightColoursAreEightColours() {
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
        assertEquals(LostTalesColors.rgb(LostTalesColors.APRICOT),
                PartyColor.ORANGE.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.SALMON),
                PartyColor.RED.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.TEAL),
                PartyColor.TEAL.getRgb());
        assertEquals(LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE),
                PartyColor.ROSE.getRgb());
    }

    /** The wire names the colours 0 to 7, in the order they are given out. */
    @Test
    public void theNetworkIdsRunFromZeroToSeven() {
        for (PartyColor color : PartyColor.values()) {
            assertEquals(color.ordinal(), color.getNetworkId());
        }
    }

    /** The compass and the map are told the member's own colour. */
    @Test
    public void theTintIsTheColour() {
        for (PartyColor color : PartyColor.values()) {
            assertEquals(color.getRgb(), Integer.parseInt(
                    color.getTint().substring(1), 16));
        }
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
