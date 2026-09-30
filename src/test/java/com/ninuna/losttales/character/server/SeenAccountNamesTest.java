package com.ninuna.losttales.character.server;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A new character may not take the name of an account the server has
 * seen, compared as character names are; the account asking may take its
 * own, and a check reads a bounded number of names.
 */
public final class SeenAccountNamesTest {

    /** A source holding the names given, as the server's lists would. */
    private static final class Fake implements SeenAccountNames.Source {
        final List<String> held;
        int asked;

        Fake(String... held) {
            this.held = Arrays.asList(held);
        }

        @Override
        public void addNames(List<String> names, int limit) {
            this.asked++;
            for (String name : this.held) {
                if (names.size() >= limit) {
                    return;
                }
                names.add(name);
            }
        }
    }

    @Test
    public void anAccountsNameIsRefusedHoweverItIsWritten() {
        Fake seen = new Fake("Notch", "Steve_2", "x_Aldric_x");
        assertTrue(SeenAccountNames.isAnotherAccountsName("Notch", "Alex", seen));
        assertTrue(SeenAccountNames.isAnotherAccountsName("notch", "Alex", seen));
        assertTrue(SeenAccountNames.isAnotherAccountsName("Steve 2", "Alex", seen));
        assertTrue(SeenAccountNames.isAnotherAccountsName("X-Aldric-X", "Alex", seen));
        assertFalse(SeenAccountNames.isAnotherAccountsName("Aldric", "Alex", seen));
        assertFalse(SeenAccountNames.isAnotherAccountsName("Steve", "Alex", seen));
    }

    @Test
    public void theAccountAskingMayTakeItsOwnName() {
        Fake seen = new Fake("Alex", "Notch");
        assertFalse(SeenAccountNames.isAnotherAccountsName("alex", "Alex", seen));
        assertEquals("its own name needs no list", 0, seen.asked);
        assertTrue(SeenAccountNames.isAnotherAccountsName("Notch", "Alex", seen));
    }

    @Test
    public void aNameWithNothingToCompareOrNoSourceIsNeverAnAccounts() {
        Fake seen = new Fake("Notch");
        assertFalse(SeenAccountNames.isAnotherAccountsName("'-'", "Alex", seen));
        assertFalse(SeenAccountNames.isAnotherAccountsName(null, "Alex", seen));
        assertFalse(SeenAccountNames.isAnotherAccountsName("Notch", "Alex", null));
    }

    @Test
    public void aCheckReadsSoManyNamesAtMost() {
        String[] many = new String[SeenAccountNames.MAX_NAMES + 1];
        for (int index = 0; index < SeenAccountNames.MAX_NAMES; index++) {
            many[index] = "Player" + index;
        }
        many[SeenAccountNames.MAX_NAMES] = "Notch";
        Fake seen = new Fake(many);
        assertTrue(SeenAccountNames.isAnotherAccountsName("Player7", "Alex", seen));
        assertFalse("past the bound", SeenAccountNames.isAnotherAccountsName(
                "Notch", "Alex", seen));
    }

    /** A source that gives more than it is asked for is read only to the bound. */
    @Test
    public void aSourceGivingTooManyIsReadOnlyToTheBound() {
        SeenAccountNames.Source careless = new SeenAccountNames.Source() {
            @Override
            public void addNames(List<String> names, int limit) {
                for (int index = 0; index <= limit; index++) {
                    names.add(index == limit ? "Notch" : "Player" + index);
                }
            }
        };
        assertFalse(SeenAccountNames.isAnotherAccountsName("Notch", "Alex", careless));
    }
}
