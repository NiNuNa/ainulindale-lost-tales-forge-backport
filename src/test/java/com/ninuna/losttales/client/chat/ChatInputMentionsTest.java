package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMentionCandidate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A mention being typed is found among exactly the names the {@code @}
 * list offers: the longest name first, whatever its case, and never a
 * name running on into another word.
 */
public final class ChatInputMentionsTest {
    private static final ChatMentionCandidate ALDRIC = ChatMentionCandidate.player(
            "1", "Aldric", "nils", "Aldric", "1", "",
            Arrays.asList("nils", "Aldric"));
    private static final ChatMentionCandidate ALDRIC_OF_BREE =
            ChatMentionCandidate.player("2", "Aldric of Bree", "rw",
                    "Aldric of Bree", "2", "",
                    Arrays.asList("rw", "Aldric of Bree"));
    private static final ChatMentionCandidate OPERATOR =
            ChatMentionCandidate.role("role:operator", "Operator", 0xA94B54);
    private static final List<ChatMentionCandidate> NAMES =
            Arrays.asList(OPERATOR, ALDRIC, ALDRIC_OF_BREE);

    @Test
    public void theLongestNameWins() {
        String text = "hi @aldric of bree!";
        ChatInputMentions.Found found = ChatInputMentions.at(text, 3, NAMES);
        assertEquals(ALDRIC_OF_BREE, found.candidate);
        assertEquals(text.indexOf('!'), found.end);
    }

    @Test
    public void anAccountNameAndARoleAreMentionsToo() {
        List<ChatMentionCandidate> names = NAMES;
        List<ChatInputMentions.Found> found =
                ChatInputMentions.find("@nils and @Operator", names);
        assertEquals(2, found.size());
        assertEquals(ALDRIC, found.get(0).candidate);
        assertEquals(OPERATOR, found.get(1).candidate);
    }

    @Test
    public void aNameRunningOnOrNotOfferedIsNoMention() {
        assertTrue(ChatInputMentions.find("@Aldricson @Nobody", NAMES).isEmpty());
        // An at-sign inside a word is an address, not a mention.
        assertNull(ChatInputMentions.at("mail@Aldric", 4, NAMES));
    }
}
