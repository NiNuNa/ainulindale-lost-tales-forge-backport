package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import cpw.mods.fml.common.FMLLog;
import lotr.client.model.LOTRModelOrc;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Delegates player rendering to a LOTR body model selected from the active,
 * server-synchronized roleplaying character.
 */
public final class LostTalesRaceAwarePlayerRenderer extends RenderPlayer {

    private static final String KEY_SEPARATOR = "|";

    private final Map<String, LostTalesConfiguredPlayerRenderer> renderers;

    public LostTalesRaceAwarePlayerRenderer() {
        this.renderers = createRenderers();
    }

    @Override
    public void doRender(AbstractClientPlayer player,
                         double x, double y, double z,
                         float entityYaw, float partialTicks) {
        LostTalesConfiguredPlayerRenderer renderer = rendererFor(player);
        if (renderer == null) {
            super.doRender(player, x, y, z, entityYaw, partialTicks);
            return;
        }
        renderer.setRenderManager(RenderManager.instance);
        renderer.doRender(player, x, y, z, entityYaw, partialTicks);
    }

    @Override
    public void renderFirstPersonArm(EntityPlayer player) {
        LostTalesConfiguredPlayerRenderer renderer = rendererFor(player);
        if (renderer == null) {
            super.renderFirstPersonArm(player);
            return;
        }
        renderer.setRenderManager(RenderManager.instance);
        renderer.renderFirstPersonArm(player);
    }

    private LostTalesConfiguredPlayerRenderer rendererFor(EntityPlayer player) {
        if (player == null || player.getUniqueID() == null) {
            return null;
        }
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.get(player.getUniqueID());
        if (appearance == null || !appearance.isPresent()) {
            return null;
        }
        String modelGender = CharacterRaceRegistry.supportsGenderedModels(
                appearance.getRaceId())
                ? appearance.getAppearanceGenderId()
                : CharacterGenderRegistry.MALE;
        return this.renderers.get(key(appearance.getRaceId(), modelGender));
    }

    private static Map<String, LostTalesConfiguredPlayerRenderer> createRenderers() {
        HashMap<String, LostTalesConfiguredPlayerRenderer> created =
                new HashMap<String, LostTalesConfiguredPlayerRenderer>();
        String[] raceIds = new String[] {
                CharacterRaceRegistry.HUMAN,
                CharacterRaceRegistry.ELF,
                CharacterRaceRegistry.DWARF,
                CharacterRaceRegistry.HOBBIT,
                CharacterRaceRegistry.ORC,
                CharacterRaceRegistry.URUK,
                CharacterRaceRegistry.HALF_TROLL
        };
        for (String raceId : raceIds) {
            if (CharacterRaceRegistry.supportsGenderedModels(raceId)) {
                addSafely(created, raceId, CharacterGenderRegistry.MALE, false);
                addSafely(created, raceId, CharacterGenderRegistry.FEMALE, true);
            } else {
                addSafely(created, raceId, CharacterGenderRegistry.MALE, false);
            }
        }
        return Collections.unmodifiableMap(created);
    }

    private static void addSafely(
            Map<String, LostTalesConfiguredPlayerRenderer> renderers,
            String raceId, String modelGenderId, boolean female) {
        try {
            LostTalesConfiguredPlayerRenderer renderer =
                    createRenderer(raceId, female);
            if (renderer != null && renderer.isConfigured()) {
                renderers.put(key(raceId, modelGenderId), renderer);
            } else {
                FMLLog.warning("[losttales] Player model %s/%s could not be configured",
                        raceId, modelGenderId);
            }
        } catch (Throwable throwable) {
            FMLLog.warning("[losttales] Player model %s/%s was disabled: %s",
                    raceId, modelGenderId, throwable.toString());
        }
    }

    private static LostTalesConfiguredPlayerRenderer createRenderer(
            String raceId, boolean female) {
        ModelBiped mainModel;
        ModelBiped chestArmorModel;
        ModelBiped armorModel;
        CharacterRaceDefinition raceDefinition = CharacterRaceRegistry.get(raceId);
        float scale = raceDefinition == null
                ? 1.0F : raceDefinition.getRendererScale();

        if (CharacterRaceRegistry.HUMAN.equals(raceId)) {
            mainModel = new LostTalesPlayerHumanModel(0.0F, false, female);
            chestArmorModel = new LostTalesPlayerHumanModel(1.0F, true, female);
            armorModel = new LostTalesPlayerHumanModel(0.5F, true, female);
        } else if (CharacterRaceRegistry.ELF.equals(raceId)) {
            mainModel = new LostTalesPlayerElfModel(0.0F, female);
            chestArmorModel = new LostTalesPlayerElfModel(1.0F, female);
            armorModel = new LostTalesPlayerElfModel(0.5F, female);
        } else if (CharacterRaceRegistry.DWARF.equals(raceId)) {
            mainModel = new LostTalesPlayerDwarfModel(0.0F, female);
            chestArmorModel = new LostTalesPlayerDwarfModel(1.0F, female);
            armorModel = new LostTalesPlayerDwarfModel(0.5F, female);
        } else if (CharacterRaceRegistry.HOBBIT.equals(raceId)) {
            mainModel = new LostTalesPlayerHobbitModel(0.0F, female);
            chestArmorModel = new LostTalesPlayerHobbitModel(1.0F, female);
            armorModel = new LostTalesPlayerHobbitModel(0.5F, female);
        } else if (CharacterRaceRegistry.ORC.equals(raceId)) {
            mainModel = new LOTRModelOrc(0.0F);
            chestArmorModel = new LOTRModelOrc(1.0F);
            armorModel = new LOTRModelOrc(0.5F);
        } else if (CharacterRaceRegistry.URUK.equals(raceId)) {
            mainModel = new LOTRModelOrc(0.0F);
            chestArmorModel = new LOTRModelOrc(1.0F);
            armorModel = new LOTRModelOrc(0.5F);
        } else if (CharacterRaceRegistry.HALF_TROLL.equals(raceId)) {
            mainModel = new LostTalesPlayerHalfTrollModel(0.0F);
            chestArmorModel = new LostTalesPlayerHalfTrollModel(1.0F);
            armorModel = new LostTalesPlayerHalfTrollModel(0.5F);
        } else {
            return null;
        }

        return new LostTalesConfiguredPlayerRenderer(
                raceId, mainModel, chestArmorModel, armorModel, scale);
    }

    private static String key(String raceId, String modelGenderId) {
        return raceId + KEY_SEPARATOR + modelGenderId;
    }
}
