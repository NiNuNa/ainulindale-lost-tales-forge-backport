package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import com.ninuna.losttales.world.room.CharacterRoomWorldType;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.ForgeHooks;

/**
 * The server's welcome: the lines of {@code chat.welcomeLines}, sent as
 * Server lines in OOC Chat the first time a player joins this world, so
 * they wait there unread (Nils, 2026-09-28, C9 a). They go out a moment
 * after the join, behind the history the player is caught up with. A
 * player is welcomed once: their saved data remembers it.
 */
public final class ChatWelcome {
    /** The translation key a welcome line is sent under; the chat files it in OOC. */
    public static final String KEY = "chat.losttales.welcome";
    /** The most lines a welcome holds. */
    public static final int MAX_LINES = 8;
    /** Where the player's saved data remembers the welcome. */
    static final String WELCOMED_TAG = "LostTalesWelcomed";
    /** How long after the join the welcome goes out: two seconds. */
    static final int DELAY_TICKS = 40;
    /** More players waiting than this is more than a server joins at once; the oldest go. */
    private static final int MAX_WAITING = 256;

    private static final Map<UUID, Integer> WAITING = new LinkedHashMap<UUID, Integer>();

    /** A new player is noted, to be welcomed in a moment. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)
                || lines(LostTalesConfig.chatWelcomeLines).isEmpty()
                || CharacterRoomWorldType.isRoomServer(MinecraftServer.getServer())
                || persisted(event.player).getBoolean(WELCOMED_TAG)) {
            return;
        }
        noteArrival(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (UUID due : due()) {
            EntityPlayerMP player = LostTalesServerPlayers.findOnline(due);
            if (player != null) {
                welcome(player);
            }
        }
    }

    /** Sends the welcome and remembers it, unless the player has had it. */
    private static void welcome(EntityPlayerMP player) {
        NBTTagCompound saved = persisted(player);
        if (saved.getBoolean(WELCOMED_TAG)) {
            return;
        }
        List<String> lines = lines(LostTalesConfig.chatWelcomeLines);
        if (lines.isEmpty()) {
            return;
        }
        saved.setBoolean(WELCOMED_TAG, true);
        for (String line : lines) {
            player.addChatMessage(new ChatComponentTranslation(KEY,
                    ForgeHooks.newChatWithLinks(line)));
        }
    }

    /**
     * The welcome's lines as they are sent: without formatting codes or
     * control characters, trimmed, blank ones left out, each cut to a
     * chat line's length, and at most {@link #MAX_LINES} of them.
     */
    static List<String> lines(String[] configured) {
        List<String> lines = new ArrayList<String>();
        if (configured == null) {
            return lines;
        }
        for (String entry : configured) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            String line = ChatFormattingCodes.stripSectionCodes(entry == null ? "" : entry)
                    .replaceAll("\\p{Cntrl}", " ").trim();
            if (line.length() > ChatMessageValidator.MAX_CHARACTERS) {
                line = line.substring(0, ChatMessageValidator.MAX_CHARACTERS).trim();
            }
            if (line.length() > 0) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The part of a player's saved data Forge keeps across deaths. */
    private static NBTTagCompound persisted(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    static synchronized void noteArrival(UUID player) {
        WAITING.remove(player);
        WAITING.put(player, Integer.valueOf(DELAY_TICKS));
        Iterator<UUID> oldest = WAITING.keySet().iterator();
        while (WAITING.size() > MAX_WAITING && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Counts every waiting player down a tick; those whose moment has come. */
    static synchronized List<UUID> due() {
        List<UUID> due = new ArrayList<UUID>();
        Iterator<Map.Entry<UUID, Integer>> each = WAITING.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<UUID, Integer> waiting = each.next();
            int left = waiting.getValue().intValue() - 1;
            if (left <= 0) {
                due.add(waiting.getKey());
                each.remove();
            } else {
                waiting.setValue(Integer.valueOf(left));
            }
        }
        return due;
    }

    public static synchronized void clear() {
        WAITING.clear();
    }
}
