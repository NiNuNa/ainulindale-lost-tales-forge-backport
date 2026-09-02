package com.ninuna.losttales.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The title after a name reads as LOTR names its NPCs, on either side. */
public final class ChatEpithetTest {

    @Test
    public void theEpithetIsThePeopleBeforeTheTitle() {
        assertEquals("Gondor Farmer", ChatEpithet.epithet("Gondor", "Farmer"));
        assertEquals("Farmer", ChatEpithet.epithet("", "Farmer"));
        assertEquals("Farmer", ChatEpithet.epithet(null, " Farmer "));
        // Callers only ask for an epithet when there is a title; the
        // titled-name form below is what guards that.
    }

    @Test
    public void aTitledNameCarriesTheSuffixAndAnUntitledOneNothing() {
        assertEquals("TestChar, the Wood-elf Explorer",
                ChatEpithet.titledName("TestChar", "Wood-elf", "Explorer"));
        assertEquals("Aragorn, the Ranger",
                ChatEpithet.titledName(" Aragorn ", "", "Ranger"));
        assertEquals("Aragorn", ChatEpithet.titledName("Aragorn", "Gondor", ""));
        assertEquals("Aragorn", ChatEpithet.titledName("Aragorn", null, null));
        assertEquals("", ChatEpithet.titledName(null, null, null));
    }
}
