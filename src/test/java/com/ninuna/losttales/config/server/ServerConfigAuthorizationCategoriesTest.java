package com.ninuna.losttales.config.server;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;

import java.util.Collections;
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
                new String[] {"operator=read:operator;send:operator"}).set(
                        new String[] {"operator=read:operator;send:operator"});
        config.get(LostTalesConfig.CATEGORY_CHAT, "proximityRadius", 64).set(64);

        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(
                config, ServerConfigSnapshot.EXCLUDED_CATEGORIES,
                Collections.<String>emptySet(), ServerConfigSnapshot.SECRET_KEYS);

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

    /**
     * A subcategory goes with its root, as the files route it: one under
     * the roles is written into the roles' file, so it stays off the
     * surface too.
     */
    @Test
    public void aSubcategoryOfTheRolesIsNotOnTheSettingsSurface() {
        Configuration config = new Configuration();
        config.get(LostTalesConfig.CATEGORY_ROLES + Configuration.CATEGORY_SPLITTER + "extra",
                "grants", new String[0]).set(new String[] {"guest=chat.moderate"});
        config.get(LostTalesConfig.CATEGORY_CHANNELS + Configuration.CATEGORY_SPLITTER + "more",
                "gates", new String[0]).set(new String[0]);
        config.get(LostTalesConfig.CATEGORY_CHAT, "proximityRadius", 64).set(64);

        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(
                config, ServerConfigSnapshot.EXCLUDED_CATEGORIES,
                Collections.<String>emptySet(), ServerConfigSnapshot.SECRET_KEYS);

        assertEquals("only the chat setting is offered", 1, entries.size());
        assertEquals(LostTalesConfig.CATEGORY_CHAT, entries.get(0).getCategory());
        assertEquals(LostTalesConfig.CATEGORY_ROLES,
                ServerConfigSnapshot.rootCategory("Roles.extra"));
        assertEquals(LostTalesConfig.CATEGORY_CHAT,
                ServerConfigSnapshot.rootCategory(LostTalesConfig.CATEGORY_CHAT));
    }

    /** A secret is a secret however its key is spelled, as excluded keys are. */
    @Test
    public void aSecretIsBlankedWhateverTheCaseOfItsKey() {
        Configuration config = new Configuration();
        config.get(LostTalesConfig.CATEGORY_DISCORD, "BOTTOKEN", "").set("hunter2");
        config.get(LostTalesConfig.CATEGORY_DISCORD, "CHANNELBINDINGS",
                new String[0]).set(new String[] {"ooc=BIDIRECTIONAL;channel=1"});

        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(
                config, ServerConfigSnapshot.EXCLUDED_CATEGORIES,
                ServerConfigSnapshot.COMMAND_KEYS, ServerConfigSnapshot.SECRET_KEYS);

        assertEquals("the links stay off, the token is offered blank", 1, entries.size());
        ServerConfigEntry token = entries.get(0);
        assertTrue(token.isSecret());
        assertEquals(Collections.singletonList(""), token.getValues());
    }

    /**
     * The Discord links hold webhook addresses, which are passwords to
     * their Discord channels: the settings surface never shows them, and
     * {@code /losttales discord} is what makes them.
     */
    @Test
    public void theDiscordLinksAreNotOnTheSettingsSurface() {
        Configuration config = new Configuration();
        config.get(LostTalesConfig.CATEGORY_DISCORD, "channelBindings",
                new String[0]).set(new String[] {
                        "ooc=BIDIRECTIONAL;channel=1;webhook=https://discord.com/api/webhooks/1/x"});
        config.get(LostTalesConfig.CATEGORY_DISCORD, "enabled", false).set(true);

        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(
                config, ServerConfigSnapshot.EXCLUDED_CATEGORIES,
                ServerConfigSnapshot.COMMAND_KEYS, ServerConfigSnapshot.SECRET_KEYS);

        assertNull(ServerConfigSnapshot.find(entries,
                LostTalesConfig.CATEGORY_DISCORD, "channelBindings"));
        assertEquals("the bridge's other settings still are", 1, entries.size());
        assertEquals("enabled", entries.get(0).getKey());
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

}
