package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A lore figure's first claim starts it in the faction its file names
 * (CHAR-44), and in no other: refused while LOTR does not know it, its
 * race may not start there, or the server's lists deny it.
 */
public final class LoreCharacterStartingFactionTest {

    @Test
    public void theFileNamesTheFactionTheFigureStartsIn() {
        Factions factions = new Factions().with("lotr:gondor", true, CharacterFactionCategory.HUMAN);
        assertEquals("lotr:gondor", LoreCharacterTransferCoordinator.startingFaction(
                figure(CharacterRaceRegistry.HUMAN, "lotr:gondor"), factions,
                CharacterRaceRegistry.get(CharacterRaceRegistry.HUMAN)));
    }

    @Test
    public void nothingStandsInForAFactionThatCannotBeStartedIn() {
        Factions factions = new Factions()
                .with("lotr:gondor", false, CharacterFactionCategory.HUMAN)
                .with("lotr:hobbit", true, CharacterFactionCategory.HUMAN)
                .with("lotr:bree", true, CharacterFactionCategory.HUMAN);
        assertNull("the server denies it", LoreCharacterTransferCoordinator.startingFaction(
                figure(CharacterRaceRegistry.HUMAN, "lotr:gondor"), factions,
                CharacterRaceRegistry.get(CharacterRaceRegistry.HUMAN)));
        assertNull("LOTR has no such faction", LoreCharacterTransferCoordinator.startingFaction(
                figure(CharacterRaceRegistry.HUMAN, "lotr:arnor"), factions,
                CharacterRaceRegistry.get(CharacterRaceRegistry.HUMAN)));
        assertNull("a Hobbit starts in no Gondor, nor in the Shire for it",
                LoreCharacterTransferCoordinator.startingFaction(
                        figure(CharacterRaceRegistry.HOBBIT, "lotr:gondor"), factions,
                        CharacterRaceRegistry.get(CharacterRaceRegistry.HOBBIT)));
        assertNull("its race gone", LoreCharacterTransferCoordinator.startingFaction(
                figure("myserver:beorning", "lotr:bree"), factions, null));
    }

    private static LoreCharacterDefinition figure(String raceId, String factionId) {
        return new LoreCharacterDefinition("losttales:test", "Test", "", 30,
                new LoreCharacterDefinition.Appearance(raceId,
                        CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0"),
                factionId, CharacterProfile.EMPTY);
    }

    private static final class Factions implements CharacterFactionResolver {
        private final Map<String, CharacterFactionDefinition> known =
                new HashMap<String, CharacterFactionDefinition>();

        Factions with(String id, boolean playable, CharacterFactionCategory category) {
            this.known.put(id, new CharacterFactionDefinition(id, playable, EnumSet.of(category)));
            return this;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public CharacterFactionDefinition resolve(String factionId) {
            return this.known.get(factionId);
        }

        @Override
        public String resolveStartingWaypointId(String factionId, String waypointId,
                                                boolean allowAnyRegion) {
            return null;
        }
    }
}
