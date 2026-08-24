package com.ninuna.losttales.user;

import com.ninuna.losttales.chat.ChatAccountRole;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Recognized users are keyed by a valid, unique account id. */
public final class ELostTalesUserTest {

    @Test
    public void everyRecognizedUserHasAValidUniqueId() {
        Set<UUID> seen = new HashSet<UUID>();
        for (ELostTalesUser user : ELostTalesUser.values()) {
            if (!user.isRecognized()) {
                continue;
            }
            assertNotNull(user + " has no parseable uuid", user.getUniqueId());
            assertEquals(user.getUuid(), user.getUniqueId().toString());
            assertTrue(user + " shares its uuid", seen.add(user.getUniqueId()));
            assertTrue(user.getName().length() > 0);
            assertEquals(user, ELostTalesUser.byUniqueId(user.getUniqueId()));
        }
    }

    @Test
    public void nullUserMatchesNobody() {
        assertFalse(ELostTalesUser.NULL.isRecognized());
        assertEquals(ELostTalesUser.NULL, ELostTalesUser.byUniqueId(null));
        assertEquals(ELostTalesUser.NULL,
                ELostTalesUser.byUniqueId(UUID.randomUUID()));
        assertEquals(ChatAccountRole.NONE,
                ELostTalesUser.NULL.getRecognition().getChatRole());
    }

    @Test
    public void theTeamWearsTheDeveloperRole() {
        ELostTalesUser[] team = { ELostTalesUser.NINUNA,
                ELostTalesUser.SCOSHER, ELostTalesUser.BALARAUKO,
                ELostTalesUser.CAPTAIN_CHEESE };
        for (ELostTalesUser member : team) {
            assertEquals(ELostTalesUserRecognition.DEVELOPER,
                    member.getRecognition());
            assertEquals(ChatAccountRole.DEVELOPER,
                    member.getRecognition().getChatRole());
        }
    }
}
