package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.chat.ChatColorMarkers;
import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Called by the coremod inside {@code LOTRTravellingTraderInfo}, just
 * before each travelling-trader notice — arrived, arrived to visit a
 * player, departed — is sent to every player in the world. LOTR builds
 * the notice with the trader's name in plain yellow; the name is marked
 * here with the trader's faction colour ({@link LotrFactionColors}), the
 * same colour the NPC speaks in and the faction explorer paints, so the
 * notice reads as the NPC's own. Only a mark is added: the words, the
 * yellow a client without the mod still shows, and who is sent the
 * notice are LOTR's. Any failure is logged once and the notice goes out
 * as LOTR built it.
 *
 * <p>Assumes LOTR v36.15's notice shape: a translation whose first
 * argument is the trader's display-name component. If LOTR builds it
 * otherwise the notice is simply left alone.</p>
 */
public final class LostTalesLotrTraderNoticeHook {
    private static volatile boolean failureLogged;

    private LostTalesLotrTraderNoticeHook() {}

    public static IChatComponent decorate(IChatComponent notice, LOTREntityNPC trader) {
        try {
            if (!(notice instanceof ChatComponentTranslation) || trader == null) {
                return notice;
            }
            Object[] arguments = ((ChatComponentTranslation)notice).getFormatArgs();
            if (arguments == null || arguments.length == 0
                    || !(arguments[0] instanceof IChatComponent)) {
                return notice;
            }
            int color = LotrFactionColors.forFaction(trader.getFaction(), -1);
            if (color < 0) {
                return notice;
            }
            IChatComponent name = (IChatComponent)arguments[0];
            name.getChatStyle().setChatClickEvent(new ClickEvent(
                    ClickEvent.Action.SUGGEST_COMMAND, ChatColorMarkers.value(color)));
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning("[LostTales] Could not colour a travelling trader "
                        + "notice by its faction: %s", throwable);
            }
        }
        return notice;
    }
}
