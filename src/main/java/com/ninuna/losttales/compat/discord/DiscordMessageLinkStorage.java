package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.chat.server.ChatHistoryStorage;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import cpw.mods.fml.common.FMLLog;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * Resolves the Discord bridge's kept message links from dimension-zero
 * MapStorage, and moves them between the save and the bridge's live map
 * at the two ends of a server's run.
 *
 * <p>They come back only with the chat history they index into. A link
 * names a game message by id, and ids go on from the kept history, so
 * an id from the save is only known to name the same message when that
 * history was read back from the same save; and then only the links to
 * messages it still keeps come back. Everything here fails open: links
 * that cannot be read leave the bridge with the links of this run
 * alone, and the save as it was.</p>
 */
final class DiscordMessageLinkStorage {

    /** The save the live links are written with, or null while they have none. */
    private static DiscordMessageLinkWorldData attached;

    /** The kept chat history, asked whether a message is still in it. */
    static final DiscordMessageLinks.MessageIndex HISTORY =
            new DiscordMessageLinks.MessageIndex() {
                @Override
                public boolean holds(long messageId) {
                    return ChatHistory.channelOf(messageId) != null;
                }
            };

    private DiscordMessageLinkStorage() {}

    static DiscordMessageLinkWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Discord message link storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        DiscordMessageLinkWorldData data = (DiscordMessageLinkWorldData) storage.loadData(
                DiscordMessageLinkWorldData.class, DiscordMessageLinkWorldData.DATA_NAME);
        if (data == null) {
            // Not marked: a world whose bridge links nothing is never
            // written a file for it.
            data = new DiscordMessageLinkWorldData(DiscordMessageLinkWorldData.DATA_NAME);
            storage.setData(DiscordMessageLinkWorldData.DATA_NAME, data);
        }
        return data;
    }

    /**
     * Hands the save's links to {@code live} as the server starts, and
     * attaches the save so every link made from here on is written with
     * the world. Called after the chat history is restored and before
     * the bridge starts. Without the history read back from the save —
     * off by the config, or a history save this build cannot read — the
     * save's links are neither read nor written this run: they wait for
     * a run that has their history back. The bridge being off changes
     * nothing here: its saved links are kept, and only those whose
     * message the restored history no longer holds are dropped, which
     * marks the save to be written again without them.
     */
    static synchronized void restore(MinecraftServer server,
                                     DiscordMessageLinks live) {
        attached = null;
        if (server == null || live == null
                || server.worldServerForDimension(0) == null) {
            return;
        }
        if (!ChatHistoryStorage.isRestoredFromSave()) {
            if (LostTalesConfig.discordEnabled) {
                FMLLog.info("[%s] The chat history was not read from the save, so "
                        + "the Discord bridge's message links are not either; "
                        + "replies, reactions and edits cross only for messages "
                        + "from this run", LostTalesMetaData.MOD_ID);
            }
            return;
        }
        try {
            DiscordMessageLinkWorldData data = get(server.worldServerForDimension(0));
            if (data.isReadOnlyForNewerVersion()) {
                FMLLog.severe("[%s] Discord message links in this save use unsupported "
                        + "version %d; they are left as they are and the bridge "
                        + "keeps its links in memory only for this run",
                        LostTalesMetaData.MOD_ID,
                        Integer.valueOf(data.getUnsupportedDataVersion()));
                return;
            }
            List<DiscordMessageLinks.SavedLink> saved = data.restoredLinks();
            int kept = live.restore(saved, HISTORY);
            data.attach(live, kept != saved.size());
            attached = data;
            if (!saved.isEmpty() || data.getQuarantinedEntryCount() > 0) {
                FMLLog.info("[%s] Restored %d of %d Discord message links from the "
                        + "save (%d quarantined)", LostTalesMetaData.MOD_ID,
                        Integer.valueOf(kept), Integer.valueOf(saved.size()),
                        Integer.valueOf(data.getQuarantinedEntryCount()));
            }
        } catch (RuntimeException failure) {
            FMLLog.severe("[%s] Discord message links could not be read from the "
                    + "save; the bridge keeps its links in memory only for this "
                    + "run: %s", LostTalesMetaData.MOD_ID, failure);
        }
    }

    /**
     * Leaves the live links' last state with the save as the server
     * stops, once the bridge has stopped adding to them: the worlds are
     * saved after this, and what they write is that snapshot.
     */
    static synchronized void release() {
        DiscordMessageLinkWorldData data = attached;
        attached = null;
        if (data != null) {
            data.detach();
        }
    }
}
