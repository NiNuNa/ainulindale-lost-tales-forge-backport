package com.ninuna.losttales.compat.lotr;

import lotr.common.fac.LOTRFaction;
import lotr.common.item.LOTRItemBanner;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Banners come from LOTR's own faction-to-banner registry; a faction
 * without one shows the Stewards' banner. The id-keyed entry points go
 * through {@link LotrCharacterAdapter}, which needs a launched FML to
 * log, so only the faction-keyed resolution is exercised here.
 */
public final class LotrFactionBannerResolverTest {

    @Test
    public void principalBannerIsTheFactionsFirstRegisteredOne() {
        assertSame(LOTRItemBanner.BannerType.GONDOR,
                LotrFactionBannerResolver.bannerTypeFor(LOTRFaction.GONDOR));
        assertSame(LOTRItemBanner.BannerType.MORDOR,
                LotrFactionBannerResolver.bannerTypeFor(LOTRFaction.MORDOR));
        assertSame(LOTRItemBanner.BannerType.ROHAN,
                LotrFactionBannerResolver.bannerTypeFor(LOTRFaction.ROHAN));
        // Every banner LOTR declares is filed under its own faction.
        for (LOTRItemBanner.BannerType type
                : LOTRItemBanner.BannerType.values()) {
            assertNotNull(type.faction);
            assertTrue(type + " is not on its faction's list",
                    type.faction.factionBanners.contains(type));
        }
    }

    @Test
    public void factionsWithoutBannersFallBackToTheStewards() {
        assertSame(LOTRItemBanner.BannerType.GONDOR_STEWARD,
                LotrFactionBannerResolver.bannerTypeFor((LOTRFaction)null));
        boolean seenBannerless = false;
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction.factionBanners == null
                    || faction.factionBanners.isEmpty()) {
                seenBannerless = true;
                assertSame(LOTRItemBanner.BannerType.GONDOR_STEWARD,
                        LotrFactionBannerResolver.bannerTypeFor(faction));
            } else {
                assertSame(faction.factionBanners.get(0),
                        LotrFactionBannerResolver.bannerTypeFor(faction));
            }
        }
        assertTrue("LOTR v36.15 has bannerless factions", seenBannerless);
    }
}
