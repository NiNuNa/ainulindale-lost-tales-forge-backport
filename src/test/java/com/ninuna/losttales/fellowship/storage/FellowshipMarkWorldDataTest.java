package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipMark;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * One store holds the marks of both kinds: a character's go-here mark is
 * found by its owner, one an owner, and a fellowship's marks never include
 * one.
 */
public final class FellowshipMarkWorldDataTest {

    private static final UUID FELLOWSHIP = new UUID(2L, 2L);
    private static final UUID OWNER = new UUID(5L, 5L);

    @Test
    public void aGoHereMarkIsFoundByItsOwner() {
        FellowshipMarkWorldData data = store();
        FellowshipMark goHere = FellowshipMark.goHere(new UUID(7L, 7L), OWNER, 0,
                10.0D, 20.0D, 1L);
        data.saveMark(goHere);

        assertSame(goHere, data.getGoHere(OWNER));
        assertNull(data.getGoHere(new UUID(6L, 6L)));
        assertTrue("a fellowship's marks hold no go-here mark",
                data.getMarks(FELLOWSHIP).isEmpty());
    }

    /** A second go-here mark of one owner stands in for the first. */
    @Test
    public void aNewGoHereMarkStandsInForTheOld() {
        FellowshipMarkWorldData data = store();
        data.saveMark(FellowshipMark.goHere(new UUID(7L, 7L), OWNER, 0, 10.0D, 20.0D, 1L));
        FellowshipMark second = FellowshipMark.goHere(new UUID(8L, 8L), OWNER, 0,
                30.0D, 40.0D, 2L);
        data.saveMark(second);

        assertSame(second, data.getGoHere(OWNER));
        assertEquals(1, data.getMarkCount());
        assertNull(data.getMark(new UUID(7L, 7L)));
    }

    @Test
    public void removingAGoHereMarkForgetsItsOwner() {
        FellowshipMarkWorldData data = store();
        data.saveMark(FellowshipMark.goHere(new UUID(7L, 7L), OWNER, 0, 10.0D, 20.0D, 1L));
        data.removeMark(new UUID(7L, 7L));

        assertNull(data.getGoHere(OWNER));
        assertEquals(0, data.getMarkCount());
    }

    /** A fellowship that ends takes its own marks, never anyone's go-here mark. */
    @Test
    public void anEndingFellowshipTakesOnlyItsMarks() {
        FellowshipMarkWorldData data = store();
        data.saveMark(FellowshipMark.ofFellowship(new UUID(9L, 9L), FELLOWSHIP, "Bree",
                OWNER, 0, 1.0D, 2.0D, 3L));
        data.saveMark(FellowshipMark.goHere(new UUID(7L, 7L), OWNER, 0, 10.0D, 20.0D, 1L));

        assertEquals(1, data.removeMarksOf(FELLOWSHIP));
        assertEquals(1, data.getMarkCount());
        assertEquals(new UUID(7L, 7L), data.getGoHere(OWNER).getMarkId());
    }

    /** The index by owner is made again as the store is read. */
    @Test
    public void theOwnerIndexSurvivesASave() {
        FellowshipMarkWorldData data = store();
        data.saveMark(FellowshipMark.goHere(new UUID(7L, 7L), OWNER, 0, 10.0D, 20.0D, 1L));
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);

        FellowshipMarkWorldData read = store();
        read.readFromNBT(saved);

        assertEquals(new UUID(7L, 7L), read.getGoHere(OWNER).getMarkId());
    }

    private static FellowshipMarkWorldData store() {
        return new FellowshipMarkWorldData(FellowshipMarkWorldData.DATA_NAME);
    }
}
