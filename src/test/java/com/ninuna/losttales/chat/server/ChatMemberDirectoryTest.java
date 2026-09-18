package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.network.packet.LostTalesChatMembersPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The order a member list stands its members in: those here first, by
 * group — a role's place, else a faction's name with Unaligned last, and
 * the ungrouped after every group — then by name; the absent at the end,
 * leaving out whoever is here, until the answer's bound, past which they
 * are counted. And who a whisper is with: the character named by id,
 * else by name, else the account.
 */
public final class ChatMemberDirectoryTest {

    private static LostTalesChatMembersPacket.Member member(String name,
                                                           String groupKey,
                                                           String groupName,
                                                           int order,
                                                           boolean online) {
        return new LostTalesChatMembersPacket.Member(UUID.randomUUID(),
                name, null, name, 0, "", "", 0, groupKey, groupName, order,
                online);
    }

    @Test
    public void membersStandByGroupThenName() {
        List<LostTalesChatMembersPacket.Member> members =
                new ArrayList<LostTalesChatMembersPacket.Member>(Arrays.asList(
                        member("zed", "", "", 0, false),
                        member("Eowyn", LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                                "Unaligned", 0, true),
                        member("bob", "lotr:rohan", "Rohan", 0, true),
                        member("Aragorn", "lotr:gondor", "Gondor", 0, true),
                        member("amy", "lotr:rohan", "Rohan", 0, true),
                        member("Loner", "", "", 0, true)));
        Collections.sort(members, LostTalesChatMembersPacket.ORDER);
        StringBuilder order = new StringBuilder();
        for (LostTalesChatMembersPacket.Member member : members) {
            order.append(member.getName()).append(' ');
        }
        assertEquals("Aragorn amy bob Eowyn Loner zed ", order.toString());
    }

    @Test
    public void theAbsentFollowThoseHereLeavingOutWhoIsHere() {
        List<LostTalesChatMembersPacket.Member> present =
                Arrays.asList(member("Aldric", "lotr:gondor", "Gondor", 0, true));
        List<ChatMemberDirectory.Absentee> absent = Arrays.asList(
                new ChatMemberDirectory.Absentee("c:aldric",
                        member("Aldric", "", "", 0, false)),
                new ChatMemberDirectory.Absentee("c:beren",
                        member("Beren", "", "", 0, false)));
        ChatMemberDirectory.Answer answer = ChatMemberDirectory.assemble(
                present, absent,
                new HashSet<String>(Arrays.asList("c:aldric")), 10);
        assertEquals(2, answer.members.size());
        assertEquals("Aldric", answer.members.get(0).getName());
        assertTrue(answer.members.get(0).isOnline());
        assertEquals("Beren", answer.members.get(1).getName());
        assertEquals(0, answer.unlisted);
    }

    @Test
    public void anAnswerStopsAtItsBoundAndCountsTheAbsentLeftOver() {
        List<ChatMemberDirectory.Absentee> absent =
                new ArrayList<ChatMemberDirectory.Absentee>();
        for (int index = 0; index < 5; index++) {
            absent.add(new ChatMemberDirectory.Absentee("c:" + index,
                    member("p" + index, "", "", 0, false)));
        }
        ChatMemberDirectory.Answer answer = ChatMemberDirectory.assemble(
                Arrays.asList(member("Aldric", "", "", 0, true)), absent,
                Collections.<String>emptySet(), 3);
        assertEquals(3, answer.members.size());
        assertEquals(3, answer.unlisted);
    }

    @Test
    public void anAbsenteeJoinsInNameOrderUnlessAlreadyThere() {
        List<ChatMemberDirectory.Absentee> absent = Arrays.asList(
                new ChatMemberDirectory.Absentee("c:a",
                        member("Aldric", "", "", 0, false)),
                new ChatMemberDirectory.Absentee("c:c",
                        member("Cirdan", "", "", 0, false)));
        List<ChatMemberDirectory.Absentee> joined = ChatMemberDirectory.with(
                absent, new ChatMemberDirectory.Absentee("c:b",
                        member("Beren", "", "", 0, false)));
        assertEquals(3, joined.size());
        assertEquals("Beren", joined.get(1).member.getName());
        assertSame("already there, nothing changes", absent,
                ChatMemberDirectory.with(absent, new ChatMemberDirectory
                        .Absentee("c:a", member("Aldric", "", "", 0, false))));
    }

    @Test
    public void aWhisperIsWithTheCharacterNamedByIdThenByNameElseTheAccount() {
        UUID owner = UUID.randomUUID();
        CharacterRoster roster = new CharacterRoster(owner);
        RoleplayCharacter beren = character(owner, 0, "Beren");
        RoleplayCharacter luthien = character(owner, 1, "Luthien");
        roster.addCharacter(beren);
        roster.addCharacter(luthien);
        assertSame(luthien, ChatMemberDirectory.identityIn(roster,
                luthien.getCharacterId(), "Beren", "Steve"));
        assertSame(beren, ChatMemberDirectory.identityIn(roster, null,
                " beren ", "Steve"));
        assertNull("a conversation with the account",
                ChatMemberDirectory.identityIn(roster, null, "Steve", "Steve"));
        assertNull("a name the roster does not hold",
                ChatMemberDirectory.identityIn(roster, null, "Aragorn", "Steve"));
        assertNull(ChatMemberDirectory.identityIn(null, null, "Beren", "Steve"));
    }

    private static RoleplayCharacter character(UUID owner, int slot, String name) {
        return RoleplayCharacter.builder(UUID.randomUUID(), owner)
                .slot(slot)
                .name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .startingFaction("lotr:gondor")
                .build();
    }

    @Test
    public void rolesStandByTheirPlace() {
        List<LostTalesChatMembersPacket.Member> members =
                new ArrayList<LostTalesChatMembersPacket.Member>(Arrays.asList(
                        member("plain", "", "", 0, true),
                        member("mod", "moderator", "Moderator", 3, true),
                        member("op", "operator", "Operator", 2, true)));
        Collections.sort(members, LostTalesChatMembersPacket.ORDER);
        assertEquals("op", members.get(0).getName());
        assertEquals("mod", members.get(1).getName());
        assertEquals("plain", members.get(2).getName());
    }
}
