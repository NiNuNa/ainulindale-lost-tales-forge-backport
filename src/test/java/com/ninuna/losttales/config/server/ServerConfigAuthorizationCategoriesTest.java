package com.ninuna.losttales.config.server;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The settings surface offers what a server may tune, and never what
 * decides who is allowed to do it. The roles, the permissions they reach
 * and the gates the channels ask for are the role command's, so holding
 * {@code server.config} cannot be turned into holding everything else.
 */
public final class ServerConfigAuthorizationCategoriesTest {

    @Test
    public void theRolesAndTheChannelGatesAreNotOnTheSettingsSurface() {
        Configuration config = new Configuration();
        config.get(LostTalesConfig.CATEGORY_ROLES, "definitions",
                new String[] {"operator=name:Operator"}).set(
                        new String[] {"operator=name:Operator"});
        config.get(LostTalesConfig.CATEGORY_ROLES, "members",
                new String[0]).set(new String[0]);
        config.get(LostTalesConfig.CATEGORY_ROLES, "permissions",
                new String[0]).set(new String[0]);
        config.get(LostTalesConfig.CATEGORY_CHANNELS, "gates",
                new String[] {"admin=read:operator;send:operator"}).set(
                        new String[] {"admin=read:operator;send:operator"});
        config.get(LostTalesConfig.CATEGORY_CHAT, "proximityRadius", 64).set(64);

        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(
                config, ServerConfigSnapshot.EXCLUDED_CATEGORIES,
                ServerConfigSnapshot.SECRET_KEYS);

        assertNull("the role definitions are not offered",
                ServerConfigSnapshot.find(entries,
                        LostTalesConfig.CATEGORY_ROLES, "definitions"));
        assertNull("nor who holds them",
                ServerConfigSnapshot.find(entries,
                        LostTalesConfig.CATEGORY_ROLES, "members"));
        assertNull("nor the capabilities they reach",
                ServerConfigSnapshot.find(entries,
                        LostTalesConfig.CATEGORY_ROLES, "permissions"));
        assertNull("nor the gates a channel asks for",
                ServerConfigSnapshot.find(entries,
                        LostTalesConfig.CATEGORY_CHANNELS, "gates"));
        assertEquals("an ordinary server setting still is", 1, entries.size());
        assertEquals(LostTalesConfig.CATEGORY_CHAT, entries.get(0).getCategory());
    }

    @Test
    public void everyAuthorizationCategoryIsExcludedAlongsideTheClientS() {
        for (String category : ServerConfigSnapshot.AUTHORIZATION_CATEGORIES) {
            assertTrue(category + " is excluded",
                    ServerConfigSnapshot.EXCLUDED_CATEGORIES.contains(
                            category.toLowerCase(Locale.ROOT)));
        }
        for (String category : ServerConfigSnapshot.CLIENT_CATEGORIES) {
            assertTrue(category + " is excluded",
                    ServerConfigSnapshot.EXCLUDED_CATEGORIES.contains(
                            category.toLowerCase(Locale.ROOT)));
        }
        assertFalse("the two sets name different categories",
                ServerConfigSnapshot.AUTHORIZATION_CATEGORIES.contains(
                        LostTalesConfig.CATEGORY_CLIENT));
    }

    /** A change naming an excluded category is refused, not quietly applied. */
    @Test
    public void aChangeNamingTheRolesIsNotFoundAmongTheEntries() {
        Configuration config = new Configuration();
        Property roles = config.get(LostTalesConfig.CATEGORY_ROLES, "definitions",
                new String[] {"operator=name:Operator"});
        roles.set(new String[] {"operator=name:Operator"});

        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(
                config, ServerConfigSnapshot.EXCLUDED_CATEGORIES,
                ServerConfigSnapshot.SECRET_KEYS);

        assertTrue("nothing on the surface at all", entries.isEmpty());
    }
}
