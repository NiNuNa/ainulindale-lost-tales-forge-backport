package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * The status a player last stated for the identity they play, kept with
 * their saved data: what the server knows of them at their next join,
 * before their client has said anything, so an Invisible player's join
 * goes unsaid. No tag reads as Online, and so does one this build does
 * not know, so a bad value only ever says a line.
 */
final class ChatPresenceRecord {
    static final String TAG = "LostTalesPresence";

    private ChatPresenceRecord() {}

    static ChatPresence read(NBTTagCompound persisted) {
        if (persisted == null || !persisted.hasKey(TAG, Constants.NBT.TAG_STRING)) {
            return ChatPresence.ONLINE;
        }
        ChatPresence presence = ChatPresence.fromId(persisted.getString(TAG));
        return presence == null || !presence.isChoosable() ? ChatPresence.ONLINE : presence;
    }

    static void write(NBTTagCompound persisted, ChatPresence presence) {
        if (presence == null || presence == ChatPresence.ONLINE || !presence.isChoosable()) {
            persisted.removeTag(TAG);
        } else {
            persisted.setString(TAG, presence.getId());
        }
    }

    static ChatPresence read(EntityPlayer player) {
        return read(LostTalesServerPlayers.persistedData(player));
    }

    /** Keeps {@code presence} with the player's data when it is not what is kept. */
    static void remember(EntityPlayer player, ChatPresence presence) {
        NBTTagCompound persisted = LostTalesServerPlayers.persistedData(player);
        if (read(persisted) != presence) {
            write(persisted, presence);
        }
    }
}
