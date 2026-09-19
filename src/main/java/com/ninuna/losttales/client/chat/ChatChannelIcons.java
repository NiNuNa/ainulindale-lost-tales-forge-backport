package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelIconSpec;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import cpw.mods.fml.common.FMLLog;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.compat.lotr.LotrFactionBannerResolver;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The icon each tab wears before its name — on its tab, in the restore
 * menu, wherever the name stands alone: an emoji per channel, for the
 * faction channel the LOTR banner of the active character's faction
 * (the Stewards' banner when the faction has none), for a whisper with
 * a player the partner's own head, exactly as their lines show it, and
 * for an NPC conversation the NPC's portrait as its speech showed it. A
 * server may choose a channel's icon itself, an emoji or an item, in its
 * channels file; the choice arrives with the chat access and stands
 * before the code's own emoji and the faction banner. A whisper whose
 * partner this client cannot place yet, an NPC whose portrait has not
 * been seen, a faction tab without LOTR's banner item, or a channel
 * given an item this client does not have wears an emoji. Purely
 * decorative: the catalogue names and ids are untouched.
 */
final class ChatChannelIcons {
    /** Icons are drawn at the sheet's own sprite size, never scaled. */
    static final int SIZE = ChatEmoji.SPRITE_SIZE;
    /** Gap between the icon and the text. */
    static final int GAP = 3;
    /** A head is drawn as it is in the lines, centred in the icon's box. */
    private static final float HEAD_SIZE = 8.0F;
    /** NPCs remembered per conversation; the oldest go first. */
    private static final int MAX_PORTRAITS = 64;
    private static final Map<ChatTab, Speaker> NPC_SPEAKERS =
            new LinkedHashMap<ChatTab, Speaker>();
    /**
     * Faction names remembered per NPC, captured when it spoke: what the
     * hover card names as the NPC's faction, so an NPC's card reads like
     * a player's without asking LOTR anything at hover time.
     */
    private static final Map<UUID, String> NPC_FACTIONS =
            new LinkedHashMap<UUID, String>();
    /** The icons the server chose, by channel id; empty until it says. */
    private static Map<String, ChatChannelIconSpec> CHOSEN =
            Collections.emptyMap();
    /**
     * The item each chosen item icon resolved to on this client, by
     * channel id, resolved the first time the channel is drawn; a null
     * value marks an item this client does not have, reported once.
     */
    private static final Map<String, ItemStack> RESOLVED =
            new HashMap<String, ItemStack>();

    /** The NPC a conversation is with, as its speech showed it. */
    private static final class Speaker {
        final UUID id;
        /** The portrait its speech was drawn with; null until one is seen. */
        final String portrait;

        Speaker(UUID id, String portrait) {
            this.id = id;
            this.portrait = portrait;
        }
    }

    private ChatChannelIcons() {}

    /**
     * Puts the server's choice of icons in force, replacing the last;
     * what each item resolves to is asked again from here.
     */
    static synchronized void install(Map<String, ChatChannelIconSpec> icons) {
        Map<String, ChatChannelIconSpec> chosen =
                new HashMap<String, ChatChannelIconSpec>();
        if (icons != null) {
            for (Map.Entry<String, ChatChannelIconSpec> entry
                    : icons.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    chosen.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                            entry.getValue());
                }
            }
        }
        CHOSEN = chosen;
        RESOLVED.clear();
    }

    /** The server's choices go with the server. */
    static synchronized void forgetChannelIcons() {
        CHOSEN = Collections.emptyMap();
        RESOLVED.clear();
    }

    private static synchronized ChatChannelIconSpec chosen(ChatChannel channel) {
        return channel == null ? null
                : CHOSEN.get(channel.getId().toLowerCase(Locale.ROOT));
    }

    /**
     * The item the server chose for the channel, or null for a channel
     * wearing an emoji or given an item this client does not have.
     */
    static synchronized ItemStack itemIconOf(ChatChannel channel) {
        ChatChannelIconSpec spec = chosen(channel);
        if (spec == null || spec.getKind() != ChatChannelIconSpec.Kind.ITEM) {
            return null;
        }
        String key = channel.getId().toLowerCase(Locale.ROOT);
        if (RESOLVED.containsKey(key)) {
            return RESOLVED.get(key);
        }
        ItemStack stack = resolveItem(spec);
        RESOLVED.put(key, stack);
        if (stack == null) {
            FMLLog.warning("[%s] Channel '%s' is given the item '%s', which "
                            + "this client does not have; its emoji is shown",
                    LostTalesMetaData.MOD_ID, channel.getId(), spec.getName());
        }
        return stack;
    }

    private static ItemStack resolveItem(ChatChannelIconSpec spec) {
        Object registered = Item.itemRegistry.getObject(spec.getName());
        return registered instanceof Item
                ? new ItemStack((Item)registered, 1, spec.getMeta()) : null;
    }

    /**
     * Remembers the NPC a conversation is with, for its tab and its member
     * list: its id and the portrait its speech was drawn with, the last
     * one seen kept where this speech had none.
     */
    static synchronized void rememberNpc(ChatTab tab, UUID npcId,
                                         String texturePath) {
        if (tab == null || !tab.isNpc() || npcId == null) {
            return;
        }
        Speaker previous = NPC_SPEAKERS.remove(tab);
        String portrait = texturePath != null && texturePath.length() > 0
                ? texturePath : previous == null ? null : previous.portrait;
        NPC_SPEAKERS.put(tab, new Speaker(npcId, portrait));
        while (NPC_SPEAKERS.size() > MAX_PORTRAITS) {
            Iterator<ChatTab> oldest = NPC_SPEAKERS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The portrait an NPC conversation's speech was drawn with; null until one is seen. */
    static synchronized String npcPortrait(ChatTab tab) {
        Speaker speaker = tab == null ? null : NPC_SPEAKERS.get(tab);
        return speaker == null ? null : speaker.portrait;
    }

    /** The NPC an NPC conversation is with; null until it has spoken this session. */
    static synchronized UUID npcId(ChatTab tab) {
        Speaker speaker = tab == null ? null : NPC_SPEAKERS.get(tab);
        return speaker == null ? null : speaker.id;
    }

    /** Remembers the faction an NPC spoke for, for its hover card. */
    static synchronized void rememberNpcFaction(UUID npcId,
                                                String factionName) {
        if (npcId == null || factionName == null
                || factionName.length() == 0) {
            return;
        }
        NPC_FACTIONS.remove(npcId);
        NPC_FACTIONS.put(npcId, factionName);
        while (NPC_FACTIONS.size() > MAX_PORTRAITS) {
            Iterator<UUID> oldest = NPC_FACTIONS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    static synchronized String npcFaction(UUID npcId) {
        return npcId == null ? null : NPC_FACTIONS.get(npcId);
    }

    /** Conversations end with the session, and so do the NPCs they were with. */
    static synchronized void forgetPortraits() {
        NPC_SPEAKERS.clear();
        NPC_FACTIONS.clear();
    }

    /**
     * Draws the tab's icon with its top-left at the point: the partner's
     * head for a whisper with a player the client can place, the tab's
     * emoji otherwise.
     */
    static void draw(Minecraft minecraft, ChatTab tab, float x, float y,
                     int alpha) {
        if (minecraft == null || tab == null) {
            return;
        }
        float inset = (SIZE - HEAD_SIZE) / 2.0F;
        int shadow = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (!tab.isNpc() && !tab.isWhisper()) {
            // The server's own choice of item stands before everything
            // else, drawn as an item's icon in a line is.
            ItemStack chosen = itemIconOf(tab.getChannel());
            if (chosen != null) {
                ChatInlineIcons.drawItem(minecraft, chosen, x, y, SIZE, alpha);
                return;
            }
        }
        if (tab.getChannel() == ChatChannel.FACTION) {
            ItemStack banner = LotrFactionBannerResolver.bannerFor(
                    ClientChatChannelState.wornFactionId(ChatChannel.FACTION));
            if (banner != null) {
                // Crisp at whole display pixels per texel, never cut,
                // as an item's icon in a line is drawn.
                ChatInlineIcons.drawItem(minecraft, banner, x, y, SIZE, alpha);
                return;
            }
        } else if (tab.isNpc()) {
            final String portrait = npcPortrait(tab);
            if (portrait != null) {
                final float headX = x + inset;
                final float headY = y + inset;
                // A head fading with its window is one picture.
                LostTalesUiFlatLayers.draw(alpha, x, y, x + SIZE + 1,
                        y + SIZE + 1, new LostTalesUiFlatLayers.Layers() {
                            @Override
                            public void draw() {
                                drawNpcIcon(minecraft, portrait, headX, headY,
                                        shadow, alpha);
                            }
                        });
                return;
            }
        } else if (tab.isWhisper()) {
            final UUID partner = partnerId(minecraft, tab.getPartner());
            if (partner != null) {
                // The head and its sphere are one icon, so the icon that
                // is centred in the slot is the pair, not the face: the
                // face sits left of centre and the sphere fills the rest.
                final float headX = x + (SIZE - HEAD_SIZE
                        - ChatPresenceMark.OVERHANG_X) / 2.0F;
                final float headY = y + (float)Math.floor((SIZE - HEAD_SIZE
                        - ChatPresenceMark.OVERHANG_Y) / 2.0F);
                // A conversation's tab wears the other player's head, so
                // it wears the status of the identity the conversation is
                // with, in the corner the head gives up for it. An NPC's
                // tab above has no account and so no status.
                final ChatPresence presence = ClientChatPresence.presenceOf(
                        partner, ChatPresenceIdentity.character(
                                ClientChatChannelState.partnerCharacterIdOf(tab)));
                LostTalesUiFlatLayers.draw(alpha, x, y, x + SIZE + 1,
                        y + SIZE + 1, new LostTalesUiFlatLayers.Layers() {
                            @Override
                            public void draw() {
                                drawPartnerIcon(minecraft, partner, presence,
                                        headX, headY, shadow, alpha);
                            }
                        });
                return;
            }
        }
        ChatEmoji icon = iconOf(tab);
        if (icon != null) {
            ChatInlineIcons.drawEmoji(minecraft, icon, x, y, SIZE, alpha);
        }
    }

    /** An NPC conversation's portrait over its shadow. */
    private static void drawNpcIcon(Minecraft minecraft, String portrait,
                                    float headX, float headY, int shadow,
                                    int alpha) {
        if (shadow > 0) {
            LostTalesSilhouetteRenderState.begin(
                    LostTalesChatVisualStyle.SHADOW);
            try {
                LostTalesCharacterHeadIconRenderer.drawTintedNpcHeadBase(
                        minecraft, portrait,
                        headX + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        headY + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        HEAD_SIZE, 1.0F, 1.0F, 1.0F, shadow / 255.0F);
            } finally {
                LostTalesSilhouetteRenderState.end();
            }
            LostTalesUiFlatLayers.nextLayer();
        }
        LostTalesCharacterHeadIconRenderer.drawNpcHead(minecraft, portrait,
                headX, headY, HEAD_SIZE, 1.0F, alpha / 255.0F);
    }

    /**
     * A conversation partner's head over its shadow, with the sphere of
     * the identity the conversation is with in the corner it gives up.
     */
    private static void drawPartnerIcon(Minecraft minecraft, UUID partner,
                                        ChatPresence presence, float headX,
                                        float headY, int shadow, int alpha) {
        if (shadow > 0) {
            ChatPresenceMark.beginShadowCut(headX, headY, HEAD_SIZE);
            LostTalesSilhouetteRenderState.begin(
                    LostTalesChatVisualStyle.SHADOW);
            try {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        minecraft, partner,
                        headX + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        headY + LostTalesChatVisualStyle.SHADOW_OFFSET,
                        HEAD_SIZE, 1.0F, 1.0F, 1.0F, shadow / 255.0F);
            } finally {
                LostTalesSilhouetteRenderState.end();
                ChatPresenceMark.endHeadCut();
            }
            LostTalesUiFlatLayers.nextLayer();
        }
        ChatPresenceMark.beginHeadCut(headX, headY, HEAD_SIZE);
        try {
            LostTalesCharacterHeadIconRenderer.drawAccountHead(minecraft,
                    partner, headX, headY, HEAD_SIZE, 1.0F, alpha / 255.0F);
        } finally {
            ChatPresenceMark.endHeadCut();
        }
        LostTalesUiFlatLayers.nextLayer();
        ChatPresenceMark.draw(headX, headY, HEAD_SIZE, presence, alpha);
    }

    /**
     * The account the name belongs to, from the players in the world or
     * the appearance the server syncs for every online player; null when
     * neither knows the name. Shared with the hover card, which places
     * a mentioned account the same way.
     */
    static UUID partnerId(Minecraft minecraft, String name) {
        if (name == null || name.length() == 0) {
            return null;
        }
        if (minecraft.theWorld != null
                && minecraft.theWorld.playerEntities != null) {
            for (Object value : minecraft.theWorld.playerEntities) {
                if (value instanceof EntityPlayer && name.equalsIgnoreCase(
                        ((EntityPlayer)value).getCommandSenderName())) {
                    return ((EntityPlayer)value).getUniqueID();
                }
            }
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null
                    && name.equalsIgnoreCase(appearance.getAccountName())) {
                return appearance.getPlayerId();
            }
        }
        return null;
    }

    static ChatEmoji iconOf(ChatTab tab) {
        if (tab == null) {
            return null;
        }
        if (tab.isNpc()) {
            return ChatEmoji.GRINNING;
        }
        if (tab.isWhisper()) {
            return ChatEmoji.BLUSH;
        }
        return iconOf(tab.getChannel());
    }

    /**
     * The emoji a channel wears: the server's choice when it chose an
     * emoji this build has, the code's own otherwise — which is also
     * what stands in for a chosen item this client cannot draw.
     */
    static ChatEmoji iconOf(ChatChannel channel) {
        if (channel == null) {
            return null;
        }
        ChatChannelIconSpec chosen = chosen(channel);
        if (chosen != null
                && chosen.getKind() == ChatChannelIconSpec.Kind.EMOJI) {
            ChatEmoji named = ChatEmoji.fromName(chosen.getName());
            if (named != null) {
                return named;
            }
        }
        // By the channel itself rather than a name, so a channel a server
        // defines falls through to the same face every unknown one wears.
        if (channel == ChatChannel.ALL) {
            return ChatEmoji.SLIGHT_SMILE;
        }
        if (channel == ChatChannel.PROXIMITY) {
            return ChatEmoji.SMILEY;
        }
        if (channel == ChatChannel.FACTION) {
            return ChatEmoji.SMIRK;
        }
        if (channel == ChatChannel.OOC) {
            return ChatEmoji.DISCORD;
        }
        if (channel == ChatChannel.PARTY) {
            return ChatEmoji.JOY;
        }
        if (channel == ChatChannel.ADMIN) {
            return ChatEmoji.EXPRESSIONLESS;
        }
        if (channel == ChatChannel.CONSOLE
                || channel == ChatChannel.SERVER_CONSOLE) {
            return ChatEmoji.CONSOLE;
        }
        if (channel == ChatChannel.WHISPER) {
            return ChatEmoji.BLUSH;
        }
        return ChatEmoji.SLIGHT_SMILE;
    }
}
