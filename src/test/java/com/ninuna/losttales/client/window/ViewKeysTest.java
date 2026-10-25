package com.ninuna.losttales.client.window;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A view's key on the screen (WIN-03, WIN-05): where no field is typed
 * in it swaps to its view, T and the command key as much as M and J, and
 * on its own view it closes the screen; in a field a letter types, and a
 * key that types nothing works anywhere. A key the page in front answers
 * itself stays the page's.
 */
public final class ViewKeysTest {
    @Test
    public void whereNoFieldIsTypedInAViewsKeySwapsOrCloses() {
        assertEquals("T on the map swaps to Chat Channels", ViewKeys.Act.SWAP,
                ViewKeys.act(true, false, false, true, false, false));
        assertEquals("pressed on its own view it closes the screen",
                ViewKeys.Act.CLOSE,
                ViewKeys.act(true, false, false, true, false, true));
        assertEquals("a key that types nothing swaps the same",
                ViewKeys.Act.SWAP,
                ViewKeys.act(true, false, false, false, false, false));
    }

    @Test
    public void inAFieldALetterTypesAndAKeyTypingNothingStillWorks() {
        assertEquals("T in a conversation types", ViewKeys.Act.PASS,
                ViewKeys.act(true, false, true, true, false, false));
        assertEquals("on its own view too", ViewKeys.Act.PASS,
                ViewKeys.act(true, false, true, true, false, true));
        assertEquals("a key that types nothing swaps from a field",
                ViewKeys.Act.SWAP,
                ViewKeys.act(true, false, true, false, false, false));
        assertEquals("and closes on its own view", ViewKeys.Act.CLOSE,
                ViewKeys.act(true, false, true, false, false, true));
    }

    @Test
    public void noViewsKeyHeldWithCtrlOrAltOrThePagesOwnKeyGoesOn() {
        for (boolean typedIn : new boolean[] {false, true}) {
            for (boolean types : new boolean[] {false, true}) {
                for (boolean onItsView : new boolean[] {false, true}) {
                    assertEquals("no view's key", ViewKeys.Act.PASS,
                            ViewKeys.act(false, false, typedIn, types, false,
                                    onItsView));
                    assertEquals("Ctrl or Alt held", ViewKeys.Act.PASS,
                            ViewKeys.act(true, true, typedIn, types, false,
                                    onItsView));
                    assertEquals("the board's T takes", ViewKeys.Act.PASS,
                            ViewKeys.act(true, false, typedIn, types, true,
                                    onItsView));
                }
            }
        }
    }

    /** A page's own key is one done alone; one held with another is not. */
    @Test
    public void aPageAnswersTheKeysItsHelpListsAlone() {
        List<PageKeys.Area> board = PageKeys.pageArea("board",
                PageKeys.pageKey("board", "take", Integer.valueOf(Keyboard.KEY_T)),
                PageKeys.pageKey("board", "walk", Integer.valueOf(Keyboard.KEY_UP),
                        PageKeys.OR, Integer.valueOf(Keyboard.KEY_DOWN)),
                PageKeys.pageKey("board", "ten", Integer.valueOf(Keyboard.KEY_LSHIFT),
                        PageKeys.PLUS, Integer.valueOf(Keyboard.KEY_M)),
                PageKeys.pageKey("board", "copy", Integer.valueOf(PageKeys.COMMAND),
                        Integer.valueOf(Keyboard.KEY_J)),
                PageKeys.pageKey("board", "open", PageKeys.CLICK));
        assertTrue(PageKeys.answersKey(board, Keyboard.KEY_T));
        assertTrue("an alternative counts",
                PageKeys.answersKey(board, Keyboard.KEY_DOWN));
        assertFalse("a key held with another does not",
                PageKeys.answersKey(board, Keyboard.KEY_M));
        assertFalse("nor one held with Ctrl",
                PageKeys.answersKey(board, Keyboard.KEY_J));
        assertFalse(PageKeys.answersKey(board, Keyboard.KEY_R));
        assertFalse(PageKeys.answersKey(board, 0));
        assertFalse(PageKeys.answersKey(
                Arrays.<PageKeys.Area>asList(), Keyboard.KEY_T));
        assertFalse(PageKeys.answersKey(null, Keyboard.KEY_T));
    }
}
