package com.ninuna.losttales.quest;

import com.ninuna.losttales.chat.share.ChatShowcase;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A quest card: who may join it and by which of the server's start
 * settings, and the words it carries always fitting the chat's bounds.
 */
public final class LostTalesQuestShareTest {
    private static final UUID AUTHOR =
            UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MEMBER =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    public void aCardIsJoinedOnlyByAWayTheServerAllows() {
        LostTalesQuestDefinition item = quest(LostTalesQuestDefinition.START_MODE_ITEM);
        LostTalesQuestDefinition giver = quest(LostTalesQuestDefinition.START_MODE_INTERACTION);
        LostTalesQuestDefinition any = quest(LostTalesQuestDefinition.START_MODE_ANY);
        LostTalesQuestDefinition locked = quest(LostTalesQuestDefinition.START_MODE_LOCKED);

        assertTrue(item.canStartFromShare(true, false));
        assertFalse(item.canStartFromShare(false, true));
        assertTrue(giver.canStartFromShare(false, true));
        assertFalse(giver.canStartFromShare(true, false));
        assertTrue(any.canStartFromShare(true, false));
        assertTrue(any.canStartFromShare(false, true));
        assertFalse(any.canStartFromShare(false, false));
        assertFalse(locked.canStartFromShare(true, true));
        assertFalse(quest("script").canStartFromShare(true, true));
    }

    @Test
    public void theAuthorNeverJoinsTheirOwnCard() {
        assertFalse(LostTalesQuestManager.mayJoinShared(AUTHOR, AUTHOR, true));
        assertTrue(LostTalesQuestManager.mayJoinShared(MEMBER, AUTHOR, true));
    }

    @Test
    public void nobodyJoinsOnceTheAuthorNoLongerRunsTheQuest() {
        assertFalse(LostTalesQuestManager.mayJoinShared(MEMBER, AUTHOR, false));
        assertFalse(LostTalesQuestManager.mayJoinShared(MEMBER, null, true));
        assertFalse(LostTalesQuestManager.mayJoinShared(null, AUTHOR, true));
    }

    @Test
    public void wordsAreCutByTheirBytesAtACharactersEnd() throws Exception {
        assertEquals("Short", LostTalesQuestShareResolver.fitBytes("Short", 256));
        assertEquals("", LostTalesQuestShareResolver.fitBytes(null, 256));
        // Two bytes each: the third does not fit in five.
        assertEquals("éé",
                LostTalesQuestShareResolver.fitBytes("ééé", 5));
        // A character outside the basic plane is four bytes and two chars; it is never split.
        String scroll = "a📜";
        assertEquals("a", LostTalesQuestShareResolver.fitBytes(scroll, 4));
        assertEquals(scroll, LostTalesQuestShareResolver.fitBytes(scroll, 5));

        StringBuilder title = new StringBuilder();
        while (utf8(title.toString()) < 1024) {
            title.append("€");
        }
        String fitted = LostTalesQuestShareResolver.fitBytes(title.toString(),
                ChatShowcase.MAX_QUEST_TITLE_BYTES);
        assertTrue(utf8(fitted) <= ChatShowcase.MAX_QUEST_TITLE_BYTES);
        assertTrue(utf8(fitted) > ChatShowcase.MAX_QUEST_TITLE_BYTES - 3);
    }

    /** A server file's long title and objective still make a card. */
    @Test
    public void aLongQuestStillMakesACard() throws Exception {
        StringBuilder words = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            words.append("ß");
        }
        String title = LostTalesQuestShareResolver.fitBytes(words.toString(),
                ChatShowcase.MAX_QUEST_TITLE_BYTES);
        String objective = LostTalesQuestShareResolver.fitBytes(words.toString(),
                ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES);
        ChatShowcase card = ChatShowcase.quest(0, "losttales:long", title,
                LostTalesQuestCategory.MISC, objective, "", true);
        assertEquals(title, card.getQuestTitle());
        assertEquals(ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES, utf8(card.getQuestObjective()));
    }

    private static int utf8(String value) throws UnsupportedEncodingException {
        return value.getBytes("UTF-8").length;
    }

    private static LostTalesQuestDefinition quest(String startMode) {
        return new LostTalesQuestDefinition("losttales:shared", "Shared", "",
                false, false, startMode, null, null, null, null, null, null,
                null, Collections.<LostTalesQuestStageDefinition>emptyList());
    }
}
