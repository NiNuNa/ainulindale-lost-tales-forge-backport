package com.ninuna.losttales.client.render.player;

import cpw.mods.fml.common.FMLLog;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;

/** Forge-event bridge that replaces only the configured renderer's cape pass. */
public final class LostTalesPlayerCapeRenderHook {

    private static final Set<String> DISABLED_RACES = new HashSet<String>();

    private LostTalesPlayerCapeRenderHook() {}

    public static void onSpecialsPre(RenderPlayerEvent.Specials.Pre event) {
        if (event == null || event.isCanceled() || !event.renderCape
                || !(event.renderer instanceof LostTalesConfiguredPlayerRenderer)
                || !(event.entityPlayer instanceof AbstractClientPlayer)) {
            return;
        }

        LostTalesConfiguredPlayerRenderer renderer =
                (LostTalesConfiguredPlayerRenderer)event.renderer;
        String raceId = renderer.getRaceId();
        if (DISABLED_RACES.contains(raceId)) {
            return;
        }

        try {
            LostTalesPlayerCapeRenderer.render(
                    renderer,
                    (AbstractClientPlayer)event.entityPlayer,
                    event.partialRenderTick);
            // The normal cape has now either been rendered or intentionally
            // skipped for the same visibility/texture conditions as vanilla.
            event.renderCape = false;
        } catch (Throwable throwable) {
            DISABLED_RACES.add(raceId);
            FMLLog.warning(
                    "[losttales] Race-adjusted cape rendering was disabled for %s: %s",
                    raceId,
                    throwable.toString());
            // Leave renderCape true so RenderPlayer falls back to vanilla.
        }
    }
}
