package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.chat.ChatMentionCandidate;
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
                ChatInputCompletion.mentionCandidatesFor(true, SELF, "Nils",
                        "Aldric", Arrays.asList("zoe", "Beren", "  ", null,
                                "NILS", "aragorn"),
                        Collections.<String, CharacterAppearance>emptyMap());
        List<String> names = new ArrayList<String>();
        for (ChatMentionCandidate candidate : candidates) {
            if (!candidate.isRole()) {
                names.add(candidate.getDisplayName());
            }
        }
        // The player's own account is first and is never listed twice.
        assertEquals(Arrays.asList("Nils", "aragorn", "Beren", "zoe"), names);
        ChatMentionCandidate self = candidates.get(names.indexOf("Nils")
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
                "Beren Erchamion", "losttales:human", "male", "", true, 0));
        List<ChatMentionCandidate> candidates =
                ChatInputCompletion.mentionCandidatesFor(false, SELF, "Nils",
                        "Aldric", Arrays.asList("Beren", "zoe"), byAccount);
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
        // With no id at all the player still has a stable key.
        List<ChatMentionCandidate> anonymous =
                ChatInputCompletion.mentionCandidatesFor(false, null, "Nils",
                        "", Collections.<String>emptyList(), byAccount);
        assertEquals("self", anonymous.get(anonymous.size() - 1).getKey());
        assertEquals("Nils", anonymous.get(anonymous.size() - 1)
                .getDisplayName());
    }

    @Test
    public void candidateListsCompareByKeyNameAndAliases() {
        List<ChatMentionCandidate> first = ChatInputCompletion
                .mentionCandidatesFor(true, SELF, "Nils", "Aldric",
                        Arrays.asList("Beren"),
                        Collections.<String, CharacterAppearance>emptyMap());
        List<ChatMentionCandidate> same = ChatInputCompletion
                .mentionCandidatesFor(true, SELF, "Nils", "Aldric",
                        Arrays.asList("beren"),
                        Collections.<String, CharacterAppearance>emptyMap());
        List<ChatMentionCandidate> renamed = ChatInputCompletion
                .mentionCandidatesFor(true, SELF, "Nils", "Aldric",
                        Arrays.asList("Beren", "Cirdan"),
                        Collections.<String, CharacterAppearance>emptyMap());
        assertFalse(ChatInputCompletion.sameCandidates(first, same));
        assertTrue(ChatInputCompletion.sameCandidates(first, first));
        assertFalse(ChatInputCompletion.sameCandidates(first, renamed));
    }
}
