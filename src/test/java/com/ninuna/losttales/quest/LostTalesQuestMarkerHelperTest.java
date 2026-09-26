package com.ninuna.losttales.quest;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdentity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesQuestMarkerHelperTest {

    @Test
    public void aMarkerIdIsKeyedAsAPlayersQuestMarker() {
        String key = LostTalesQuestMarkerHelper.markerCanonicalKey(" bree_gate ");

        assertEquals(LostTalesMapMarkerIdentity.create("bree_gate",
                LostTalesMapMarkerIdentity.Authority.QUEST_PLAYER)
                .getCanonicalKey(), key);
        assertEquals(key, LostTalesQuestMarkerHelper.markerCanonicalKey("bree_gate"));
    }

    @Test
    public void noIdHasNoKey() {
        assertEquals("", LostTalesQuestMarkerHelper.markerCanonicalKey(null));
        assertEquals("", LostTalesQuestMarkerHelper.markerCanonicalKey("   "));
    }
}
