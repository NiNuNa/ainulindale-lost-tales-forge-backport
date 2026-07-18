package com.ninuna.losttales.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesHudPlacementConfigTest {
    @Test
    public void integerHudOffsetsAreRecreatedAsPreciseProperties() {
        Configuration config = new Configuration();
        config.get(LostTalesConfig.CATEGORY_CLIENT,
                "quickLootHudOffsetX", 24).set(24);

        double value = LostTalesConfig.getHudPercent(config,
                "quickLootHudOffsetX", 62.0D, 0.0D, 100.0D, "test");
        Property migrated = config.getCategory(
                LostTalesConfig.CATEGORY_CLIENT).get("quickLootHudOffsetX");

        assertEquals(24.0D, value, 0.0001D);
        assertEquals(Property.Type.DOUBLE, migrated.getType());
        migrated.set(24.75D);
        assertEquals(24.75D, migrated.getDouble(), 0.0001D);
    }

    @Test
    public void legacyQuickLootOffsetsKeepTheirPreviousScreenPosition() {
        assertEquals(50.0D,
                LostTalesConfig.migrateLegacyQuickLootOffsetX(0), 0.0001D);
        assertEquals(62.0D,
                LostTalesConfig.migrateLegacyQuickLootOffsetX(24), 0.0001D);
        assertEquals(100.0D,
                LostTalesConfig.migrateLegacyQuickLootOffsetX(100), 0.0001D);
    }

    @Test
    public void everyPlacementScreenElementHasAStableConfigKey() {
        assertEquals("compass",
                LostTalesConfig.normalizeHudElement("compass"));
        assertEquals("party",
                LostTalesConfig.normalizeHudElement("partyHud"));
        assertEquals("quickloot",
                LostTalesConfig.normalizeHudElement("quick-loot"));
        assertEquals("quest",
                LostTalesConfig.normalizeHudElement("tracker"));
        assertEquals("questnotifications",
                LostTalesConfig.normalizeHudElement("quest notifications"));
        assertEquals("mapdiscovery",
                LostTalesConfig.normalizeHudElement("location discovery"));
        assertEquals("areanotice",
                LostTalesConfig.normalizeHudElement("area name"));
    }
}
