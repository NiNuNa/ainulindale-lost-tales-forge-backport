package com.ninuna.losttales.compat.discord;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Once the live connection is back, each bridged message whose reactions
 * Discord counts otherwise than the game holds them is put right: the
 * members read where Discord has some, the game's taken away where it has
 * none, a custom emoji matched by its id; bounded per return.
 */
public final class DiscordReturnCheckTest {
    private static final String CHANNEL = "900000000000000001";
    private static final String MESSAGE = "800000000000000001";
    private static final String UNICORN = "🦄";

    private static DiscordJson.ReactionCount count(String id, String name, int members) {
        return new DiscordJson.ReactionCount(id, name, members, false);
    }

    private static Map<String, Integer> held(Object... keysAndCounts) {
        Map<String, Integer> held = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < keysAndCounts.length; index += 2) {
            held.put((String)keysAndCounts[index], (Integer)keysAndCounts[index + 1]);
        }
        return held;
    }

    private static List<DiscordReturnCheck.Fix> fixes(List<DiscordJson.ReactionCount> onDiscord,
                                                      Map<String, Integer> inGame) {
        return DiscordReturnCheck.fixesFor(CHANNEL, MESSAGE, onDiscord, inGame);
    }

    @Test
    public void reactionsTheGameHoldsAsDiscordCountsThemNeedNothing() {
        assertTrue(fixes(Arrays.asList(count("", "😄", 2), count("", UNICORN, 1)),
                held("smile", 2, UNICORN, 1)).isEmpty());
        assertTrue(fixes(Collections.<DiscordJson.ReactionCount>emptyList(),
                held()).isEmpty());
        assertTrue("only the bot's own reaction, standing for others elsewhere",
                fixes(Arrays.asList(count("", "😄", 0)), held()).isEmpty());
        assertTrue(fixes(null, null).isEmpty());
    }

    /** A member reacted while the connection was down: their emoji's members are read. */
    @Test
    public void aReactionMadeMeanwhileIsRead() {
        List<DiscordReturnCheck.Fix> found = fixes(Arrays.asList(
                count("", "😄", 3), count("", UNICORN, 1)), held("smile", 2));
        assertEquals(2, found.size());
        assertEquals("smile", found.get(0).key);
        assertEquals("😄", found.get(0).pathForm);
        assertTrue(found.get(0).reads());
        assertTrue(found.get(0).alone);
        assertEquals(UNICORN, found.get(1).key);
    }

    /**
     * A reaction taken back meanwhile: read when Discord still has some
     * of that emoji, taken away without a read when it has none, and an
     * emoji gone from the message altogether taken away too.
     */
    @Test
    public void aReactionTakenBackMeanwhileIsPutRight() {
        List<DiscordReturnCheck.Fix> fewer = fixes(Arrays.asList(count("", "😄", 1)),
                held("smile", 2));
        assertEquals(1, fewer.size());
        assertTrue(fewer.get(0).reads());

        List<DiscordReturnCheck.Fix> none = fixes(Arrays.asList(count("", "😄", 0)),
                held("smile", 1));
        assertEquals(1, none.size());
        assertFalse(none.get(0).reads());
        assertEquals("smile", none.get(0).key);

        List<DiscordReturnCheck.Fix> gone = fixes(
                Collections.<DiscordJson.ReactionCount>emptyList(),
                held(UNICORN, 1, "partyparrot:556", 2));
        assertEquals(2, gone.size());
        assertFalse(gone.get(0).reads());
        assertFalse(gone.get(1).reads());
        assertEquals("556", gone.get(1).customId);
    }

    /** A custom emoji is matched by its id, whatever it is called on Discord now. */
    @Test
    public void aCustomEmojiIsMatchedByItsId() {
        assertTrue(fixes(Arrays.asList(count("556", "parrot_dance", 2)),
                held("partyparrot:556", 2)).isEmpty());
        List<DiscordReturnCheck.Fix> more = fixes(Arrays.asList(
                count("556", "parrot_dance", 3)), held("partyparrot:556", 2));
        assertEquals(1, more.size());
        assertEquals("parrot_dance:556", more.get(0).pathForm);
        assertEquals("556", more.get(0).customId);
        assertTrue(more.get(0).alone);
    }

    /**
     * A custom emoji named as one of the chat's own shares that emoji's
     * key: the two are counted together, and their fixes only add.
     */
    @Test
    public void twoEmojiUnderOneKeyOnlyAdd() {
        assertTrue(fixes(Arrays.asList(count("", "😄", 1), count("777", "smile", 1)),
                held("smile", 2)).isEmpty());
        List<DiscordReturnCheck.Fix> more = fixes(Arrays.asList(count("", "😄", 2),
                count("777", "smile", 1)), held("smile", 2));
        assertEquals(2, more.size());
        assertFalse(more.get(0).alone);
        assertFalse(more.get(1).alone);
    }

    /** One page a channel, read again first after a pause; a bounded number of fixes. */
    @Test
    public void aReturnIsBounded() {
        DiscordReturnCheck check = new DiscordReturnCheck(Arrays.asList("1", "2"));
        assertFalse(check.isDone());
        assertEquals("1", check.nextChannel());
        check.readAgain("1");
        assertEquals("1", check.nextChannel());
        assertEquals("2", check.nextChannel());
        assertNull(check.nextChannel());
        assertTrue(check.isDone());
        DiscordReturnCheck.Fix fix = new DiscordReturnCheck.Fix(CHANNEL, MESSAGE,
                "smile", "", "😄", false, true);
        for (int index = 0; index < DiscordReturnCheck.MAX_FIXES; index++) {
            assertTrue(check.offer(fix));
        }
        assertFalse("past the bound", check.offer(fix));
        assertFalse(check.isDone());
        int made = 0;
        while (check.pollFix() != null) {
            made++;
        }
        assertEquals(DiscordReturnCheck.MAX_FIXES, made);
        assertTrue(check.isDone());
        assertFalse("the bound is for the whole return", check.offer(fix));
    }
}
