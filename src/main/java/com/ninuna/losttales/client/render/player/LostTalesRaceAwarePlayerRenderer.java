package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBodyModelDefinition;
import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Delegates player rendering to the body resolved from the active,
 * server-synchronized roleplaying character, or to the plain player body
 * for a player without one. Vanilla's RenderPlayer only draws an entity the
 * resolver cannot describe.
 *
 * A configured renderer exists per body model, skin layout, arm width and
 * chest choice. They are built the first time a player needs them and kept
 * for the rest of the client run; one that fails to build is logged once
 * and its players fall back to vanilla.
 */
public final class LostTalesRaceAwarePlayerRenderer extends RenderPlayer {

    private final Map<String, LostTalesConfiguredPlayerRenderer> renderers =
            new HashMap<String, LostTalesConfiguredPlayerRenderer>();
    private final Set<String> failed = new HashSet<String>();

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
        ResolvedPlayerAppearance appearance = PlayerAppearanceResolver.resolve(player);
        if (appearance == null) {
            return null;
        }
        String key = appearance.getRendererKey();
        LostTalesConfiguredPlayerRenderer renderer = this.renderers.get(key);
        if (renderer == null && !this.failed.contains(key)) {
            renderer = build(appearance);
            if (renderer == null) {
                this.failed.add(key);
            } else {
                this.renderers.put(key, renderer);
            }
        }
        return renderer;
    }

    private static LostTalesConfiguredPlayerRenderer build(ResolvedPlayerAppearance appearance) {
        String key = appearance.getRendererKey();
        try {
            CharacterBodyModelDefinition definition =
                    CharacterBodyModelRegistry.get(appearance.getModelId());
            LostTalesConfiguredPlayerRenderer renderer =
                    LostTalesPlayerBodyModelFactory.create(definition,
                            appearance.getLayout(), appearance.getBodyTypeId(),
                            appearance.getChestTypeId());
            if (renderer != null && renderer.isConfigured()) {
                return renderer;
            }
            FMLLog.warning("[losttales] Player body %s could not be configured", key);
        } catch (Throwable throwable) {
            FMLLog.warning("[losttales] Player body %s was disabled: %s",
                    key, throwable.toString());
        }
        return null;
    }
}
