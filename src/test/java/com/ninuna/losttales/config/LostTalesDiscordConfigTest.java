package com.ninuna.losttales.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The Discord topic's interval starts at Discord's ten-minute window,
 * which is also its default, and goes up to an hour.
 */
public final class LostTalesDiscordConfigTest {
    @Test
    public void theTopicIntervalStartsAtTenMinutes() {
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        Property interval = definitions.getCategory(LostTalesConfig.CATEGORY_DISCORD)
                .get("channelStatusIntervalSeconds");
        assertEquals("600", interval.getMinValue());
        assertEquals("600", interval.getDefault());
        assertEquals("3600", interval.getMaxValue());
    }
}
