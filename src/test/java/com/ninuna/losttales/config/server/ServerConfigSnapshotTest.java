package com.ninuna.losttales.config.server;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The snapshot lists every server category with its types, bounds and
 * comments, leaves the client's out, and blanks the secrets, saying only
 * whether each is set.
 */
public final class ServerConfigSnapshotTest {

    private static Configuration sample() {
        Configuration config = new Configuration();
        config.get("client", "showHud", true, "Client only.");
        config.get("discord", "pollIntervalSeconds", 3, "How often.", 2, 60);
        config.get("discord", "botToken", "", "Secret.");
        config.get("discord", "channelBindings", new String[] {"ooc=DISABLED;channel=;webhook="},
                "Bindings.");
        config.get("chat", "proximityRadius", 24.5D, "Blocks.", 1.0D, 512.0D);
        Property style = config.get("chat", "style", "plain", "Style.");
        style.setValidValues(new String[] {"plain", "embed"});
        return config;
    }

    @Test
    public void everyServerKeyIsListedWithItsShapeAndTheClientsAreNot() {
        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(sample(),
                ServerConfigSnapshot.CLIENT_CATEGORIES, Collections.<String>emptySet(),
                ServerConfigSnapshot.SECRET_KEYS);
        assertNull(ServerConfigSnapshot.find(entries, "client", "showHud"));
        ServerConfigEntry poll = ServerConfigSnapshot.find(entries, "discord", "pollIntervalSeconds");
        assertNotNull(poll);
        assertEquals(ServerConfigEntry.Type.INTEGER, poll.getType());
        assertFalse(poll.isList());
        assertEquals("3", poll.getValue());
        assertEquals("3", poll.getDefault());
        assertEquals("2", poll.getMinValue());
        assertEquals("60", poll.getMaxValue());
        assertEquals("How often.", poll.getComment());
        ServerConfigEntry radius = ServerConfigSnapshot.find(entries, "chat", "proximityRadius");
        assertEquals(ServerConfigEntry.Type.DOUBLE, radius.getType());
        assertEquals("24.5", radius.getValue());
        ServerConfigEntry bindings = ServerConfigSnapshot.find(entries, "discord", "channelBindings");
        assertTrue(bindings.isList());
        assertEquals(Collections.singletonList("ooc=DISABLED;channel=;webhook="),
                bindings.getValues());
        ServerConfigEntry style = ServerConfigSnapshot.find(entries, "chat", "style");
        assertEquals(Arrays.asList("plain", "embed"), style.getValidValues());
        // Sorted by category, then key.
        assertEquals("chat", entries.get(0).getCategory());
        assertEquals("discord", entries.get(entries.size() - 1).getCategory());
    }

    @Test
    public void aSecretLeavesAsAnEmptyValueMarkedSecret() {
        Configuration config = sample();
        config.getCategory("discord").get("botToken").set("hunter2");
        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(config,
                ServerConfigSnapshot.CLIENT_CATEGORIES, Collections.<String>emptySet(),
                ServerConfigSnapshot.SECRET_KEYS);
        ServerConfigEntry token = ServerConfigSnapshot.find(entries, "discord", "botToken");
        assertTrue(token.isSecret());
        assertTrue(token.isSecretSet());
        assertEquals(Collections.singletonList(""), token.getValues());
        assertEquals("", token.getDefault());
        // An empty secret leaves with no value at all.
        ServerConfigEntry unset = ServerConfigSnapshot.find(
                ServerConfigSnapshot.fromConfiguration(sample(),
                        ServerConfigSnapshot.CLIENT_CATEGORIES, Collections.<String>emptySet(),
                        ServerConfigSnapshot.SECRET_KEYS),
                "discord", "botToken");
        assertTrue(unset.isSecret());
        assertFalse(unset.isSecretSet());
        assertEquals("", unset.getValue());
        // Without the secret set the value would travel.
        ServerConfigEntry shown = ServerConfigSnapshot.find(
                ServerConfigSnapshot.fromConfiguration(config,
                        ServerConfigSnapshot.CLIENT_CATEGORIES, Collections.<String>emptySet(),
                        new HashSet<String>()),
                "discord", "botToken");
        assertFalse(shown.isSecret());
        assertFalse(shown.isSecretSet());
        assertEquals("hunter2", shown.getValue());
    }
}
