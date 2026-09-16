package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import com.ninuna.losttales.chat.ChatNarrator;
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
 * line reads exactly like a served one: the identity the tab
 * currently speaks as decides the name and the head, and the tab's
 * channel decides whether the roles are tagged and what colours the
 * name ({@link ChatRolePresentation}). The LOTR title is the server's
 * to resolve, so a locally signed line carries none.</p>
 */
final class ClientChatSignature {

    private ClientChatSignature() {}

    /** How the tab's current identity signs a line built here. */
    static Signature of(ChatTab tab) {
        Minecraft minecraft = Minecraft.getMinecraft();
        String account = minecraft == null || minecraft.thePlayer == null
                ? "" : minecraft.thePlayer.getCommandSenderName();
        ClientChatIdentities.Identity identity =
                ClientChatIdentities.effectiveFor(tab);
        ChatChannel channel = tab == null ? null : tab.getChannel();
        int roles = ChatRolePresentation.rolesShown(channel,
                statedRoleMask(identity));
        if (ClientChatIdentities.isNarrating()
                && ClientChatIdentities.speaksInCharacter(tab)) {
            // The Narrator's voice over the identity: its name, colour
            // and mark, on the line the identity would sign.
            return new Signature(ChatNarrator.NAME, account,
                    ChatNarrator.color(), ChatNarrator.SKIN_ID, roles, false);
        }
        if (identity == null || identity.account
                || identity.name.length() == 0) {
            return new Signature(account, account,
                    ChatRolePresentation.nameColor(channel, roles, true, 0),
                    "", roles, true);
        }
        return new Signature(identity.name, account,
                ChatRolePresentation.nameColor(channel, roles, false,
                        factionColor(identity)),
                identity.skinId, roles, false);
    }

    /**
     * The roles the identity wears, as the server stated them: an account
     * line the account's own, a character line the account's together
     * with that character's — a role given to an account is worn by every
     * character of it, one given to a character by that character alone.
     * A server that does not state the two apart gives one mask for the
     * character being played, which then stands for the account too, and
     * nothing for any other character; the served copy, signed with the
     * worn character's own roles, takes the line's place a moment later.
     */
    private static int statedRoleMask(
            ClientChatIdentities.Identity identity) {
        if (identity == null || identity.account
                || identity.characterId == null) {
            return ClientChatChannelState.getAccountRoleMask();
        }
        return ClientChatChannelState.ownCharacterRoles(identity.characterId);
    }

    /**
     * The character's own faction colour, the same source the server
     * reads a role-play line's name colour from; the chat's ivory when
     * the roster no longer holds the character.
     */
    private static int factionColor(
            ClientChatIdentities.Identity identity) {
        int ivory = ChatRolePresentation.unassignedColor();
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary summary = roster == null ? null
                : roster.getCharacter(identity.characterId);
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
