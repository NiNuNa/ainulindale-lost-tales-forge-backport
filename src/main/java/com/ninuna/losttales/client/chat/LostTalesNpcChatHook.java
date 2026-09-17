package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.EntityRenderTextureAccess;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.common.FMLLog;
import lotr.client.render.entity.LOTRNPCRendering;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

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

    /**
     * Stands in for LOTR's own pass over every NPC's floating speech.
     * Lost Tales draws that speech with each NPC instead — in the chat's
     * ivory on the chat's black, under the NPC's name in its faction's
     * colour, so an NPC reads the same over its head as in the
     * conversation. With the styling turned off, LOTR's pass runs
     * exactly as it did.
     */
    public static void renderNpcSpeeches(Minecraft minecraft, World world,
                                         float partialTicks) {
        try {
            if (LostTalesConfig.enableNpcChatStyling
                    && LostTalesConfig.showChatSpeechBubbles) {
                return;
            }
        } catch (Throwable throwable) {
            logFailureOnce(throwable);
        }
        LOTRNPCRendering.renderAllNPCSpeeches(minecraft, world, partialTicks);
    }

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
            // The world's copy, in the chat's own colours; LOTR's own
            // drawing of it is redirected below.
            ChatSpeechBubbles.receiveNpc(npc.getUniqueID(), name,
                    nameColor(npc), plain);
            if (fileSpeech(npc, name, plain)) {
                lastFiledEntityId = npc.getEntityId();
                lastFiledSpeech = plain;
                lastFiledNanos = System.nanoTime();
            }
        } catch (Throwable throwable) {
            logFailureOnce(throwable);
        }
    }

    /**
     * Somebody saying something to this player, filed the way an NPC's
     * speech always is: a bubble over their head and a line in their own
     * conversation tab. What a quest conversation uses, so a giver's
     * words are read where every other word is read and stay in the log
     * once the talk is over.
     *
     * <p>{@code nameColor} is the colour their name is written in, and
     * {@code faction} what stands under it; either may be empty.</p>
     */
    public static void sayToPlayer(EntityLivingBase speaker, String speech,
                                   int nameColor, String faction) {
        try {
            if (speaker == null || speech == null) {
                return;
            }
            String plain = EnumChatFormatting.getTextWithoutFormattingCodes(
                    speech);
            plain = plain == null ? "" : plain.trim();
            String name = speaker.getCommandSenderName();
            if (name == null || name.length() == 0 || plain.length() == 0) {
                return;
            }
            ChatSpeechBubbles.receiveNpc(speaker.getUniqueID(), name,
                    nameColor, plain);
            ResourceLocation texture =
                    EntityRenderTextureAccess.resolveEntityTexture(speaker);
            LostTalesChatPresentation.receiveNpcSpeech(ChatTab.npc(name),
                    speaker.getUniqueID(), name,
                    texture == null ? "" : texture.toString(), plain,
                    nameColor, faction == null ? "" : faction);
        } catch (Throwable throwable) {
            logFailureOnce(throwable);
        }
    }

    /** The colour a LOTR NPC's name is written in; for callers outside. */
    public static int speakerColor(LOTREntityNPC npc) {
        return nameColor(npc);
    }

    /** What stands under a LOTR NPC's name; for callers outside. */
    public static String speakerFaction(LOTREntityNPC npc) {
        return factionName(npc);
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
     * The colour the NPC speaks in: its faction's, as
     * {@link LotrFactionColors} decides it for every faction-coloured
     * name; an NPC whose faction cannot be read keeps LOTR's yellow-name
     * honey.
     */
    private static int nameColor(LOTREntityNPC npc) {
        LOTRFaction faction;
        try {
            faction = npc.getFaction();
        } catch (LinkageError ignored) {
            faction = null;
        } catch (RuntimeException ignored) {
            faction = null;
        }
        return LotrFactionColors.forFaction(faction,
                LostTalesColors.rgb(LostTalesColors.HONEY));
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
