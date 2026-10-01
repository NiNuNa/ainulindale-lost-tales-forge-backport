package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every fellowship has a name its leader gives it: 32 characters at most,
 * trimmed, with no formatting codes or control characters, no word of the
 * profanity list, and none another fellowship of the character has.
 */
public final class FellowshipNameTest {

    private static final ChatProfanityWords BUNDLED = ChatProfanityCatalog.bundled();

    @Test
    public void anOrdinaryNameIsAllowedAndNoNameIsMissing() {
        assertEquals(FellowshipErrorId.NONE,
                FellowshipService.checkName("The Grey Company", BUNDLED));
        assertEquals(FellowshipErrorId.NAME_MISSING,
                FellowshipService.checkName("", BUNDLED));
    }

    @Test
    public void thirtyTwoCharactersIsTheMost() {
        String longest = "abcdefghijklmnopqrstuvwxyz012345";
        assertEquals(Fellowship.MAX_NAME_LENGTH, longest.length());
        assertEquals(FellowshipErrorId.NONE, FellowshipService.checkName(longest, BUNDLED));
        assertEquals(FellowshipErrorId.NAME_TOO_LONG,
                FellowshipService.checkName(longest + "y", BUNDLED));
    }

    @Test
    public void formattingCodesAndControlCharactersAreRefused() {
        assertEquals(FellowshipErrorId.NAME_NOT_ALLOWED,
                FellowshipService.checkName("§cRed Company", BUNDLED));
        assertEquals(FellowshipErrorId.NAME_NOT_ALLOWED,
                FellowshipService.checkName("Grey\nCompany", BUNDLED));
        assertEquals(FellowshipErrorId.NAME_NOT_ALLOWED,
                FellowshipService.checkName("Grey‮Company", BUNDLED));
    }

    @Test
    public void aWordOfTheProfanityListIsRefused() {
        assertEquals(FellowshipErrorId.NAME_NOT_ALLOWED,
                FellowshipService.checkName("Shitty Company", BUNDLED));
        ChatProfanityWords server = ChatProfanityWords.parse(
                new String[] {"grumbold=grumpy"}, ChatProfanityWords.MAX_WORDS, null);
        assertEquals(FellowshipErrorId.NONE,
                FellowshipService.checkName("Grumbold's Band", BUNDLED));
        assertEquals("the server's own words count too",
                FellowshipErrorId.NAME_NOT_ALLOWED,
                FellowshipService.checkName("Grumbold Band", BUNDLED.plus(server)));
    }

    @Test
    public void onlyAWellFormedNameIsStored() {
        assertFalse(Fellowship.isWellFormedName(""));
        assertTrue(Fellowship.isWellFormedName("The Grey Company"));
        assertFalse("untrimmed", Fellowship.isWellFormedName(" Grey "));
        assertFalse(Fellowship.isWellFormedName(null));
        assertFalse(Fellowship.isWellFormedName("abcdefghijklmnopqrstuvwxyz0123456"));
    }

    @Test
    public void aCharacterHasOneFellowshipOfAName() {
        Fellowship grey = named("The Grey Company");
        Fellowship rangers = named("Rangers");
        assertTrue("whatever its case", FellowshipService.hasFellowshipNamed(
                Arrays.asList(grey, rangers), "the grey company", null));
        assertFalse("its own name is no other's", FellowshipService.hasFellowshipNamed(
                Arrays.asList(grey, rangers), "The Grey Company", grey.getFellowshipId()));
    }

    @Test
    public void renamingBumpsTheRevisionOnlyWhenTheNameChanges() {
        Fellowship fellowship = named("The Grey Company");
        long revision = fellowship.getRevision();

        assertTrue(fellowship.rename("Rangers of the North"));
        assertEquals("Rangers of the North", fellowship.getName());
        assertEquals(revision + 1L, fellowship.getRevision());

        assertFalse("the same name again changes nothing",
                fellowship.rename("Rangers of the North"));
        assertEquals(revision + 1L, fellowship.getRevision());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNameCannotBeTakenAway() {
        named("The Grey Company").rename("");
    }

    private static Fellowship named(String name) {
        return Fellowship.createNew(UUID.randomUUID(), name, new FellowshipMember(
                UUID.randomUUID(), UUID.randomUUID(), "Aldric", 1L,
                FellowshipColor.GREEN), 1L);
    }
}
