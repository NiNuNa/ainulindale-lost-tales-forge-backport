package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateSnapshot;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.component.LotrProgressionStateComponent;
import com.ninuna.losttales.compat.lotr.LotrFellowshipRules;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * How many fellowships a character may lead is LOTR's count of the
 * Middle-earth achievements in its own LOTR state (FEL-08): for a
 * character nobody plays, the progression saved with it as it was last
 * played, never another character's, and one where none is saved.
 */
public final class FellowshipLeadLimitsTest {

    private static final UUID OWNER = new UUID(1L, 1L);
    private static final UUID ALDRIC = new UUID(2L, 2L);
    private static final UUID BEREN = new UUID(3L, 3L);

    @Test
    public void everyTwentyAchievementsLeadOneMore() {
        assertEquals(1, LotrFellowshipRules.leadLimitFor(0));
        assertEquals(1, LotrFellowshipRules.leadLimitFor(19));
        assertEquals(2, LotrFellowshipRules.leadLimitFor(20));
        assertEquals(3, LotrFellowshipRules.leadLimitFor(45));
        assertEquals("no count below none", 1, LotrFellowshipRules.leadLimitFor(-5));
    }

    @Test
    public void theProgressionSavedWithTheCharacterIsRead() {
        CharacterPlayerStateWorldData states = states(OWNER);
        save(states, OWNER, ALDRIC, progression("Aldric's"));
        save(states, OWNER, BEREN, progression("Beren's"));

        NBTTagCompound read = FellowshipLeadLimits.savedProgressionOf(states,
                member(ALDRIC, OWNER));
        assertEquals("Aldric's", read.getString("Marker"));
    }

    @Test
    public void noneIsReadForAnotherAccountsCharacterOrOneNeverSaved() {
        CharacterPlayerStateWorldData states = states(OWNER);
        save(states, OWNER, ALDRIC, progression("Aldric's"));

        assertNull("another account's file says nothing of its characters",
                FellowshipLeadLimits.savedProgressionOf(states,
                        member(ALDRIC, new UUID(9L, 9L))));
        assertNull("no state kept for Beren yet",
                FellowshipLeadLimits.savedProgressionOf(states, member(BEREN, OWNER)));
        assertNull("a figure nobody holds", FellowshipLeadLimits.savedProgressionOf(states,
                member(ALDRIC, FellowshipMember.UNHELD)));
    }

    @Test
    public void aCharacterWithNothingSavedLeadsOne() {
        assertEquals(LotrFellowshipRules.LEAST_LEAD_LIMIT,
                LotrFellowshipRules.leadLimit((NBTTagCompound) null));
        assertEquals("a saved state without achievements",
                LotrFellowshipRules.LEAST_LEAD_LIMIT,
                LotrFellowshipRules.leadLimit(new NBTTagCompound()));
    }

    private static CharacterPlayerStateWorldData states(UUID owner) {
        return new CharacterPlayerStateWorldData(CharacterPlayerStateWorldData.dataName(owner));
    }

    private static void save(CharacterPlayerStateWorldData states, UUID owner,
                             UUID character, NBTTagCompound progression) {
        CharacterPlayerStateAccount account = states.getOrCreateAccount(owner);
        NBTTagCompound component = new NBTTagCompound();
        component.setInteger("Version", 1);
        component.setTag("Progression", progression);
        Map<String, NBTTagCompound> components = new HashMap<String, NBTTagCompound>();
        components.put(LotrProgressionStateComponent.ID, component);
        account.putRecord(new CharacterPlayerStateRecord(character,
                new CharacterPlayerStateSnapshot(character, 1L, 10L,
                        CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION, components),
                null));
        states.saveAccount(account);
    }

    private static NBTTagCompound progression(String marker) {
        NBTTagCompound progression = new NBTTagCompound();
        progression.setString("Marker", marker);
        return progression;
    }

    private static FellowshipMember member(UUID identity, UUID owner) {
        return new FellowshipMember(identity, owner, "Aldric", 1L, FellowshipColor.GREEN);
    }
}
