package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Whom a line's mentions reach is found by the whole name after the
 * at-sign, the longest first, among the conversation's members.
 */
public final class ChatMentionsReachTest {
    private static final ChatNamedPlayer ALDRIC = new ChatNamedPlayer(
            UUID.randomUUID(), "Nils", UUID.randomUUID(), "Aldric", "");
    private static final ChatNamedPlayer ALDRIC_OF_BREE = new ChatNamedPlayer(
            UUID.randomUUID(), "Sam", UUID.randomUUID(), "Aldric of Bree", "");
    private static final ChatNamedPlayer ARWEN = ChatNamedPlayer.account(
            UUID.randomUUID(), "Arwen Undomiel");

    @Test
    public void theLongestWholeNameWins() {
        List<ChatNamedPlayer> members = Arrays.asList(ALDRIC, ALDRIC_OF_BREE, ARWEN);
        assertEquals(Collections.singletonList(ALDRIC_OF_BREE),
                ChatMentions.reached("hi @Aldric of Bree!", members, 8));
        assertEquals(Collections.singletonList(ALDRIC),
                ChatMentions.reached("hi @aldric, and you", members, 8));
        // An account names its player whichever character they play.
        assertEquals(Collections.singletonList(ALDRIC),
                ChatMentions.reached("@Nils", members, 8));
        assertEquals(Arrays.asList(ARWEN, ALDRIC),
                ChatMentions.reached("@Arwen Undomiel and @Aldric and @Aldric",
                        members, 8));
    }

    @Test
    public void aNameMustStandWhole() {
        List<ChatNamedPlayer> members = Arrays.asList(ALDRIC, ARWEN);
        assertTrue(ChatMentions.reached("@Aldrics", members, 8).isEmpty());
        assertTrue(ChatMentions.reached("mail@Aldric", members, 8).isEmpty());
        assertTrue(ChatMentions.reached("no at-sign Aldric", members, 8).isEmpty());
        assertNull(ChatMentions.nameAt("@Arwen", 0, members));
        ChatMentions.Hit hit = ChatMentions.nameAt("x @Aldric y", 2, members);
        assertEquals(ALDRIC, hit.player);
        assertEquals(9, hit.end);
    }

    @Test
    public void aLineReachesAtMostItsShare() {
        List<ChatNamedPlayer> members = Arrays.asList(ALDRIC, ALDRIC_OF_BREE, ARWEN);
        assertEquals(1, ChatMentions.reached("@Nils @Sam @Arwen Undomiel",
                members, 1).size());
    }
}
