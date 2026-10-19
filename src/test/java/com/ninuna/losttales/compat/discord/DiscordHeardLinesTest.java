package com.ninuna.losttales.compat.discord;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * After the live connection drops, the re-read that stands in watches the
 * lines heard live before the drop, each with the stamp it was heard
 * with, so their edits and deletions made while the connection is down
 * reach the game; bounded to the sweep's own reach.
 */
public final class DiscordHeardLinesTest {
    private static final String CHANNEL = "900000000000000001";
    private static final String OTHER = "900000000000000002";

    private static DiscordJson.Message message(String id, String content,
                                               String editedTimestamp) {
        return new DiscordJson.Message(id, "1", "User", false, content,
                Collections.<String, String>emptyMap(), "",
                editedTimestamp, CHANNEL, "", "", null, null, "");
    }

    /** What a worker does once a drop is noticed: the lines heard come into its sweep. */
    private static void takeUp(DiscordHeardLines heard, DiscordMessageSweep sweep,
                               String channelId) {
        for (Map.Entry<String, String> line : heard.linesIn(channelId).entrySet()) {
            sweep.track(line.getKey(), line.getValue());
        }
    }

    /**
     * Lines heard live, one of them edited live, then the connection
     * drops: the first re-read reports the edit made while it was down
     * and the deletion, and nothing the gateway already said.
     */
    @Test
    public void aDropCatchesEditsAndDeletionsOfLinesHeardLive() {
        DiscordHeardLines heard = new DiscordHeardLines();
        heard.heard(CHANNEL, "100", "");
        heard.heard(CHANNEL, "200", "");
        heard.heard(CHANNEL, "300", "");
        // Edited while live: the gateway said so, with its stamp.
        heard.heard(CHANNEL, "200", "2026-10-18T10:00:00.000000+00:00");

        // The worker's own sweep watched none of them: the gateway heard them.
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        assertTrue(sweep.isEmpty());
        takeUp(heard, sweep, CHANNEL);

        // While down: 100 edited, 300 deleted; 200 as the gateway left it.
        DiscordMessageSweep.Changes changes = sweep.apply(Arrays.asList(
                message("100", "rewritten", "2026-10-18T10:05:00.000000+00:00"),
                message("200", "edited live", "2026-10-18T10:00:00.000000+00:00"),
                message("400", "newer", "")));
        assertEquals(1, changes.edited.size());
        assertEquals("100", changes.edited.get(0).id);
        assertEquals(Arrays.asList("300"), changes.deletedIds);
    }

    /** A line deleted live is no longer watched: the re-read says nothing of it. */
    @Test
    public void aLineDeletedLiveIsLetGo() {
        DiscordHeardLines heard = new DiscordHeardLines();
        heard.heard(CHANNEL, "100", "");
        heard.heard(CHANNEL, "200", "");
        heard.forget(CHANNEL, "200");
        assertEquals(Collections.singletonMap("100", ""), heard.linesIn(CHANNEL));
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        takeUp(heard, sweep, CHANNEL);
        assertTrue(sweep.apply(Arrays.asList(message("100", "x", ""),
                message("300", "y", ""))).deletedIds.isEmpty());
    }

    /**
     * Taken up again — a second drop, or a worker a restart started — a
     * line keeps the newer stamp, so an edit already reported is not
     * reported twice, and a stale stamp heard late changes nothing.
     */
    @Test
    public void theNewerStampWins() {
        DiscordHeardLines heard = new DiscordHeardLines();
        heard.heard(CHANNEL, "100", "2026-10-18T10:05:00.000000+00:00");
        heard.heard(CHANNEL, "100", "");
        assertEquals("2026-10-18T10:05:00.000000+00:00",
                heard.linesIn(CHANNEL).get("100"));
        DiscordMessageSweep sweep = new DiscordMessageSweep();
        sweep.track("100", "2026-10-18T10:09:00.000000+00:00");
        takeUp(heard, sweep, CHANNEL);
        assertTrue("the sweep's own later stamp stays", sweep.apply(Arrays.asList(
                message("100", "x", "2026-10-18T10:09:00.000000+00:00"))).edited.isEmpty());
        // A page older than what was heard brings stale words: no edit.
        DiscordMessageSweep stale = new DiscordMessageSweep();
        stale.track("100", "2026-10-18T10:09:00.000000+00:00");
        assertTrue(stale.apply(Arrays.asList(
                message("100", "older", "2026-10-18T10:05:00.000000+00:00"))).edited.isEmpty());
    }

    /**
     * Bounded: a channel keeps its newest lines, as many as one re-read
     * watches, and only the channels a start reads are kept.
     */
    @Test
    public void theLinesHeardAreBounded() {
        DiscordHeardLines heard = new DiscordHeardLines();
        for (int index = 0; index < 300; index++) {
            heard.heard(CHANNEL, Integer.toString(1000 + index), "");
        }
        Map<String, String> kept = heard.linesIn(CHANNEL);
        assertEquals(DiscordMessageSweep.MAX_TRACKED, kept.size());
        assertEquals(DiscordHeardLines.MAX_PER_CHANNEL, kept.size());
        List<String> ids = new java.util.ArrayList<String>(kept.keySet());
        assertEquals("1172", ids.get(0));
        assertEquals("1299", ids.get(ids.size() - 1));

        heard.heard(OTHER, "5", "");
        heard.keepOnly(Collections.singleton(OTHER));
        assertTrue(heard.linesIn(CHANNEL).isEmpty());
        assertEquals(1, heard.linesIn(OTHER).size());

        DiscordHeardLines many = new DiscordHeardLines();
        for (int index = 0; index < DiscordHeardLines.MAX_CHANNELS + 10; index++) {
            many.heard(Integer.toString(10000 + index), "1", "");
        }
        assertTrue(many.linesIn(Integer.toString(10000 + DiscordHeardLines.MAX_CHANNELS))
                .isEmpty());
        many.clear();
        assertTrue(many.linesIn("10000").isEmpty());
    }

    /** What nothing names is not kept. */
    @Test
    public void emptyIdsAreNotHeard() {
        DiscordHeardLines heard = new DiscordHeardLines();
        heard.heard("", "1", "");
        heard.heard(CHANNEL, "", "");
        heard.heard(null, null, null);
        heard.forget(null, null);
        assertTrue(heard.linesIn(CHANNEL).isEmpty());
        assertTrue(heard.linesIn(null).isEmpty());
    }
}
