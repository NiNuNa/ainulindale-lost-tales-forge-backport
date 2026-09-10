package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One reaction per reactor per emoji, emoji in the order first used,
 * bounds refused rather than trimmed, and a Discord clear that leaves
 * the players' own reactions standing.
 */
public final class ChatReactionsTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID DISCORD_MEMBER =
            LostTalesChatMessagePacket.discordSenderId("123456789012345678");

    @Test
    public void aReactorReactsOncePerEmoji() {
        ChatReactions reactions = new ChatReactions();
        assertTrue(reactions.set("smile", ALICE, "Aldric", true));
        assertFalse("the same reaction twice changes nothing",
                reactions.set("smile", ALICE, "Aldric", true));
        assertTrue(reactions.set("smile", BOB, "Beren", true));
        assertTrue(reactions.set("joy", ALICE, "Aldric", true));
        assertEquals(3, reactions.total());

        assertTrue(reactions.set("smile", ALICE, "", false));
        assertFalse("nothing there to take back",
                reactions.set("smile", ALICE, "", false));
        assertFalse("an emoji the registry does not know is refused",
                reactions.set("not_an_emoji", ALICE, "Aldric", true));
    }

    @Test
    public void theSummaryIsEachReadersOwn() {
        ChatReactions reactions = new ChatReactions();
        reactions.set("joy", BOB, "Beren", true);
        reactions.set("smile", ALICE, "Aldric", true);
        reactions.set("smile", BOB, "Beren", true);

        ChatReactionSummary forAlice = reactions.summaryFor(ALICE);
        assertEquals("emoji keep the order they were first used in",
                "joy", forAlice.getReactions().get(0).emoji);
        ChatReactionSummary.Reaction smile = forAlice.find("smile");
        assertNotNull(smile);
        assertEquals(2, smile.count);
        assertTrue(smile.mine);
        assertEquals("Aldric", smile.names.get(0));
        assertFalse(forAlice.find("joy").mine);
        assertFalse(reactions.summaryFor(null).find("smile").mine);
        assertNull(forAlice.find("wave"));
    }

    @Test
    public void theNamesShownAreBoundedAndTheCountSaysTheRest() {
        ChatReactions reactions = new ChatReactions();
        for (int index = 0; index < ChatReactionSummary.MAX_NAMES + 3; index++) {
            reactions.set("smile", UUID.randomUUID(), "Reader " + index, true);
        }
        ChatReactionSummary.Reaction smile =
                reactions.summaryFor(ALICE).find("smile");
        assertEquals(ChatReactionSummary.MAX_NAMES + 3, smile.count);
        assertEquals(ChatReactionSummary.MAX_NAMES, smile.names.size());
        assertEquals(3, smile.others());
    }

    @Test
    public void theBoundsRefuseTheNewestRatherThanDropTheOldest() {
        ChatReactions reactions = new ChatReactions();
        for (int index = 0; index < ChatReactions.MAX_REACTORS; index++) {
            assertTrue(reactions.set("smile", UUID.randomUUID(), "", true));
        }
        assertFalse(reactions.set("smile", UUID.randomUUID(), "", true));
        assertEquals(ChatReactions.MAX_REACTORS, reactions.total());
    }

    @Test
    public void aLongNameIsCutToAGlance() {
        ChatReactions reactions = new ChatReactions();
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 50; index++) {
            name.append('x');
        }
        reactions.set("smile", ALICE, name.toString(), true);
        assertEquals(ChatReactionSummary.MAX_NAME_CHARS,
                reactions.summaryFor(ALICE).find("smile").names.get(0).length());
    }

    @Test
    public void aDiscordClearLeavesThePlayersReactions() {
        ChatReactions reactions = new ChatReactions();
        reactions.set("smile", ALICE, "Aldric", true);
        reactions.set("smile", DISCORD_MEMBER, "Nils", true);
        reactions.set("joy", DISCORD_MEMBER, "Nils", true);
        assertEquals("the bridge's own reaction stands for the players alone",
                1, reactions.gameCount("smile"));
        assertEquals(0, reactions.gameCount("joy"));

        assertTrue(reactions.clearDiscord("joy"));
        assertNull(reactions.summaryFor(ALICE).find("joy"));
        assertEquals(2, reactions.summaryFor(ALICE).find("smile").count);

        assertTrue(reactions.clearDiscord(null));
        assertEquals(1, reactions.summaryFor(ALICE).find("smile").count);
        assertFalse(reactions.clearDiscord(null));
    }
}
