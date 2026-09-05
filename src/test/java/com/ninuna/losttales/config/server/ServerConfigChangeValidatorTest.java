package com.ninuna.losttales.config.server;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A change must fit its entry's shape, type, bounds and valid values. */
public final class ServerConfigChangeValidatorTest {

    private static ServerConfigEntry entry(ServerConfigEntry.Type type, boolean list,
                                           String minimum, String maximum,
                                           List<String> valid) {
        return new ServerConfigEntry("chat", "key", type, list,
                Collections.singletonList("0"), Collections.singletonList("0"),
                minimum, maximum, "", "", false, valid);
    }

    @Test
    public void wholeNumbersStayInsideTheirBounds() {
        ServerConfigEntry entry = entry(ServerConfigEntry.Type.INTEGER, false, "2", "60", null);
        assertNull(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "30")));
        assertNotNull(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "1")));
        assertNotNull(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "61")));
        assertNotNull(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "three")));
        assertNotNull(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "99999999999")));
    }

    @Test
    public void numbersBooleansAndChoicesAreCheckedByType() {
        ServerConfigEntry decimal = entry(ServerConfigEntry.Type.DOUBLE, false, "1.0", "512.0", null);
        assertNull(ServerConfigChangeValidator.refusal(decimal,
                new ServerConfigChange("chat", "key", "24.5")));
        assertNotNull(ServerConfigChangeValidator.refusal(decimal,
                new ServerConfigChange("chat", "key", "NaN")));
        assertNotNull(ServerConfigChangeValidator.refusal(decimal,
                new ServerConfigChange("chat", "key", "600")));
        ServerConfigEntry flag = entry(ServerConfigEntry.Type.BOOLEAN, false, "", "", null);
        assertNull(ServerConfigChangeValidator.refusal(flag,
                new ServerConfigChange("chat", "key", "TRUE")));
        assertNotNull(ServerConfigChangeValidator.refusal(flag,
                new ServerConfigChange("chat", "key", "yes")));
        ServerConfigEntry choice = entry(ServerConfigEntry.Type.STRING, false, "", "",
                Arrays.asList("plain", "embed"));
        assertNull(ServerConfigChangeValidator.refusal(choice,
                new ServerConfigChange("chat", "key", "embed")));
        assertNotNull(ServerConfigChangeValidator.refusal(choice,
                new ServerConfigChange("chat", "key", "fancy")));
    }

    @Test
    public void theShapeMustMatchAndListsAreBounded() {
        ServerConfigEntry list = entry(ServerConfigEntry.Type.STRING, true, "", "", null);
        assertNull(ServerConfigChangeValidator.refusal(list,
                new ServerConfigChange("chat", "key", true, Arrays.asList("a", "b"))));
        assertEquals("expects a list", ServerConfigChangeValidator.refusal(list,
                new ServerConfigChange("chat", "key", "a")));
        ServerConfigEntry single = entry(ServerConfigEntry.Type.STRING, false, "", "", null);
        assertEquals("expects a single value", ServerConfigChangeValidator.refusal(single,
                new ServerConfigChange("chat", "key", true, Arrays.asList("a"))));
        String[] many = new String[ServerConfigChangeValidator.MAX_LIST_ITEMS + 1];
        Arrays.fill(many, "x");
        assertNotNull(ServerConfigChangeValidator.refusal(list,
                new ServerConfigChange("chat", "key", true, Arrays.asList(many))));
        char[] longValue = new char[ServerConfigChangeValidator.MAX_VALUE_LENGTH + 1];
        Arrays.fill(longValue, 'y');
        assertNotNull(ServerConfigChangeValidator.refusal(single,
                new ServerConfigChange("chat", "key", new String(longValue))));
        assertEquals("no such key", ServerConfigChangeValidator.refusal(null,
                new ServerConfigChange("chat", "key", "a")));
        assertTrue(ServerConfigChangeValidator.refusal(single,
                new ServerConfigChange("chat", "key", "anything")) == null);
    }
}
