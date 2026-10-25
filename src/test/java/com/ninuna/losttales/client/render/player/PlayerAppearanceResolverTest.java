package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinLayout;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The resolver must make the same decisions the race switch made before it:
 * the race's body, the stored chest type only for bodies that can carry one,
 * the selected skin when valid and a deterministic fallback otherwise, plus
 * the layout and arms the body is built for.
 */
public final class PlayerAppearanceResolverTest {

    private static final UUID PLAYER = UUID.fromString(
            "5b1a8c2e-2b6f-4b7e-9a1e-0f3c6d2a8b11");

    /** An appearance whose arms and chest were never chosen: the body type's own apply. */
    private static CharacterAppearance unchosen(String raceId, String bodyTypeId,
                                                String skinId) {
        return new CharacterAppearance(PLAYER, raceId, bodyTypeId, skinId, "", "");
    }

    @Test
    public void playersWithoutACharacterRenderAsVanilla() {
        assertNull(PlayerAppearanceResolver.resolve(PLAYER, null));
        assertNull(PlayerAppearanceResolver.resolve(
                PLAYER, CharacterAppearance.removed(PLAYER)));
    }

    @Test
    public void unknownRaceRendersAsVanilla() {
        assertNull(PlayerAppearanceResolver.resolve(PLAYER, unchosen(
                "losttales:balrog", CharacterBodyTypeRegistry.MASCULINE, "")));
    }

    @Test
    public void feminineHumanGetsTheDefaultChestAndHerSkin() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, unchosen(CharacterRaceRegistry.HUMAN,
                        CharacterBodyTypeRegistry.FEMININE,
                        "losttales:human_bree_feminine_0"));
        assertNotNull(resolved);
        assertEquals(CharacterBodyModelRegistry.LOTR_HUMAN, resolved.getModelId());
        assertEquals(CharacterSkinLayout.LOTR_64X64, resolved.getLayout());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM, resolved.getChestTypeId());
        assertEquals("lotr:human|lotr_64x64|losttales:rounded_medium|losttales:slender",
                resolved.getRendererKey());
        assertEquals(1.0F, resolved.getRendererScale(), 0.0F);
        assertEquals("lotr:mob/bree/bree_female/0.png", resolved.getTexture().toString());
    }

    @Test
    public void masculineHumanHasNoChestUnlessChosen() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, unchosen(CharacterRaceRegistry.HUMAN,
                        CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0"));
        assertNotNull(resolved);
        assertEquals(CharacterChestTypeRegistry.NONE, resolved.getChestTypeId());
        assertEquals("lotr:human|lotr_64x64|losttales:none|losttales:broad",
                resolved.getRendererKey());

        ResolvedPlayerAppearance chosen = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0",
                        "losttales:broad", CharacterChestTypeRegistry.FULL_SMALL));
        assertEquals(CharacterChestTypeRegistry.FULL_SMALL, chosen.getChestTypeId());
    }

    @Test
    public void unisexRacesNeverGetAChestAndKeepTheirScale() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.ORC,
                        CharacterBodyTypeRegistry.NEUTRAL, "losttales:orc_0",
                        "losttales:broad", CharacterChestTypeRegistry.ROUNDED_LARGE));
        assertNotNull(resolved);
        assertEquals(CharacterBodyModelRegistry.LOTR_ORC, resolved.getModelId());
        assertEquals(CharacterSkinLayout.LOTR_64X32, resolved.getLayout());
        assertEquals(CharacterChestTypeRegistry.NONE, resolved.getChestTypeId());
        assertEquals("losttales:broad", resolved.getArmsId());
        assertEquals(CharacterRaceRegistry.get(CharacterRaceRegistry.ORC).getRendererScale(),
                resolved.getRendererScale(), 0.0F);
    }

    @Test
    public void rendererKeysNameModelLayoutChestAndArmWidth() {
        assertEquals("lotr:human|lotr_64x64|losttales:classic",
                ResolvedPlayerAppearance.rendererKey(
                        "lotr:human", CharacterSkinLayout.LOTR_64X64, "",
                        CharacterChestTypeRegistry.CLASSIC));
        assertEquals("losttales:player|minecraft_64x64|losttales:none|losttales:slender",
                ResolvedPlayerAppearance.rendererKey(
                        "losttales:player", CharacterSkinLayout.MINECRAFT_64X64,
                        "losttales:slender", "losttales:bogus"));
    }

    @Test
    public void accountSkinUsesThePlayerBodyWithTheStoredArmWidth() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, new CharacterAppearance(PLAYER, CharacterRaceRegistry.HUMAN,
                        CharacterBodyTypeRegistry.MASCULINE, CharacterSkinRegistry.ACCOUNT_SKIN_ID,
                        "losttales:slender", ""));
        assertNotNull(resolved);
        assertEquals(CharacterBodyModelRegistry.LOSTTALES_PLAYER, resolved.getModelId());
        assertEquals(CharacterSkinLayout.MINECRAFT_64X64, resolved.getLayout());
        assertEquals("losttales:slender", resolved.getArmsId());
        assertTrue(resolved.usesAccountSkin());
        assertNull(resolved.getTexture());
        assertEquals(CharacterChestTypeRegistry.NONE, resolved.getChestTypeId());
        assertEquals("losttales:player|minecraft_64x64|losttales:none|losttales:slender",
                resolved.getRendererKey());

        // The body type only picks the defaults when nothing was stored.
        ResolvedPlayerAppearance defaulted = PlayerAppearanceResolver.resolve(
                PLAYER, unchosen(CharacterRaceRegistry.HUMAN,
                        CharacterBodyTypeRegistry.FEMININE, CharacterSkinRegistry.ACCOUNT_SKIN_ID));
        assertEquals("losttales:slender", defaulted.getArmsId());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM, defaulted.getChestTypeId());
        ResolvedPlayerAppearance male = PlayerAppearanceResolver.resolve(
                PLAYER, unchosen(CharacterRaceRegistry.HUMAN,
                        CharacterBodyTypeRegistry.MASCULINE, CharacterSkinRegistry.ACCOUNT_SKIN_ID));
        assertEquals("losttales:broad", male.getArmsId());
    }

    @Test
    public void theAccountIdentityIsThePlayerBodyWithTheProfileArmWidth() {
        ResolvedPlayerAppearance resolved = PlayerAppearanceResolver.resolve(
                PLAYER, CharacterAppearance.forAccount(PLAYER, "Steve",
                        "losttales:slender"));
        assertNotNull(resolved);
        assertEquals(CharacterBodyModelRegistry.LOSTTALES_PLAYER, resolved.getModelId());
        assertEquals(CharacterSkinLayout.MINECRAFT_64X64, resolved.getLayout());
        assertEquals("losttales:slender", resolved.getArmsId());
        assertTrue(resolved.usesAccountSkin());
        assertNull(resolved.getTexture());
        assertEquals(CharacterChestTypeRegistry.NONE, resolved.getChestTypeId());
        assertEquals(1.0F, resolved.getRendererScale(), 0.0F);
    }

    @Test
    public void otherRacesWearTheAccountSkinOnTheirOwnBody() {
        ResolvedPlayerAppearance elf = PlayerAppearanceResolver.resolve(
                PLAYER, unchosen(CharacterRaceRegistry.ELF,
                        CharacterBodyTypeRegistry.FEMININE, "losttales:account_skin_elf"));
        assertNotNull(elf);
        assertEquals(CharacterBodyModelRegistry.LOTR_ELF, elf.getModelId());
        assertEquals(CharacterSkinLayout.MINECRAFT_64X64, elf.getLayout());
        assertTrue(elf.usesAccountSkin());
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM, elf.getChestTypeId());
        assertEquals("lotr:elf|minecraft_64x64|losttales:rounded_medium|losttales:slender",
                elf.getRendererKey());

        ResolvedPlayerAppearance orc = PlayerAppearanceResolver.resolve(
                PLAYER, unchosen(CharacterRaceRegistry.ORC,
                        CharacterBodyTypeRegistry.NEUTRAL, "losttales:account_skin_orc"));
        assertNotNull(orc);
        assertEquals(CharacterSkinLayout.MINECRAFT_64X64, orc.getLayout());
        assertTrue(orc.usesAccountSkin());
    }

    @Test
    public void incompatibleSkinFallsBackDeterministically() {
        CharacterAppearance appearance = unchosen(
                CharacterRaceRegistry.ELF, CharacterBodyTypeRegistry.MASCULINE,
                "losttales:human_bree_masculine_0");
        ResolvedPlayerAppearance first = PlayerAppearanceResolver.resolve(PLAYER, appearance);
        ResolvedPlayerAppearance second = PlayerAppearanceResolver.resolve(PLAYER, appearance);
        assertNotNull(first);
        assertEquals(CharacterBodyModelRegistry.LOTR_ELF, first.getModelId());
        String expected = CharacterSkinRegistry.get(CharacterSkinRegistry.getDefaultSkinId(
                CharacterRaceRegistry.ELF, CharacterBodyTypeRegistry.MASCULINE, PLAYER))
                .getTextureLocation();
        assertEquals(expected, first.getTexture().toString());
        assertEquals(first.getTexture().toString(), second.getTexture().toString());
        assertTrue(first.getTexture().toString().startsWith("lotr:mob/elf/"));
    }

    /** A character falls back by its own id, whoever plays it. */
    @Test
    public void aCharacterFallsBackByItsOwnId() {
        UUID character = UUID.fromString("c2000000-0000-0000-0000-00000000002c");
        CharacterAppearance appearance = new CharacterAppearance(
                com.ninuna.losttales.character.sync.CharacterAppearanceKind.CHARACTER,
                PLAYER, character, "Steve", "Aldric", CharacterRaceRegistry.ELF,
                CharacterBodyTypeRegistry.MASCULINE, "losttales:human_bree_masculine_0",
                false, "", "", 30, "", "");
        ResolvedPlayerAppearance resolved =
                PlayerAppearanceResolver.resolve(PLAYER, appearance);
        assertNotNull(resolved);
        assertEquals(CharacterSkinRegistry.get(CharacterSkinRegistry.getDefaultSkinId(
                CharacterRaceRegistry.ELF, CharacterBodyTypeRegistry.MASCULINE, character))
                .getTextureLocation(), resolved.getTexture().toString());
    }
}
