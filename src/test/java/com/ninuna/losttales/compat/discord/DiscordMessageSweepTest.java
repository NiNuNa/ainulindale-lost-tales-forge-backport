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
                editedTimestamp);
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

    @Test
    public void theWatchIsBounded() {
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        for (int index = 0; index < 200; index++) {
            sweep.track(message(Integer.toString(1000 + index), "x", ""));
        }
        assertEquals(128, sweep.size());
    }
}
