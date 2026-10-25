package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterGear;
import java.util.UUID;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the stage keeps of the server's answers about a character's gear
 * (CHAR-42): one question per character and roster revision, each answer
 * kept for the revision it was asked at, nothing kept unasked.
 */
public final class ClientCharacterGearCacheTest {

    private static final UUID BEREN = UUID.randomUUID();
    private static final long START = 1000L;

    @Before
    @After
    public void forget() {
        ClientCharacterGearCache.clear();
    }

    @Test
    public void anAnswerNobodyAskedForIsDropped() {
        ClientCharacterGearCache.accept(BEREN, CharacterGear.NONE);
        assertNull(ClientCharacterGearCache.known(BEREN, 7L));
    }

    @Test
    public void anAnswerIsKeptForTheRevisionItWasAskedAt() {
        assertTrue(ClientCharacterGearCache.markAsked(BEREN, 7L, START));
        ClientCharacterGearCache.accept(BEREN, CharacterGear.NONE);
        assertSame(CharacterGear.NONE, ClientCharacterGearCache.known(BEREN, 7L));
        assertNull("the roster changed since", ClientCharacterGearCache.known(BEREN, 8L));
    }

    @Test
    public void aQuestionWaitsForItsAnswerBeforeItIsAskedAgain() {
        assertTrue(ClientCharacterGearCache.markAsked(BEREN, 7L, START));
        assertFalse(ClientCharacterGearCache.markAsked(BEREN, 7L, START + 1L));
        assertFalse(ClientCharacterGearCache.markAsked(BEREN, 7L,
                START + ClientCharacterGearCache.ANSWER_NANOS - 1L));
        assertTrue("an answer lost on the way is asked for again",
                ClientCharacterGearCache.markAsked(BEREN, 7L,
                        START + ClientCharacterGearCache.ANSWER_NANOS));
    }

    @Test
    public void aChangedRosterAsksAtOnce() {
        assertTrue(ClientCharacterGearCache.markAsked(BEREN, 7L, START));
        assertTrue(ClientCharacterGearCache.markAsked(BEREN, 8L, START + 1L));
        ClientCharacterGearCache.accept(BEREN, CharacterGear.NONE);
        assertSame(CharacterGear.NONE, ClientCharacterGearCache.known(BEREN, 8L));
        assertNull(ClientCharacterGearCache.known(BEREN, 7L));
    }

    @Test
    public void aSecondAnswerToOneQuestionIsDropped() {
        CharacterGear held = CharacterGear.of(new ItemStack[] {
                null, null, null, null, new ItemStack(new ItemSnowball())});
        assertTrue(ClientCharacterGearCache.markAsked(BEREN, 7L, START));
        ClientCharacterGearCache.accept(BEREN, held);
        ClientCharacterGearCache.accept(BEREN, CharacterGear.NONE);
        assertSame(held, ClientCharacterGearCache.known(BEREN, 7L));
    }

    @Test
    public void leavingTheServerForgetsEverything() {
        assertTrue(ClientCharacterGearCache.markAsked(BEREN, 7L, START));
        ClientCharacterGearCache.accept(BEREN, CharacterGear.NONE);
        ClientCharacterGearCache.clear();
        assertNull(ClientCharacterGearCache.known(BEREN, 7L));
        assertTrue("a question is asked afresh",
                ClientCharacterGearCache.markAsked(BEREN, 7L, START + 1L));
    }
}
