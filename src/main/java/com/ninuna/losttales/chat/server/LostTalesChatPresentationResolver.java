package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import com.ninuna.losttales.faction.FactionDemonyms;
import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.entity.player.EntityPlayerMP;

/** Reads optional LOTR presentation fields without making routing depend on them. */
final class LostTalesChatPresentationResolver {

    private LostTalesChatPresentationResolver() {}

    /**
     * How {@code character} — or, for null, the account — is shown on a
     * line or in a member list: its title, and its faction's colour and
     * people.
     */
    static Presentation resolve(EntityPlayerMP player,
                                RoleplayCharacter character) {
        String title = titleOf(player, character);
        int factionColor = LostTalesColors.rgb(
                LostTalesColors.HUD_LABEL);
        String factionName = "";
        if (character != null) {
            factionColor = LotrFactionColors.forFactionId(
                    character.getFactionId(), factionColor);
            // The epithet names the sender's people, which is not always
            // what the realm is called: a Lothlórien character is a
            // Galadhrim Miner. Formatting codes stay behind; the client
            // colours the title.
            String name = LotrCharacterAdapter.getInstance()
                    .getFactionDisplayName(character.getFactionId());
            String plain = ChatFormattingCodes.stripSectionCodes(name).trim();
            factionName = FactionDemonyms.of(
                    character.getFactionId(), plain);
        }
        // The faction explorer renders LOTRFaction#getFactionColor(). Keep
        // title and character name on that exact same RGB source.
        return new Presentation(title, factionColor, factionColor,
                factionName);
    }

    /**
     * The title a character shows after its name: LOTR's live one for the
     * character the player plays (and the account playing as itself), the
     * one its record kept for any other character the player speaks as.
     */
    private static String titleOf(EntityPlayerMP player,
                                  RoleplayCharacter character) {
        if (character != null && !character.getCharacterId().equals(
                ChatIdentitySelection.playedId(player))) {
            return character.getLotrTitle();
        }
        String live = LotrCharacterAdapter.getInstance().getTitleName(player);
        return live == null ? "" : live;
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
