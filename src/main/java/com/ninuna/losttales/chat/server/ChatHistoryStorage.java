package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import cpw.mods.fml.common.FMLLog;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * Resolves the kept chat history from dimension-zero MapStorage, and
 * moves it between the save and the live {@link ChatHistory} at the two
 * ends of a server's run. Everything here fails open: a save that cannot
 * be read leaves the history in memory alone, as it was before it had a
 * save at all.
 */
public final class ChatHistoryStorage {

    private ChatHistoryStorage() {}

    public static ChatHistoryWorldData get(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (world.isRemote) {
            throw new IllegalArgumentException(
                    "Chat history storage is server-side only");
        }
        WorldServer overworld = LostTalesDimensionHelper.overworld(world);
        MapStorage storage = overworld.mapStorage;
        ChatHistoryWorldData data = (ChatHistoryWorldData) storage.loadData(
                ChatHistoryWorldData.class, ChatHistoryWorldData.DATA_NAME);
        if (data == null) {
            data = new ChatHistoryWorldData(ChatHistoryWorldData.DATA_NAME);
            storage.setData(ChatHistoryWorldData.DATA_NAME, data);
            data.markDirty();
        }
        return data;
    }

    /**
     * Hands the save's kept lines to the live history and its kept
     * console events to the console stream as the server starts, and
     * attaches the save so everything said from here on is written with
     * the world. Called after the channels and the config are in force,
     * since every kept line names its channel by id, and after the live
     * stores were cleared. Off by the server's config, or with a save
     * this build cannot read, both stay in memory.
     */
    public static void restore(MinecraftServer server) {
        if (server == null || server.worldServerForDimension(0) == null) {
            return;
        }
        if (!LostTalesConfig.chatHistoryPersisted) {
            ChatHistory.attach(null);
            return;
        }
        try {
            ChatHistoryWorldData data = get(server.worldServerForDimension(0));
            if (data.isReadOnlyForNewerVersion()) {
                FMLLog.severe("[%s] Chat history in this save uses unsupported version %d; the history is kept in memory only for this run",
                        LostTalesMetaData.MOD_ID,
                        Integer.valueOf(data.getUnsupportedDataVersion()));
                ChatHistory.attach(null);
                return;
            }
            List<ChatHistory.Entry> entries = data.takeRestored();
            int kept = ChatHistory.restore(entries);
            List<ChatConsoleEvent> events = data.takeRestoredEvents();
            int keptEvents = ChatConsoleStream.restore(events);
            ChatHistory.attach(data);
            FMLLog.info("[%s] Restored %d of %d kept chat lines and %d of %d console events from the save (%d quarantined)",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(kept),
                    Integer.valueOf(entries.size()),
                    Integer.valueOf(keptEvents), Integer.valueOf(events.size()),
                    Integer.valueOf(data.getQuarantinedEntryCount()));
        } catch (RuntimeException failure) {
            FMLLog.severe("[%s] Chat history could not be read from the save; it is kept in memory only for this run: %s",
                    LostTalesMetaData.MOD_ID, failure);
            ChatHistory.attach(null);
        }
    }

    /**
     * Leaves the live history's and the console's last state with the
     * save as the server stops, before the live stores are cleared: the
     * world is saved after this, and what it writes is these snapshots.
     */
    public static void release() {
        ChatHistoryWorldData data = ChatHistory.attached();
        if (data != null) {
            data.hold(ChatHistory.snapshot(), ChatConsoleStream.snapshot());
        }
        ChatHistory.attach(null);
    }

    /**
     * Whether the live history was read back from the save this run and
     * is written with it; false while it is kept in memory only — off by
     * the config, a save from a newer build, or one that could not be
     * read. Message ids go on from the kept history, so this is what
     * says whether an id kept elsewhere in the save, such as in the
     * Discord bridge's links, still names the same message.
     */
    public static boolean isRestoredFromSave() {
        return ChatHistory.attached() != null;
    }
}
