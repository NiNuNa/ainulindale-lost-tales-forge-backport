package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Two ways to ask for a shape, and they answer differently on purpose.
 *
 * <p>Something deciding whether a model is one it knows wants the null;
 * something about to draw wants a body. Handing a renderer the null is a
 * crash on whatever frame an unfilled template first reaches it.</p>
 */
public final class PlayerBodyShapeLookupTest {

    @Test
    public void aKnownModelAnswersTheSameEitherWay() {
        for (String modelId : new String[] {
                CharacterBodyModelRegistry.LOSTTALES_PLAYER,
                CharacterBodyModelRegistry.LOTR_HUMAN,
                CharacterBodyModelRegistry.LOTR_ELF,
                CharacterBodyModelRegistry.LOTR_DWARF,
                CharacterBodyModelRegistry.LOTR_HOBBIT,
                CharacterBodyModelRegistry.LOTR_ORC,
                CharacterBodyModelRegistry.LOTR_URUK,
                CharacterBodyModelRegistry.LOTR_HALF_TROLL}) {
            assertNotNull(modelId, PlayerBodyShape.forModelId(modelId));
            assertEquals(modelId, PlayerBodyShape.forModelId(modelId),
                    PlayerBodyShape.forModelIdOrDefault(modelId));
        }
    }

    @Test
    public void anUnknownModelIsNothingToOneAskerAndABipedToTheOther() {
        for (String modelId : new String[] {"", null, "lotr:ent"}) {
            assertNull(PlayerBodyShape.forModelId(modelId));
            assertEquals(PlayerBodyShape.PLAYER,
                    PlayerBodyShape.forModelIdOrDefault(modelId));
        }
    }
}
