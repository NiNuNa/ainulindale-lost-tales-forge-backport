package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.faction.FactionDemonyms;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.regex.Pattern;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRTitle;
import net.minecraft.entity.player.EntityPlayerMP;

/** Reads optional LOTR presentation fields without making routing depend on them. */
final class LostTalesChatPresentationResolver {
    /**
     * Vanilla's own formatting-code pattern. Not
     * {@code EnumChatFormatting.getTextWithoutFormattingCodes}: that is
     * {@code @SideOnly(CLIENT)} in 1.7.10 and does not exist on a
     * dedicated server.
     */
    private static final Pattern FORMATTING_CODES =
            Pattern.compile("(?i)§[0-9A-FK-OR]");

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
            // The epithet names the sender's people, which is not always
            // what the realm is called: a Lothlórien character is a
            // Galadhrim Miner. Formatting codes stay behind; the client
            // colours the title.
            String name = LotrCharacterAdapter.getInstance()
                    .getFactionDisplayName(character.getStartingFactionId());
            String plain = name == null ? ""
                    : FORMATTING_CODES.matcher(name).replaceAll("").trim();
            factionName = FactionDemonyms.of(
                    character.getStartingFactionId(), plain);
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
