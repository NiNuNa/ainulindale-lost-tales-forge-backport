package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatPresence;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Each player is told the statuses of the Discord members they may see, and no others. */
public class DiscordMemberStatusesTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final List<String> OOC = Collections.singletonList("200");
    private static final List<String> NONE = Collections.emptyList();

    private final Map<String, ChatPresence> statuses = new HashMap<String, ChatPresence>();
    private final Map<String, String> lines = new HashMap<String, String>();
    private final Map<String, Set<String>> seeing = new HashMap<String, Set<String>>();
    private final DiscordMemberStatuses.Source source = new DiscordMemberStatuses.Source() {
        @Override
        public Set<String> usersSeeing(List<String> channelIds) {
            Set<String> users = new HashSet<String>();
            for (String channelId : channelIds) {
                Set<String> of = seeing.get(channelId);
                if (of != null) {
                    users.addAll(of);
                }
            }
            return users;
        }

        @Override
        public ChatPresence statusOf(String userId) {
            return statuses.get(userId);
        }

        @Override
        public String lineOf(String userId) {
            String line = lines.get(userId);
            return line == null ? "" : line;
        }
    };
    private DiscordMemberStatuses told;

    @Before
    public void twoMembersCanSeeTheChannel() {
        this.told = new DiscordMemberStatuses();
        this.seeing.put("200", new HashSet<String>(java.util.Arrays.asList("1", "2")));
        this.statuses.put("1", ChatPresence.ONLINE);
        this.statuses.put("3", ChatPresence.ONLINE);
    }

    /** A player who has just joined is told everyone online they may see, and nobody else. */
    @Test
    public void aJoiningPlayerIsToldWhoIsOnline() {
        Map<String, ChatPresence> sent = step(OOC, Collections.<String>emptySet(), false);
        assertEquals(Collections.singletonMap("1", ChatPresence.ONLINE), sent);
        // Nothing changed, nothing is sent.
        assertTrue(step(OOC, Collections.<String>emptySet(), false).isEmpty());
    }

    /** A change reaches the players who may see the member, and no others. */
    @Test
    public void aChangeReachesOnlyThoseWhoMaySeeTheMember() {
        step(OOC, Collections.<String>emptySet(), false);
        this.statuses.put("2", ChatPresence.DO_NOT_DISTURB);
        this.statuses.put("3", ChatPresence.AWAY);
        Map<String, ChatPresence> sent = step(OOC, set("2", "3"), false);
        assertEquals(Collections.singletonMap("2", ChatPresence.DO_NOT_DISTURB), sent);
        this.statuses.remove("1");
        sent = step(OOC, set("1"), false);
        assertTrue(sent.containsKey("1"));
        assertNull(sent.get("1"));
    }

    /** A player who can no longer read the channel hears that its members are offline. */
    @Test
    public void losingTheChannelTellsItsMembersOffline() {
        step(OOC, Collections.<String>emptySet(), false);
        Map<String, ChatPresence> sent = step(NONE, Collections.<String>emptySet(), false);
        assertTrue(sent.containsKey("1"));
        assertNull(sent.get("1"));
        assertTrue(step(NONE, Collections.<String>emptySet(), false).isEmpty());
    }

    /** Who can see a channel changing has everything checked again. */
    @Test
    public void aChangeOfWhoCanSeeIsCheckedForEveryone() {
        step(OOC, Collections.<String>emptySet(), false);
        this.seeing.get("200").add("3");
        assertTrue(step(OOC, Collections.<String>emptySet(), false).isEmpty());
        assertEquals(Collections.singletonMap("3", ChatPresence.ONLINE),
                step(OOC, Collections.<String>emptySet(), true));
    }

    /** A player who left is told everything again when they come back. */
    @Test
    public void aPlayerWhoLeftIsToldEverythingAgain() {
        step(OOC, Collections.<String>emptySet(), false);
        this.told.forget(PLAYER);
        assertEquals(Collections.singletonMap("1", ChatPresence.ONLINE),
                step(OOC, Collections.<String>emptySet(), false));
    }

    /** A member's custom status travels as their line, and a new line alone is sent again. */
    @Test
    public void aCustomStatusTravelsAsTheLine() {
        this.lines.put("1", "Out riding");
        assertEquals("Out riding", shownStep(OOC, Collections.<String>emptySet(), false)
                .get("1").line);
        this.lines.put("1", "Back soon");
        Map<String, DiscordMemberStatuses.Shown> sent = shownStep(OOC, set("1"), false);
        assertEquals(ChatPresence.ONLINE, sent.get("1").status);
        assertEquals("Back soon", sent.get("1").line);
        assertTrue(shownStep(OOC, set("1"), false).isEmpty());
    }

    /** A large server reaches a player over several steps. */
    @Test
    public void aLargeServerIsToldOverSeveralSteps() {
        Set<String> many = new HashSet<String>();
        for (int index = 0; index < DiscordMemberStatuses.MAX_PER_STEP + 88; index++) {
            many.add("m" + index);
            this.statuses.put("m" + index, ChatPresence.ONLINE);
        }
        this.seeing.put("200", many);
        assertEquals(DiscordMemberStatuses.MAX_PER_STEP,
                step(OOC, Collections.<String>emptySet(), false).size());
        assertEquals(88, step(OOC, Collections.<String>emptySet(), false).size());
        assertTrue(step(OOC, Collections.<String>emptySet(), false).isEmpty());
    }

    /** One step for the one player: each member's status sent (null for offline). */
    private Map<String, ChatPresence> step(List<String> channels, Set<String> changed,
                                           boolean seeingChanged) {
        Map<String, ChatPresence> statusesSent = new LinkedHashMap<String, ChatPresence>();
        for (Map.Entry<String, DiscordMemberStatuses.Shown> entry
                : shownStep(channels, changed, seeingChanged).entrySet()) {
            statusesSent.put(entry.getKey(), entry.getValue() == null ? null
                    : entry.getValue().status);
        }
        return statusesSent;
    }

    private Map<String, DiscordMemberStatuses.Shown> shownStep(List<String> channels,
            Set<String> changed, boolean seeingChanged) {
        Map<UUID, List<String>> players = new LinkedHashMap<UUID, List<String>>();
        players.put(PLAYER, channels);
        Map<UUID, Map<String, DiscordMemberStatuses.Shown>> sends = this.told.step(players,
                changed, seeingChanged, this.source);
        Map<String, DiscordMemberStatuses.Shown> sent = sends.get(PLAYER);
        return sent == null ? Collections.<String, DiscordMemberStatuses.Shown>emptyMap() : sent;
    }

    private static Set<String> set(String... ids) {
        return new HashSet<String>(java.util.Arrays.asList(ids));
    }
}
