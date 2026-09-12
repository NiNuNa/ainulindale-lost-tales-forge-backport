package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * One reaction per reactor per emoji, emoji in the order first used,
 * bounds refused rather than trimmed, a Discord clear that leaves the
 * players' own reactions standing, a foreign emoji that only a Discord
 * member can bring, and a custom emoji followed by its id through a
 * rename.
 */
public final class ChatReactionsTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID DISCORD_MEMBER =
            LostTalesChatMessagePacket.discordSenderId("123456789012345678");
    private static final UUID SECOND_MEMBER =
            LostTalesChatMessagePacket.discordSenderId("223456789012345678");
    private static final UUID THIRD_MEMBER =
            LostTalesChatMessagePacket.discordSenderId("323456789012345678");
    private static final String PARROT = "partyparrot:556";
    /** The same custom emoji as {@link #PARROT}, renamed on Discord. */
    private static final String RENAMED = "parrot_dance:556";
    private static final String UNICORN = "🦄";

    @Test
    public void aForeignEmojiComesOnlyFromDiscordAndPlayersJoinIt() {
        ChatReactions reactions = new ChatReactions();
        assertFalse("a player never brings a foreign emoji",
                reactions.set(PARROT, ALICE, "Aldric", true));
        assertTrue(reactions.set(PARROT, DISCORD_MEMBER, "Nils", true));
        assertTrue("on a message that carries it, a player may add theirs",
                reactions.set(PARROT, ALICE, "Aldric", true));
        assertEquals(1, reactions.gameCount(PARROT));
        assertTrue(reactions.hasForeign());

        assertTrue(reactions.set(PARROT, DISCORD_MEMBER, "", false));
        assertEquals("the player's stays when the member's goes",
                1, reactions.summaryFor(ALICE).find(PARROT).count);
        assertTrue(reactions.set(PARROT, ALICE, "", false));
        assertNull(reactions.summaryFor(ALICE).find(PARROT));
        assertFalse(reactions.hasForeign());
        assertFalse("gone from the message, a player cannot bring it back",
                reactions.set(PARROT, ALICE, "Aldric", true));
    }

    @Test
    public void aMalformedForeignKeyIsRefusedFromAnyone() {
        ChatReactions reactions = new ChatReactions();
        assertFalse(reactions.set("partyparrot:0556", DISCORD_MEMBER, "Nils",
                true));
        assertFalse("named as ours, it is ours by name",
                reactions.set("Cutesy:555", DISCORD_MEMBER, "Nils", true));
        assertFalse("the registry's smile goes by its name",
                reactions.set("😄", DISCORD_MEMBER, "Nils", true));
        assertTrue(reactions.isEmpty());
        assertTrue(reactions.set(UNICORN, DISCORD_MEMBER, "Nils", true));
        assertFalse(reactions.isEmpty());
    }

    @Test
    public void theSaveRestoresAForeignEmojiAPlayerHoldsAlone() {
        ChatReactions reactions = new ChatReactions();
        assertTrue(reactions.restore(PARROT, ALICE, "Aldric"));
        assertFalse("a reactor twice is still refused",
                reactions.restore(PARROT, ALICE, "Aldric"));
        assertFalse(reactions.restore("not_an_emoji", BOB, "Beren"));
        assertEquals(1, reactions.gameCount(PARROT));
        assertTrue(reactions.summaryFor(ALICE).find(PARROT).mine);
    }

    /**
     * A foreign key the registry has come to carry since it was saved is
     * restored under the registry's name, merged with what is already
     * there, whichever of the two the save wrote first.
     */
    @Test
    public void aSavedForeignKeyTheRegistryNowCarriesIsMergedUnderItsName() {
        ChatReactions reactions = new ChatReactions();
        assertTrue(reactions.restore("grinning", ALICE, "Aldric"));
        assertFalse(reactions.renamedOnRestore());
        assertTrue(reactions.restore("😀", DISCORD_MEMBER, "Nils"));
        assertTrue("the same reaction under two keys is kept once",
                reactions.restore("grinning_face:123", ALICE, "Aldric"));
        assertTrue(reactions.renamedOnRestore());
        assertEquals(1, reactions.snapshot().size());
        assertEquals(2, reactions.summaryFor(ALICE).find("grinning").count);
        assertTrue(reactions.summaryFor(ALICE).find("grinning").mine);
        assertFalse(reactions.hasForeign());
        // A kind the save wrote under its own name still takes a reactor once.
        assertTrue(reactions.restore("smile", BOB, "Beren"));
        assertFalse(reactions.restore("smile", BOB, "Beren"));

        ChatReactions reversed = new ChatReactions();
        assertTrue(reversed.restore("😀", ALICE, "Aldric"));
        assertTrue("whichever key the save wrote first",
                reversed.restore("grinning", ALICE, "Aldric"));
        assertEquals(1, reversed.total());
        assertFalse(reversed.restore("grinning_face:0123", BOB, "Beren"));
    }

    @Test
    public void aCustomEmojiIsFoundByItsIdAlone() {
        ChatReactions reactions = new ChatReactions();
        reactions.set(PARROT, DISCORD_MEMBER, "Nils", true);
        reactions.set(UNICORN, DISCORD_MEMBER, "Nils", true);
        reactions.set("smile", ALICE, "Aldric", true);
        assertEquals(Arrays.asList(PARROT), reactions.customKeysOf("556"));
        assertTrue(reactions.customKeysOf("557").isEmpty());
        assertTrue("the end of an id is not the id",
                reactions.customKeysOf("56").isEmpty());
        assertTrue(reactions.customKeysOf("0556").isEmpty());
        assertTrue(reactions.customKeysOf("").isEmpty());
        assertTrue(reactions.customKeysOf(null).isEmpty());
    }

    /** The emoji renamed on Discord from partyparrot to parrot_dance keeps its id. */
    @Test
    public void aDiscordAdditionAfterARenameJoinsTheChipItsIdHas() {
        ChatReactions reactions = new ChatReactions();
        assertEquals("with no key for the id it starts one",
                PARROT, reactions.discordKeyOf(PARROT, "556", DISCORD_MEMBER, true));
        assertTrue(reactions.set(PARROT, DISCORD_MEMBER, "Nils", true));

        String key = reactions.discordKeyOf(RENAMED, "556", SECOND_MEMBER, true);
        assertEquals("one chip, under the name first seen", PARROT, key);
        assertTrue(reactions.set(key, SECOND_MEMBER, "Ana", true));
        assertEquals(Arrays.asList(PARROT), reactions.customKeysOf("556"));
        assertEquals(2, reactions.summaryFor(ALICE).find(PARROT).count);

        assertEquals("a member already on it is found there",
                PARROT, reactions.discordKeyOf(RENAMED, "556", DISCORD_MEMBER, true));
        assertFalse(reactions.set(PARROT, DISCORD_MEMBER, "Nils", true));
        assertEquals("sent without a name, it joins by the id",
                PARROT, reactions.discordKeyOf(null, "556", THIRD_MEMBER, true));
        assertNull("sent without a name and held nowhere, it starts nothing",
                reactions.discordKeyOf(null, "557", THIRD_MEMBER, true));
    }

    @Test
    public void aRemovalGoesByTheIdToTheKeyThatHoldsTheMember() {
        ChatReactions reactions = new ChatReactions();
        reactions.set(PARROT, DISCORD_MEMBER, "Nils", true);
        reactions.set(PARROT, ALICE, "Aldric", true);

        assertEquals("a removal after a rename finds the old name",
                PARROT, reactions.discordKeyOf(RENAMED, "556", DISCORD_MEMBER, false));
        assertEquals("so does one sent without a name",
                PARROT, reactions.discordKeyOf(null, "556", DISCORD_MEMBER, false));
        String elsewhere = reactions.discordKeyOf(RENAMED, "556", SECOND_MEMBER,
                false);
        assertFalse("a member on no key of the id takes nothing back",
                elsewhere != null && reactions.set(elsewhere, SECOND_MEMBER, "",
                        false));
        assertNull(reactions.discordKeyOf(null, "556", SECOND_MEMBER, false));

        assertTrue(reactions.set(PARROT, DISCORD_MEMBER, "", false));
        assertEquals("the player's stays", 1,
                reactions.summaryFor(ALICE).find(PARROT).count);

        // A custom emoji named as a registry name is kept under that name.
        reactions.set("smile", DISCORD_MEMBER, "Nils", true);
        assertEquals("smile",
                reactions.discordKeyOf("smile", "42", DISCORD_MEMBER, false));
    }

    /**
     * A save may hold one emoji under two names. Each member's removal
     * takes the key that holds them, and the players on both keys count
     * together for the bot's one reaction.
     */
    @Test
    public void twoKeysOfOneIdAreResolvedByTheReactor() {
        ChatReactions reactions = new ChatReactions();
        assertTrue(reactions.restore(PARROT, DISCORD_MEMBER, "Nils"));
        assertTrue(reactions.restore(RENAMED, SECOND_MEMBER, "Ana"));
        assertTrue(reactions.restore(RENAMED, ALICE, "Aldric"));
        assertEquals(Arrays.asList(PARROT, RENAMED),
                reactions.customKeysOf("556"));
        assertEquals("players on either key count for the emoji",
                1, reactions.gameCount(PARROT));
        assertEquals(1, reactions.gameCount(RENAMED));

        assertEquals(RENAMED,
                reactions.discordKeyOf(PARROT, "556", SECOND_MEMBER, false));
        assertEquals(PARROT,
                reactions.discordKeyOf(null, "556", DISCORD_MEMBER, false));
        assertEquals(RENAMED,
                reactions.discordKeyOf(PARROT, "556", SECOND_MEMBER, true));
        assertEquals("a newcomer joins the first key",
                PARROT, reactions.discordKeyOf(RENAMED, "556", THIRD_MEMBER, true));
    }

    @Test
    public void aModeratorClearOfOneEmojiTakesEveryKeyOfItsId() {
        ChatReactions reactions = new ChatReactions();
        reactions.restore(PARROT, DISCORD_MEMBER, "Nils");
        reactions.restore(RENAMED, SECOND_MEMBER, "Ana");
        reactions.restore(RENAMED, ALICE, "Aldric");
        reactions.set(UNICORN, DISCORD_MEMBER, "Nils", true);

        assertFalse("an id with no key clears nothing, never everything",
                reactions.clearDiscord(null, "557"));
        assertFalse(reactions.clearDiscord("pepe:557", "557"));
        assertEquals(4, reactions.total());

        assertTrue(reactions.clearDiscord("parrot_party:556", "556"));
        assertNull(reactions.summaryFor(ALICE).find(PARROT));
        assertEquals("the player's stays", 1,
                reactions.summaryFor(ALICE).find(RENAMED).count);
        assertEquals("another emoji stays", 1,
                reactions.summaryFor(ALICE).find(UNICORN).count);

        assertTrue("no emoji and no id is every emoji",
                reactions.clearDiscord(null, ""));
        assertNull(reactions.summaryFor(ALICE).find(UNICORN));
        assertEquals(1, reactions.total());
    }

    @Test
    public void aDiscordClearOfAForeignEmojiLeavesThePlayers() {
        ChatReactions reactions = new ChatReactions();
        reactions.set(UNICORN, DISCORD_MEMBER, "Nils", true);
        reactions.set(UNICORN, ALICE, "Aldric", true);
        assertTrue(reactions.clearDiscord(UNICORN));
        assertEquals(1, reactions.summaryFor(ALICE).find(UNICORN).count);
        assertTrue(reactions.summaryFor(ALICE).find(UNICORN).mine);
    }

    @Test
    public void aSummaryCarriesForeignKeysAndRefusesAnythingElse() {
        ChatReactionSummary.Reaction parrot = new ChatReactionSummary.Reaction(
                PARROT, 1, false, Arrays.asList("Nils"));
        assertEquals(PARROT, parrot.emoji);
        assertEquals(UNICORN, new ChatReactionSummary.Reaction(UNICORN, 1,
                false, null).emoji);
        try {
            new ChatReactionSummary.Reaction("partyparrot", 1, false, null);
            fail("a bare custom name is no key");
        } catch (IllegalArgumentException expected) {
            // Refused, as an unknown registry name is.
        }
    }

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
