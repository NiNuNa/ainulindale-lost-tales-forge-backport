package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import net.minecraft.client.Minecraft;

/**
 * How this client signs a line of its own. The server signs every line
 * it routes — name, colour, roles and skin — and nothing here is ever
 * sent or trusted by anyone else: the lines the client builds for
 * itself are the ones nobody else sees as such — a message shown before
 * the server's copy replaces it, the player's own half of an NPC
 * conversation, the echo of a command.
 *
 * <p>It follows the same rules the server signs by, so a locally built
 * line reads exactly like a served one: the appearance the tab
 * currently speaks as decides the name and the head, and the tab's
 * channel decides whether the roles are tagged and what colours the
 * name ({@link ChatRolePresentation}). The LOTR title is the server's
 * to resolve, so a locally signed line carries none.</p>
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
        ChatChannel channel = tab == null ? null : tab.getChannel();
        int roles = ChatRolePresentation.rolesShown(channel,
                ClientChatChannelState.getRoleMask());
        if (appearance == null || appearance.account
                || appearance.name.length() == 0) {
            return new Signature(account, account,
                    ChatRolePresentation.nameColor(channel, roles, true, 0),
                    "", roles, true);
        }
        return new Signature(appearance.name, account,
                ChatRolePresentation.nameColor(channel, roles, false,
                        factionColor(appearance)),
                appearance.skinId, roles, false);
    }

    /**
     * The character's own faction colour, the same source the server
     * reads a role-play line's name colour from; the chat's ivory when
     * the roster no longer holds the character.
     */
    private static int factionColor(
            ClientChatAppearances.Appearance appearance) {
        int ivory = ChatRolePresentation.unassignedColor();
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary summary = roster == null ? null
                : roster.getCharacter(appearance.characterId);
        return summary == null ? ivory
                : LotrFactionColors.forFactionId(
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
