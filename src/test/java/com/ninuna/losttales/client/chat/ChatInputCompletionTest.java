package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatInputCompletionTest {
    private static final UUID SELF = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final List<LostTalesChatMembersPacket.Member> NO_MEMBERS =
            Collections.<LostTalesChatMembersPacket.Member>emptyList();

    @Test
    public void suggestionKeysAreClassifiedOnceForEveryList() {
        assertEquals(1, ChatInputCompletion.suggestionAction(Keyboard.KEY_UP));
        assertEquals(2, ChatInputCompletion.suggestionAction(Keyboard.KEY_DOWN));
        assertEquals(3, ChatInputCompletion.suggestionAction(Keyboard.KEY_TAB));
        assertEquals(3, ChatInputCompletion.suggestionAction(Keyboard.KEY_RETURN));
        assertEquals(3, ChatInputCompletion.suggestionAction(
                Keyboard.KEY_NUMPADENTER));
        assertEquals(4, ChatInputCompletion.suggestionAction(Keyboard.KEY_ESCAPE));
        assertEquals(0, ChatInputCompletion.suggestionAction(Keyboard.KEY_A));
        assertEquals(0, ChatInputCompletion.suggestionAction(Keyboard.KEY_LEFT));
    }

    @Test
    public void thePlayerComesFirstAndEveryoneElseAlphabetical() {
        List<ChatMentionCandidate> candidates =
                ChatInputCompletion.mentionCandidatesFor(SELF, "Nils",
                        "Aldric", null, NO_MEMBERS, Arrays.asList("zoe",
                                "Beren", "  ", null, "NILS", "aragorn"),
                        Collections.<String, CharacterAppearance>emptyMap());
        List<String> names = new ArrayList<String>();
        for (ChatMentionCandidate candidate : candidates) {
            if (!candidate.isRole()) {
                names.add(candidate.getDisplayName());
            }
        }
        // The player's own account is first and is never listed twice.
        assertEquals(Arrays.asList("Aldric", "aragorn", "Beren", "zoe"), names);
        ChatMentionCandidate self = candidates.get(names.indexOf("Aldric")
                + candidates.size() - names.size());
        assertEquals(SELF.toString(), self.getKey());
        assertEquals("Nils", self.getAccountName());
        assertEquals("Aldric", self.getCharacterName());
        boolean characterIsAnAlias = false;
        for (String alias : self.getAliases()) {
            characterIsAnAlias |= "aldric".equalsIgnoreCase(alias);
        }
        assertTrue(characterIsAnAlias);
    }

    @Test
    public void rolePlayChannelsShowTheCharacterAndKeyPlayersByAppearance() {
        UUID beren = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<String, CharacterAppearance> byAccount =
                new HashMap<String, CharacterAppearance>();
        byAccount.put("beren", new CharacterAppearance(beren, "Beren",
                "Beren Erchamion", "losttales:human", "male", "", true, 0,
                "", 0, 0, "", ""));
        List<ChatMentionCandidate> candidates =
                ChatInputCompletion.mentionCandidatesFor(SELF, "Nils",
                        "Aldric", null, NO_MEMBERS,
                        Arrays.asList("Beren", "zoe"), byAccount);
        List<ChatMentionCandidate> players = new ArrayList<ChatMentionCandidate>();
        for (ChatMentionCandidate candidate : candidates) {
            if (!candidate.isRole()) {
                players.add(candidate);
            }
        }
        assertEquals("Aldric", players.get(0).getDisplayName());
        assertEquals("Beren Erchamion", players.get(1).getDisplayName());
        assertEquals(beren.toString(), players.get(1).getKey());
        assertEquals("zoe", players.get(2).getDisplayName());
        assertEquals("account:zoe", players.get(2).getKey());
        assertEquals("", players.get(2).getCharacterId());
        assertEquals("", players.get(2).getAccountId());
        // With no id at all the player still has a stable key.
        List<ChatMentionCandidate> anonymous =
                ChatInputCompletion.mentionCandidatesFor(null, "Nils",
                        "", null, NO_MEMBERS, Collections.<String>emptyList(),
                        byAccount);
        assertEquals("self", anonymous.get(anonymous.size() - 1).getKey());
        assertEquals("Nils", anonymous.get(anonymous.size() - 1)
                .getDisplayName());
    }

    /**
     * The character a row is displayed as is named by id, the sync's
     * for another player and the roster's for the player themself, so
     * the row is joined to the appearance rather than to a name.
     */
    @Test
    public void candidatesCarryTheCharacterTheyAreDisplayedAs() {
        UUID beren = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID erchamion = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID aldric = UUID.fromString("00000000-0000-0000-0000-000000000011");
        Map<String, CharacterAppearance> byAccount =
                new HashMap<String, CharacterAppearance>();
        byAccount.put("beren", new CharacterAppearance(beren, "Beren",
                "Beren Erchamion", "losttales:human", "male", "", true, 0,
                "lotr:gondor", 1, 30, "", "").withCharacterId(erchamion));
        List<ChatMentionCandidate> candidates =
                ChatInputCompletion.mentionCandidatesFor(SELF, "Nils",
                        "Aldric", aldric, NO_MEMBERS, Arrays.asList("Beren"),
                        byAccount);
        List<ChatMentionCandidate> players = new ArrayList<ChatMentionCandidate>();
        for (ChatMentionCandidate candidate : candidates) {
            if (!candidate.isRole()) {
                players.add(candidate);
            }
        }
        assertEquals(aldric.toString(), players.get(0).getCharacterId());
        assertEquals(SELF.toString(), players.get(0).getAccountId());
        assertEquals(erchamion.toString(), players.get(1).getCharacterId());
        assertEquals(beren.toString(), players.get(1).getAccountId());
        // Without ids the same player is still listed, by name alone.
        List<ChatMentionCandidate> nameless = ChatInputCompletion
                .mentionCandidatesFor(SELF, "Nils", "Aldric", null,
                        NO_MEMBERS, Arrays.asList("Beren"),
                        Collections.<String, CharacterAppearance>emptyMap());
        assertEquals("", nameless.get(nameless.size() - 1).getCharacterId());
        assertEquals("", nameless.get(nameless.size() - 2).getCharacterId());
        // A player displayed as another character is a different list.
        List<ChatMentionCandidate> switched = ChatInputCompletion
                .mentionCandidatesFor(SELF, "Nils", "Aldric", beren,
                        NO_MEMBERS, Arrays.asList("Beren"), byAccount);
        assertFalse(ChatInputCompletion.sameCandidates(candidates, switched));
    }

    @Test
    public void candidateListsCompareByKeyNameAndAliases() {
        List<ChatMentionCandidate> first = ChatInputCompletion
                .mentionCandidatesFor(SELF, "Nils", "Aldric", null,
                        NO_MEMBERS, Arrays.asList("Beren"),
                        Collections.<String, CharacterAppearance>emptyMap());
        List<ChatMentionCandidate> same = ChatInputCompletion
                .mentionCandidatesFor(SELF, "Nils", "Aldric", null,
                        NO_MEMBERS, Arrays.asList("beren"),
                        Collections.<String, CharacterAppearance>emptyMap());
        List<ChatMentionCandidate> renamed = ChatInputCompletion
                .mentionCandidatesFor(SELF, "Nils", "Aldric", null,
                        NO_MEMBERS, Arrays.asList("Beren", "Cirdan"),
                        Collections.<String, CharacterAppearance>emptyMap());
        assertFalse(ChatInputCompletion.sameCandidates(first, same));
        assertTrue(ChatInputCompletion.sameCandidates(first, first));
        assertFalse(ChatInputCompletion.sameCandidates(first, renamed));
    }

    /**
     * A conversation's member list is who a mention may reach: those here
     * after the player, then those absent, a Discord member among them,
     * each alphabetical. The Server is nobody to mention, nor is one of the
     * player's own other characters, and an online account the list
     * already shows is not listed twice.
     */
    @Test
    public void theMemberListIsWhoAMentionMayReach() {
        UUID beren = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID erchamion = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID sam = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID ownOther = UUID.fromString("00000000-0000-0000-0000-000000000012");
        UUID discord = LostTalesChatMessagePacket.discordSenderId("123456789012345678");
        List<LostTalesChatMembersPacket.Member> members =
                new ArrayList<LostTalesChatMembersPacket.Member>();
        members.add(new LostTalesChatMembersPacket.Member(beren, "Beren",
                erchamion, "Beren Erchamion", 0xFFFFFF, "", "", 0xFFFFFF,
                "", "", 0, true));
        members.add(new LostTalesChatMembersPacket.Member(
                LostTalesChatMessagePacket.SERVER_SENDER_ID, "Server", null,
                "Server", 0xFFFFFF, "", "", 0xFFFFFF, "", "", 0, true));
        members.add(new LostTalesChatMembersPacket.Member(sam, "Sam", null,
                "Sam", 0xFFFFFF, "", "", 0xFFFFFF, "", "", 0, false));
        members.add(new LostTalesChatMembersPacket.Member(discord,
                "Arwen Undomiel", null, "Arwen Undomiel", 0xFFFFFF, "", "",
                0xFFFFFF, "", "", 0, false));
        members.add(new LostTalesChatMembersPacket.Member(SELF, "Nils",
                ownOther, "Tauriel", 0xFFFFFF, "", "", 0xFFFFFF, "", "", 0,
                false));
        List<ChatMentionCandidate> candidates = ChatInputCompletion
                .mentionCandidatesFor(SELF, "Nils", "Aldric", null, members,
                        Arrays.asList("Beren", "Zoe"),
                        Collections.<String, CharacterAppearance>emptyMap());
        List<String> names = new ArrayList<String>();
        for (ChatMentionCandidate candidate : candidates) {
            if (!candidate.isRole()) {
                names.add(candidate.getDisplayName());
            }
        }
        assertEquals(Arrays.asList("Aldric", "Beren Erchamion", "Zoe",
                "Arwen Undomiel", "Sam"), names);
    }
}
