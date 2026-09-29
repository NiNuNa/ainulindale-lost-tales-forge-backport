package com.ninuna.losttales.party.server;

import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A leader may name the party: 24 characters at most, trimmed, with no
 * formatting codes or control characters, and no word of the profanity
 * list. An empty name takes the name away.
 */
public final class PartyNameTest {

    private static final ChatProfanityWords BUNDLED = ChatProfanityCatalog.bundled();

    @Test
    public void anOrdinaryNameAndNoNameAreAllowed() {
        assertEquals(PartyErrorId.NONE,
                PartyService.checkName("The Grey Company", BUNDLED));
        assertEquals("an empty name takes the name away", PartyErrorId.NONE,
                PartyService.checkName("", BUNDLED));
    }

    @Test
    public void twentyFourCharactersIsTheMost() {
        String longest = "abcdefghijklmnopqrstuvwx";
        assertEquals(Party.MAX_NAME_LENGTH, longest.length());
        assertEquals(PartyErrorId.NONE, PartyService.checkName(longest, BUNDLED));
        assertEquals(PartyErrorId.NAME_TOO_LONG,
                PartyService.checkName(longest + "y", BUNDLED));
    }

    @Test
    public void formattingCodesAndControlCharactersAreRefused() {
        assertEquals(PartyErrorId.NAME_NOT_ALLOWED,
                PartyService.checkName("§cRed Company", BUNDLED));
        assertEquals(PartyErrorId.NAME_NOT_ALLOWED,
                PartyService.checkName("Grey\nCompany", BUNDLED));
        assertEquals(PartyErrorId.NAME_NOT_ALLOWED,
                PartyService.checkName("Grey‮Company", BUNDLED));
    }

    @Test
    public void aWordOfTheProfanityListIsRefused() {
        assertEquals(PartyErrorId.NAME_NOT_ALLOWED,
                PartyService.checkName("Shitty Company", BUNDLED));
        ChatProfanityWords server = ChatProfanityWords.parse(
                new String[] {"grumbold=grumpy"}, ChatProfanityWords.MAX_WORDS, null);
        assertEquals(PartyErrorId.NONE,
                PartyService.checkName("Grumbold's Band", BUNDLED));
        assertEquals("the server's own words count too",
                PartyErrorId.NAME_NOT_ALLOWED,
                PartyService.checkName("Grumbold Band", BUNDLED.plus(server)));
    }

    @Test
    public void onlyAWellFormedNameIsStored() {
        assertTrue(Party.isWellFormedName(""));
        assertTrue(Party.isWellFormedName("The Grey Company"));
        assertFalse("untrimmed", Party.isWellFormedName(" Grey "));
        assertFalse(Party.isWellFormedName(null));
        assertFalse(Party.isWellFormedName("abcdefghijklmnopqrstuvwxy"));
    }

    @Test
    public void renamingBumpsTheRevisionOnlyWhenTheNameChanges() {
        Party party = Party.createNew(UUID.randomUUID(), new PartyMember(
                UUID.randomUUID(), UUID.randomUUID(), "Aldric", 1L,
                PartyColor.GREEN), 1L);
        assertEquals("", party.getName());
        long revision = party.getRevision();

        assertTrue(party.rename("The Grey Company"));
        assertEquals("The Grey Company", party.getName());
        assertEquals(revision + 1L, party.getRevision());

        assertFalse("the same name again changes nothing",
                party.rename("The Grey Company"));
        assertEquals(revision + 1L, party.getRevision());

        assertTrue(party.rename(""));
        assertEquals("", party.getName());
    }
}
