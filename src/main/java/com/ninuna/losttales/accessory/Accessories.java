package com.ninuna.losttales.accessory;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.AccessoryCatalogPacket;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.FMLLog;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;

/**
 * Reads the accessory files (ACC-01, MOD-17) and puts them in place. The
 * bundled ones are read once every item is registered, for both sides; the
 * server reads them again with its own from {@code config/losttales/accessories/}
 * as it starts, each in place of a bundled one of its id, and goes back to
 * the bundled ones as it stops. A joining player's game is sent the
 * server's list before anything that names a ring, and goes back to the
 * bundled list as it leaves.
 */
public final class Accessories {
    /** Every mod's items, as the running game has them. */
    public static final AccessoryFiles.Game GAME = new AccessoryFiles.Game() {
        @Override
        public boolean hasItem(String itemId) {
            return Item.itemRegistry.getObject(itemId) instanceof Item;
        }
    };

    private static AccessoryCatalogue bundled = AccessoryCatalogue.EMPTY;

    private Accessories() {}

    /** Reads the bundled files, for the server and the client alike; once every item exists. */
    public static synchronized void loadBundled() {
        List<String> problems = new ArrayList<String>();
        DataFiles.Kind<AccessoryDefinition> kind = AccessoryFiles.kind(GAME);
        DataFiles.Result<AccessoryDefinition> read = DataFiles.readBundled(
                ProgressionFiles.CLASSPATH, AccessoryFiles.INDEX, AccessoryFiles.LIST_KEY, kind);
        note(problems, "Bundled accessory", read.problems);
        List<String> unfit = new ArrayList<String>();
        bundled = AccessoryCatalogue.of(read.values, unfit);
        note(problems, "Bundled accessory", unfit);
        AccessoryCompatibilityRegistry.server().set(bundled);
        AccessoryCompatibilityRegistry.client().set(bundled);
    }

    /**
     * Reads every accessory file again, the bundled ones with the server's
     * own, and puts them in place for the server; answers why each file
     * left out was left out.
     */
    public static synchronized List<String> load() {
        return load(ProgressionFiles.folder(AccessoryFiles.FOLDER));
    }

    static synchronized List<String> load(File folder) {
        List<String> problems = new ArrayList<String>();
        AccessoryCompatibilityRegistry.server().set(read(ProgressionFiles.CLASSPATH, folder,
                GAME, problems));
        return problems;
    }

    /**
     * The catalogue of the bundled files and {@code folder}: a server's own
     * in place of a bundled one of its id and its new ones after, each
     * checked against {@code game}. Every problem is logged and added to
     * {@code problems}.
     */
    public static AccessoryCatalogue read(DataFiles.Source bundledSource, File folder,
                                          AccessoryFiles.Game game, List<String> problems) {
        DataFiles.Kind<AccessoryDefinition> kind = AccessoryFiles.kind(game);
        DataFiles.Result<AccessoryDefinition> fromBundle = DataFiles.readBundled(bundledSource,
                AccessoryFiles.INDEX, AccessoryFiles.LIST_KEY, kind);
        DataFiles.Result<AccessoryDefinition> own = DataFiles.readFolder(folder, kind);
        note(problems, "Bundled accessory", fromBundle.problems);
        note(problems, "Server accessory", own.problems);
        List<String> unfit = new ArrayList<String>();
        AccessoryCatalogue catalogue = AccessoryCatalogue.of(
                DataFiles.merge(fromBundle.values, own.values, kind), unfit);
        note(problems, "Server accessory", unfit);
        return catalogue;
    }

    /**
     * Reads every accessory file again and sends the list to every player
     * online; answers why each file left out was left out. A worn ring the
     * new list no longer takes goes back to its wearer's inventory as the
     * slot is next looked at.
     */
    public static List<String> reload() {
        List<String> problems = load();
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object player : server.getConfigurationManager().playerEntityList) {
                if (player instanceof EntityPlayerMP) {
                    sendTo((EntityPlayerMP) player);
                }
            }
        }
        return problems;
    }

    /** The bundled accessories again, for the server, as it stops. */
    public static synchronized void clearServer() {
        AccessoryCompatibilityRegistry.server().set(bundled);
    }

    /** The bundled accessories again, for the client, as it leaves a server. */
    public static synchronized void resetClient() {
        AccessoryCompatibilityRegistry.client().set(bundled);
    }

    /**
     * The list a server sent, for the client: each accessory whose item
     * this game has, as the server's own catalogue holds them.
     */
    public static synchronized void useOnClient(List<AccessoryDefinition> definitions) {
        List<AccessoryDefinition> known = new ArrayList<AccessoryDefinition>();
        for (AccessoryDefinition definition : definitions) {
            if (GAME.hasItem(definition.getItemId())) {
                known.add(definition);
            } else {
                LostTalesLog.warning("The server's accessory %s is the item %s, which this game"
                        + " does not have; its slot here will not take it",
                        definition.getId(), definition.getItemId());
            }
        }
        AccessoryCompatibilityRegistry.client().set(AccessoryCatalogue.of(known, null));
    }

    /** Sends a joining player the server's accessories. */
    public static void sendTo(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        try {
            LostTalesNetworkHandler.CHANNEL.sendTo(new AccessoryCatalogPacket(
                    AccessoryCompatibilityRegistry.server().getCatalogue().getDefinitions()),
                    player);
        } catch (RuntimeException exception) {
            // The player's game keeps the bundled list until the next join.
            FMLLog.warning("[%s] Could not send the accessories to %s: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(), exception.toString());
        }
    }

    private static void note(List<String> problems, String what, List<String> found) {
        for (String problem : found) {
            problems.add(problem);
            LostTalesLog.warning("%s left out: %s", what, problem);
        }
    }
}
