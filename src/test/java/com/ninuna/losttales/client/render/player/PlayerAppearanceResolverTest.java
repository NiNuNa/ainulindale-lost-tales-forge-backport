package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinLayout;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The resolver must make the same decisions the race switch made before it:
 * the race's body, the stored chest type only for bodies that can carry one,
 * the selected skin when valid and a deterministic fallback otherwise, plus
 * the layout and arm width the body is built for.
 */
public final class PlayerAppearanceResolverTest {

    private static final UUID PLAYER = UUID.fromString(
            "5b1a8c2e-2b6f-4b7e-9a1e-0f3c6d2a8b11");

    @Test
    public void playersWithoutACharacterRenderAsVanilla() {
        assertNull(PlayerAppearanceResolver.resolve(PLAYER, null));
        assertNull(PlayerAppearanceResolver.resolve(
                PLAYER, CharacterAppearance.removed(PLAYER)));
    }

    @Test
    public void unknownRaceRendersAsVanilla() {
        assertNull(PlayerAppearanceResolver.resolve(PLAYER, new CharacterAppearance(
                PLAYER, "losttales:balrog", CharacterGenderRegistry.MALE, "")));
    }

    @Test
    public void feminineHumanGetsTheDefaultChestAndHerSkin() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.FEMALE,
                        "losttales:human_bree_female_0"));
        assertNotNull(resolved);
        assertEquals(CharacterBodyModelRegistry.LOTR_HUMAN, resolved.getModelId());
        assertEquals(CharacterSkinLayout.LOTR_64X64, resolved.getLayout());
        assertTrue(resolved.hasChest());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM, resolved.getChestTypeId());
        assertEquals("lotr:human|lotr_64x64|losttales:rounded_medium|losttales:slim",
                resolved.getRendererKey());
        assertEquals(1.0F, resolved.getRendererScale(), 0.0F);
        assertEquals("lotr:mob/bree/bree_female/0.png", resolved.getTexture().toString());
    }

    @Test
    public void masculineHumanHasNoChestUnlessChosen() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, "losttales:human_bree_male_0"));
        assertNotNull(resolved);
        assertFalse(resolved.hasChest());
        assertEquals("lotr:human|lotr_64x64|losttales:none|losttales:wide",
                resolved.getRendererKey());

        ResolvedPlayerAppearance chosen = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, "losttales:human_bree_male_0",
                        "losttales:wide", CharacterChestTypeRegistry.FULL_SMALL));
        assertEquals(CharacterChestTypeRegistry.FULL_SMALL, chosen.getChestTypeId());
    }

    @Test
    public void unisexRacesNeverGetAChestAndKeepTheirScale() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.ORC,
                        CharacterGenderRegistry.NON_BINARY, "losttales:orc_0",
                        "losttales:wide", CharacterChestTypeRegistry.ROUNDED_LARGE));
        assertNotNull(resolved);
        assertEquals(CharacterBodyModelRegistry.LOTR_ORC, resolved.getModelId());
        assertEquals(CharacterSkinLayout.LOTR_64X32, resolved.getLayout());
        assertFalse(resolved.hasChest());
        assertEquals("losttales:wide", resolved.getBodyTypeId());
        assertEquals(CharacterRaceRegistry.get(CharacterRaceRegistry.ORC).getRendererScale(),
                resolved.getRendererScale(), 0.0F);

        ResolvedPlayerAppearance troll = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.LEGACY_TROLL,
                        CharacterGenderRegistry.FEMALE, ""));
        assertNotNull(troll);
        assertEquals(CharacterRaceRegistry.HALF_TROLL, troll.getRaceId());
        // No chest for a unisex body; the arm width still follows the record.
        assertEquals("lotr:half_troll|lotr_64x64|losttales:none|losttales:slim",
                troll.getRendererKey());
    }

    @Test
    public void rendererKeysNameModelLayoutChestAndArmWidth() {
        assertEquals("lotr:human|lotr_64x64|losttales:classic",
                ResolvedPlayerAppearance.rendererKey(
                        "lotr:human", CharacterSkinLayout.LOTR_64X64, "",
                        CharacterChestTypeRegistry.CLASSIC));
        assertEquals("losttales:player|minecraft_64x64|losttales:none|losttales:slim",
                ResolvedPlayerAppearance.rendererKey(
                        "losttales:player", CharacterSkinLayout.MINECRAFT_64X64,
                        "losttales:slim", "losttales:bogus"));
    }

    @Test
    public void accountSkinUsesThePlayerBodyWithTheStoredArmWidth() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, CharacterSkinRegistry.ACCOUNT_SKIN_ID,
                        "losttales:slim"));
        assertNotNull(resolved);
        assertEquals(CharacterBodyModelRegistry.LOSTTALES_PLAYER, resolved.getModelId());
        assertEquals(CharacterSkinLayout.MINECRAFT_64X64, resolved.getLayout());
        assertEquals("losttales:slim", resolved.getBodyTypeId());
        assertTrue(resolved.usesAccountSkin());
        assertNull(resolved.getTexture());
        assertFalse(resolved.hasChest());
        assertEquals("losttales:player|minecraft_64x64|losttales:none|losttales:slim",
                resolved.getRendererKey());

        // The sex only picks the defaults when nothing was stored.
        ResolvedPlayerAppearance defaulted = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.FEMALE, CharacterSkinRegistry.ACCOUNT_SKIN_ID));
        assertEquals("losttales:slim", defaulted.getBodyTypeId());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM, defaulted.getChestTypeId());
        ResolvedPlayerAppearance male = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, CharacterSkinRegistry.ACCOUNT_SKIN_ID));
        assertEquals("losttales:wide", male.getBodyTypeId());
    }

    @Test
    public void otherRacesWearTheAccountSkinOnTheirOwnBody() {
        ResolvedPlayerAppearance elf = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.ELF,
                        CharacterGenderRegistry.FEMALE, "losttales:account_skin_elf"));
        assertNotNull(elf);
        assertEquals(CharacterBodyModelRegistry.LOTR_ELF, elf.getModelId());
        assertEquals(CharacterSkinLayout.MINECRAFT_64X64, elf.getLayout());
        assertTrue(elf.usesAccountSkin());
        assertTrue(elf.hasChest());
        assertEquals("lotr:elf|minecraft_64x64|losttales:rounded_medium|losttales:slim",
                elf.getRendererKey());

        ResolvedPlayerAppearance orc = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.ORC,
                        CharacterGenderRegistry.NON_BINARY, "losttales:account_skin_orc"));
        assertNotNull(orc);
        assertEquals(CharacterSkinLayout.MINECRAFT_64X64, orc.getLayout());
        assertTrue(orc.usesAccountSkin());
    }

    @Test
    public void incompatibleSkinFallsBackDeterministically() {
        CharacterAppearance appearance = new CharacterAppearance(
                PLAYER, CharacterRaceRegistry.ELF, CharacterGenderRegistry.MALE,
                "losttales:human_bree_male_0");
        ResolvedPlayerAppearance first = PlayerAppearanceResolver.resolve(PLAYER, appearance);
        ResolvedPlayerAppearance second = PlayerAppearanceResolver.resolve(PLAYER, appearance);
        assertNotNull(first);
        assertEquals(CharacterBodyModelRegistry.LOTR_ELF, first.getModelId());
        String expected = CharacterSkinRegistry.get(CharacterSkinRegistry.getDefaultSkinId(
                CharacterRaceRegistry.ELF, CharacterGenderRegistry.MALE, PLAYER))
                .getTextureLocation();
        assertEquals(expected, first.getTexture().toString());
        assertEquals(first.getTexture().toString(), second.getTexture().toString());
        assertTrue(first.getTexture().toString().startsWith("lotr:mob/elf/"));
    }
}
