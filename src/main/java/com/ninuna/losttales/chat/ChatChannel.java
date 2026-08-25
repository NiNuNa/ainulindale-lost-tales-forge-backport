package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Small stable channel catalogue shared by packet validation and client UI.
 * Declaration order is storage surface (ordinals index client view state);
 * the order channels are presented in is {@link #presentationOrder()}.
 */
public enum ChatChannel {
    ALL("all", "Global", ChatIdentityType.CHARACTER,
            ChatRecipientRule.GLOBAL,
            LostTalesColors.rgb(LostTalesColors.FERN_GREEN)),
    PROXIMITY("proximity", "Proximity", ChatIdentityType.CHARACTER,
            ChatRecipientRule.PROXIMITY,
            LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN)),
    // Presentation shows the member's own party colour; this seafoam is
    // only the fallback outside a party.
    PARTY("party", "Party", ChatIdentityType.CHARACTER,
            ChatRecipientRule.PARTY,
            LostTalesColors.rgb(LostTalesColors.SEAFOAM)),
    // Presentation shows the sender's LOTR faction colour; this palette
    // honey is only the indicator/selector fallback.
    FACTION("faction", "Faction", ChatIdentityType.CHARACTER,
            ChatRecipientRule.FACTION,
            LostTalesColors.rgb(LostTalesColors.HONEY)),
    OOC("ooc", "OOC", ChatIdentityType.ACCOUNT,
            ChatRecipientRule.GLOBAL,
            LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE)),
    /** Staff channel: operators only, account identity; the wire id stays. */
    ADMIN("admin", "Operator", ChatIdentityType.ACCOUNT,
            ChatRecipientRule.OPERATORS,
            LostTalesColors.rgb(LostTalesColors.CRIMSON)),
    /**
     * The player's private console: what only they see anyway — command
     * output, fast-travel countdowns, other mods' notices — plus anything
     * they type there, which is echoed back to them alone.
     */
    CONSOLE("console", "Console", ChatIdentityType.ACCOUNT,
            ChatRecipientRule.SELF,
            LostTalesColors.rgb(LostTalesColors.MAUVE)),
    /**
     * A private conversation between two accounts. Not a tab of its own:
     * every whisper partner is one tab on this channel, and the client
     * keeps them apart by the partner's name.
     */
    WHISPER("whisper", "Whisper", ChatIdentityType.ACCOUNT,
            ChatRecipientRule.WHISPER,
            LostTalesColors.rgb(LostTalesColors.APRICOT)),
    /**
     * The server's Discord channel, bridged both ways by the server's
     * own bridge: account identity, everyone in the game reads it, and
     * the tab exists only while the server says the bridge is on.
     */
    DISCORD("discord", "Discord", ChatIdentityType.ACCOUNT,
            ChatRecipientRule.GLOBAL,
            LostTalesColors.rgb(LostTalesColors.STEEL_BLUE));

    /** Tab, indicator, and cycle order: the two global channels bracket
     *  the scoped role-play ones, Discord beside OOC as the other account
     *  conversation, then Party, staff, and the console. Whispers are not
     *  listed: their tabs exist per conversation. */
    private static final List<ChatChannel> PRESENTATION_ORDER =
            Collections.unmodifiableList(Arrays.asList(
                    ALL, PROXIMITY, FACTION, OOC, DISCORD, PARTY, ADMIN,
                    CONSOLE));

    private final String id;
    private final String displayName;
    private final ChatIdentityType identityType;
    private final ChatRecipientRule recipientRule;
    private final int displayColor;

    ChatChannel(String id, String displayName,
                ChatIdentityType identityType,
                ChatRecipientRule recipientRule, int displayColor) {
        this.id = id;
        this.displayName = displayName;
        this.identityType = identityType;
        this.recipientRule = recipientRule;
        this.displayColor = displayColor;
    }

    public String getId() { return this.id; }
    public String getDisplayName() { return this.displayName; }
    public ChatIdentityType getIdentityType() { return this.identityType; }
    public ChatRecipientRule getRecipientRule() { return this.recipientRule; }
    public int getDisplayColor() { return this.displayColor; }

    /** Every channel in the order the client presents them. */
    public static List<ChatChannel> presentationOrder() {
        return PRESENTATION_ORDER;
    }

    public static ChatChannel fromId(String id) {
        String normalized = id == null ? ""
                : id.trim().toLowerCase(Locale.ROOT);
        for (ChatChannel channel : values()) {
            if (channel.id.equals(normalized)) {
                return channel;
            }
        }
        return null;
    }
}
