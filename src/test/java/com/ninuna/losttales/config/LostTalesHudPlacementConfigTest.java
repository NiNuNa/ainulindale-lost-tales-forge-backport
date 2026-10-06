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
                "quickLootHudOffsetX", 62.0D, 0.0D, 100.0D);
        Property migrated = config.getCategory(
                LostTalesConfig.CATEGORY_CLIENT).get("quickLootHudOffsetX");

        assertEquals(24.0D, value, 0.0001D);
        assertEquals(Property.Type.DOUBLE, migrated.getType());
        migrated.set(24.75D);
        assertEquals(24.75D, migrated.getDouble(), 0.0001D);
    }

    @Test
    public void everyPlacementScreenElementHasAStableConfigKey() {
        for (String element : new String[] {"compass", "fellowship", "quickloot",
                "quest", "notifications"}) {
            assertEquals(element, LostTalesConfig.normalizeHudElement(element));
        }
        assertEquals("quest", LostTalesConfig.normalizeHudElement(" Quest "));
        assertEquals("a name no panel goes by is none",
                "", LostTalesConfig.normalizeHudElement("tracker"));
    }
}
