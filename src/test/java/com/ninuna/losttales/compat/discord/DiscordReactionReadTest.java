package com.ninuna.losttales.compat.discord;

import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What a return reads of Discord: the reactions a channel's page counts,
 * the bot's own left out, the members listed behind one emoji, and the
 * address that list is read at.
 */
public final class DiscordReactionReadTest {

    @Test
    public void aPageCountsEachMessagesReactionsWithoutTheBot() {
        Map<String, List<DiscordJson.ReactionCount>> counts =
                DiscordJson.parseReactionCounts("[{\"id\":\"2\",\"reactions\":["
                        + "{\"emoji\":{\"id\":null,\"name\":\"😄\"},\"count\":3,\"me\":true,"
                        + "\"count_details\":{\"burst\":0,\"normal\":3}},"
                        + "{\"emoji\":{\"id\":\"556\",\"name\":\"parrot_dance\"},\"count\":2,"
                        + "\"me\":false,\"me_burst\":false,"
                        + "\"count_details\":{\"burst\":1,\"normal\":1}}]},"
                        + "{\"id\":\"1\",\"content\":\"no reactions\"}]");
        assertEquals(1, counts.size());
        List<DiscordJson.ReactionCount> onTwo = counts.get("2");
        assertEquals(2, onTwo.size());
        assertEquals("smile", onTwo.get(0).reactionKey());
        assertEquals("the bot's own reaction is not a member's", 2, onTwo.get(0).members);
        assertFalse(onTwo.get(0).burst);
        assertEquals("😄", onTwo.get(0).pathForm());
        assertEquals("parrot_dance:556", onTwo.get(1).reactionKey());
        assertEquals(2, onTwo.get(1).members);
        assertTrue(onTwo.get(1).burst);
        assertEquals("parrot_dance:556", onTwo.get(1).pathForm());
        assertNull("no listing", DiscordJson.parseReactionCounts("{\"message\":\"x\"}"));
        assertNull(DiscordJson.parseReactionCounts(""));
    }

    @Test
    public void aDeletedCustomEmojiHasNoPathAndNoKey() {
        DiscordJson.ReactionCount nameless = new DiscordJson.ReactionCount("556", "", 1,
                false);
        assertEquals("", nameless.pathForm());
        assertNull(nameless.reactionKey());
    }

    @Test
    public void theMembersBehindAnEmojiAreListedBounded() {
        List<DiscordJson.Reactor> reactors = DiscordJson.parseReactors("["
                + "{\"id\":\"11\",\"username\":\"frodo\",\"global_name\":\"Frodo\"},"
                + "{\"id\":\"12\",\"username\":\"sam\"},"
                + "{\"id\":\"13\",\"username\":\"bot\",\"bot\":true}]");
        assertEquals(3, reactors.size());
        assertEquals("Frodo", reactors.get(0).name);
        assertEquals("sam", reactors.get(1).name);
        assertTrue(reactors.get(2).bot);
        assertNull("a list that cannot be read says nothing",
                DiscordJson.parseReactors("[{\"username\":\"no id\"}]"));
        assertNull(DiscordJson.parseReactors("{\"code\":10008}"));
        StringBuilder many = new StringBuilder("[");
        for (int index = 0; index < 150; index++) {
            many.append(index == 0 ? "" : ",").append("{\"id\":\"").append(1000 + index)
                    .append("\",\"username\":\"m\"}");
        }
        assertEquals(DiscordJson.MAX_REACTORS_READ,
                DiscordJson.parseReactors(many.append("]").toString()).size());
    }

    @Test
    public void theListIsReadAtDiscordsAddress() throws Exception {
        assertEquals("https://discord.com/api/v10/channels/9/messages/8/reactions/"
                        + "parrot_dance:556?limit=100&type=0",
                DiscordHttp.reactorsUrl("9", "8", "parrot_dance:556", false));
        assertTrue(DiscordHttp.reactorsUrl("9", "8", "😄", true)
                .endsWith("/reactions/%F0%9F%98%84?limit=100&type=1"));
        try {
            DiscordHttp.reactorsUrl("9", "../8", "😄", false);
            fail("a message id is digits");
        } catch (java.io.IOException expected) {
            // refused
        }
    }

    /** A topic's pause runs to Discord's whole window; other pauses to a minute. */
    @Test
    public void aTopicsPauseIsTakenWhole() {
        String nineMinutes = "{\"retry_after\":540.5}";
        assertEquals(60000L, DiscordJson.retryAfterMillis(nineMinutes));
        assertEquals(540500L, DiscordJson.retryAfterMillis(nineMinutes,
                DiscordTopicPacing.WINDOW_MILLIS));
        assertEquals(DiscordTopicPacing.WINDOW_MILLIS, DiscordJson.retryAfterMillis(
                "{\"retry_after\":4000}", DiscordTopicPacing.WINDOW_MILLIS));
    }
}
