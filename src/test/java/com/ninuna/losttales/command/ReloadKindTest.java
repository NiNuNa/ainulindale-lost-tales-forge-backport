package com.ninuna.losttales.command;

import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What {@code /losttales reload} reads and who may ask for it (DATA-11,
 * I82): each kind its own system's capability, {@code all} the server's
 * settings', every kind once in the order the server reads them as it
 * starts.
 */
public final class ReloadKindTest {

    private static ReloadKind.Holder holding(LostTalesCapability... capabilities) {
        final Set<LostTalesCapability> held =
                new HashSet<LostTalesCapability>(Arrays.asList(capabilities));
        return new ReloadKind.Holder() {
            @Override
            public boolean holds(LostTalesCapability capability) {
                return held.contains(capability);
            }
        };
    }

    @Test
    public void eachKindNeedsItsSystemsCapability() {
        assertEquals(LostTalesCapability.QUEST_ADMIN, ReloadKind.capabilityFor("quests"));
        assertEquals(LostTalesCapability.QUEST_ADMIN, ReloadKind.capabilityFor("missives"));
        assertEquals(LostTalesCapability.PROGRESSION_ADMIN, ReloadKind.capabilityFor("skills"));
        assertEquals(LostTalesCapability.MAPMARKER_MANAGE, ReloadKind.capabilityFor("markers"));
        assertEquals(LostTalesCapability.CHARACTER_ADMIN, ReloadKind.capabilityFor("races"));
        assertEquals(LostTalesCapability.CHARACTER_ADMIN, ReloadKind.capabilityFor("capes"));
        assertEquals(LostTalesCapability.CHARACTER_ADMIN, ReloadKind.capabilityFor("kits"));
        assertEquals(LostTalesCapability.CHARACTER_ADMIN, ReloadKind.capabilityFor("lore"));
        assertEquals(LostTalesCapability.SERVER_CONFIG, ReloadKind.capabilityFor("ALL"));
        assertNull(ReloadKind.capabilityFor("recipes"));
        assertNull(ReloadKind.capabilityFor(null));
    }

    @Test
    public void allReadsEveryKindOnceInTheOrderOfAStart() {
        assertEquals(Arrays.asList(ReloadKind.QUESTS, ReloadKind.SKILLS, ReloadKind.RACES,
                ReloadKind.LORE, ReloadKind.MARKERS, ReloadKind.KITS, ReloadKind.MISSIVES),
                ReloadKind.asked("all"));
        assertEquals(Collections.singletonList(ReloadKind.CAPES), ReloadKind.asked(" Capes "));
        assertTrue(ReloadKind.asked("everything").isEmpty());
    }

    @Test
    public void aSenderIsOfferedOnlyTheKindsItMayRead() {
        assertEquals(Arrays.asList("quests", "missives"),
                ReloadKind.wordsFor(holding(LostTalesCapability.QUEST_ADMIN)));
        assertEquals(Arrays.asList("races", "capes", "kits", "lore"),
                ReloadKind.wordsFor(holding(LostTalesCapability.CHARACTER_ADMIN)));
        assertEquals(Collections.singletonList("all"),
                ReloadKind.wordsFor(holding(LostTalesCapability.SERVER_CONFIG)));
        assertTrue(ReloadKind.wordsFor(holding()).isEmpty());
    }

    @Test
    public void theUsageNamesEveryWord() {
        StringBuilder words = new StringBuilder();
        for (String word : ReloadKind.words()) {
            words.append(words.length() == 0 ? "" : "|").append(word);
        }
        assertEquals("reload <" + words + ">", ELostTalesSubCommand.RELOAD.getUsage());
        assertEquals("/losttales " + ELostTalesSubCommand.RELOAD.getUsage(),
                new LostTalesCommandReload().getCommandUsage(null));
    }

    @Test
    public void anOperatorMayReadEachKindAndOnlyAnAdministratorAll() {
        LostTalesCommandReload command = new LostTalesCommandReload();
        assertEquals(2, command.getRequiredPermissionLevel());
        assertTrue(command.canCommandSenderUseCommand(FakeCommandSender.operator("Ops")));
        assertFalse(command.canCommandSenderUseCommand(FakeCommandSender.player("Someone")));

        List<?> operator = command.addTabCompletionOptions(
                FakeCommandSender.operator("Ops"), new String[] {""});
        assertEquals(Arrays.asList("quests", "skills", "markers", "races", "capes", "kits",
                "missives", "lore"), operator);
        List<?> administrator = command.addTabCompletionOptions(
                FakeCommandSender.administrator("Admin"), new String[] {"a"});
        assertEquals(Collections.singletonList("all"), administrator);
    }
}
