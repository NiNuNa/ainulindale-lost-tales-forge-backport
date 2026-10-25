package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.storage.NbtQuarantine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The record of when each world quest schedule last started its quest
 * (QUEST-36): an entry it cannot read, or the earlier of two for one
 * line, is set aside and written back, never dropped; a full record
 * forgets no line and starts nothing for a line it has no room for; a
 * record it cannot read whole is kept as it was and starts nothing
 * (DATA-10).
 */
public final class WorldQuestSchedulesTest {
    private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Berlin");
    private static final String LINE = "losttales:server/wolf_hunt saturday 18:00";

    /** 17 October 2026 is a Saturday. */
    private static long at(int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(ZONE, Locale.ROOT);
        calendar.clear();
        calendar.set(2026, Calendar.OCTOBER, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static WorldQuestSchedules.Store store() {
        return new WorldQuestSchedules.Store(WorldQuestSchedules.Store.DATA_NAME);
    }

    private static NBTTagCompound start(String line, long at) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Line", line);
        entry.setLong("At", at);
        return entry;
    }

    /** A save as the store writes it, with the starts and the quarantine given. */
    private static NBTTagCompound saved(List<NBTTagCompound> starts,
                                        List<NBTTagCompound> quarantine) {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("DataVersion", WorldQuestSchedules.Store.DATA_VERSION);
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound entry : starts) {
            list.appendTag(entry);
        }
        saved.setTag("Starts", list);
        NbtQuarantine.write(saved, quarantine);
        return saved;
    }

    private static NBTTagCompound written(WorldQuestSchedules.Store store) {
        NBTTagCompound written = new NBTTagCompound();
        store.writeToNBT(written);
        return written;
    }

    private static List<NBTTagCompound> quarantineOf(NBTTagCompound saved) {
        NbtQuarantine.Read read = NbtQuarantine.read(saved);
        assertTrue("the quarantine is written in the shared layout",
                read.isSupported());
        return read.getEntries();
    }

    private static List<WorldQuestSchedule> schedules(String... lines) {
        List<WorldQuestSchedule> schedules = new ArrayList<WorldQuestSchedule>();
        for (String line : lines) {
            schedules.add(WorldQuestSchedule.parse(line));
        }
        return schedules;
    }

    @Test
    public void startsAndTheQuarantineRoundTrip() {
        NBTTagCompound aside = NbtQuarantine.entry("missing_time", "StartIndex", 3,
                start("losttales:a monday 10:00", 1L));
        WorldQuestSchedules.Store store = store();
        store.readFromNBT(saved(Arrays.asList(start(LINE, 42L),
                start("losttales:b every 3 days 20:00", 7L)),
                Collections.singletonList(aside)));
        assertTrue(store.isUsable());
        assertEquals(42L, store.lastStart(LINE));
        assertEquals(7L, store.lastStart("losttales:b every 3 days 20:00"));

        NBTTagCompound written = written(store);
        assertEquals(WorldQuestSchedules.Store.DATA_VERSION,
                written.getInteger("DataVersion"));
        assertEquals(Collections.singletonList(aside), quarantineOf(written));
        WorldQuestSchedules.Store again = store();
        again.readFromNBT(written);
        assertTrue("read again, it is written the same", written.equals(written(again)));
        assertEquals(42L, again.lastStart(LINE));
    }

    /** An entry with a bad line or no time is set aside whole, and stays so across saves. */
    @Test
    public void anEntryThatCannotBeReadIsSetAsideAndWrittenBack() {
        NBTTagCompound noLine = new NBTTagCompound();
        noLine.setLong("At", 5L);
        NBTTagCompound emptyLine = start("", 5L);
        StringBuilder longLine = new StringBuilder();
        while (longLine.length() <= WorldQuestSchedules.Store.MAX_LINE_LENGTH) {
            longLine.append("x");
        }
        NBTTagCompound tooLong = start(longLine.toString(), 5L);
        NBTTagCompound noTime = new NBTTagCompound();
        noTime.setString("Line", "losttales:c friday 12:00");
        NBTTagCompound intTime = new NBTTagCompound();
        intTime.setString("Line", "losttales:d friday 12:00");
        intTime.setInteger("At", 5);
        List<NBTTagCompound> broken = Arrays.asList(noLine, emptyLine, tooLong,
                noTime, intTime);
        List<NBTTagCompound> starts = new ArrayList<NBTTagCompound>(broken);
        starts.add(start(LINE, 9L));

        WorldQuestSchedules.Store store = store();
        store.readFromNBT(saved(starts, Collections.<NBTTagCompound>emptyList()));

        assertTrue(store.isUsable());
        assertEquals(9L, store.lastStart(LINE));
        assertTrue("setting aside is written at the next save", store.isDirty());
        NBTTagCompound written = written(store);
        List<NBTTagCompound> quarantine = quarantineOf(written);
        assertEquals(5, quarantine.size());
        String[] reasons = {"missing_line", "unreadable_line", "unreadable_line",
                "missing_time", "missing_time"};
        for (int index = 0; index < reasons.length; index++) {
            assertEquals(reasons[index], quarantine.get(index).getString("Reason"));
            assertEquals(index, quarantine.get(index).getInteger("StartIndex"));
            assertEquals(broken.get(index),
                    quarantine.get(index).getCompoundTag("OriginalData"));
        }
        assertEquals(1, written.getTagList("Starts", Constants.NBT.TAG_COMPOUND)
                .tagCount());

        WorldQuestSchedules.Store again = store();
        again.readFromNBT(written);
        assertFalse("nothing is set aside twice", again.isDirty());
        assertEquals(quarantine, quarantineOf(written(again)));
    }

    /** Of two entries for one line, the later time stays and the other is set aside. */
    @Test
    public void aDuplicateLineKeepsTheLaterTime() {
        for (boolean laterFirst : new boolean[] {false, true}) {
            NBTTagCompound earlier = start(LINE, 100L);
            NBTTagCompound later = start(LINE, 200L);
            WorldQuestSchedules.Store store = store();
            store.readFromNBT(saved(laterFirst ? Arrays.asList(later, earlier)
                    : Arrays.asList(earlier, later),
                    Collections.<NBTTagCompound>emptyList()));

            assertEquals(200L, store.lastStart(LINE));
            List<NBTTagCompound> quarantine = quarantineOf(written(store));
            assertEquals(1, quarantine.size());
            assertEquals("duplicate_line", quarantine.get(0).getString("Reason"));
            assertEquals(laterFirst ? 1 : 0,
                    quarantine.get(0).getInteger("StartIndex"));
            assertEquals(earlier, quarantine.get(0).getCompoundTag("OriginalData"));
        }
    }

    /**
     * A full record forgets no line: a line it does not hold yet is
     * neither kept nor started, while a line it holds still starts.
     */
    @Test
    public void aFullRecordStartsNothingForANewLineAndForgetsNone() {
        List<NBTTagCompound> starts = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < WorldQuestSchedules.Store.MAX_ENTRIES - 1; index++) {
            starts.add(start("losttales:q" + index + " monday 10:00", 1000L + index));
        }
        starts.add(start(LINE, at(10, 18, 0)));
        WorldQuestSchedules.Store store = store();
        store.readFromNBT(saved(starts, Collections.<NBTTagCompound>emptyList()));
        String newLine = "losttales:server/harvest saturday 18:00";
        long saturdayEvening = at(17, 19, 0);

        List<WorldQuestSchedule> due = WorldQuestSchedules.due(
                schedules(newLine, LINE), store, saturdayEvening, ZONE);

        assertEquals("only the line held starts its quest", 1, due.size());
        assertEquals(LINE, due.get(0).getLine());
        assertEquals(at(17, 18, 0), store.lastStart(LINE));
        assertEquals(0L, store.lastStart(newLine));
        assertTrue(WorldQuestSchedules.due(schedules(newLine, LINE), store,
                saturdayEvening + 60000L, ZONE).isEmpty());
        assertEquals(0L, store.lastStart(newLine));
        assertFalse(store.started(newLine, saturdayEvening));
        for (int index = 0; index < WorldQuestSchedules.Store.MAX_ENTRIES - 1; index++) {
            assertEquals("no line is forgotten", 1000L + index,
                    store.lastStart("losttales:q" + index + " monday 10:00"));
        }
        assertEquals(WorldQuestSchedules.Store.MAX_ENTRIES, written(store)
                .getTagList("Starts", Constants.NBT.TAG_COMPOUND).tagCount());
    }

    /** A schedule starts its quest once for its time, and again only at its next time. */
    @Test
    public void aScheduleStartsOncePerTime() {
        WorldQuestSchedules.Store store = store();
        store.readFromNBT(saved(Collections.<NBTTagCompound>emptyList(),
                Collections.<NBTTagCompound>emptyList()));
        assertEquals(1, WorldQuestSchedules.due(schedules(LINE), store,
                at(17, 18, 5), ZONE).size());
        assertTrue(WorldQuestSchedules.due(schedules(LINE), store,
                at(17, 18, 6), ZONE).isEmpty());
        WorldQuestSchedules.Store restarted = store();
        restarted.readFromNBT(written(store));
        assertTrue("a restart does not start it again", WorldQuestSchedules.due(
                schedules(LINE), restarted, at(17, 20, 0), ZONE).isEmpty());
    }

    /** A line longer than the record keeps is neither kept nor started. */
    @Test
    public void aLineTooLongToKeepStartsNothing() {
        WorldQuestSchedules.Store store = store();
        StringBuilder line = new StringBuilder();
        while (line.length() <= WorldQuestSchedules.Store.MAX_LINE_LENGTH) {
            line.append("y");
        }
        assertFalse(store.started(line.toString(), 5L));
        assertEquals(0L, store.lastStart(line.toString()));
    }

    /**
     * A quarantine that would hold more than its bound keeps the whole
     * record as it was: no schedule starts anything, and it is written
     * back unchanged.
     */
    @Test
    public void aQuarantinePastItsBoundKeepsTheRecordAsItWas() {
        List<NBTTagCompound> aside = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < NbtQuarantine.MAX_ENTRIES; index++) {
            aside.add(NbtQuarantine.entry("missing_time", "StartIndex", index,
                    start("losttales:aside" + index + " monday 10:00", 0L)));
        }
        NBTTagCompound atBound = saved(Collections.singletonList(start(LINE, 1L)),
                aside);
        WorldQuestSchedules.Store full = store();
        full.readFromNBT(atBound);
        assertTrue("a quarantine at its bound is read", full.isUsable());
        assertEquals(aside, quarantineOf(written(full)));

        NBTTagCompound past = saved(Arrays.asList(start(LINE, 1L), start(LINE, 2L)),
                aside);
        WorldQuestSchedules.Store store = store();
        store.readFromNBT(past);

        assertFalse(store.isUsable());
        assertEquals(0L, store.lastStart(LINE));
        assertTrue(WorldQuestSchedules.due(schedules(LINE), store, at(17, 19, 0),
                ZONE).isEmpty());
        assertFalse(store.started(LINE, at(17, 18, 0)));
        assertTrue("written back as it was saved", past.equals(written(store)));
    }

    /** A record of another version, or with a quarantine this build cannot read, is kept as it was. */
    @Test
    public void aRecordThatCannotBeReadIsKeptAsItWas() {
        NBTTagCompound older = saved(Collections.singletonList(start(LINE, 1L)),
                Collections.<NBTTagCompound>emptyList());
        older.setInteger("DataVersion", WorldQuestSchedules.Store.DATA_VERSION - 1);
        older.removeTag("Quarantine");

        NBTTagCompound noQuarantine = saved(Collections.singletonList(start(LINE, 1L)),
                Collections.<NBTTagCompound>emptyList());
        noQuarantine.removeTag("Quarantine");

        NBTTagCompound newerQuarantine = saved(
                Collections.singletonList(start(LINE, 1L)),
                Collections.<NBTTagCompound>emptyList());
        newerQuarantine.getCompoundTag("Quarantine").setInteger("DataVersion",
                NbtQuarantine.CURRENT_DATA_VERSION + 1);

        NBTTagCompound bareQuarantine = saved(
                Collections.singletonList(start(LINE, 1L)),
                Collections.<NBTTagCompound>emptyList());
        bareQuarantine.setTag("Quarantine", new NBTTagList());

        for (NBTTagCompound saved : Arrays.asList(older, noQuarantine,
                newerQuarantine, bareQuarantine)) {
            WorldQuestSchedules.Store store = store();
            store.readFromNBT(saved);
            assertFalse(store.isUsable());
            assertTrue(WorldQuestSchedules.due(schedules(LINE), store,
                    at(17, 19, 0), ZONE).isEmpty());
            assertTrue(saved.equals(written(store)));
        }
    }
}
