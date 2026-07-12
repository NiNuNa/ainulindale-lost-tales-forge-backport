package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import cpw.mods.fml.common.FMLLog;
import lotr.common.LOTRCapes;
import net.minecraft.util.ResourceLocation;

/** Client-only bridge from stable Lost Tales cape IDs to LOTR's public textures. */
final class LostTalesLotrCapeTextureResolver {

    private static boolean disabled;
    private static boolean failureLogged;

    private LostTalesLotrCapeTextureResolver() {}

    static ResourceLocation resolve(int cosmeticCapeId) {
        if (cosmeticCapeId == CharacterCapeCatalog.NONE_ID || disabled) {
            return null;
        }
        try {
            switch (cosmeticCapeId) {
                case CharacterCapeCatalog.GONDOR:
                    return LOTRCapes.GONDOR;
                case CharacterCapeCatalog.TOWER_GUARD:
                    return LOTRCapes.TOWER_GUARD;
                case CharacterCapeCatalog.RANGER:
                    return LOTRCapes.RANGER;
                case CharacterCapeCatalog.RANGER_ITHILIEN:
                    return LOTRCapes.RANGER_ITHILIEN;
                case CharacterCapeCatalog.LOSSARNACH:
                    return LOTRCapes.LOSSARNACH;
                case CharacterCapeCatalog.PELARGIR:
                    return LOTRCapes.PELARGIR;
                case CharacterCapeCatalog.BLACKROOT:
                    return LOTRCapes.BLACKROOT;
                case CharacterCapeCatalog.PINNATH_GELIN:
                    return LOTRCapes.PINNATH_GELIN;
                case CharacterCapeCatalog.LAMEDON:
                    return LOTRCapes.LAMEDON;
                case CharacterCapeCatalog.ROHAN:
                    return LOTRCapes.ROHAN;
                case CharacterCapeCatalog.DALE:
                    return LOTRCapes.DALE;
                case CharacterCapeCatalog.DUNLENDING_BERSERKER:
                    return LOTRCapes.DUNLENDING_BERSERKER;
                case CharacterCapeCatalog.GALADHRIM:
                    return LOTRCapes.GALADHRIM;
                case CharacterCapeCatalog.GALADHRIM_TRADER:
                    return LOTRCapes.GALADHRIM_TRADER;
                case CharacterCapeCatalog.WOOD_ELF:
                    return LOTRCapes.WOOD_ELF;
                case CharacterCapeCatalog.HIGH_ELF:
                    return LOTRCapes.HIGH_ELF;
                case CharacterCapeCatalog.RIVENDELL:
                    return LOTRCapes.RIVENDELL;
                case CharacterCapeCatalog.RIVENDELL_TRADER:
                    return LOTRCapes.RIVENDELL_TRADER;
                case CharacterCapeCatalog.NEAR_HARAD:
                    return LOTRCapes.NEAR_HARAD;
                case CharacterCapeCatalog.SOUTHRON_CHAMPION:
                    return LOTRCapes.SOUTHRON_CHAMPION;
                case CharacterCapeCatalog.GULF_HARAD:
                    return LOTRCapes.GULF_HARAD;
                case CharacterCapeCatalog.TAURETHRIM:
                    return LOTRCapes.TAURETHRIM;
                case CharacterCapeCatalog.GALADHRIM_SMITH:
                    return LOTRCapes.GALADHRIM_SMITH;
                case CharacterCapeCatalog.DORWINION_CAPTAIN:
                    return LOTRCapes.DORWINION_CAPTAIN;
                case CharacterCapeCatalog.DORWINION_ELF_CAPTAIN:
                    return LOTRCapes.DORWINION_ELF_CAPTAIN;
                case CharacterCapeCatalog.GANDALF:
                    return LOTRCapes.GANDALF;
                case CharacterCapeCatalog.GANDALF_SANTA:
                    return LOTRCapes.GANDALF_SANTA;
                default:
                    return null;
            }
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
}
