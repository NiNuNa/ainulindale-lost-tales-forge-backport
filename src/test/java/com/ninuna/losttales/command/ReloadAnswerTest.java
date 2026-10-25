package com.ninuna.losttales.command;

import com.ninuna.losttales.util.EnglishWords;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * What {@code /losttales reload} answers (DATA-11): each kind with what it
 * holds now and each reason a file was left out; a kind whose read fails
 * stays as it was and says so while the others are still read; at most
 * {@link ReloadAnswer#MOST_REASONS} reasons in one answer.
 */
public final class ReloadAnswerTest {

    private static List<String> english(List<IChatComponent> lines) {
        List<String> read = new ArrayList<String>();
        for (IChatComponent line : lines) {
            read.add(EnglishWords.INSTANCE.read(line));
        }
        return read;
    }

    /** Reads every kind but the races, which throw; remembers what it sent on. */
    private static final class Reader implements ReloadAnswer.Reader {
        final List<ReloadKind> spread = new ArrayList<ReloadKind>();

        @Override
        public ReloadAnswer.Outcome read(ReloadKind kind) {
            if (kind == ReloadKind.RACES) {
                throw new IllegalStateException("the characters cannot be read");
            }
            if (kind == ReloadKind.KITS) {
                return ReloadAnswer.Outcome.read(kind,
                        Arrays.asList("dwarf.json: names the race losttales:ent"),
                        Integer.valueOf(5));
            }
            return ReloadAnswer.Outcome.read(kind, Collections.<String>emptyList(),
                    Integer.valueOf(41), Integer.valueOf(2));
        }

        @Override
        public void spread(ReloadKind kind) {
            if (kind == ReloadKind.LORE) {
                throw new IllegalStateException("no world");
            }
            this.spread.add(kind);
        }
    }

    @Test
    public void aKindThatFailsStaysAsItWasAndTheOthersAreRead() {
        Reader reader = new Reader();
        List<ReloadAnswer.Outcome> outcomes = ReloadAnswer.run(
                Arrays.asList(ReloadKind.QUESTS, ReloadKind.RACES, ReloadKind.LORE,
                        ReloadKind.KITS), reader);

        assertEquals(4, outcomes.size());
        assertEquals("the characters cannot be read", outcomes.get(1).failure);
        assertEquals(Arrays.asList(ReloadKind.QUESTS, ReloadKind.KITS), reader.spread);

        List<IChatComponent> lines = ReloadAnswer.lines(outcomes);
        assertEquals(Arrays.asList(
                "Read the quests again: 41 quests, 2 of them the server's own.",
                "Could not read the races and capes again, so they stay as they were:"
                        + " the characters cannot be read",
                "Read the lore characters again: 41 lore characters.",
                "Not every player online was sent them: no world",
                "Read the starting kits again: 5 starting kits. Files left out:",
                "  dwarf.json: names the race losttales:ent"), english(lines));
        assertEquals(EnumChatFormatting.GREEN, lines.get(0).getChatStyle().getColor());
        assertEquals(EnumChatFormatting.RED, lines.get(1).getChatStyle().getColor());
        assertEquals(EnumChatFormatting.YELLOW, lines.get(4).getChatStyle().getColor());
    }

    @Test
    public void theMarkersAndTheRacesSayWhatTheyLeaveToOtherCommands() {
        List<String> lines = english(ReloadAnswer.lines(Arrays.asList(
                ReloadAnswer.Outcome.read(ReloadKind.MARKERS, null,
                        Integer.valueOf(290), Integer.valueOf(1)),
                ReloadAnswer.Outcome.read(ReloadKind.RACES, null,
                        Integer.valueOf(7), Integer.valueOf(12)))));
        assertEquals(4, lines.size());
        assertEquals("Read the map markers and waystone houses again: 290 markers and 1"
                + " waystone houses.", lines.get(0));
        assertEquals(true, lines.get(1).contains("/losttales mapmarker reseed"));
        assertEquals("Read the races and capes again: 7 races and 12 capes.", lines.get(2));
        assertEquals(true, lines.get(3).contains("/losttales reload kits"));
    }

    @Test
    public void oneAnswerSaysAtMostSixtyFourReasons() {
        List<String> many = new ArrayList<String>();
        for (int index = 0; index < 50; index++) {
            many.add("file" + index + ".json: broken");
        }
        List<String> lines = english(ReloadAnswer.lines(Arrays.asList(
                ReloadAnswer.Outcome.read(ReloadKind.QUESTS, many,
                        Integer.valueOf(1), Integer.valueOf(0)),
                ReloadAnswer.Outcome.read(ReloadKind.SKILLS, many,
                        Integer.valueOf(11), Integer.valueOf(20)))));
        // Each kind's head, 50 reasons, then 14 and how many more.
        assertEquals(1 + 50 + 1 + 14 + 1, lines.size());
        assertEquals("And 36 more reasons, in the server's log file.",
                lines.get(lines.size() - 1));
    }
}
