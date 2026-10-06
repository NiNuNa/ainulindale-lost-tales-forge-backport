package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import com.ninuna.losttales.faction.FactionDemonyms;
import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Reads optional LOTR presentation fields without making routing depend
 * on them. A title and a faction leave here as LOTR's lang key and the
 * faction's id, so each game names them in its own words; only what the
 * server writes in its own words, a post to Discord, names them here.
 */
final class LostTalesChatPresentationResolver {

    private LostTalesChatPresentationResolver() {}

    /**
     * How {@code character} — or, for null, the account — is shown on a
     * line or in a member list: its title, and its faction and its colour.
     */
    static Presentation resolve(EntityPlayerMP player,
                                RoleplayCharacter character) {
        String title = titleOf(player, character);
        int factionColor = LostTalesColors.rgb(
                LostTalesColors.HUD_LABEL);
        String factionId = "";
        if (character != null) {
            factionColor = LotrFactionColors.forFactionId(
                    character.getFactionId(), factionColor);
            factionId = character.getFactionId() == null ? ""
                    : character.getFactionId();
        }
        // The faction explorer renders LOTRFaction#getFactionColor(). Keep
        // title and character name on that exact same RGB source.
        return new Presentation(title, factionColor, factionColor,
                factionId);
    }

    /**
     * The title a character shows after its name, by LOTR's lang key:
     * LOTR's live one for the character the player plays (and the account
     * playing as itself), the one its record kept for any other character
     * the player speaks as.
     */
    private static String titleOf(EntityPlayerMP player,
                                  RoleplayCharacter character) {
        if (character != null && !character.getCharacterId().equals(
                ChatIdentitySelection.playedId(player))) {
            return character.getLotrTitle();
        }
        String live = LotrCharacterAdapter.getInstance().getTitleKey(player);
        return live == null ? "" : live;
    }

    /**
     * The people of a faction in this server's words, as a title names
     * them: a Lothlórien character is a Galadhrim Miner, not a Lothlórien
     * one ({@link FactionDemonyms}). Empty for no faction.
     */
    static String peopleOf(String factionId) {
        String normalized = LotrCharacterAdapter.normalizeFactionId(factionId);
        if (normalized.length() == 0) {
            return "";
        }
        String name = LotrCharacterAdapter.getInstance()
                .getFactionDisplayName(normalized);
        String plain = ChatFormattingCodes.stripSectionCodes(name).trim();
        return FactionDemonyms.of(normalized, plain);
    }

    static final class Presentation {
        /** The title's LOTR lang key; empty for none. */
        final String title;
        final int titleColor;
        final int nameColor;
        /** The faction's id; empty for the account. */
        final String factionId;

        private Presentation(String title, int titleColor, int nameColor,
                             String factionId) {
            this.title = title == null ? "" : title;
            this.titleColor = titleColor & 0xFFFFFF;
            this.nameColor = nameColor & 0xFFFFFF;
            this.factionId = factionId == null ? "" : factionId;
        }

        /**
         * {@code name} with its title in this server's words —
         * {@code Aldric, the Gondor Farmer} — for a post to Discord.
         */
        String titledName(String name) {
            return ChatEpithet.titledName(name, peopleOf(this.factionId),
                    ChatEpithet.titleName(this.title));
        }
    }
}
