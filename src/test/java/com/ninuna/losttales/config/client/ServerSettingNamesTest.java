package com.ninuna.losttales.config.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A server setting is named from its key in Title Case, a run of capitals kept whole. */
public final class ServerSettingNamesTest {
    @Test
    public void aKeyIsSplitAtItsCapitals() {
        assertEquals("History Persisted", ServerSettingNames.name("historyPersisted"));
        assertEquals("Max Members", ServerSettingNames.name("maxMembers"));
        assertEquals("Charge Tier One Ticks",
                ServerSettingNames.name("chargeTierOneTicks"));
    }

    @Test
    public void smallWordsStayLowerAfterTheFirst() {
        assertEquals("History per Channel", ServerSettingNames.name("historyPerChannel"));
        assertEquals("Auto Pin Quest on Start",
                ServerSettingNames.name("autoPinQuestOnStart"));
        assertEquals("To Channel", ServerSettingNames.name("toChannel"));
    }

    @Test
    public void anAcronymStaysWhole() {
        assertEquals("Discord URL Timeout", ServerSettingNames.name("discordURLTimeout"));
        assertEquals("HTTP Server", ServerSettingNames.name("HTTPServer"));
        assertEquals("Max HUD", ServerSettingNames.name("maxHUD"));
        assertEquals("Avatar Url Template", ServerSettingNames.name("avatarUrlTemplate"));
    }

    @Test
    public void underscoresPartWordsToo() {
        assertEquals("Combat Markers", ServerSettingNames.name("combat_markers"));
        assertEquals("combatMarkers", ServerSettingNames.camelCase("combat_markers"));
        assertEquals("discord", ServerSettingNames.camelCase("discord"));
        assertEquals("", ServerSettingNames.name(""));
        assertEquals("", ServerSettingNames.name(null));
    }
}
