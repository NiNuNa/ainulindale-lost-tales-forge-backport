package com.ninuna.losttales.quest;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Which of the server's own quests a client is sent: those it needs,
 * each once, never a bundled quest or a missive, and after a reload what
 * it held that still exists and what its log names.
 */
public final class ServerQuestVisibilityTest {
    private static final Set<String> SERVER = new LinkedHashSet<String>(
            Arrays.asList("server:bree/rats", "server:bree/well",
                    "server:world/greenway"));

    @Test
    public void onlyTheServersOwnQuestsAreSent() {
        assertEquals(Arrays.asList("server:bree/rats"),
                ServerQuestVisibility.newlyVisible(
                        Collections.<String>emptySet(),
                        Arrays.asList("losttales:tutorial/meet_nia",
                                "server:bree/rats",
                                "losttales:missive/generated/dim0_1_2_3/100_0"),
                        SERVER));
    }

    @Test
    public void aQuestIsSentOnce() {
        Set<String> sent = new LinkedHashSet<String>(
                Collections.singleton("server:bree/rats"));
        assertTrue(ServerQuestVisibility.newlyVisible(sent,
                Arrays.asList("server:bree/rats"), SERVER).isEmpty());
        assertEquals(Arrays.asList("server:bree/well"),
                ServerQuestVisibility.newlyVisible(sent,
                        Arrays.asList("server:bree/well", "server:bree/rats",
                                "server:bree/well"), SERVER));
    }

    @Test
    public void aQuestNobodyNeedsIsNeverSent() {
        assertTrue(ServerQuestVisibility.newlyVisible(
                Collections.<String>emptySet(), null, SERVER).isEmpty());
        assertTrue(ServerQuestVisibility.newlyVisible(
                Collections.<String>emptySet(),
                Collections.<String>emptyList(), SERVER).isEmpty());
    }

    @Test
    public void aReloadKeepsWhatStillExistsAndWhatTheLogNames() {
        Set<String> sent = new LinkedHashSet<String>(Arrays.asList(
                "server:bree/rats", "server:gone"));
        Set<String> after = ServerQuestVisibility.afterReload(sent,
                Arrays.asList("server:bree/well", "losttales:tutorial/meet_nia"),
                SERVER);
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "server:bree/rats", "server:bree/well")), after);
    }
}
