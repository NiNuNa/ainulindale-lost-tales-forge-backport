package com.ninuna.losttales.character.registry;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks the skin catalogue to the resources it points at: every bundled skin
 * must exist in the jar as a 64x64 PNG, every LOTR skin must stay inside the
 * LOTR namespace, and every display group must have a name in the lang file.
 */
public final class CharacterSkinRegistryTest {

    private static final String EXILED_DWARF = "losttales:dwarf_exiled_male_0";

    @Test
    public void bundledSkinsShipAsSkinSizedTextures() throws Exception {
        int bundled = 0;
        for (CharacterSkinDefinition definition : CharacterSkinRegistry.getAll()) {
            if (!definition.isBundled()) {
                continue;
            }
            bundled++;
            String location = definition.getTextureLocation();
            String path = "/assets/losttales/"
                    + location.substring("losttales:".length());
            InputStream stream = CharacterSkinRegistryTest.class.getResourceAsStream(path);
            assertNotNull(definition.getId() + " texture missing at " + path, stream);
            try {
                BufferedImage image = ImageIO.read(stream);
                assertNotNull(definition.getId() + " texture is not a readable PNG", image);
                assertEquals(definition.getId() + " width", 64, image.getWidth());
                assertEquals(definition.getId() + " height", 64, image.getHeight());
            } finally {
                stream.close();
            }
        }
        assertTrue("expected at least one bundled skin", bundled > 0);
    }

    @Test
    public void lotrSkinsStayInsideTheLotrNamespace() {
        for (CharacterSkinDefinition definition : CharacterSkinRegistry.getAll()) {
            if (definition.isBundled()) {
                continue;
            }
            assertTrue(definition.getId() + " must point into LOTR Legacy",
                    definition.getTextureLocation().startsWith(
                            CharacterSkinRegistry.LOTR_TEXTURE_ROOT));
        }
    }

    @Test
    public void exiledDwarfIsOfferedNextToLotrMaleDwarfSkins() {
        List<CharacterSkinDefinition> male = CharacterSkinRegistry.getCompatibleSkins(
                CharacterRaceRegistry.DWARF, CharacterGenderRegistry.MALE);
        Set<String> ids = new HashSet<String>();
        for (CharacterSkinDefinition definition : male) {
            ids.add(definition.getId());
        }
        assertTrue(ids.contains("losttales:dwarf_erebor_male_0"));
        assertTrue(ids.contains(EXILED_DWARF));

        CharacterSkinDefinition exiled = CharacterSkinRegistry.get(EXILED_DWARF);
        assertNotNull(exiled);
        assertTrue(exiled.isBundled());
        assertEquals("losttales:textures/skins/dwarf/exiled_male/0.png",
                exiled.getTextureLocation());
        assertTrue(CharacterSkinRegistry.isCompatible(EXILED_DWARF,
                CharacterRaceRegistry.DWARF, CharacterGenderRegistry.MALE));
        assertFalse(CharacterSkinRegistry.isCompatible(EXILED_DWARF,
                CharacterRaceRegistry.DWARF, CharacterGenderRegistry.FEMALE));
        assertFalse(CharacterSkinRegistry.isCompatible(EXILED_DWARF,
                CharacterRaceRegistry.HUMAN, CharacterGenderRegistry.MALE));
    }

    @Test
    public void everyDisplayGroupHasAnEnglishName() throws Exception {
        Set<String> groups = new HashSet<String>();
        for (CharacterSkinDefinition definition : CharacterSkinRegistry.getAll()) {
            groups.add(definition.getDisplayGroupId());
        }
        InputStream stream = CharacterSkinRegistryTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        assertNotNull("en_US.lang missing", stream);
        Set<String> named = new HashSet<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        try {
            String prefix = "gui.losttales.character.skin_group.";
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (line.startsWith(prefix) && equals > prefix.length()) {
                    named.add(line.substring(prefix.length(), equals));
                }
            }
        } finally {
            reader.close();
        }
        for (String group : groups) {
            assertTrue("skin group " + group + " has no en_US name", named.contains(group));
        }
    }
}
