package com.ninuna.losttales.util;

import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.camera.CameraPresetId;
import com.ninuna.losttales.item.ELostTalesItem;
import java.util.Arrays;
import java.util.List;
import net.minecraft.item.EnumRarity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The words the code names by key have their English line, so no screen,
 * tooltip or notice ever shows a bare key: item tooltips and kinds, the
 * HUD's and the map's fixed words, the fallbacks drawn where the server
 * sends no name, and the lines content names by its id.
 */
public final class LostTalesLangLinesTest {
    private static final List<String> NAMED = Arrays.asList(
            "item.losttales.tooltip.hold.lore",
            "item.losttales.tooltip.hold.details",
            "item.losttales.tooltip.hold.set",
            "item.losttales.tooltip.key.shift",
            "item.losttales.tooltip.key.ctrl",
            "item.losttales.tooltip.faction",
            "item.losttales.tooltip.repair",
            "item.losttales.tooltip.type",
            "item.losttales.tooltip.created_by",
            "item.losttales.tooltip.rarity",
            "item.losttales.tooltip.set.heavy",
            "item.losttales.tooltip.set.light",
            "item.losttales.tooltip.set.heavy.bonus",
            "item.losttales.tooltip.set.light.bonus",
            "item.losttales.tooltip.set.no_bonus",
            "item.losttales.tooltip.set.empty",
            LostTalesClientQuickLootCache.CONTAINER_KEY,
            LostTalesClientQuickLootCache.SEALED_KEY,
            LostTalesClientMobAggroCache.ENEMY_KEY,
            "map.losttales.marker.unnamed",
            "gui.losttales.hud.location_discovered",
            "gui.losttales.compass.tracked",
            "gui.losttales.compass.distance",
            "gui.losttales.chat.hover.invalid_item",
            "gui.losttales.chat.hover.invalid_statistic",
            "gui.losttales.fellowship.unknown",
            "gui.losttales.map.go_here.description",
            "gui.losttales.character.attribute.value.hearts",
            "gui.losttales.character.attribute.value.percent",
            "gui.losttales.character.attribute.value.size",
            "gui.losttales.character.error.wait",
            "gui.losttales.character.room.menu.title",
            "disconnect.losttales.character_recovery",
            "gui.losttales.character.lore.eomer.description",
            "gui.losttales.character.lore.frodo.description",
            "gui.losttales.character.lore.gandalf.description",
            "gui.losttales.character.lore.gollum.description",
            "gui.losttales.character.lore.sauron.description",
            "entity.losttales.TestPerson.name",
            "entity.losttales.Nia.name",
            "lotr.achievement.enterMoonElfBiome.title",
            "lotr.achievement.enterMoonElfBiome.desc",
            "lotr.waypoint.SUN_ELVES",
            "lotr.waypoint.SUN_ELVES.info",
            "lotr.waypoint.MOON_ELVES_2",
            "lotr.waypoint.MOON_ELVES_2.info");

    @Test
    public void everyWordTheCodeNamesHasAnEnglishLine() {
        for (String key : NAMED) {
            assertTrue(key, EnglishWords.INSTANCE.has(key));
        }
        for (ELostTalesItem.Type type : ELostTalesItem.Type.values()) {
            assertTrue(type.name(), EnglishWords.INSTANCE.has(type.getNameKey()));
        }
        for (EnumRarity rarity : EnumRarity.values()) {
            assertTrue(rarity.name(), EnglishWords.INSTANCE.has(
                    "item.losttales.tooltip.rarity." + rarity.name()));
        }
        for (CameraPresetId preset : CameraPresetId.values()) {
            assertTrue(preset.name(), EnglishWords.INSTANCE.has(
                    "losttales.config.third_person_camera.cameraPreset."
                            + preset.getConfigValue()));
        }
    }

    /** English reads as it did before the words moved to the lang file. */
    @Test
    public void englishReadsAsItDid() {
        assertEquals("Heavy Armor", EnglishWords.INSTANCE.format(
                ELostTalesItem.Type.ARMOR_HEAVY.getNameKey()));
        assertEquals("Urn (Sealed)", EnglishWords.INSTANCE.format(
                LostTalesClientQuickLootCache.SEALED_KEY, "Urn"));
        assertEquals("20 (10 hearts)", EnglishWords.INSTANCE.format(
                "gui.losttales.character.attribute.value.hearts", "20", "10"));
        assertEquals("110%", EnglishWords.INSTANCE.format(
                "gui.losttales.character.attribute.value.percent", "110"));
        assertEquals("12m", EnglishWords.INSTANCE.format(
                "gui.losttales.compass.distance", Long.valueOf(12L)));
    }
}
