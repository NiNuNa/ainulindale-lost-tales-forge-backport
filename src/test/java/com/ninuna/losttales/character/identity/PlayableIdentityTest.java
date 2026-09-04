package com.ninuna.losttales.character.identity;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.party.model.PartyPersonalMarkerOwner;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PlayableIdentityTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CHARACTER =
            UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    public void theAccountIsAnIdentityKeyedByItsOwnId() {
        PlayableIdentity account = PlayableIdentity.account(OWNER);
        assertTrue(account.isAccount());
        assertNull(account.getCharacterId());
        assertEquals(OWNER, account.getGameplayId());
        assertEquals(OWNER, account.getOwnerId());
        assertTrue(account.matchesGameplayId(OWNER));
        assertFalse(account.matchesGameplayId(CHARACTER));
    }

    @Test
    public void aCharacterIsKeyedByItsOwnUuid() {
        PlayableIdentity character = PlayableIdentity.character(OWNER, CHARACTER);
        assertFalse(character.isAccount());
        assertEquals(CHARACTER, character.getCharacterId());
        assertEquals(CHARACTER, character.getGameplayId());
        assertEquals(OWNER, character.getOwnerId());
    }

    @Test
    public void ofReadsANullCharacterAsTheAccount() {
        assertEquals(PlayableIdentity.account(OWNER), PlayableIdentity.of(OWNER, null));
        assertEquals(PlayableIdentity.character(OWNER, CHARACTER),
                PlayableIdentity.of(OWNER, CHARACTER));
        assertNotEquals(PlayableIdentity.account(OWNER),
                PlayableIdentity.character(OWNER, CHARACTER));
    }

    @Test
    public void theGameplayIdRuleIsSharedAndNullTolerant() {
        assertEquals(CHARACTER, PlayableIdentity.gameplayId(CHARACTER, OWNER));
        assertEquals(OWNER, PlayableIdentity.gameplayId(null, OWNER));
        assertNull(PlayableIdentity.gameplayId(null, null));
        assertEquals(PlayableIdentity.gameplayId(null, OWNER),
                PartyPersonalMarkerOwner.resolve(null, OWNER));
        assertEquals(PlayableIdentity.gameplayId(CHARACTER, OWNER),
                PartyPersonalMarkerOwner.resolve(CHARACTER, OWNER));
    }

    @Test
    public void theRosterAnswersWithTheSameRule() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertEquals(OWNER, roster.getActiveGameplayId());
        assertEquals(PlayableIdentity.account(OWNER), PlayableIdentity.fromRoster(roster));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anIdentityNeedsAnOwner() {
        PlayableIdentity.account(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aCharacterIdentityNeedsACharacter() {
        PlayableIdentity.character(OWNER, null);
    }

    @Test
    public void toStringNamesTheKind() {
        assertEquals("account:" + OWNER, PlayableIdentity.account(OWNER).toString());
        assertEquals("character:" + CHARACTER,
                PlayableIdentity.character(OWNER, CHARACTER).toString());
    }
}
