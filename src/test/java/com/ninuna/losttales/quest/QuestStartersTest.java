package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.missive.MissiveSeal;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A starter item's seal (QUEST-34): it covers the quest the starter names
 * and the starter's own nonce, so a starter changed by hand to name
 * another quest, or made by hand, carries no seal that fits.
 */
public final class QuestStartersTest {
    private static byte[] key(int seed) {
        byte[] key = new byte[MissiveSeal.LENGTH];
        Arrays.fill(key, (byte) seed);
        return key;
    }

    @Test
    public void aStartersSealCoversItsQuestAndNonce() {
        byte[] nonce = new byte[] { 1, 2, 3, 4 };
        byte[] seal = MissiveSeal.signBytes(key(7),
                QuestStarters.content("losttales:path/survivalist/first_sparks", nonce));
        assertNotNull(seal);
        assertTrue(MissiveSeal.verifiesBytes(key(7), QuestStarters.content(
                "losttales:path/survivalist/first_sparks", nonce), seal));
        assertFalse("another quest", MissiveSeal.verifiesBytes(key(7),
                QuestStarters.content("losttales:tutorial/starter_note", nonce), seal));
        assertFalse("another nonce", MissiveSeal.verifiesBytes(key(7),
                QuestStarters.content("losttales:path/survivalist/first_sparks",
                        new byte[] { 1, 2, 3, 5 }), seal));
        assertFalse("another world's key", MissiveSeal.verifiesBytes(key(8),
                QuestStarters.content("losttales:path/survivalist/first_sparks", nonce),
                seal));
    }
}
