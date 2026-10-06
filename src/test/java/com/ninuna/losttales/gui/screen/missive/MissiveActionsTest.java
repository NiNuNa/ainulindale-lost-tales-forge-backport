package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.window.WorldPageReach;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.MissiveBoardStateReason;
import com.ninuna.losttales.quest.missive.MissiveNotice;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What greys each action on the missive pages' bars and why, the letter
 * page's check of the slot it was opened on, and the words every one of
 * them and every answer from the board says.
 */
public final class MissiveActionsTest {
    private static final String WHY = MissiveActions.WHY;

    private static final MissiveNotice READABLE = new MissiveNotice(2, 100L,
            LostTalesMissiveData.builder("q/road", "kill").titleId("trouble_on_the_road")
                    .objective(new LostTalesMissiveObjectiveData("o", "kill", false, null))
                    .build());
    private static final MissiveNotice UNREADABLE = new MissiveNotice(4, 100L,
            null);

    @Test
    public void acceptOnTheBoardNeedsAReadableNoticeNotAlreadyTaken() {
        assertEquals(WHY + "pick", MissiveActions.whyNotAccept(null, false, false));
        assertEquals(WHY + "unreadable",
                MissiveActions.whyNotAccept(UNREADABLE, false, false));
        assertEquals(WHY + "already_active",
                MissiveActions.whyNotAccept(READABLE, true, false));
        assertEquals(WHY + "waiting",
                MissiveActions.whyNotAccept(READABLE, false, true));
        assertEquals("", MissiveActions.whyNotAccept(READABLE, false, false));
    }

    @Test
    public void takeNeedsANoticeAndRoomInTheInventory() {
        assertEquals(WHY + "pick", MissiveActions.whyNotTake(null, false, false));
        assertEquals(WHY + "inventory_full",
                MissiveActions.whyNotTake(READABLE, true, false));
        assertEquals(WHY + "waiting",
                MissiveActions.whyNotTake(READABLE, false, true));
        assertEquals("a notice that cannot be read can still come down", "",
                MissiveActions.whyNotTake(UNREADABLE, false, false));
    }

    @Test
    public void pinNeedsALetterCarriedAndRoomOnTheBoard() {
        assertEquals(WHY + "no_letters",
                MissiveActions.whyNotPin(0, 2, 9, 9, false));
        assertEquals(WHY + "board_full",
                MissiveActions.whyNotPin(1, 9, 9, 9, false));
        assertEquals("a board posting fewer than its slots is full sooner",
                WHY + "board_full", MissiveActions.whyNotPin(2, 5, 5, 9, false));
        assertEquals(WHY + "waiting", MissiveActions.whyNotPin(1, 2, 9, 9, true));
        assertEquals("", MissiveActions.whyNotPin(1, 8, 9, 9, false));
    }

    @Test
    public void acceptOnALetterNeedsOneThatReadsAndIsNotTaken() {
        assertEquals(WHY + "unreadable",
                MissiveActions.whyNotAcceptLetter(false, false, false));
        assertEquals(WHY + "already_active",
                MissiveActions.whyNotAcceptLetter(true, true, false));
        assertEquals(WHY + "waiting",
                MissiveActions.whyNotAcceptLetter(true, false, true));
        assertEquals("", MissiveActions.whyNotAcceptLetter(true, false, false));
    }

    @Test
    public void theLetterPageStaysOnlyWhileItsSlotHoldsThatLetter() {
        assertTrue(MissiveActions.stillHolds("q/road", "q/road"));
        assertFalse("another letter", MissiveActions.stillHolds("q/road", "q/coal"));
        assertFalse("no letter at all", MissiveActions.stillHolds("q/road", null));
        assertFalse("one that cannot be read",
                MissiveActions.stillHolds("q/road", ""));
        assertTrue("a letter that could not be read, still there",
                MissiveActions.stillHolds("", ""));
        assertFalse(MissiveActions.stillHolds("", null));
    }

    @Test
    public void everyWordThePagesSayIsInTheLangFile() throws IOException {
        Set<String> keys = langKeys();
        List<String> wanted = new ArrayList<String>();
        for (String why : new String[] {"pick", "unreadable", "already_active",
                "waiting", "inventory_full", "no_letters", "board_full"}) {
            wanted.add(WHY + why);
        }
        for (MissiveBoardStateReason reason : MissiveBoardStateReason.values()) {
            if (reason.getMessageKey().length() > 0) {
                wanted.add(reason.getMessageKey());
            }
        }
        for (WorldPageReach.Leave leave : WorldPageReach.Leave.values()) {
            wanted.add(leave.messageKey("missive_board"));
            wanted.add(leave.messageKey("waystone"));
        }
        String board = "gui.losttales.missive_board.";
        for (String key : new String[] {"list.show", "list.hide", "search",
                "search.none", "empty", "pick", "unreadable", "accept",
                "accept.tip", "take", "take.tip", "pin", "pin.tip",
                "pin.title", "pin.hand", "pin.hotbar", "pin.pack", "available",
                "said.waiting", "said.no_answer", "said.picked_gone"}) {
            wanted.add(board + key);
        }
        String letter = "gui.losttales.missive_letter.";
        for (String key : new String[] {"accept", "accept.tip", "work", "left",
                "invalid", "issuer", "reward", "time_limit"}) {
            wanted.add(letter + key);
        }
        wanted.add("gui.losttales.page.missive_board");
        wanted.add("gui.losttales.page.missive_letter");
        wanted.add("chat.losttales.missive.board_gone");
        wanted.add("chat.losttales.missive.too_far");
        for (String key : wanted) {
            assertTrue(key, keys.contains(key));
        }
    }

    private static Set<String> langKeys() throws IOException {
        InputStream input = MissiveActionsTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        Set<String> keys = new HashSet<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (!line.startsWith("#") && equals > 0) {
                    keys.add(line.substring(0, equals));
                }
            }
        } finally {
            reader.close();
        }
        return keys;
    }
}
