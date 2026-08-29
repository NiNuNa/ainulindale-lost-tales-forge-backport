package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.client.Minecraft;

/**
 * How this client signs a line of its own. The server signs every line
 * it routes — name, colour, roles and skin — and nothing here is ever
 * sent or trusted by anyone else; the one line the client builds for
 * itself is the player's own half of an NPC conversation, which nobody
 * is on the other end of.
 *
 * <p>It follows the same rules the server signs by, so a conversation
 * with an NPC reads exactly like one with a player: the appearance the
 * tab currently speaks as decides the name, an account line takes its
 * roles and the colour they give it from {@link ChatAccountRole}, and a
 * character line takes its own faction's colour. The LOTR title is the
 * server's to resolve, so a locally signed line carries none.</p>
 */
final class ClientChatIdentity {

    private ClientChatIdentity() {}

    /** How the tab's current appearance signs a line built here. */
    static Signature of(ChatTab tab) {
        Minecraft minecraft = Minecraft.getMinecraft();
        String account = minecraft == null || minecraft.thePlayer == null
                ? "" : minecraft.thePlayer.getCommandSenderName();
        ClientChatAppearances.Appearance appearance =
                ClientChatAppearances.effectiveFor(tab);
        if (appearance == null || appearance.account
                || appearance.name.length() == 0) {
            int roles = ClientChatChannelState.getRoleMask();
            return new Signature(account, account,
                    ChatAccountRole.nameColor(roles), "", roles, true);
        }
        return new Signature(appearance.name, account,
                factionColor(appearance), appearance.skinId, 0, false);
    }

    /**
     * The character's own faction colour, the same source the server
     * reads a role-play line's name colour from; the chat's ivory when
     * the roster no longer holds the character.
     */
    private static int factionColor(
            ClientChatAppearances.Appearance appearance) {
        int ivory = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary summary = roster == null ? null
                : roster.getCharacter(appearance.characterId);
        return summary == null ? ivory
                : LotrCharacterAdapter.getInstance().getFactionColor(
                        summary.getStartingFactionId(), ivory);
    }

    /** The identity fields a locally built line is signed with. */
    static final class Signature {
        final String identityName;
        final String accountName;
        final int nameColor;
        final String skinId;
        final int roles;
        final boolean accountLine;

        private Signature(String identityName, String accountName,
                          int nameColor, String skinId, int roles,
                          boolean accountLine) {
            this.identityName = identityName;
            this.accountName = accountName;
            this.nameColor = nameColor & 0xFFFFFF;
            this.skinId = skinId;
            this.roles = roles;
            this.accountLine = accountLine;
        }
    }
}
