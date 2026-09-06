package com.ninuna.losttales.config.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.compat.discord.LostTalesDiscordBridge;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The server's own config, read and written live: a snapshot of every
 * server-side key for an operator's screen or a command, and an apply
 * that validates each change against the file, writes the file once,
 * reloads the static values and restarts whatever the changed categories
 * own — the Discord bridge for its category, the chat access sync for the
 * chat's. Server thread only; the operator check is the caller's on the
 * network path and the command permission's on the command path.
 */
public final class LostTalesServerConfigService {

    private LostTalesServerConfigService() {}

    /**
     * Whether this player may read and change the server's config: the
     * {@link LostTalesCapability#SERVER_CONFIG} capability, held by
     * operators and by any role the config grants it to. A holder can
     * edit the roles themselves, so granting it is granting everything.
     */
    public static boolean canEditServerConfig(EntityPlayerMP player) {
        return LostTalesPermissions.has(player, LostTalesCapability.SERVER_CONFIG);
    }

    /** Every server-side key as the file holds it now; empty without a file. */
    public static List<ServerConfigEntry> snapshot() {
        Configuration config = openFile();
        if (config == null) {
            return new ArrayList<ServerConfigEntry>();
        }
        return ServerConfigSnapshot.fromConfiguration(config,
                ServerConfigSnapshot.CLIENT_CATEGORIES, ServerConfigSnapshot.SECRET_KEYS);
    }

    /**
     * Validates and writes the changes, then reloads. A secret left empty
     * is kept as it is. A change naming a client key is refused: the
     * server's file is not the place for it.
     */
    public static ServerConfigApplyResult apply(List<ServerConfigChange> changes) {
        Configuration config = openFile();
        if (config == null) {
            return ServerConfigApplyResult.refusedOutright(
                    "The server has no config file loaded.");
        }
        List<ServerConfigEntry> entries = ServerConfigSnapshot.fromConfiguration(config,
                ServerConfigSnapshot.CLIENT_CATEGORIES, ServerConfigSnapshot.SECRET_KEYS);
        List<String> applied = new ArrayList<String>();
        List<ServerConfigApplyResult.Refusal> refused =
                new ArrayList<ServerConfigApplyResult.Refusal>();
        Set<String> touchedCategories = new LinkedHashSet<String>();
        for (ServerConfigChange change : changes) {
            ServerConfigEntry entry = ServerConfigSnapshot.find(entries,
                    change.getCategory(), change.getKey());
            if (entry == null) {
                refused.add(new ServerConfigApplyResult.Refusal(change.qualifiedName(),
                        ServerConfigSnapshot.CLIENT_CATEGORIES.contains(
                                change.getCategory().toLowerCase(Locale.ROOT))
                                ? "a client setting, not the server's" : "no such key"));
                continue;
            }
            if (entry.isSecret() && !change.isList() && change.getValue().length() == 0) {
                // An empty secret is the screen saying "leave it".
                continue;
            }
            String reason = ServerConfigChangeValidator.refusal(entry, change);
            if (reason != null) {
                refused.add(new ServerConfigApplyResult.Refusal(change.qualifiedName(), reason));
                continue;
            }
            ConfigCategory category = config.getCategory(entry.getCategory());
            Property property = category.get(entry.getKey());
            if (change.isList()) {
                property.set(change.getValues().toArray(new String[change.getValues().size()]));
            } else {
                property.set(change.getValue().trim());
            }
            applied.add(entry.qualifiedName());
            touchedCategories.add(entry.getCategory().toLowerCase(Locale.ROOT));
        }
        List<String> restarted = new ArrayList<String>();
        if (!applied.isEmpty()) {
            if (config.hasChanged()) {
                config.save();
            }
            LostTalesConfig.reload();
            restarted.addAll(restartOwners(touchedCategories));
            FMLLog.info("[%s] Server config changed live: %s", LostTalesMetaData.MOD_ID,
                    applied);
        }
        return new ServerConfigApplyResult(applied, refused, restarted, "");
    }

    /** Re-reads the file and restarts everything a category can own. */
    public static List<String> reloadAll() {
        LostTalesConfig.reload();
        Set<String> all = new LinkedHashSet<String>();
        all.add(LostTalesConfig.CATEGORY_DISCORD);
        all.add(LostTalesConfig.CATEGORY_CHAT);
        List<String> restarted = restartOwners(all);
        FMLLog.info("[%s] Server config reloaded from %s", LostTalesMetaData.MOD_ID,
                LostTalesConfig.getServerConfigFile());
        return restarted;
    }

    /** What the changed categories own, restarted; the names for the operator. */
    private static List<String> restartOwners(Set<String> categories) {
        List<String> restarted = new ArrayList<String>();
        if (categories.contains(LostTalesConfig.CATEGORY_DISCORD)) {
            LostTalesDiscordBridge.getInstance().start();
            restarted.add("Discord bridge");
        }
        if (categories.contains(LostTalesConfig.CATEGORY_CHAT)
                || categories.contains(LostTalesConfig.CATEGORY_ROLES)
                || categories.contains(LostTalesConfig.CATEGORY_CHANNELS)) {
            LostTalesChatService.sendAccessToAll(null);
            restarted.add("chat access");
        }
        return restarted;
    }

    /**
     * The server's files as they are on disk — the options, the roles
     * and the channels as one configuration — with the category metadata
     * the screen shows.
     */
    private static Configuration openFile() {
        Configuration config = LostTalesConfig.openServerConfiguration();
        if (config == null) {
            return null;
        }
        config.load();
        LostTalesConfig.applyGuiMetadata(config);
        return config;
    }
}
