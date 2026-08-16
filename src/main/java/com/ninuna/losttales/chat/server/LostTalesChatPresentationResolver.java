package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRTitle;
import net.minecraft.entity.player.EntityPlayerMP;

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

        int factionColor = 0xFFFFFF;
        if (character != null) {
            factionColor = LotrCharacterAdapter.getInstance()
                    .getFactionColor(character.getStartingFactionId(),
                            factionColor);
        }
        // The faction explorer renders LOTRFaction#getFactionColor(). Keep
        // title and character name on that exact same RGB source.
        return new Presentation(title, factionColor, factionColor);
    }

    static final class Presentation {
        final String title;
        final int titleColor;
        final int nameColor;

        private Presentation(String title, int titleColor, int nameColor) {
            this.title = title == null ? "" : title;
            this.titleColor = titleColor & 0xFFFFFF;
            this.nameColor = nameColor & 0xFFFFFF;
        }
    }
}
