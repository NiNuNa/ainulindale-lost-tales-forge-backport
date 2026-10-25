package com.ninuna.losttales.armour;

import com.ninuna.losttales.LostTalesMetaData;
import net.minecraft.util.StatCollector;

/**
 * A named set's and a relic piece's words in the reader's language
 * (CONTENT-07, CONTENT-11): a bundled set's lang lines under
 * {@code armour.set.<domain>.<path>}, a server's own file's words where
 * it wrote some, else the set's id.
 */
public final class ArmourWords {
    private ArmourWords() {}

    /** {@code armour.set.losttales.gondorian}, the start of a set's lang keys. */
    public static String key(String setId) {
        String id = setId == null ? "" : setId;
        int colon = id.indexOf(':');
        String domain = colon < 0 ? LostTalesMetaData.MOD_ID : id.substring(0, colon);
        return "armour.set." + domain + "." + id.substring(colon + 1);
    }

    public static String nameKey(String setId) {
        return key(setId) + ".name";
    }

    public static String pieceNameKey(String setId, ArmourSlot slot) {
        return key(setId) + "." + slot.word() + ".name";
    }

    public static String pieceLoreKey(String setId, ArmourSlot slot) {
        return key(setId) + "." + slot.word() + ".lore";
    }

    public static String setName(NamedSet set) {
        return set.getName().length() > 0 ? set.getName()
                : translated(nameKey(set.getId()), set.getId());
    }

    /** A relic piece's own name; empty where neither its file nor a lang line names it. */
    public static String pieceName(NamedSet set, ArmourSlot slot) {
        return set.pieceName(slot).length() > 0 ? set.pieceName(slot)
                : translated(pieceNameKey(set.getId(), slot), "");
    }

    /** A relic piece's lore line; empty where neither its file nor a lang line gives one. */
    public static String pieceLore(NamedSet set, ArmourSlot slot) {
        return set.pieceLore(slot).length() > 0 ? set.pieceLore(slot)
                : translated(pieceLoreKey(set.getId(), slot), "");
    }

    private static String translated(String key, String fallback) {
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : fallback;
    }
}
