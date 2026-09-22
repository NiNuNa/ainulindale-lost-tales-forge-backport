package com.ninuna.losttales.chat;

/**
 * How a channel's lines present their sender. The identity a line wears
 * is the player's choice; left unchosen it follows the mode, the
 * character being played in character and the account out of
 * character. The mode also decides how the identity reads: in
 * character, with the name in the character's faction colour, or out of
 * character, with the name in the colour of the account's highest role.
 * {@link ChatRolePresentation} is the one place the two are turned into
 * colours, and the chat names the players a line mentions by the same
 * mode.
 */
public enum ChatPresentationMode {
    /** Global, Proximity, Party, Faction and whispers: roleplay conversation. */
    IN_CHARACTER,
    /** OOC, Operator and the two consoles: talk about the game. */
    OUT_OF_CHARACTER
}
