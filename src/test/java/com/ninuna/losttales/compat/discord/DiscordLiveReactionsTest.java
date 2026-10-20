package com.ninuna.losttales.compat.discord;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a return's read of an emoji's members found gives way to the
 * reactions the live connection heard after the read began, so a member
 * who reacts or takes a reaction back in that moment is not undone.
 */
public final class DiscordLiveReactionsTest {
    private static final String MESSAGE = "500";
    private static final String OTHER = "600";

    private static Map<String, String> listed(String... idsAndNames) {
        Map<String, String> listed = new LinkedHashMap<String, String>();
        for (int index = 0; index + 1 < idsAndNames.length; index += 2) {
            listed.put(idsAndNames[index], idsAndNames[index + 1]);
        }
        return listed;
    }

    @Test
    public void whatWasHeardAfterTheReadBeganStands() {
        DiscordLiveReactions reactions = new DiscordLiveReactions();
        // Heard before the read: Discord's answer already holds it.
        reactions.heard(DiscordLiveReactions.Change.ADDED, MESSAGE, "smile", "", "1", "Ann");
        long mark = reactions.mark();
        reactions.heard(DiscordLiveReactions.Change.ADDED, MESSAGE, "smile", "", "2", "Bo");
        reactions.heard(DiscordLiveReactions.Change.REMOVED, MESSAGE, "smile", "", "1", "Ann");
        // Another emoji and another message are none of the read's business.
        reactions.heard(DiscordLiveReactions.Change.ADDED, MESSAGE, "heart", "", "3", "Cy");
        reactions.heard(DiscordLiveReactions.Change.ADDED, OTHER, "smile", "", "4", "Di");

        Map<String, String> found = listed("1", "Ann");
        assertTrue(reactions.overlay(mark, MESSAGE, "smile", "", found));
        assertEquals(listed("2", "Bo"), found);
    }

    @Test
    public void aCustomEmojiIsMatchedByItsId() {
        DiscordLiveReactions reactions = new DiscordLiveReactions();
        long mark = reactions.mark();
        reactions.heard(DiscordLiveReactions.Change.ADDED, MESSAGE, "", "77", "2", "Bo");
        reactions.heard(DiscordLiveReactions.Change.ADDED, MESSAGE, "parrot:88", "88", "3", "Cy");
        Map<String, String> found = listed();
        assertTrue(reactions.overlay(mark, MESSAGE, "partyparrot:77", "77", found));
        assertEquals(Arrays.asList("2"), Arrays.asList(found.keySet().toArray()));
    }

    @Test
    public void aClearHeardSinceTakesEveryoneListedBeforeIt() {
        DiscordLiveReactions reactions = new DiscordLiveReactions();
        long mark = reactions.mark();
        reactions.heard(DiscordLiveReactions.Change.EMOJI_CLEARED, MESSAGE, "smile", "", "", "");
        reactions.heard(DiscordLiveReactions.Change.ADDED, MESSAGE, "smile", "", "3", "Cy");
        Map<String, String> found = listed("1", "Ann", "2", "Bo");
        assertTrue(reactions.overlay(mark, MESSAGE, "smile", "", found));
        assertEquals(listed("3", "Cy"), found);

        DiscordLiveReactions all = new DiscordLiveReactions();
        long before = all.mark();
        all.heard(DiscordLiveReactions.Change.ALL_CLEARED, MESSAGE, "", "", "", "");
        Map<String, String> emptied = listed("1", "Ann");
        assertTrue(all.overlay(before, MESSAGE, "heart", "", emptied));
        assertTrue(emptied.isEmpty());
    }

    @Test
    public void nothingHeardSinceLeavesTheReadAsItIs() {
        DiscordLiveReactions reactions = new DiscordLiveReactions();
        reactions.heard(DiscordLiveReactions.Change.REMOVED, MESSAGE, "smile", "", "1", "Ann");
        Map<String, String> found = listed("1", "Ann");
        assertTrue(reactions.overlay(reactions.mark(), MESSAGE, "smile", "", found));
        assertEquals(listed("1", "Ann"), found);
    }

    /** A read older than what the record still holds cannot be vouched for. */
    @Test
    public void aReadOlderThanTheRecordIsNotHanded() {
        DiscordLiveReactions reactions = new DiscordLiveReactions();
        long mark = reactions.mark();
        for (int index = 0; index <= DiscordLiveReactions.MAX_HEARD; index++) {
            reactions.heard(DiscordLiveReactions.Change.ADDED, OTHER, "smile", "",
                    String.valueOf(index), "x");
        }
        assertFalse(reactions.overlay(mark, MESSAGE, "smile", "", listed()));
        assertTrue("one begun within the record is",
                reactions.overlay(mark + 1L, MESSAGE, "smile", "", listed()));

        DiscordLiveReactions cleared = new DiscordLiveReactions();
        long begun = cleared.mark();
        cleared.heard(DiscordLiveReactions.Change.ADDED, MESSAGE, "smile", "", "1", "Ann");
        cleared.clear();
        assertFalse(cleared.overlay(begun, MESSAGE, "smile", "", listed()));
        assertTrue(cleared.overlay(cleared.mark(), MESSAGE, "smile", "", listed()));
    }
}
