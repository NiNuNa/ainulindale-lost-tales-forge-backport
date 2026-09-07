package com.ninuna.losttales.character.identity;

import com.ninuna.losttales.character.model.CharacterKind;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * How an identity is named. One rule, so the chat, the nameplates, the
 * party and the LOTR records cannot answer the same question three
 * different ways for the same player.
 */
public final class PlayableIdentityDisplayNameTest {

    private static final UUID OWNER =
            UUID.fromString("f0000000-0000-0000-0000-00000000000f");

    @Test
    public void aCharacterGoesByItsOwnName() {
        assertEquals("Aldric",
                PlayableIdentity.displayName(named("Aldric"), "Steve"));
    }

    @Test
    public void anIdentityWithNoCharacterGoesByTheAccount() {
        assertEquals("Steve", PlayableIdentity.displayName(null, "Steve"));
    }

    /**
     * A name that is only spaces is no name. It used to answer three ways
     * across the surfaces that asked, which is exactly what one rule is
     * for.
     */
    @Test
    public void aCharacterWhoseNameIsBlankGoesByTheAccount() {
        assertEquals("Steve", PlayableIdentity.displayName(named("   "), "Steve"));
        assertEquals("Steve", PlayableIdentity.displayName(named(""), "Steve"));
    }

    /** A name is given as it reads, without the spaces around it. */
    @Test
    public void aNameIsTrimmed() {
        assertEquals("Aldric",
                PlayableIdentity.displayName(named("  Aldric  "), "Steve"));
        assertEquals("Steve", PlayableIdentity.displayName(null, "  Steve  "));
    }

    /**
     * With nothing to go by the rule says so rather than inventing a
     * name: a caller with somewhere better to fall back to is the one
     * that knows what that is.
     */
    @Test
    public void withNothingToGoByTheAnswerIsEmpty() {
        assertEquals("", PlayableIdentity.displayName(null, null));
        assertEquals("", PlayableIdentity.displayName(null, ""));
        assertEquals("", PlayableIdentity.displayName(named(" "), "  "));
    }

    /** The account's own identity is named like any other character. */
    @Test
    public void theDefaultCharacterIsNamedLikeAnyOther() {
        RoleplayCharacter account = RoleplayCharacter.builder(OWNER, OWNER)
                .kind(CharacterKind.DEFAULT)
                .slot(CharacterRoster.DEFAULT_SLOT_INDEX)
                .name("Steve")
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .startingFaction("")
                .build();

        assertEquals("Steve", PlayableIdentity.displayName(account, "Steve"));
    }

    private static RoleplayCharacter named(String name) {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(0)
                .name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .startingFaction("lotr:gondor")
                .build();
    }
}
