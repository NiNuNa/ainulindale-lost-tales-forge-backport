package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Locale;

/** Small stable channel catalogue shared by packet validation and client UI. */
public enum ChatChannel {
    ALL("all", "All", ChatIdentityType.CHARACTER,
            ChatRecipientRule.GLOBAL,
            LostTalesColors.rgb(LostTalesColors.GREEN)),
    PROXIMITY("proximity", "Proximity", ChatIdentityType.CHARACTER,
            ChatRecipientRule.PROXIMITY,
            LostTalesColors.rgb(LostTalesColors.TEAL)),
    PARTY("party", "Party", ChatIdentityType.CHARACTER,
            ChatRecipientRule.PARTY,
            LostTalesColors.rgb(LostTalesColors.BLUE)),
    // Presentation shows the sender's LOTR faction colour; this palette
    // honey is only the indicator/selector fallback.
    FACTION("faction", "Faction", ChatIdentityType.CHARACTER,
            ChatRecipientRule.FACTION,
            LostTalesColors.rgb(LostTalesColors.HONEY)),
    OOC("ooc", "OOC", ChatIdentityType.ACCOUNT,
            ChatRecipientRule.GLOBAL,
            LostTalesColors.rgb(LostTalesColors.ORCHID));

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
