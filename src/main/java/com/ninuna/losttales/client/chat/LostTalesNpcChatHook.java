package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.EntityRenderTextureAccess;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

/**
 * Called by the coremod in place of the plain chat print inside LOTR's
 * client-side NPC speech packet handler. Purely presentational: recipients,
 * speech content, immersive floating speech, and NPC behaviour are LOTR's.
 * LOTR sends speech to one player, so it shows as a whisper from the NPC.
 * Any failure falls back to LOTR's original yellow chat line.
 */
public final class LostTalesNpcChatHook {
    private static volatile boolean failureLogged;

    private LostTalesNpcChatHook() {}

    public static void addNpcChatMessage(EntityPlayer player,
                                         IChatComponent original,
                                         LOTREntityNPC npc) {
        try {
            if (LostTalesConfig.enableNpcChatStyling && player != null
                    && npc != null && original != null
                    && printStyled(player, original, npc)) {
                return;
            }
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning("[LostTales] Styled NPC chat failed; "
                        + "falling back to LOTR's chat line: %s", throwable);
            }
        }
        if (player != null && original != null) {
            player.addChatMessage(original);
        }
    }

    private static boolean printStyled(EntityPlayer player,
                                       IChatComponent original,
                                       LOTREntityNPC npc) {
        String name = npc.getCommandSenderName();
        String speech = extractSpeech(original.getUnformattedText(), name);
        if (name == null || name.length() == 0 || speech.length() == 0) {
            return false;
        }
        // LOTR addresses its speech to this one player, so it is a
        // whisper from the NPC: a tab of its own, named after it.
        ResourceLocation texture =
                EntityRenderTextureAccess.resolveEntityTexture(npc);
        return LostTalesChatPresentation.receiveNpcSpeech(
                ChatTab.npc(name), npc.getUniqueID(), name,
                texture == null ? "" : texture.toString(), speech);
    }

    /**
     * Recovers the bare speech from LOTR's {@code <Name> speech} line. The
     * name prefix is stripped by matching the NPC's own name, so a name
     * containing formatting or brackets cannot desynchronize the result.
     */
    static String extractSpeech(String unformattedLine, String npcName) {
        if (unformattedLine == null) {
            return "";
        }
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(
                unformattedLine);
        if (plain == null) {
            return "";
        }
        String prefix = "<" + (npcName == null ? "" : npcName) + ">";
        return (plain.startsWith(prefix)
                ? plain.substring(prefix.length()) : plain).trim();
    }
}
