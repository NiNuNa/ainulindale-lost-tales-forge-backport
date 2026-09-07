package com.ninuna.losttales.client.render.player;

import cpw.mods.fml.common.FMLLog;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;

/** Forge-event bridge that replaces only the configured renderer's cape pass. */
public final class LostTalesPlayerCapeRenderHook {

    /** Bodies whose cape drawing threw once and is not tried again. */
    private static final Set<String> DISABLED_MODELS = new HashSet<String>();

    private LostTalesPlayerCapeRenderHook() {}

    public static void onSpecialsPre(RenderPlayerEvent.Specials.Pre event) {
        if (event == null || event.isCanceled() || !event.renderCape
                || !(event.renderer instanceof LostTalesConfiguredPlayerRenderer)
                || !(event.entityPlayer instanceof AbstractClientPlayer)) {
            return;
        }

        ResolvedPlayerAppearance appearance =
                PlayerAppearanceResolver.resolve(event.entityPlayer);
        if (appearance == null) {
            return;
        }
        LostTalesConfiguredPlayerRenderer renderer =
                (LostTalesConfiguredPlayerRenderer)event.renderer;
        // The cape hangs on the body, so what it follows is the model,
        // not the race: one race may be drawn on more than one.
        String modelId = appearance.getModelId();
        if (DISABLED_MODELS.contains(modelId)) {
            return;
        }

        try {
            LostTalesPlayerCapeRenderer.render(
                    renderer,
                    modelId,
                    (AbstractClientPlayer)event.entityPlayer,
                    event.partialRenderTick);
            // The normal cape has now either been rendered or intentionally
            // skipped for the same visibility/texture conditions as vanilla.
            event.renderCape = false;
        } catch (Throwable throwable) {
            DISABLED_MODELS.add(modelId);
            FMLLog.warning(
                    "[losttales] Body-adjusted cape rendering was disabled for %s: %s",
                    modelId,
                    throwable.toString());
            // Leave renderCape true so RenderPlayer falls back to vanilla.
        }
    }
}
