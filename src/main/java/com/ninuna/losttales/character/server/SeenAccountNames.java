package com.ninuna.losttales.character.server;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.validation.CharacterNames;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.World;

/**
 * The names of the Minecraft accounts this server has seen. A new
 * character may not take one, so nobody plays under an account's name
 * before that account first joins. The names come from what the server
 * already holds: the players online, its user cache
 * ({@code usercache.json}), its operators, its whitelist, and the owners
 * of this world's rosters. Nothing here asks Mojang: the user cache is
 * read as it stands, never through its lookup by name.
 */
public final class SeenAccountNames {
    /** The most names one check reads, every source together. */
    public static final int MAX_NAMES = 16384;

    /** Where the names are read from: the server's lists, or a test's own. */
    public interface Source {
        /** Adds account names to {@code names} until it holds {@code limit}. */
        void addNames(List<String> names, int limit);
    }

    private SeenAccountNames() {}

    /**
     * Whether {@code name} is the name of an account {@code source}
     * holds, compared as {@link CharacterNames#same} compares names. The
     * name of the account asking, {@code ownAccountName}, is its own to
     * take and never counts.
     */
    public static boolean isAnotherAccountsName(String name,
                                                String ownAccountName,
                                                Source source) {
        String key = CharacterNames.key(name);
        if (source == null || key.length() == 0
                || CharacterNames.same(name, ownAccountName)) {
            return false;
        }
        List<String> names = new ArrayList<String>();
        source.addNames(names, MAX_NAMES);
        int read = Math.min(names.size(), MAX_NAMES);
        for (int index = 0; index < read; index++) {
            if (key.equals(CharacterNames.key(names.get(index)))) {
                return true;
            }
        }
        return false;
    }

    /** The name of the player's own account, as its profile gives it. */
    public static String accountNameOf(EntityPlayerMP player) {
        if (player == null) {
            return "";
        }
        GameProfile profile = player.getGameProfile();
        String name = profile == null || profile.getName() == null
                || profile.getName().trim().length() == 0
                ? player.getCommandSenderName() : profile.getName();
        return name == null ? "" : name.trim();
    }

    /**
     * The running server's lists and the rosters of {@code world}, the
     * roster of {@code ownerId} left out.
     */
    public static Source ofServer(World world, UUID ownerId) {
        return new ServerSource(world, ownerId);
    }

    /** The running server's own lists, read as they stand. */
    private static final class ServerSource implements Source {
        private final World world;
        private final UUID ownerId;

        ServerSource(World world, UUID ownerId) {
            this.world = world;
            this.ownerId = ownerId;
        }

        @Override
        public void addNames(List<String> names, int limit) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                try {
                    for (GameProfile online : server.func_152357_F()) {
                        add(names, limit, online == null ? null : online.getName());
                    }
                } catch (RuntimeException unreadable) {
                    warn("the players online", unreadable);
                }
                try {
                    PlayerProfileCache cache = server.func_152358_ax();
                    if (cache != null) {
                        addAll(names, limit, cache.func_152654_a());
                    }
                } catch (RuntimeException unreadable) {
                    warn("the user cache", unreadable);
                }
                try {
                    ServerConfigurationManager players =
                            server.getConfigurationManager();
                    if (players != null) {
                        addAll(names, limit, players.func_152606_n());
                        addAll(names, limit, players.func_152598_l());
                    }
                } catch (RuntimeException unreadable) {
                    warn("the operators and the whitelist", unreadable);
                }
            }
            if (this.world == null || names.size() >= limit) {
                return;
            }
            try {
                for (CharacterRoster roster
                        : CharacterStorage.get(this.world).getRosters()) {
                    if (names.size() >= limit) {
                        return;
                    }
                    UUID owner = roster == null ? null : roster.getOwnerId();
                    if (owner != null && !owner.equals(this.ownerId)) {
                        add(names, limit, KnownAccounts.nameOf(owner, roster));
                    }
                }
            } catch (RuntimeException unreadable) {
                warn("this world's rosters", unreadable);
            }
        }

        private static void addAll(List<String> names, int limit,
                                   String[] found) {
            if (found == null) {
                return;
            }
            for (String name : found) {
                if (names.size() >= limit) {
                    return;
                }
                add(names, limit, name);
            }
        }

        private static void add(List<String> names, int limit, String name) {
            if (name != null && name.length() > 0 && names.size() < limit) {
                names.add(name);
            }
        }

        private static void warn(String what, RuntimeException failure) {
            FMLLog.warning("[%s] Could not read %s for the names a new character "
                            + "may not take; that list is left out: %s",
                    LostTalesMetaData.MOD_ID, what, failure.toString());
        }
    }
}
