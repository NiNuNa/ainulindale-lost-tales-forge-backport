package com.ninuna.losttales.compat.discord;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The sweep hears of edits and deletions by looking again: a watched
 * message found with another edit stamp was edited, one missing from a
 * page that reaches back past its place was deleted, and one the page
 * no longer reaches is let go rather than mourned.
 */
public final class DiscordMessageSweepTest {

    private static DiscordJson.Message message(String id, String content,
                                               String editedTimestamp) {
        return new DiscordJson.Message(id, "1", "User", false, content,
                Collections.<String, String>emptyMap(), "",
                editedTimestamp, "", "", "", null, null, "");
    }

    @Test
    public void anotherEditStampIsAnEditAndIsReportedOnce() {
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        sweep.track(message("100", "hello", ""));
        List<DiscordJson.Message> page = Arrays.asList(
                message("100", "hello there", "2026-09-01T00:00:00Z"),
                message("200", "unrelated", ""));
        DiscordMessageSweep.Changes changes = sweep.apply(page);
        assertEquals(1, changes.edited.size());
        assertEquals("hello there", changes.edited.get(0).content);
        assertTrue(changes.deletedIds.isEmpty());
        // The stamp was taken: the same page again reports nothing.
        assertTrue(sweep.apply(page).edited.isEmpty());
        // A further edit reports again.
        assertEquals(1, sweep.apply(Arrays.asList(
                message("100", "third", "2026-09-01T00:01:00Z")))
                .edited.size());
    }

    @Test
    public void missingFromACoveringPageIsDeleted() {
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        sweep.track(message("200", "doomed", ""));
        // The page reaches back to id 100, past 200's place, and 200 is
        // not in it: deleted, and the watch on it ends.
        DiscordMessageSweep.Changes changes = sweep.apply(Arrays.asList(
                message("100", "older", ""),
                message("300", "newer", "")));
        assertEquals(Arrays.asList("200"), changes.deletedIds);
        assertTrue(sweep.isEmpty());
        assertTrue(sweep.apply(Collections.<DiscordJson.Message>emptyList())
                .deletedIds.isEmpty());
    }

    @Test
    public void driftingOutOfThePageIsNotADeletion() {
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        sweep.track(message("100", "old", ""));
        // The page begins at 500: 100 is out of sight, not gone.
        DiscordMessageSweep.Changes changes = sweep.apply(Arrays.asList(
                message("500", "newer", ""),
                message("600", "newest", "")));
        assertTrue(changes.deletedIds.isEmpty());
        assertTrue(changes.edited.isEmpty());
        // The watch ended anyway; nothing more will be said of it.
        assertTrue(sweep.isEmpty());
    }

    @Test
    public void anEmptyPageIsAChannelWithNothingLeft() {
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        sweep.track(message("100", "first", ""));
        sweep.track(message("200", "second", ""));
        DiscordMessageSweep.Changes changes = sweep.apply(
                Collections.<DiscordJson.Message>emptyList());
        assertEquals(Arrays.asList("100", "200"), changes.deletedIds);
        assertTrue(sweep.isEmpty());
    }

    /**
     * A page that could not be read says nothing: bad JSON on a good
     * status must never read as a channel emptied.
     */
    @Test
    public void aPageThatCouldNotBeReadChangesNothing() {
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        sweep.track(message("100", "first", ""));
        sweep.track(message("200", "second", ""));
        DiscordMessageSweep.Changes changes = sweep.apply(
                DiscordJson.parseMessages("{\"not\":\"a page\"}"));
        assertTrue(changes.deletedIds.isEmpty());
        assertTrue(changes.edited.isEmpty());
        // Every watch stays: the next page that can be read is compared
        // as ever, for both.
        DiscordMessageSweep.Changes next = sweep.apply(Arrays.asList(
                message("100", "first, rewritten", "2026-09-01T00:00:00Z"),
                message("300", "newer", "")));
        assertEquals(1, next.edited.size());
        assertEquals("100", next.edited.get(0).id);
        assertEquals(Arrays.asList("200"), next.deletedIds);
    }

    /** An emptied channel names every message watched, so it says which the bound kept. */
    @Test
    public void theWatchIsBounded() {
        DiscordMessageSweep relayed = new DiscordMessageSweep();
        for (int index = 0; index < 200; index++) {
            relayed.track(message(Integer.toString(1000 + index), "x", ""));
        }
        List<String> kept = relayed.apply(
                Collections.<DiscordJson.Message>emptyList()).deletedIds;
        assertEquals(128, kept.size());
        assertEquals("1072", kept.get(0));
        assertEquals("1199", kept.get(127));

        DiscordMessageSweep sweep = new DiscordMessageSweep();
        for (int index = 0; index < 200; index++) {
            sweep.track(message(Integer.toString(1000 + index), "x", ""));
        }
        for (int index = 0; index < 200; index++) {
            sweep.watch(Integer.toString(5000 + index));
        }
        kept = sweep.apply(Collections.<DiscordJson.Message>emptyList()).deletedIds;
        assertEquals(128, kept.size());
        assertEquals("5072", kept.get(0));
        assertEquals("5199", kept.get(127));
    }

    /**
     * A message relayed before the watch began, as after a reload or a
     * restart, is watched without its stamp: the first page only teaches
     * the stamp, and from then on it is watched like any other.
     */
    @Test
    public void aMessageWatchedWithoutItsStampLearnsItFirst() {
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        sweep.watch("100");
        sweep.watch("100");
        // Edited while nothing watched: not reported.
        assertTrue(sweep.apply(Arrays.asList(
                message("100", "edited before", "2026-09-01T00:00:00Z"),
                message("300", "newer", ""))).edited.isEmpty());
        assertEquals(1, sweep.apply(Arrays.asList(
                message("100", "edited again", "2026-09-01T00:01:00Z"),
                message("300", "newer", ""))).edited.size());
        // Gone from a covering page, it was deleted like any other.
        sweep.watch("200");
        assertEquals(Arrays.asList("200"), sweep.apply(Arrays.asList(
                message("100", "edited again", "2026-09-01T00:01:00Z"),
                message("300", "newer", ""))).deletedIds);
        // Watched twice, it is one watch: an emptied channel names it once.
        assertEquals(Arrays.asList("100"), sweep.apply(
                Collections.<DiscordJson.Message>emptyList()).deletedIds);
        // A message already watched keeps the stamp it has.
        DiscordMessageSweep tracked = new DiscordMessageSweep();
        tracked.track(message("100", "hello", ""));
        tracked.watch("100");
        assertEquals(1, tracked.apply(Arrays.asList(
                message("100", "hello there", "2026-09-01T00:00:00Z")))
                .edited.size());
    }
}
