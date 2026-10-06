package com.ninuna.losttales.config.server;

import com.ninuna.losttales.util.EnglishWords;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A change must fit its entry's shape, type, bounds and valid values, and
 * a refusal says why in the lang file's words.
 */
public final class ServerConfigChangeValidatorTest {

    /** The reason a change is refused, in English. */
    private static String reason(ServerConfigApplyResult.Refusal refusal) {
        return refusal.reason(EnglishWords.INSTANCE);
    }

    private static ServerConfigEntry entry(ServerConfigEntry.Type type, boolean list,
                                           String minimum, String maximum,
                                           List<String> valid) {
        return new ServerConfigEntry("chat", "key", type, list,
                Collections.singletonList("0"), Collections.singletonList("0"),
                minimum, maximum, false, valid);
    }

    @Test
    public void wholeNumbersStayInsideTheirBounds() {
        ServerConfigEntry entry = entry(ServerConfigEntry.Type.INTEGER, false, "2", "60", null);
        assertNull(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "30")));
        assertEquals("below the minimum 2", reason(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "1"))));
        assertEquals("above the maximum 60", reason(ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "61"))));
        assertEquals("expects a whole number", reason(ServerConfigChangeValidator.refusal(
                entry, new ServerConfigChange("chat", "key", "three"))));
        assertEquals("outside the whole-number range", reason(
                ServerConfigChangeValidator.refusal(entry,
                        new ServerConfigChange("chat", "key", "99999999999"))));
        assertEquals("chat.key", ServerConfigChangeValidator.refusal(entry,
                new ServerConfigChange("chat", "key", "1")).getName());
    }

    @Test
    public void numbersBooleansAndChoicesAreCheckedByType() {
        ServerConfigEntry decimal = entry(ServerConfigEntry.Type.DOUBLE, false, "1.0", "512.0", null);
        assertNull(ServerConfigChangeValidator.refusal(decimal,
                new ServerConfigChange("chat", "key", "24.5")));
        assertEquals("expects a finite number", reason(ServerConfigChangeValidator.refusal(
                decimal, new ServerConfigChange("chat", "key", "NaN"))));
        assertEquals("above the maximum 512.0", reason(ServerConfigChangeValidator.refusal(
                decimal, new ServerConfigChange("chat", "key", "600"))));
        ServerConfigEntry flag = entry(ServerConfigEntry.Type.BOOLEAN, false, "", "", null);
        assertNull(ServerConfigChangeValidator.refusal(flag,
                new ServerConfigChange("chat", "key", "TRUE")));
        assertEquals("expects true or false", reason(ServerConfigChangeValidator.refusal(flag,
                new ServerConfigChange("chat", "key", "yes"))));
        ServerConfigEntry choice = entry(ServerConfigEntry.Type.STRING, false, "", "",
                Arrays.asList("plain", "embed"));
        assertNull(ServerConfigChangeValidator.refusal(choice,
                new ServerConfigChange("chat", "key", "embed")));
        assertEquals("expects one of [plain, embed]", reason(
                ServerConfigChangeValidator.refusal(choice,
                        new ServerConfigChange("chat", "key", "fancy"))));
    }

    @Test
    public void theShapeMustMatchAndListsAreBounded() {
        ServerConfigEntry list = entry(ServerConfigEntry.Type.STRING, true, "", "", null);
        assertNull(ServerConfigChangeValidator.refusal(list,
                new ServerConfigChange("chat", "key", true, Arrays.asList("a", "b"))));
        assertEquals("expects a list", reason(ServerConfigChangeValidator.refusal(list,
                new ServerConfigChange("chat", "key", "a"))));
        ServerConfigEntry single = entry(ServerConfigEntry.Type.STRING, false, "", "", null);
        assertEquals("expects a single value", reason(ServerConfigChangeValidator.refusal(
                single, new ServerConfigChange("chat", "key", true, Arrays.asList("a")))));
        String[] many = new String[ServerConfigChangeValidator.MAX_LIST_ITEMS + 1];
        Arrays.fill(many, "x");
        assertEquals("more than 256 items", reason(ServerConfigChangeValidator.refusal(list,
                new ServerConfigChange("chat", "key", true, Arrays.asList(many)))));
        char[] longValue = new char[ServerConfigChangeValidator.MAX_VALUE_LENGTH + 1];
        Arrays.fill(longValue, 'y');
        assertEquals("longer than 4096 characters", reason(ServerConfigChangeValidator.refusal(
                single, new ServerConfigChange("chat", "key", new String(longValue)))));
        assertEquals("no such key", reason(ServerConfigChangeValidator.refusal(null,
                new ServerConfigChange("chat", "key", "a"))));
        assertTrue(ServerConfigChangeValidator.refusal(single,
                new ServerConfigChange("chat", "key", "anything")) == null);
    }
}
