package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRCapes;
import net.minecraft.util.ResourceLocation;

/**
 * Client-only bridge from a cape file's picture (CHAR-39) to its texture:
 * one of LOTR's public capes by its name, or one of the mod's own.
 */
final class LostTalesLotrCapeTextureResolver {

    private static boolean disabled;
    private static boolean failureLogged;

    private LostTalesLotrCapeTextureResolver() {}

    static ResourceLocation resolve(String cosmeticCapeId) {
        CharacterCapeDefinition cape = CharacterCapeCatalog.get(cosmeticCapeId);
        if (cape == null || disabled) {
            return null;
        }
        String picture = cape.getPicture();
        if (picture.startsWith(CharacterCapeCatalog.OWN_PICTURE)) {
            return new ResourceLocation(picture);
        }
        if (!picture.startsWith(CharacterCapeCatalog.LOTR_PICTURE)) {
            return null;
        }
        try {
            return lotrCape(picture.substring(CharacterCapeCatalog.LOTR_PICTURE.length()));
        } catch (Throwable throwable) {
            disabled = true;
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning(
                        "[" + LostTalesMetaData.MOD_ID + "] LOTR cosmetic cape textures are unavailable; "
                                + "falling back to normal Minecraft capes: %s",
                        throwable.toString());
            }
            return null;
        }
    }

    /** LOTR's cape of that name; null for a name LOTR gives none. */
    private static ResourceLocation lotrCape(String name) {
        if ("GONDOR".equals(name)) return LOTRCapes.GONDOR;
        if ("TOWER_GUARD".equals(name)) return LOTRCapes.TOWER_GUARD;
        if ("RANGER".equals(name)) return LOTRCapes.RANGER;
        if ("RANGER_ITHILIEN".equals(name)) return LOTRCapes.RANGER_ITHILIEN;
        if ("LOSSARNACH".equals(name)) return LOTRCapes.LOSSARNACH;
        if ("PELARGIR".equals(name)) return LOTRCapes.PELARGIR;
        if ("BLACKROOT".equals(name)) return LOTRCapes.BLACKROOT;
        if ("PINNATH_GELIN".equals(name)) return LOTRCapes.PINNATH_GELIN;
        if ("LAMEDON".equals(name)) return LOTRCapes.LAMEDON;
        if ("ROHAN".equals(name)) return LOTRCapes.ROHAN;
        if ("DALE".equals(name)) return LOTRCapes.DALE;
        if ("DUNLENDING_BERSERKER".equals(name)) return LOTRCapes.DUNLENDING_BERSERKER;
        if ("GALADHRIM".equals(name)) return LOTRCapes.GALADHRIM;
        if ("GALADHRIM_TRADER".equals(name)) return LOTRCapes.GALADHRIM_TRADER;
        if ("WOOD_ELF".equals(name)) return LOTRCapes.WOOD_ELF;
        if ("HIGH_ELF".equals(name)) return LOTRCapes.HIGH_ELF;
        if ("RIVENDELL".equals(name)) return LOTRCapes.RIVENDELL;
        if ("RIVENDELL_TRADER".equals(name)) return LOTRCapes.RIVENDELL_TRADER;
        if ("NEAR_HARAD".equals(name)) return LOTRCapes.NEAR_HARAD;
        if ("SOUTHRON_CHAMPION".equals(name)) return LOTRCapes.SOUTHRON_CHAMPION;
        if ("GULF_HARAD".equals(name)) return LOTRCapes.GULF_HARAD;
        if ("TAURETHRIM".equals(name)) return LOTRCapes.TAURETHRIM;
        if ("GALADHRIM_SMITH".equals(name)) return LOTRCapes.GALADHRIM_SMITH;
        if ("DORWINION_CAPTAIN".equals(name)) return LOTRCapes.DORWINION_CAPTAIN;
        if ("DORWINION_ELF_CAPTAIN".equals(name)) return LOTRCapes.DORWINION_ELF_CAPTAIN;
        if ("GANDALF".equals(name)) return LOTRCapes.GANDALF;
        if ("GANDALF_SANTA".equals(name)) return LOTRCapes.GANDALF_SANTA;
        return null;
    }
}
