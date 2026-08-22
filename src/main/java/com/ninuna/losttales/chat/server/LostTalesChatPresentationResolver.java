package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.gui.style.LostTalesColors;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRTitle;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

/** Reads optional LOTR presentation fields without making routing depend on them. */
final class LostTalesChatPresentationResolver {

    private LostTalesChatPresentationResolver() {}

    static Presentation resolve(EntityPlayerMP player,
                                RoleplayCharacter character) {
        String title = "";
        try {
            LOTRPlayerData data = LOTRLevelData.getData(player);
            LOTRTitle.PlayerTitle playerTitle = data == null
                    ? null : data.getPlayerTitle();
            if (playerTitle != null && playerTitle.getTitle() != null) {
                title = playerTitle.getTitle().getDisplayName(player);
            }
        } catch (LinkageError ignored) {
            title = "";
        } catch (RuntimeException ignored) {
            title = "";
        }

        int factionColor = LostTalesColors.rgb(
                LostTalesColors.HUD_LABEL);
        String factionName = "";
        if (character != null) {
            factionColor = LotrCharacterAdapter.getInstance()
                    .getFactionColor(character.getStartingFactionId(),
                            factionColor);
            // LOTR's own faction name, as its NPC naming uses it; any
            // formatting codes stay behind, the client colours the title.
            String name = LotrCharacterAdapter.getInstance()
                    .getFactionDisplayName(character.getStartingFactionId());
            String plain = name == null ? null
                    : EnumChatFormatting.getTextWithoutFormattingCodes(name);
            factionName = plain == null ? "" : plain.trim();
        }
        // The faction explorer renders LOTRFaction#getFactionColor(). Keep
        // title and character name on that exact same RGB source.
        return new Presentation(title, factionColor, factionColor,
                factionName);
    }

    static final class Presentation {
        final String title;
        final int titleColor;
        final int nameColor;
        final String factionName;

        private Presentation(String title, int titleColor, int nameColor,
                             String factionName) {
            this.title = title == null ? "" : title;
            this.titleColor = titleColor & 0xFFFFFF;
            this.nameColor = nameColor & 0xFFFFFF;
            this.factionName = factionName == null ? "" : factionName;
        }
    }
}
