package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.util.LostTalesLangFile;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The question before a restore says what comes back with the character
 * and what deleting took from others, which stays taken (CHAR-33).
 */
public final class CharacterRestoreQuestionTest {

    @Test
    public void theQuestionSaysWhatComesBackAndWhatStaysTaken() {
        String detail = LostTalesLangFile.english().get(
                "gui.losttales.character.restore_detail");
        for (String back : new String[] {"as it was when it was deleted",
                "hired units", "waystones shared with it"}) {
            assertTrue("it comes back with " + back, detail.contains(back));
        }
        int staysTaken = detail.indexOf("do not come back");
        assertTrue(staysTaken > 0);
        for (String taken : new String[] {"fellowships", "invitations",
                "go-here mark"}) {
            int at = detail.indexOf(taken);
            assertTrue(taken + " stays taken", at > 0 && at < staysTaken);
        }
    }
}
