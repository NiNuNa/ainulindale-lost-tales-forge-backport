package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.deletion.CharacterDeletionTombstone;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.SavedLifeItems;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.storage.NbtTags;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a player, once, what their saved lives held that the game no
 * longer takes and the server set aside (CHAR-46): a plain line per
 * character, naming each stack the game still knows by its count and
 * name, and counting those it does not. Only the current generation is
 * named; the one before it holds a copy of the same things.
 */
public final class SavedLifeNotices {

    private SavedLifeNotices() {}

    /** Says what was set aside and not yet told, at a join or once a figure's life comes in. */
    public static void tell(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            return;
        }
        List<NBTTagList> untold;
        try {
            CharacterPlayerStateWorldData data =
                    CharacterPlayerStateStorage.get(player.worldObj, player.getUniqueID());
            untold = data.takeUntoldSetAside();
        } catch (RuntimeException unreadable) {
            FMLLog.warning("[%s] Could not read what was set aside for %s: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(), unreadable.toString());
            return;
        }
        if (untold.isEmpty()) {
            return;
        }
        Map<UUID, Kept> byCharacter = new LinkedHashMap<UUID, Kept>();
        for (NBTTagList items : untold) {
            collect(items, byCharacter);
        }
        for (Map.Entry<UUID, Kept> each : byCharacter.entrySet()) {
            player.addChatMessage(line(player, each.getKey(), each.getValue()));
        }
    }

    /** Sorts the current generation's stacks by the character that held them. */
    static void collect(NBTTagList items, Map<UUID, Kept> byCharacter) {
        Map<UUID, Long> newest = new LinkedHashMap<UUID, Long>();
        for (int index = 0; index < items.tagCount(); index++) {
            NBTTagCompound item = items.getCompoundTagAt(index);
            UUID character = NbtTags.readUuid(item, SavedLifeItems.TAG_CHARACTER);
            long generation = item.getLong(SavedLifeItems.TAG_GENERATION);
            Long seen = newest.get(character);
            if (character != null && (seen == null || generation > seen.longValue())) {
                newest.put(character, Long.valueOf(generation));
            }
        }
        for (int index = 0; index < items.tagCount(); index++) {
            NBTTagCompound item = items.getCompoundTagAt(index);
            UUID character = NbtTags.readUuid(item, SavedLifeItems.TAG_CHARACTER);
            if (character == null || item.getLong(SavedLifeItems.TAG_GENERATION)
                    != newest.get(character).longValue()) {
                continue;
            }
            Kept kept = byCharacter.get(character);
            if (kept == null) {
                kept = new Kept();
                byCharacter.put(character, kept);
            }
            if (SavedLifeItems.WHY_UNKNOWN.equals(item.getString(SavedLifeItems.TAG_WHY))) {
                kept.unknown++;
            } else {
                kept.known.add((NBTTagCompound)item.getCompoundTag(SavedLifeItems.TAG_ITEM).copy());
            }
        }
    }

    private static IChatComponent line(EntityPlayerMP player, UUID characterId, Kept kept) {
        IChatComponent list = new ChatComponentText("");
        boolean first = true;
        for (NBTTagCompound itemTag : kept.known) {
            ItemStack stack;
            try {
                stack = ItemStack.loadItemStackFromNBT(itemTag);
            } catch (RuntimeException unreadable) {
                stack = null;
            }
            IChatComponent words = stack == null
                    ? new ChatComponentTranslation("chat.losttales.character.set_aside.unknown.one")
                    : new ChatComponentTranslation("chat.losttales.character.set_aside.stack",
                            Integer.valueOf(stack.stackSize),
                            new ChatComponentTranslation(stack.getUnlocalizedName() + ".name"));
            first = append(list, words, first);
        }
        if (kept.unknown == 1) {
            first = append(list, new ChatComponentTranslation(
                    "chat.losttales.character.set_aside.unknown.one"), first);
        } else if (kept.unknown > 1) {
            first = append(list, new ChatComponentTranslation(
                    "chat.losttales.character.set_aside.unknown.many",
                    Integer.valueOf(kept.unknown)), first);
        }
        return new ChatComponentTranslation("chat.losttales.character.set_aside",
                nameOf(player, characterId), list);
    }

    private static boolean append(IChatComponent list, IChatComponent words, boolean first) {
        if (!first) {
            list.appendSibling(new ChatComponentText(", "));
        }
        list.appendSibling(words);
        return false;
    }

    /** The character's name: from the roster, else its deletion; the account's own life is the ghost's. */
    private static Object nameOf(EntityPlayerMP player, UUID characterId) {
        if (characterId.equals(player.getUniqueID())) {
            return new ChatComponentTranslation("chat.losttales.character.set_aside.ghost");
        }
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            RoleplayCharacter character = roster == null ? null : roster.getCharacter(characterId);
            if (character != null) {
                return character.getName();
            }
            CharacterDeletionTombstone deleted = CharacterDeletionService.getInstance()
                    .getTombstone(player.worldObj, characterId);
            if (deleted != null && deleted.getCharacterName() != null) {
                return deleted.getCharacterName();
            }
        } catch (RuntimeException unreadable) {
            // Unnamed below.
        }
        return new ChatComponentTranslation("chat.losttales.character.set_aside.someone");
    }

    /** What one character's life had set aside: the stacks the game knows, and how many it does not. */
    static final class Kept {
        final List<NBTTagCompound> known = new ArrayList<NBTTagCompound>();
        int unknown;
    }
}
