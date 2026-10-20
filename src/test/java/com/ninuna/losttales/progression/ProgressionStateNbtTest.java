package com.ninuna.losttales.progression;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** A character's progression saves and reads back whole, and anything else is refused. */
public final class ProgressionStateNbtTest {

    @Test
    public void aFullStateReadsBackAsItWasWritten() {
        ProgressionState state = sample();
        ProgressionState read = ProgressionStateNbt.read(ProgressionStateNbt.write(state));
        assertEquals(state.skills(), read.skills());
        assertEquals(12, read.getCharacterLevel());
        assertEquals(40L, read.getCharacterInto());
        assertEquals(state.perks(), read.perks());
        assertTrue(read.hasDeed("losttales:greenway"));
        assertEquals(7L, read.deedCounter("losttales:troll_slayer"));
        assertEquals(2, read.getResets());
        assertEquals(ProgressionStateNbt.write(state), ProgressionStateNbt.write(read));
    }

    @Test
    public void anotherVersionIsRefused() {
        NBTTagCompound tag = ProgressionStateNbt.write(sample());
        tag.setInteger(ProgressionStateNbt.TAG_VERSION, ProgressionStateNbt.VERSION + 1);
        refused(tag);
    }

    @Test
    public void aKeyNothingReadsIsRefused() {
        NBTTagCompound tag = ProgressionStateNbt.write(sample());
        tag.setString("Renown", "50");
        refused(tag);
    }

    @Test
    public void aMissingKeyIsRefused() {
        NBTTagCompound tag = ProgressionStateNbt.write(sample());
        tag.removeTag(ProgressionStateNbt.TAG_RESETS);
        refused(tag);
    }

    @Test
    public void aSkillPastTheTopOrWrittenTwiceIsRefused() {
        NBTTagCompound tag = ProgressionStateNbt.write(new ProgressionState());
        NBTTagList skills = new NBTTagList();
        skills.appendTag(skill("losttales:archery", 101));
        tag.setTag(ProgressionStateNbt.TAG_SKILLS, skills);
        refused(tag);
        skills = new NBTTagList();
        skills.appendTag(skill("losttales:archery", 5));
        skills.appendTag(skill("losttales:archery", 6));
        tag.setTag(ProgressionStateNbt.TAG_SKILLS, skills);
        refused(tag);
    }

    @Test
    public void aCounterForADeedAlreadyDoneIsRefused() {
        NBTTagCompound tag = ProgressionStateNbt.write(sample());
        NBTTagList counters = tag.getTagList(ProgressionStateNbt.TAG_DEED_COUNTERS, 10);
        NBTTagCompound counter = new NBTTagCompound();
        counter.setString(ProgressionStateNbt.TAG_ID, "losttales:greenway");
        counter.setLong(ProgressionStateNbt.TAG_COUNT, 1L);
        counters.appendTag(counter);
        refused(tag);
    }

    private static NBTTagCompound skill(String id, int level) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(ProgressionStateNbt.TAG_ID, id);
        entry.setInteger(ProgressionStateNbt.TAG_LEVEL, level);
        entry.setDouble(ProgressionStateNbt.TAG_INTO, 0.0D);
        return entry;
    }

    private static void refused(NBTTagCompound tag) {
        try {
            ProgressionStateNbt.read(tag);
            fail("read a state that is out of shape");
        } catch (IllegalArgumentException expected) {
            // Refused whole.
        }
    }

    static ProgressionState sample() {
        ProgressionState state = new ProgressionState();
        state.setSkill("losttales:archery", new SkillStanding(31, 12.5D));
        state.setSkill("losttales:lore", new SkillStanding(100, 0.0D));
        state.setCharacter(12, 40L);
        state.setPerkRank("losttales:archery", "steady_draw", 2);
        state.setPerkRank("losttales:lore", "sage", 1);
        state.addDeed("losttales:greenway");
        state.setDeedCounter("losttales:troll_slayer", 7L);
        state.setResets(2);
        return state;
    }
}
