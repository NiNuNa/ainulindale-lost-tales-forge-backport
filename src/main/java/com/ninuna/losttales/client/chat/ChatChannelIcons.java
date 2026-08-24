package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.compat.lotr.LotrFactionBannerResolver;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * The icon each tab wears before its name — on its tab, in the restore
 * menu, wherever the name stands alone: an emote per channel, for the
 * faction channel the LOTR banner of the active character's faction
 * (the Stewards' banner when the faction has none), for a whisper with
 * a player the partner's own head, exactly as their lines show it, and
 * for an NPC conversation the NPC's portrait as its speech showed it. A
 * whisper whose partner this client cannot place yet, an NPC whose
 * portrait has not been seen, or a faction tab without LOTR's banner
 * item wears an emote. Purely decorative: the catalogue names and ids
 * are untouched.
 */
final class ChatChannelIcons {
    /** Icons are drawn at the sheet's own sprite size, never scaled. */
    static final int SIZE = ChatEmoji.SPRITE_SIZE;
    /** Gap between the icon and the text. */
    static final int GAP = 3;
    /** A head is drawn as it is in the lines, centred in the icon's box. */
    private static final float HEAD_SIZE = 8.0F;
    /** Portraits remembered per NPC conversation; the oldest go first. */
    private static final int MAX_PORTRAITS = 64;
    private static final Map<ChatTab, String> NPC_PORTRAITS =
            new LinkedHashMap<ChatTab, String>();

    private ChatChannelIcons() {}

    /** Remembers the portrait an NPC's speech was drawn with, for its tab. */
    static synchronized void rememberNpcPortrait(ChatTab tab,
                                                 String texturePath) {
        if (tab == null || !tab.isNpc() || texturePath == null
                || texturePath.length() == 0) {
            return;
        }
        NPC_PORTRAITS.remove(tab);
        NPC_PORTRAITS.put(tab, texturePath);
        while (NPC_PORTRAITS.size() > MAX_PORTRAITS) {
            Iterator<ChatTab> oldest = NPC_PORTRAITS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    static synchronized String npcPortrait(ChatTab tab) {
        return tab == null ? null : NPC_PORTRAITS.get(tab);
    }

    /** Conversations end with the session, and so do their portraits. */
    static synchronized void forgetPortraits() {
        NPC_PORTRAITS.clear();
    }

    /**
     * Draws the tab's icon with its top-left at the point: the partner's
     * head for a whisper with a player the client can place, the tab's
     * emote otherwise.
     */
    static void draw(Minecraft minecraft, ChatTab tab, float x, float y,
                     int alpha) {
        if (minecraft == null || tab == null) {
            return;
        }
        float inset = (SIZE - HEAD_SIZE) / 2.0F;
        int shadow = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (tab.getChannel() == ChatChannel.FACTION) {
            ItemStack banner = LotrFactionBannerResolver.bannerFor(
                    ClientChatChannelState.activeFactionId());
            if (banner != null) {
                // The banner fills the icon box like a toolbar item does.
                ChatInlineIcons.drawItem(minecraft, banner, x, y, SIZE, alpha);
                return;
            }
        } else if (tab.isNpc()) {
            String portrait = npcPortrait(tab);
            if (portrait != null) {
                if (shadow > 0) {
                    LostTalesSilhouetteRenderState.begin(
                            LostTalesChatVisualStyle.SHADOW);
                    try {
                        LostTalesCharacterHeadIconRenderer.drawTintedNpcHeadBase(
                                minecraft, portrait,
                                x + inset + LostTalesChatVisualStyle.SHADOW_OFFSET,
                                y + inset + LostTalesChatVisualStyle.SHADOW_OFFSET,
                                HEAD_SIZE, 1.0F, 1.0F, 1.0F, shadow / 255.0F);
                    } finally {
                        LostTalesSilhouetteRenderState.end();
                    }
                }
                LostTalesCharacterHeadIconRenderer.drawNpcHead(minecraft,
                        portrait, x + inset, y + inset, HEAD_SIZE, 1.0F,
                        alpha / 255.0F);
                return;
            }
        } else if (tab.isWhisper()) {
            UUID partner = partnerId(minecraft, tab.getPartner());
            if (partner != null) {
                if (shadow > 0) {
                    LostTalesSilhouetteRenderState.begin(
                            LostTalesChatVisualStyle.SHADOW);
                    try {
                        LostTalesCharacterHeadIconRenderer
                                .drawTintedAccountHeadBase(minecraft, partner,
                                        x + inset
                                                + LostTalesChatVisualStyle
                                                .SHADOW_OFFSET,
                                        y + inset
                                                + LostTalesChatVisualStyle
                                                .SHADOW_OFFSET,
                                        HEAD_SIZE, 1.0F, 1.0F, 1.0F,
                                        shadow / 255.0F);
                    } finally {
                        LostTalesSilhouetteRenderState.end();
                    }
                }
                LostTalesCharacterHeadIconRenderer.drawAccountHead(minecraft,
                        partner, x + inset, y + inset, HEAD_SIZE, 1.0F,
                        alpha / 255.0F);
                return;
            }
        }
        ChatEmoji icon = iconOf(tab);
        if (icon != null) {
            ChatInlineIcons.drawEmoji(minecraft, icon, x, y, SIZE, alpha);
        }
    }

    /**
     * The account the name belongs to, from the players in the world or
     * the appearance the server syncs for every online player; null when
     * neither knows the name.
     */
    private static UUID partnerId(Minecraft minecraft, String name) {
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
            return ChatEmoji.GRIN;
        }
        if (tab.isWhisper()) {
            return ChatEmoji.SHY;
        }
        return iconOf(tab.getChannel());
    }

    static ChatEmoji iconOf(ChatChannel channel) {
        if (channel == null) {
            return null;
        }
        switch (channel) {
            case ALL:
                return ChatEmoji.SMILE;
            case PROXIMITY:
                return ChatEmoji.CALM;
            case FACTION:
                return ChatEmoji.SMUG;
            case OOC:
                return ChatEmoji.SILLY;
            case PARTY:
                return ChatEmoji.JOY;
            case ADMIN:
                return ChatEmoji.STARE;
            case CONSOLE:
                return ChatEmoji.CONFUSED;
            case WHISPER:
                return ChatEmoji.SHY;
            case DISCORD:
                return ChatEmoji.AWE;
            default:
                return ChatEmoji.SMILE;
        }
    }
}
