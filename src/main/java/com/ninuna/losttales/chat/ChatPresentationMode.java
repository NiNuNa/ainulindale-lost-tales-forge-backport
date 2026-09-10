package com.ninuna.losttales.chat;

/**
 * How a channel's lines present their sender. The identity a line wears
 * is the player's choice; left unchosen it follows the mode, the
 * character being played in character and the account out of
 * character. The mode also decides how the identity reads: in
 * character, with no role worn and the name in the character's faction
 * colour, or out of character, with the primary role tagged and
 * colouring the name. {@link ChatRolePresentation} is the one place the
 * two are turned into tags and colours.
 */
public enum ChatPresentationMode {
    /** Global, Proximity, Party, Faction and whispers: roleplay conversation. */
    IN_CHARACTER,
    /** OOC &amp; Discord, Operator and the Console: talk about the game. */
    OUT_OF_CHARACTER
}
