package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.EntityRenderTextureAccess;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

/**
 * Called by the coremod inside LOTR's client-side NPC speech packet
 * handler. Purely presentational: recipients, speech content, immersive
 * floating speech, and NPC behaviour are LOTR's. LOTR sends speech to one
 * player, so it shows as a whisper from the NPC — from the chat print
 * when LOTR prints one, and from the immersive floating speech when it
 * does not, so the words above an NPC's head are also kept where
 * conversations are kept. Any failure of the chat-print path falls back
 * to LOTR's original yellow chat line; a failure of the immersive path
 * simply adds nothing.
 */
public final class LostTalesNpcChatHook {
    /**
     * One packet can take both paths — immersive speech shown, then the
     * chat log printed — microseconds apart; anything filed longer ago
     * is a genuine repeat.
     */
    private static final long DUPLICATE_WINDOW_NANOS = 50L * 1000000L;

    private static volatile boolean failureLogged;
    /** What the immersive path last filed, so the chat log never doubles it. */
    private static int lastFiledEntityId = -1;
    private static String lastFiledSpeech = "";
    private static long lastFiledNanos;

    private LostTalesNpcChatHook() {}

    public static void addNpcChatMessage(EntityPlayer player,
                                         IChatComponent original,
                                         LOTREntityNPC npc) {
        try {
            if (LostTalesConfig.enableNpcChatStyling && player != null
                    && npc != null && original != null) {
                String name = npc.getCommandSenderName();
                String speech = extractSpeech(
                        original.getUnformattedText(), name);
                if (isJustFiled(npc, speech)) {
                    // The immersive path of this very packet already
                    // delivered these words to the conversation.
                    return;
                }
                if (name != null && name.length() > 0
                        && speech.length() > 0
                        && fileSpeech(npc, name, speech)) {
                    return;
                }
            }
        } catch (Throwable throwable) {
            logFailureOnce(throwable);
        }
        if (player != null && original != null) {
            player.addChatMessage(original);
        }
    }

    /**
     * Called by the coremod right after LOTR shows its immersive
     * floating speech: the same words are filed into the NPC's
     * conversation tab, which is the only chat delivery this player gets
     * while LOTR's chat log is off. The floating speech itself, and
     * whether LOTR also prints a chat line, are untouched.
     */
    public static void addImmersiveSpeech(EntityPlayer player,
                                          LOTREntityNPC npc,
                                          String speech) {
        try {
            if (!LostTalesConfig.enableNpcChatStyling || player == null
                    || npc == null || speech == null) {
                return;
            }
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(
                    speech);
            plain = plain == null ? "" : plain.trim();
            String name = npc.getCommandSenderName();
            if (name == null || name.length() == 0 || plain.length() == 0) {
                return;
            }
            if (fileSpeech(npc, name, plain)) {
                lastFiledEntityId = npc.getEntityId();
                lastFiledSpeech = plain;
                lastFiledNanos = System.nanoTime();
            }
        } catch (Throwable throwable) {
            logFailureOnce(throwable);
        }
    }

    /** Whether the immersive path filed exactly this speech just now. */
    private static boolean isJustFiled(LOTREntityNPC npc, String speech) {
        return npc.getEntityId() == lastFiledEntityId
                && lastFiledSpeech.equals(speech)
                && System.nanoTime() - lastFiledNanos
                        < DUPLICATE_WINDOW_NANOS;
    }

    private static boolean fileSpeech(LOTREntityNPC npc, String name,
                                      String speech) {
        // LOTR addresses its speech to this one player, so it is a
        // whisper from the NPC: a tab of its own, named after it, in the
        // NPC's own faction colour like a role-playing character's line.
        ResourceLocation texture =
                EntityRenderTextureAccess.resolveEntityTexture(npc);
        return LostTalesChatPresentation.receiveNpcSpeech(
                ChatTab.npc(name), npc.getUniqueID(), name,
                texture == null ? "" : texture.toString(), speech,
                nameColor(npc), factionName(npc));
    }

    private static void logFailureOnce(Throwable throwable) {
        if (!failureLogged) {
            failureLogged = true;
            FMLLog.warning("[LostTales] Styled NPC chat failed; "
                    + "falling back to LOTR's own presentation: %s",
                    throwable);
        }
    }

    /**
     * The faction the NPC speaks for, as LOTR displays it, captured for
     * the hover card while the entity is at hand; empty when it cannot
     * be read.
     */
    private static String factionName(LOTREntityNPC npc) {
        try {
            LOTRFaction faction = npc.getFaction();
            String name = faction == null ? null : faction.factionName();
            return name == null ? "" : name.trim();
        } catch (LinkageError ignored) {
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    /**
     * The colour the NPC speaks in: its faction's, exactly as the
     * faction explorer and player lines colour it. Factionless
     * wanderers belong to LOTR's UNALIGNED faction, whose colour is
     * pure black and unreadable on the chat — they speak in the
     * palette's light grey instead — and an NPC whose faction cannot be
     * read at all keeps LOTR's yellow-name honey.
     */
    private static int nameColor(LOTREntityNPC npc) {
        try {
            LOTRFaction faction = npc.getFaction();
            if (faction != null) {
                int color = faction.getFactionColor() & 0xFFFFFF;
                return color != 0 ? color
                        : LostTalesColors.rgb(LostTalesColors.ROSE_GRAY);
            }
        } catch (LinkageError ignored) {
        } catch (RuntimeException ignored) {
        }
        return LostTalesColors.rgb(LostTalesColors.HONEY);
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
