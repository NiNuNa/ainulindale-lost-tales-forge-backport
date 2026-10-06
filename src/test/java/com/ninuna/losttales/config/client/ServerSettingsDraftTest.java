package com.ninuna.losttales.config.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.window.NumberStepper;
import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigChangeValidator;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * What waits on the Server Settings page: a change put back is none, a
 * list's lines change one by one, a secret is only ever replaced, and a
 * Save's answer leaves what was refused waiting.
 */
public final class ServerSettingsDraftTest {
    private static ServerConfigEntry single(String category, String key,
                                            ServerConfigEntry.Type type,
                                            String value, String min, String max) {
        return new ServerConfigEntry(category, key, type, false,
                Collections.singletonList(value), Collections.singletonList(value),
                min, max, false, null);
    }

    private static ServerConfigEntry list(String key, String... values) {
        return new ServerConfigEntry("chat", key, ServerConfigEntry.Type.STRING, true,
                Arrays.asList(values), Collections.<String>emptyList(), "", "",
                false, null);
    }

    private static ServerConfigEntry secret(boolean set) {
        return new ServerConfigEntry("discord", "botToken", ServerConfigEntry.Type.STRING,
                false, set ? Collections.singletonList("") : Collections.<String>emptyList(),
                Collections.singletonList(""), "", "", true, null);
    }

    private static ServerSettingsDraft loaded(ServerConfigEntry... entries) {
        ServerSettingsDraft draft = new ServerSettingsDraft();
        draft.load(Arrays.asList(entries));
        return draft;
    }

    private static ServerConfigApplyResult applied(String... names) {
        return new ServerConfigApplyResult(Arrays.asList(names), null, null, "");
    }

    @Test
    public void aSwitchFlippedBackIsNoChange() {
        ServerConfigEntry persisted = single("chat", "historyPersisted",
                ServerConfigEntry.Type.BOOLEAN, "true", "", "");
        ServerSettingsDraft draft = loaded(persisted);
        draft.set(persisted, "false");
        assertTrue(draft.isChanged(persisted));
        assertEquals(1, draft.count());
        assertEquals("false", draft.value(persisted));
        draft.set(persisted, "true");
        assertFalse(draft.isChanged(persisted));
        assertTrue(draft.changes().isEmpty());
    }

    @Test
    public void numbersAreComparedAsNumbers() {
        ServerConfigEntry radius = single("chat", "proximityRadius",
                ServerConfigEntry.Type.DOUBLE, "24.5", "1.0", "512.0");
        ServerSettingsDraft draft = loaded(radius);
        draft.set(radius, "24.50");
        assertFalse(draft.isChanged(radius));
        draft.set(radius, "25.0");
        List<ServerConfigChange> changes = draft.changes();
        assertEquals(1, changes.size());
        assertEquals("chat.proximityRadius", changes.get(0).qualifiedName());
        assertFalse(changes.get(0).isList());
        assertEquals("25.0", changes.get(0).getValue());
    }

    @Test
    public void aListChangesLineByLineAndBackIsNoChange() {
        ServerConfigEntry welcome = list("welcomeLines", "Hello", "Mind the orcs");
        ServerSettingsDraft draft = loaded(welcome);
        draft.setItem(welcome, 1, "  Mind the wargs ");
        assertEquals(Arrays.asList("Hello", "Mind the wargs"), draft.values(welcome));
        draft.addItem(welcome, "Be kind");
        draft.addItem(welcome, "   ");
        assertEquals(3, draft.values(welcome).size());
        // An empty line takes the item out.
        draft.setItem(welcome, 0, "");
        assertEquals(Arrays.asList("Mind the wargs", "Be kind"), draft.values(welcome));
        List<ServerConfigChange> changes = draft.changes();
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).isList());
        assertEquals(Arrays.asList("Mind the wargs", "Be kind"), changes.get(0).getValues());
        // Put back as the server holds it, nothing waits.
        draft.setItem(welcome, 1, "");
        draft.setItem(welcome, 0, "Mind the orcs");
        draft.setItem(welcome, 5, "Out of range");
        assertEquals(Collections.singletonList("Mind the orcs"), draft.values(welcome));
        draft.addItem(welcome, "Hello");
        assertTrue(draft.isChanged(welcome));
        draft.setItem(welcome, 0, "Hello");
        draft.setItem(welcome, 1, "Mind the orcs");
        assertFalse(draft.isChanged(welcome));
        assertTrue(draft.changes().isEmpty());
    }

    @Test
    public void aListTakesNoMoreLinesThanAChangeCarries() {
        String[] lines = new String[ServerConfigChangeValidator.MAX_LIST_ITEMS];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = "line " + index;
        }
        ServerConfigEntry full = list("profanityWords", lines);
        ServerSettingsDraft draft = loaded(full);
        assertFalse(draft.canAdd(full));
        draft.addItem(full, "one more");
        assertFalse(draft.isChanged(full));
        draft.setItem(full, 0, "");
        assertTrue(draft.canAdd(full));
    }

    @Test
    public void aSecretIsOnlyReplacedAndAnEmptyValueChangesNothing() {
        ServerConfigEntry token = secret(false);
        ServerSettingsDraft draft = loaded(token);
        draft.set(token, "   ");
        assertFalse(draft.isChanged(token));
        draft.set(token, "abc.def");
        assertTrue(draft.isChanged(token));
        draft.set(token, "");
        assertEquals("abc.def", draft.value(token));
        List<ServerConfigChange> sent = draft.changes();
        assertEquals("abc.def", sent.get(0).getValue());
        // Read again, a secret still waits: what the server holds is never known.
        draft.load(Collections.singletonList(secret(true)));
        assertTrue(draft.isChanged(secret(true)));
        draft.settle(sent, applied("discord.botToken"));
        assertFalse(draft.isChanged(token));
        assertTrue(draft.find("discord.botToken").isSecretSet());
        assertEquals("", draft.value(token));
    }

    @Test
    public void aSaveLeavesTheRefusedWaitingAndTakesTheApplied() {
        ServerConfigEntry members = single("fellowship", "maxMembers",
                ServerConfigEntry.Type.INTEGER, "4", "2", "8");
        ServerConfigEntry radius = single("fellowship", "sharedQuestRadius",
                ServerConfigEntry.Type.INTEGER, "32", "1", "128");
        ServerConfigEntry audit = single("chat", "auditLog",
                ServerConfigEntry.Type.BOOLEAN, "false", "", "");
        ServerSettingsDraft draft = loaded(audit, members, radius);
        draft.set(members, "6");
        draft.set(radius, "200");
        draft.set(audit, "true");
        List<ServerConfigChange> sent = draft.changes();
        assertEquals("chat.auditLog", sent.get(0).qualifiedName());
        assertEquals(3, sent.size());
        // Changed again while the Save was on its way: it still waits.
        draft.set(audit, "false");
        draft.set(audit, "true");
        draft.set(members, "7");
        draft.settle(sent, new ServerConfigApplyResult(
                Arrays.asList("chat.auditLog", "fellowship.maxMembers"),
                Collections.singletonList(new ServerConfigApplyResult.Refusal(
                        "fellowship.sharedQuestRadius",
                        ServerConfigChangeValidator.REASON + "above_maximum", "128")),
                null, ""));
        assertFalse(draft.isChanged(audit));
        assertEquals("true", draft.find("chat.auditLog").getValue());
        assertTrue(draft.isChanged(members));
        assertEquals("7", draft.value(members));
        assertEquals("6", draft.find("fellowship.maxMembers").getValue());
        assertTrue(draft.isChanged(radius));
        assertEquals(2, draft.count());
    }

    @Test
    public void readingAgainKeepsWhatStillDiffers() {
        ServerConfigEntry members = single("fellowship", "maxMembers",
                ServerConfigEntry.Type.INTEGER, "4", "2", "8");
        ServerConfigEntry radius = single("fellowship", "sharedQuestRadius",
                ServerConfigEntry.Type.INTEGER, "32", "1", "128");
        ServerConfigEntry gone = single("fellowship", "oldKey",
                ServerConfigEntry.Type.INTEGER, "1", "0", "9");
        ServerSettingsDraft draft = loaded(members, radius, gone);
        draft.set(members, "6");
        draft.set(radius, "64");
        draft.set(gone, "2");
        List<ServerConfigEntry> again = new ArrayList<ServerConfigEntry>();
        again.add(single("fellowship", "maxMembers", ServerConfigEntry.Type.INTEGER,
                "6", "2", "8"));
        again.add(radius);
        draft.load(again);
        assertFalse(draft.isChanged(members));
        assertTrue(draft.isChanged(radius));
        assertNull(draft.find("fellowship.oldKey"));
        assertEquals(1, draft.count());
        draft.discard();
        assertEquals(0, draft.count());
        draft.clear();
        assertFalse(draft.isLoaded());
        assertTrue(draft.entries().isEmpty());
    }

    @Test
    public void aNumberStepsByAHundredthOfItsRangeAtTheLeast() {
        NumberStepper members = ServerSettingsDraft.stepper(single("fellowship", "maxMembers",
                ServerConfigEntry.Type.INTEGER, "4", "2", "8"));
        assertEquals(1.0D, members.step, 0.0D);
        assertEquals(0, members.decimals);
        assertEquals(2.0D, members.min, 0.0D);
        assertEquals(8.0D, members.max, 0.0D);
        NumberStepper bytes = ServerSettingsDraft.stepper(single("characters",
                "characterStateMaxSnapshotBytes", ServerConfigEntry.Type.INTEGER,
                "2097152", "65536", "16777216"));
        assertEquals(100000.0D, bytes.step, 0.0D);
        NumberStepper multiplier = ServerSettingsDraft.stepper(single("ranged_combat",
                "chargeTierOneDamageMultiplier", ServerConfigEntry.Type.DOUBLE,
                "1.12", "1.0", "3.0"));
        assertEquals(0.01D, multiplier.step, 1.0E-9D);
        assertEquals(2, multiplier.decimals);
        assertEquals("1.12", multiplier.format(1.12D));
        NumberStepper knockback = ServerSettingsDraft.stepper(single("ranged_combat",
                "chargeTierOneKnockback", ServerConfigEntry.Type.DOUBLE, "0.0", "0.0", "1.0"));
        assertEquals(0.01D, knockback.step, 1.0E-9D);
        assertEquals(2, knockback.decimals);
        NumberStepper radius = ServerSettingsDraft.stepper(single("chat",
                "proximityRadius", ServerConfigEntry.Type.DOUBLE, "24.5", "1.0", "512.0"));
        assertEquals(1.0D, radius.step, 0.0D);
        assertEquals(1, radius.decimals);
        assertEquals("25.0", radius.format(radius.stepped(24.5D, true, false)));
    }

    @Test
    public void forgesWidestBoundsAreNoBounds() {
        ServerConfigEntry whole = single("chat", "anything", ServerConfigEntry.Type.INTEGER,
                "5", String.valueOf(Integer.MIN_VALUE), String.valueOf(Integer.MAX_VALUE));
        assertNull(ServerSettingsDraft.bounds(whole));
        assertEquals(1.0D, ServerSettingsDraft.stepper(whole).step, 0.0D);
        ServerConfigEntry decimal = single("chat", "anything", ServerConfigEntry.Type.DOUBLE,
                "0.5", String.valueOf(-Double.MAX_VALUE), String.valueOf(Double.MAX_VALUE));
        assertNull(ServerSettingsDraft.bounds(decimal));
        assertEquals(0.1D, ServerSettingsDraft.stepper(decimal).step, 1.0E-9D);
        ServerConfigEntry unnamed = single("chat", "anything", ServerConfigEntry.Type.INTEGER,
                "5", "", "");
        assertNull(ServerSettingsDraft.bounds(unnamed));
        ServerConfigEntry bounded = single("chat", "anything", ServerConfigEntry.Type.INTEGER,
                "5", "0", "10");
        assertEquals(0.0D, ServerSettingsDraft.bounds(bounded)[0], 0.0D);
        assertEquals(10.0D, ServerSettingsDraft.bounds(bounded)[1], 0.0D);
    }
}
