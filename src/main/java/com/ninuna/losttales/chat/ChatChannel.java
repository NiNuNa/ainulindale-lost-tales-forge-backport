package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Small stable channel catalogue shared by packet validation and client UI.
 * Each constant is one built-in {@link ChatChannelDescriptor}: the id, how
 * its lines present their sender, the routing rule, the access a player
 * needs, the colour, and whether the Discord bridge may carry it — the
 * Party channel, the console and whispers are private and never leave
 * the game. The string ids are the wire and storage surface —
 * packets and the layout file carry them, and client view state is keyed
 * by tab identity — so an id is permanent while declaration order carries
 * no meaning of its own; the order channels are presented in is
 * {@link #presentationOrder()}.
 */
public enum ChatChannel {
    ALL("all", "Global", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.FERN_GREEN), true),
    PROXIMITY("proximity", "Proximity", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.PROXIMITY, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN), true),
    // Presentation shows the member's own party colour; this seafoam is
    // only the fallback outside a party.
    PARTY("party", "Party", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.PARTY, ChatChannelAccess.PARTY_MEMBERSHIP,
            LostTalesColors.rgb(LostTalesColors.SEAFOAM), false),
    // Presentation shows the sender's LOTR faction colour; this palette
    // honey is only the indicator/selector fallback.
    FACTION("faction", "Faction", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.FACTION, ChatChannelAccess.CHARACTER_FACTION,
            LostTalesColors.rgb(LostTalesColors.HONEY), true),
    /**
     * Out-of-character conversation, and the channel the Discord bridge
     * carries by default: out of character, everyone online reads it,
     * and it is there whether or not the server bridges anything. An
     * older build kept a Discord channel of its own beside it; its id
     * still resolves here, see {@link #fromId}.
     */
    OOC("ooc", "OOC & Discord", ChatPresentationMode.OUT_OF_CHARACTER,
            ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.STEEL_BLUE), true),
    /** Staff channel: operators only, out of character; the wire id stays. */
    ADMIN("admin", "Operator", ChatPresentationMode.OUT_OF_CHARACTER,
            ChatRecipientRule.OPERATORS, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.CRIMSON), true),
    /**
     * The player's private console: what only they see anyway — command
     * output, fast-travel countdowns, other mods' notices — plus anything
     * they type there, which is echoed back to them alone.
     */
    CONSOLE("console", "Console", ChatPresentationMode.OUT_OF_CHARACTER,
            ChatRecipientRule.SELF, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.MAUVE), false),
    /**
     * A private conversation between two players, in character. Not a tab of its own:
     * every whisper partner is one tab on this channel, and the client
     * keeps them apart by the partner's name.
     */
    WHISPER("whisper", "Whisper", ChatPresentationMode.IN_CHARACTER,
            ChatRecipientRule.WHISPER, ChatChannelAccess.NONE,
            LostTalesColors.rgb(LostTalesColors.APRICOT), false);

    /** Tab, indicator, and cycle order: the two global channels bracket
     *  the scoped role-play ones, then Party, staff, and the console.
     *  Whispers are not listed: their tabs exist per conversation. */
    private static final List<ChatChannel> PRESENTATION_ORDER =
            Collections.unmodifiableList(Arrays.asList(
                    ALL, PROXIMITY, FACTION, OOC, PARTY, ADMIN, CONSOLE));

    /**
     * The id an older build wrote for a channel that since became part
     * of another: {@code discord}, the Discord channel OOC &amp; Discord
     * took in. A layout file, a packet from an older client and a
     * configuration entry naming it all resolve to the channel that
     * took it in.
     */
    private static final String LEGACY_DISCORD_ID = "discord";

    private final ChatChannelDescriptor descriptor;

    ChatChannel(String id, String displayName,
                ChatPresentationMode presentation,
                ChatRecipientRule recipientRule,
                ChatChannelAccess access, int displayColor,
                boolean bridgeable) {
        this.descriptor = new ChatChannelDescriptor(id, displayName,
                presentation, recipientRule, access, displayColor,
                bridgeable);
    }

    public String getId() { return this.descriptor.getId(); }
    public String getDisplayName() { return this.descriptor.getDisplayName(); }
    /** How the channel's lines present their sender; see {@link ChatRolePresentation}. */
    public ChatPresentationMode getPresentation() {
        return this.descriptor.getPresentation();
    }
    public ChatRecipientRule getRecipientRule() {
        return this.descriptor.getRecipientRule();
    }
    public ChatChannelAccess getAccess() {
        return this.descriptor.getAccess();
    }
    public int getDisplayColor() { return this.descriptor.getDisplayColor(); }
    /** Whether the Discord bridge may carry this channel at all; see {@link ChatChannelDescriptor#isBridgeable}. */
    public boolean isBridgeable() { return this.descriptor.isBridgeable(); }
    /** The channel as the facts that describe it. */
    public ChatChannelDescriptor getDescriptor() { return this.descriptor; }

    /** Every channel in the order the client presents them. */
    public static List<ChatChannel> presentationOrder() {
        return PRESENTATION_ORDER;
    }

    /**
     * The channel an id names, an older build's id for a channel since
     * merged included; null for anything unknown.
     */
    public static ChatChannel fromId(String id) {
        String normalized = id == null ? ""
                : id.trim().toLowerCase(Locale.ROOT);
        for (ChatChannel channel : values()) {
            if (channel.getId().equals(normalized)) {
                return channel;
            }
        }
        return LEGACY_DISCORD_ID.equals(normalized) ? OOC : null;
    }
}
