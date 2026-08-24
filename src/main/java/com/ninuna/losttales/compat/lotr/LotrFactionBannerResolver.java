package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lotr.common.LOTRMod;
import lotr.common.fac.LOTRFaction;
import lotr.common.item.LOTRItemBanner;
import net.minecraft.item.ItemStack;

/**
 * The LOTR banner that stands for a faction, by LOTR's own registry:
 * every {@link LOTRItemBanner.BannerType} lists itself on its faction's
 * {@code factionBanners} as it is declared, so the first entry is the
 * faction's principal banner (Gondor's white tree ahead of its fiefdoms'
 * banners, Mordor's eye ahead of Nan Ungol's). A faction LOTR gives no
 * banner — an unaligned one, or one an add-on appended — shows the
 * Banner of the Stewards of Gondor instead. Names are never compared;
 * the link is the enum field, and the item is whatever LOTR registered
 * as {@code LOTRMod.banner} with the type's own damage value.
 *
 * <p>Results are cached per faction id for rendering, which asks every
 * frame: the stacks handed out are shared and must not be mutated.</p>
 */
public final class LotrFactionBannerResolver {
    private static final Map<String, ItemStack> BANNERS =
            new HashMap<String, ItemStack>();
    private static ItemStack fallback;
    private static boolean fallbackResolved;
    private static boolean unavailableLogged;

    private LotrFactionBannerResolver() {}

    /**
     * The banner type for a stable faction id ({@code lotr:gondor}):
     * the faction's first banner, or the Stewards' when it has none or
     * the id is unknown. Null only when LOTR's banner types cannot be
     * read at all.
     */
    public static synchronized LOTRItemBanner.BannerType bannerTypeFor(
            String factionId) {
        try {
            LOTRFaction faction = LotrCharacterAdapter.getInstance()
                    .resolveFactionForState(factionId);
            return bannerTypeFor(faction);
        } catch (LinkageError error) {
            logUnavailable(error);
            return null;
        } catch (RuntimeException exception) {
            logUnavailable(exception);
            return null;
        }
    }

    /** As above, from a live LOTR faction; the fallback for null. */
    public static synchronized LOTRItemBanner.BannerType bannerTypeFor(
            LOTRFaction faction) {
        // The fallback constant also forces the enum's registration of
        // every banner on its faction before the list is read.
        LOTRItemBanner.BannerType stewards =
                LOTRItemBanner.BannerType.GONDOR_STEWARD;
        if (faction == null) {
            return stewards;
        }
        List<LOTRItemBanner.BannerType> banners = faction.factionBanners;
        if (banners == null || banners.isEmpty()) {
            return stewards;
        }
        LOTRItemBanner.BannerType first = banners.get(0);
        return first == null ? stewards : first;
    }

    /**
     * A shared, render-only stack of the banner for a faction id, the
     * fallback when the faction has none, or null when LOTR's banner item
     * is not registered (a dedicated server before items exist, a test).
     */
    public static synchronized ItemStack bannerFor(String factionId) {
        String key = LotrCharacterAdapter.normalizeFactionId(factionId);
        ItemStack cached = BANNERS.get(key);
        if (cached != null) {
            return cached;
        }
        LOTRItemBanner.BannerType type = bannerTypeFor(key);
        ItemStack stack = type == null ? null : stackOf(type);
        if (stack == null) {
            return fallbackBanner();
        }
        BANNERS.put(key, stack);
        return stack;
    }

    /** The Banner of the Stewards of Gondor, or null without LOTR's item. */
    public static synchronized ItemStack fallbackBanner() {
        if (!fallbackResolved) {
            fallbackResolved = true;
            try {
                fallback = stackOf(LOTRItemBanner.BannerType.GONDOR_STEWARD);
            } catch (LinkageError error) {
                logUnavailable(error);
                fallback = null;
            } catch (RuntimeException exception) {
                logUnavailable(exception);
                fallback = null;
            }
        }
        return fallback;
    }

    private static ItemStack stackOf(LOTRItemBanner.BannerType type) {
        if (LOTRMod.banner == null || type == null) {
            return null;
        }
        return new ItemStack(LOTRMod.banner, 1, type.bannerID);
    }

    private static void logUnavailable(Throwable cause) {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;
        FMLLog.warning("[%s] LOTR faction banners are unavailable; faction "
                + "chat shows no banner icon: %s", LostTalesMetaData.MOD_ID,
                cause.toString());
    }
}
