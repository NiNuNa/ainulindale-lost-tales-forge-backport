package com.ninuna.losttales.armour;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.ArmourCatalogPacket;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.common.FMLLog;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ISpecialArmor;

/**
 * Reads the armour files (CONTENT-06, CONTENT-07, MOD-17) and puts them in
 * place, and reads what a player wears through them. The bundled files
 * are read once as the mod loads, for both sides; the server reads them
 * again with its own as it starts, each in place of a bundled one of its
 * id, and goes back to the bundled ones as it stops. A joining player's
 * game is sent the server's weights, sets and weight-set numbers, and
 * goes back to the bundled ones as it leaves.
 */
public final class Armour {
    private static final String SERVER_THREAD = "Server thread";
    private static ArmourCatalogue bundled = ArmourCatalogue.EMPTY;
    private static volatile ArmourCatalogue server = ArmourCatalogue.EMPTY;
    private static volatile ArmourCatalogue client = ArmourCatalogue.EMPTY;
    private static volatile ArmourSettings clientSettings = ArmourSettings.SHIPPED;

    private Armour() {}

    /** Reads the bundled files, for the server and the client alike. */
    public static synchronized void loadBundled() {
        bundled = read(ProgressionFiles.CLASSPATH, null, null, new ArrayList<String>());
        server = bundled;
        client = bundled;
    }

    /**
     * Reads every armour file again, the bundled ones with the server's
     * own, and puts them in place for the server; answers why each file
     * left out was left out.
     */
    public static synchronized List<String> load() {
        List<String> problems = new ArrayList<String>();
        server = read(ProgressionFiles.CLASSPATH,
                ProgressionFiles.folder(ArmourFiles.WEIGHTS_FOLDER),
                ProgressionFiles.folder(ArmourFiles.SETS_FOLDER), problems);
        return problems;
    }

    /**
     * The catalogue of the bundled files and the server's own folders (none
     * where null): a server's file in place of a bundled one of its id and
     * its new ones after. Every problem is logged and added to {@code problems}.
     */
    public static ArmourCatalogue read(DataFiles.Source bundledSource, File weightsFolder,
                                       File setsFolder, List<String> problems) {
        DataFiles.Result<WeightFile> bundledWeights = DataFiles.readBundled(bundledSource,
                ArmourFiles.INDEX, ArmourFiles.WEIGHTS_KEY, ArmourFiles.WEIGHTS);
        DataFiles.Result<NamedSet> bundledSets = DataFiles.readBundled(bundledSource,
                ArmourFiles.INDEX, ArmourFiles.SETS_KEY, ArmourFiles.SETS);
        note(problems, "Bundled armour weight", bundledWeights.problems);
        note(problems, "Bundled armour set", bundledSets.problems);
        List<WeightFile> weights = bundledWeights.values;
        List<NamedSet> sets = bundledSets.values;
        if (weightsFolder != null) {
            DataFiles.Result<WeightFile> own = DataFiles.readFolder(weightsFolder,
                    ArmourFiles.WEIGHTS);
            note(problems, "Server armour weight", own.problems);
            weights = DataFiles.merge(weights, own.values, ArmourFiles.WEIGHTS);
        }
        if (setsFolder != null) {
            DataFiles.Result<NamedSet> own = DataFiles.readFolder(setsFolder, ArmourFiles.SETS);
            note(problems, "Server armour set", own.problems);
            sets = DataFiles.merge(sets, own.values, ArmourFiles.SETS);
        }
        return ArmourCatalogue.of(weights, sets);
    }

    /** The bundled files again, for the server, as it stops. */
    public static synchronized void clearServer() {
        server = bundled;
    }

    /** The bundled files and shipped numbers again, for the client, as it leaves a server. */
    public static synchronized void resetClient() {
        client = bundled;
        clientSettings = ArmourSettings.SHIPPED;
    }

    /** What a server sent, for the client. */
    public static synchronized void useOnClient(ArmourCatalogue catalogue,
                                                ArmourSettings settings) {
        client = catalogue == null ? bundled : catalogue;
        clientSettings = settings == null ? ArmourSettings.SHIPPED : settings;
    }

    /** The catalogue the side a player stands on plays with. */
    public static ArmourCatalogue catalogue(EntityPlayer player) {
        return player != null && player.worldObj != null && player.worldObj.isRemote
                ? client : server;
    }

    /**
     * The catalogue of the side the calling thread runs, as FML tells the
     * sides apart by the server thread's name: the server's on it, the
     * client's on any other, for words that both sides write.
     */
    public static ArmourCatalogue sideCatalogue() {
        return SERVER_THREAD.equals(Thread.currentThread().getName()) ? server : client;
    }

    public static ArmourCatalogue serverCatalogue() {
        return server;
    }

    public static ArmourCatalogue clientCatalogue() {
        return client;
    }

    /** The weight-set and slowdown numbers the side a player stands on plays with. */
    public static ArmourSettings settings(EntityPlayer player) {
        return player != null && player.worldObj != null && player.worldObj.isRemote
                ? clientSettings : serverSettings();
    }

    /** The server's numbers, from its settings. */
    public static ArmourSettings serverSettings() {
        return ArmourSettings.ofPercent(LostTalesConfig.lightSetSpeedPercent,
                LostTalesConfig.heavySetKnockbackPercent,
                LostTalesConfig.heavyHelmetBootsSlowPercent,
                LostTalesConfig.heavyChestLegsSlowPercent);
    }

    /** What a player wears now, as the side they stand on reads it. */
    public static WornArmour worn(EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return WornArmour.NONE;
        }
        Map<ArmourSlot, WornArmour.Piece> pieces =
                new EnumMap<ArmourSlot, WornArmour.Piece>(ArmourSlot.class);
        for (ArmourSlot slot : ArmourSlot.values()) {
            ItemStack stack = player.inventory.armorInventory[slot.getInventoryIndex()];
            WornArmour.Piece piece = piece(player, stack, slot.getInventoryIndex());
            if (piece != null) {
                pieces.put(slot, piece);
            }
        }
        return WornArmour.of(pieces, catalogue(player));
    }

    /**
     * A stack as a worn piece; null for none. Another mod's armour that
     * cannot tell its material or its points reads as having none.
     */
    static WornArmour.Piece piece(EntityPlayer player, ItemStack stack, int inventoryIndex) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        Item item = stack.getItem();
        return new WornArmour.Piece(itemId(item), material(item),
                Relics.relicOf(stack), points(player, stack, inventoryIndex));
    }

    /** An armour item's material by the name the game registers it under; empty for none. */
    public static String material(Item item) {
        if (!(item instanceof ItemArmor)) {
            return "";
        }
        try {
            ItemArmor.ArmorMaterial material = ((ItemArmor) item).getArmorMaterial();
            return material == null ? "" : material.name();
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    /** The armour points a worn piece gives, as Forge counts them for the armour bar. */
    private static int points(EntityPlayer player, ItemStack stack, int inventoryIndex) {
        try {
            if (stack.getItem() instanceof ISpecialArmor) {
                return ((ISpecialArmor) stack.getItem()).getArmorDisplay(player, stack,
                        inventoryIndex);
            }
            return stack.getItem() instanceof ItemArmor
                    ? ((ItemArmor) stack.getItem()).damageReduceAmount : 0;
        } catch (RuntimeException unreadable) {
            return 0;
        }
    }

    /** An item's registry name, {@code lotr:item.helmetGondor}; empty for one not registered. */
    public static String itemId(Item item) {
        Object name = item == null ? null : Item.itemRegistry.getNameForObject(item);
        return name == null ? "" : name.toString();
    }

    /** Sends a joining player the server's armour files and numbers. */
    public static void sendTo(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        try {
            LostTalesNetworkHandler.CHANNEL.sendTo(new ArmourCatalogPacket(server,
                    serverSettings()), player);
        } catch (RuntimeException exception) {
            // The player's game keeps the bundled files until the next join.
            FMLLog.warning("[%s] Could not send the armour files to %s: %s",
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
