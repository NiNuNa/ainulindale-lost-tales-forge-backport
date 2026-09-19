package com.ninuna.losttales.client.chat;

import java.util.HashMap;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelIconSpec;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/**
 * Remembers the selected tab for this session. Server gates control visibility;
 * membership channels remain visible but read-only without a membership.
 * All roleplaying conversations follow the shared chat identity.
 */
public final class ClientChatChannelState {
    /** How often an unavailable faction-name lookup is retried. */
    private static final long FACTION_NAME_RETRY_NANOS = 5000L * 1000000L;

    private static ChatTab selected = ChatTab.of(ChatChannel.ALL);
    /** Conversations remembered for their partner's colour; oldest go first. */
    private static final int MAX_PARTNER_COLORS = 64;
    private static final LinkedHashMap<ChatTab, Integer> PARTNER_COLORS =
            new LinkedHashMap<ChatTab, Integer>();
    /**
     * How a conversation's tab names its partner: the identity their
     * last line wore, the account in brackets behind it when the two
     * differ. Remembered like the colours, from their lines alone.
     */
    private static final LinkedHashMap<ChatTab, String> PARTNER_NAMES =
            new LinkedHashMap<ChatTab, String>();
    /**
     * The id of the character a conversation is with, when the server
     * has said; a reply is then addressed by it. Remembered like the
     * names, from the conversation's lines alone.
     */
    private static final LinkedHashMap<ChatTab, UUID> PARTNER_CHARACTER_IDS =
            new LinkedHashMap<ChatTab, UUID>();
    private static String cachedFactionId = "";
    private static String cachedFactionName = "";
    private static long cachedFactionNanos;
    /** Server-stated operator status; the Admin tab exists only with it. */
    private static boolean adminAccess;
    /**
     * Every capability the server says this player holds, by id. The
     * menus ask this rather than a flag of their own, so a capability
     * the code gains later needs no new field here.
     */
    private static java.util.Set<String> capabilities =
            java.util.Collections.emptySet();
    /** The server's word on whether this player may moderate the chat. */
    private static boolean canModerate;
    /** The server's word on whether this player may edit its settings. */
    private static boolean canEditServerConfig;
    /** Server-stated roles of this player; what {@code @Operator} reaches. */
    private static int roleMask;
    /**
     * The account's own roles apart from the character being played, and
     * the roles assigned to each of this player's own characters, as the
     * server stated them.
     */
    private static int accountRoleMask;
    private static final Map<UUID, Integer> CHARACTER_ROLES =
            new HashMap<UUID, Integer>();
    /** The server's Proximity radius in blocks; zero until it says. */
    private static int proximityRadius;
    /**
     * The gates before the server's first word: what a fresh server file
     * states, read for a player with no role — the Operator channel
     * closed, everything else open. Once the access packet arrives the
     * server's own answer replaces them.
     */
    private static final java.util.Set<String> DEFAULT_READABLE =
            seededGates(true);
    private static final java.util.Set<String> DEFAULT_SENDABLE =
            seededGates(false);
    /**
     * The channels the server last said this player may read and send
     * into, by channel id. Ids rather than positions: a channel's id is
     * its wire surface, while the order the constants are declared in
     * carries no meaning and is not sent.
     */
    private static java.util.Set<String> readableChannels = DEFAULT_READABLE;
    private static java.util.Set<String> sendableChannels = DEFAULT_SENDABLE;
    /** Server-stated muted senders; filled for operators only. */
    private static final java.util.Set<UUID> MUTED_SENDERS =
            new java.util.HashSet<UUID>();
    /**
     * Server-stated online role holders, account name to mask, in the
     * order the server listed them: what the role hover card names its
     * members from. Replaced whole with every access packet.
     */
    private static final LinkedHashMap<String, Integer> ROLE_HOLDERS =
            new LinkedHashMap<String, Integer>();
    /**
     * Each roster holder's account roles apart from what it wears as the
     * identity it plays, and the character it plays, by account name in
     * lower case.
     */
    private static final Map<String, Integer> ROLE_HOLDER_ACCOUNT_ROLES =
            new HashMap<String, Integer>();
    private static final Map<String, UUID> ROLE_HOLDER_CHARACTERS =
            new HashMap<String, UUID>();
    /**
     * Unsent text per tab, kept across closing and reopening the chat
     * screen: oldest first, bounded, whispers included.
     */
    private static final Map<ChatTab, String> DRAFTS =
            new LinkedHashMap<ChatTab, String>();
    private static final int MAX_DRAFTS = 64;
    /** What was sent from each tab, for the arrows to recall there. */
    private static final ChatSentHistory SENT_HISTORY = new ChatSentHistory();

    private ClientChatChannelState() {}

    public static synchronized ChatTab getSelected() {
        ensureAvailable();
        return selected;
    }

    public static synchronized ChatChannel getSelectedChannel() {
        return getSelected().getChannel();
    }

    public static synchronized void select(ChatTab tab) {
        selected = isSelectable(tab) ? tab : fallbackTab();
    }

    public static synchronized void select(ChatChannel channel) {
        select(ChatTab.of(channel));
    }

    /** Next available tab of the selected tab's window, in row order. */
    public static synchronized ChatTab cycle() {
        return cycle(1);
    }

    /** The previous one, for walking the row the other way. */
    public static synchronized ChatTab cycleBack() {
        return cycle(-1);
    }

    /**
     * The tab {@code step} places along the selected tab's window, in
     * row order; a selection without a window walks every open tab.
     */
    private static synchronized ChatTab cycle(int step) {
        ChatTab current = getSelected();
        List<ChatTab> order = selectedWindowOrder(current);
        int index = Math.max(0, order.indexOf(current));
        selected = order.get(
                ((index + step) % order.size() + order.size())
                        % order.size());
        return selected;
    }

    /**
     * The tab at {@code ordinal} (from one) along the selected tab's
     * window, the way a browser's Ctrl+1 to Ctrl+8 reach its tabs; nine
     * is always the last tab, whatever the row holds. A number past the
     * row leaves the selection where it is.
     */
    public static synchronized ChatTab selectOrdinal(int ordinal) {
        ChatTab current = getSelected();
        if (ordinal < 1) {
            return current;
        }
        List<ChatTab> order = selectedWindowOrder(current);
        int index = ordinal >= 9 ? order.size() - 1 : ordinal - 1;
        if (index >= order.size()) {
            return current;
        }
        selected = order.get(index);
        return selected;
    }

    /**
     * The tabs of the selected tab's window in row order, those the
     * player may use; every open tab when the selection has no window,
     * and Global when nothing is open at all. Never empty.
     */
    private static List<ChatTab> selectedWindowOrder(ChatTab current) {
        ChatWindow window = ChatWindowLayout.windowOf(current);
        List<ChatTab> order = new ArrayList<ChatTab>();
        if (window != null) {
            for (ChatTab tab : window.getTabs()) {
                if (isAvailable(tab)) {
                    order.add(tab);
                }
            }
        }
        if (order.isEmpty()) {
            order = getOpenTabs();
        }
        if (order.isEmpty()) {
            order.add(ChatTab.of(ChatChannel.ALL));
        }
        return order;
    }

    /**
     * Next (or previous) open tab across every window, in window and
     * tab order — the keyboard's way from one window to another.
     */
    public static synchronized ChatTab cycleAll(boolean backward) {
        ChatTab current = getSelected();
        List<ChatTab> order = getOpenTabs();
        if (order.isEmpty()) {
            return current;
        }
        int index = order.indexOf(current);
        int step = backward ? -1 : 1;
        selected = order.get(
                ((index < 0 ? 0 : index) + step + order.size())
                        % order.size());
        return selected;
    }

    /** Available channels in presentation order (plain tabs only). */
    public static synchronized List<ChatChannel> getAvailableChannels() {
        ArrayList<ChatChannel> result = new ArrayList<ChatChannel>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (isAvailable(channel)) {
                result.add(channel);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Available tabs that are open in some window, in window and tab
     * order.
     */
    public static synchronized List<ChatTab> getOpenTabs() {
        ArrayList<ChatTab> result = new ArrayList<ChatTab>();
        for (ChatTab tab : ChatWindowLayout.order()) {
            if (isAvailable(tab)) {
                result.add(tab);
            }
        }
        return result;
    }

    /** The channels of the open, available tabs, in window and tab order. */
    public static synchronized List<ChatChannel> getOpenChannels() {
        ArrayList<ChatChannel> result = new ArrayList<ChatChannel>();
        for (ChatTab tab : getOpenTabs()) {
            result.add(tab.getChannel());
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized void ensureAvailable() {
        if (!isSelectable(selected)) {
            selected = fallbackTab();
        }
    }

    /** Available to this player and open in a window. */
    public static synchronized boolean isSelectable(ChatTab tab) {
        return isAvailable(tab) && ChatWindowLayout.isOpen(tab);
    }

    public static synchronized boolean isSelectable(ChatChannel channel) {
        return isSelectable(ChatTab.of(channel));
    }

    /**
     * Whether the player may close the tab: it is open and its window is
     * unlocked. Nothing is held back — the last tab of the last window
     * closes like any other, and the screen shows its empty state.
     */
    public static synchronized boolean isClosable(ChatTab tab) {
        return ChatWindowLayout.isClosable(tab);
    }

    /**
     * Closes the tab under {@link #isClosable} and moves the selection
     * off it if it was selected: onto its neighbour in the same window,
     * so the input stays where the player was working and no other
     * window comes forward for it; only a window emptied by the close
     * hands the selection elsewhere. Closing never mutes: the channel
     * keeps receiving and keeps its own mute setting.
     */
    public static synchronized boolean close(ChatTab tab) {
        ChatWindow window = ChatWindowLayout.windowOf(tab);
        int index = window == null ? -1 : window.getTabs().indexOf(tab);
        boolean wasSelected = tab != null && tab.equals(selected);
        if (!isClosable(tab) || !ChatWindowLayout.close(tab)) {
            return false;
        }
        if (wasSelected) {
            ChatTab neighbour = neighbourIn(window, index);
            selected = neighbour != null ? neighbour : fallbackTab();
        }
        ensureAvailable();
        return true;
    }

    /**
     * The tab that takes a closed tab's place in its window: the one now
     * standing where it stood, else the last, else any selectable one;
     * null when the window is gone or holds nothing selectable.
     */
    private static ChatTab neighbourIn(ChatWindow window, int index) {
        if (window == null || index < 0) {
            return null;
        }
        List<ChatTab> tabs = window.getTabs();
        if (tabs.isEmpty()) {
            return null;
        }
        ChatTab nearest = tabs.get(Math.min(index, tabs.size() - 1));
        if (isSelectable(nearest)) {
            return nearest;
        }
        for (ChatTab candidate : tabs) {
            if (isSelectable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Whether the tab's history is readable and its tab shown. A
     * conversation is shown only while the chat is being read as the
     * identity it is held as: what the player said as one character is
     * not on screen while they read as another. Its lines are still
     * filed and counted, and it shows again the moment that identity is
     * read as again. An NPC conversation belongs to nobody in particular
     * and is always shown; so is every plain channel, a scoped one
     * included — one row entry, showing the conversation being read.
     */
    public static synchronized boolean isAvailable(ChatTab tab) {
        if (tab == null || !isAvailable(tab.getChannel())) {
            return false;
        }
        if (tab.isNpc() || tab.getOwnerKey().length() == 0) {
            // A row entry stands for whichever conversation is read.
            return true;
        }
        // A conversation held as one identity shows while that identity
        // is read: a whisper by the identity itself, a scoped channel by
        // the conversation that identity is in.
        return tab.isWhisper()
                ? tab.getOwnerKey().equals(ClientChatIdentities.viewIdentityKey())
                : tab.getOwnerKey().equals(scopeKeyRead(tab.getChannel()));
    }

    /**
     * Whether any window has a tab the player can currently see. The one
     * question the chat screen asks to tell its two states apart: with
     * windows it draws them and takes input for the selected tab, and
     * without it shows its empty state. Channels exist either way.
     */
    public static synchronized boolean hasVisibleWindow() {
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            if (isVisible(windows.get(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the window has a tab the player can currently see. One
     * that has not is not drawn and is in nothing's way until one of its
     * channels becomes available.
     */
    public static synchronized boolean isVisible(ChatWindow window) {
        if (window == null) {
            return false;
        }
        List<ChatTab> tabs = window.getTabs();
        for (int index = 0; index < tabs.size(); index++) {
            if (isAvailable(tabs.get(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the channel's tab is shown and its history readable: every
     * open channel always, a gated one while its access is held, and
     * Party only while the selected identity is in a party.
     */
    public static synchronized boolean isAvailable(ChatChannel channel) {
        if (channel == null || !isGateOpen(readableChannels, channel)) {
            return false;
        }
        return channel.getAccess() != ChatChannelAccess.PARTY_MEMBERSHIP
                || ClientChatIdentitySelection.partyKey().length() > 0;
    }

    /** Whether the server's gate for this player lets the channel be used. */
    private static boolean isGateOpen(java.util.Set<String> gates,
                                      ChatChannel channel) {
        return gates.contains(channel.getId());
    }

    /**
     * The channels the seeded gates leave open to a player with no role.
     * The Server Console is left out whatever the gates say: a
     * capability opens it, and only the server knows who holds one, so
     * its tab waits for the server's word rather than showing for a
     * frame to everybody.
     */
    private static java.util.Set<String> seededGates(boolean read) {
        ChatChannelGates seeded = ChatRoleConfig.parseGates(
                new String[] {ChatRoleConfig.DEFAULT_ADMIN_GATE},
                ChatRoleCatalog.builtIn(), ChatRoleConfig.SILENT);
        java.util.Set<String> open = new java.util.HashSet<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            boolean allowed = (read ? seeded.canRead(0, channel)
                    : seeded.canSend(0, channel))
                    && channel.getRecipientRule()
                            != ChatRecipientRule.CONSOLE_READERS;
            if (allowed) {
                open.add(channel.getId());
            }
        }
        return java.util.Collections.unmodifiableSet(open);
    }

    /**
     * The server's word on the channels this player may read and send
     * into, by id. A channel the server did not name is closed: a client
     * never decides a gate for itself, and a channel this build does not
     * know is not one it can show anyway.
     */
    public static synchronized void setChannelGates(
            java.util.Collection<String> readable,
            java.util.Collection<String> sendable) {
        readableChannels = idSet(readable);
        sendableChannels = idSet(sendable);
        ensureAvailable();
    }

    private static java.util.Set<String> idSet(
            java.util.Collection<String> ids) {
        java.util.Set<String> copy = new java.util.HashSet<String>();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && id.length() > 0) {
                    copy.add(id.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        return java.util.Collections.unmodifiableSet(copy);
    }

    public static synchronized boolean canSend(ChatTab tab) {
        return isAvailable(tab) && canSend(tab.getChannel());
    }

    /**
     * Sending requires the server's send gate on a channel that is shown:
     * role gates, the Operator channel's included, are answered by the
     * sendable mask the server sent, and membership by the tab being
     * there at all. Every identity is in a faction, Unaligned by default,
     * so the Faction channel asks nothing more.
     */
    public static synchronized boolean canSend(ChatChannel channel) {
        return channel != null && isGateOpen(sendableChannels, channel)
                && isAvailable(channel);
    }

    /**
     * A conversation is named in the colour the other party speaks in —
     * an NPC's faction colour, a player's own name colour — so the tab,
     * its icon and its lines read as one. The colour their last line wore
     * is the surest; before they have said anything this session a
     * player's conversation takes the colour their name wears in
     * character — the faction's of the character it is with, as the
     * client knows it, or the plain ivory of an account — never a colour
     * of the channel's own (Nils, 2026-09-19: "Whispers should be the
     * colour of the person that you are talking to"). Every other tab
     * takes its channel's colour.
     */
    public static synchronized int displayColor(ChatTab tab) {
        if (tab == null) {
            return LostTalesChatVisualStyle.IVORY;
        }
        Integer partner = PARTNER_COLORS.get(ChatTab.row(tab));
        if (partner != null) {
            return partner.intValue();
        }
        if (tab.isWhisper() && !tab.isNpc()) {
            return partnerNameColor(tab);
        }
        return displayColor(tab.getChannel());
    }

    /**
     * The colour a player's name wears in character, for the identity a
     * conversation is with: the faction colour of that character where
     * the client holds its appearance — by its id, or else by its name
     * among the partner's — and the chat's plain ivory for an account, or
     * for a character the client cannot place.
     */
    private static int partnerNameColor(ChatTab tab) {
        int plain = com.ninuna.losttales.chat.ChatRolePresentation
                .unassignedColor();
        UUID characterId = PARTNER_CHARACTER_IDS.get(ChatTab.row(tab));
        String identity = tab.getPartnerIdentity();
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance == null || !appearance.hasCharacter()) {
                continue;
            }
            boolean same = characterId != null
                    ? characterId.equals(appearance.getCharacterId())
                    : tab.getPartner().equalsIgnoreCase(
                            appearance.getAccountName())
                            && identity != null
                            && identity.equalsIgnoreCase(
                                    appearance.getCharacterName());
            if (same) {
                return LotrFactionColors.forFactionId(
                        appearance.getStartingFactionId(), plain);
            }
        }
        return plain;
    }

    /**
     * Remembers the colour the other party's name is drawn in, for the
     * conversation's own tab. Only their lines say it: the player's own
     * copy of a whisper carries the player's colour, not theirs.
     */
    public static synchronized void rememberPartnerColor(ChatTab tab,
                                                         int color) {
        if (tab == null || (!tab.isWhisper() && !tab.isNpc())) {
            return;
        }
        PARTNER_COLORS.put(ChatTab.row(tab), Integer.valueOf(color & 0xFFFFFF));
        while (PARTNER_COLORS.size() > MAX_PARTNER_COLORS) {
            Iterator<ChatTab> oldest = PARTNER_COLORS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Remembers the identity the partner's last line wore, so the
     * person's tab names them the way they speak: the character's name
     * alone, never the account behind it.
     */
    public static synchronized void rememberPartnerName(ChatTab tab,
                                                        String identityName) {
        if (tab == null || !tab.isWhisper() || identityName == null
                || identityName.length() == 0) {
            return;
        }
        PARTNER_NAMES.put(ChatTab.row(tab), identityName);
        while (PARTNER_NAMES.size() > MAX_PARTNER_COLORS) {
            Iterator<ChatTab> oldest = PARTNER_NAMES.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Remembers which character of the other party a conversation is
     * with; null forgets, for a conversation with their account.
     */
    public static synchronized void rememberPartnerCharacterId(ChatTab tab,
                                                               UUID characterId) {
        if (tab == null || !tab.isWhisper() || tab.isNpc()) {
            return;
        }
        if (characterId == null) {
            PARTNER_CHARACTER_IDS.remove(ChatTab.row(tab));
            return;
        }
        PARTNER_CHARACTER_IDS.put(ChatTab.row(tab), characterId);
        while (PARTNER_CHARACTER_IDS.size() > MAX_PARTNER_COLORS) {
            Iterator<ChatTab> oldest = PARTNER_CHARACTER_IDS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The id of the character a conversation is with; null for their account, or unknown. */
    public static synchronized UUID partnerCharacterIdOf(ChatTab tab) {
        return tab == null ? null : PARTNER_CHARACTER_IDS.get(ChatTab.row(tab));
    }

    public static synchronized int displayColor(ChatChannel channel) {
        if (channel == null) {
            return LostTalesChatVisualStyle.IVORY;
        }
        if (channel == ChatChannel.FACTION) {
            String factionId = wornFactionId(channel);
            return factionId.length() == 0 ? channel.getDisplayColor()
                    : LotrFactionColors.forFactionId(factionId,
                            channel.getDisplayColor());
        }
        if (channel == ChatChannel.PARTY) {
            // The party speaks in the colour the player wears in it —
            // the one the party HUD and management screen show them in.
            return ClientChatIdentitySelection.partyColor();
        }
        return channel.getDisplayColor();
    }

    /** Visible label for a tab: the partner's name for a whisper —
     *  the identity their last line wore, when one is remembered. */
    public static synchronized String displayName(ChatTab tab) {
        if (tab == null) {
            return "";
        }
        if (tab.isWhisper()) {
            String remembered = PARTNER_NAMES.get(ChatTab.row(tab));
            // The identity is what the conversation is with; the account
            // behind it is never shown beside it.
            return remembered != null ? remembered
                    : tab.getPartnerIdentity();
        }
        return displayName(tab.getChannel());
    }

    /**
     * Visible label for a channel. Faction shows the LOTR faction name
     * ("Gondor") of the identity its tab speaks as, so the tab, indicator
     * and message prefix all agree and follow the chat identity; the
     * logical channel id is untouched. The LOTR lookup is cached per
     * faction id, and an unavailable lookup is retried on an interval
     * rather than every frame, falling back to the catalogue name.
     * Party shows its leader's name ("Aldric's Party") while the chat
     * identity is in one, since a party has no name of its own.
     */
    public static synchronized String displayName(ChatChannel channel) {
        if (channel == null) {
            return "";
        }
        if (channel == ChatChannel.PARTY) {
            String leader = ClientChatIdentitySelection.partyLeader();
            return leader.length() == 0 ? channel.getDisplayName()
                    : StatCollector.translateToLocalFormatted(
                            "gui.losttales.chat.party.named", leader);
        }
        if (channel != ChatChannel.FACTION) {
            return channel.getDisplayName();
        }
        String factionId = wornFactionId(channel);
        if (factionId.length() == 0) {
            return channel.getDisplayName();
        }
        if (LotrCharacterAdapter.UNALIGNED_FACTION_ID.equals(factionId)) {
            // Unaligned's name is this mod's own lang entry, since LOTR
            // ships none; nothing to ask LOTR for.
            return StatCollector.translateToLocal("lotr.faction.UNALIGNED.name");
        }
        long now = System.nanoTime();
        if (!factionId.equals(cachedFactionId)
                || (cachedFactionName.length() == 0
                        && now - cachedFactionNanos
                        > FACTION_NAME_RETRY_NANOS)) {
            String name = LotrCharacterAdapter.getInstance()
                    .getFactionDisplayName(factionId);
            String plain = name == null ? null
                    : EnumChatFormatting.getTextWithoutFormattingCodes(name);
            cachedFactionId = factionId;
            cachedFactionName = plain == null ? "" : plain.trim();
            cachedFactionNanos = now;
        }
        return cachedFactionName.length() == 0
                ? channel.getDisplayName() : cachedFactionName;
    }

    /**
     * The roles the server says this player holds. Only used to notice
     * that a role mention was addressed to this client: nothing here
     * grants anything, and the server never reads it back.
     */
    public static synchronized void setRoleMask(int mask) {
        roleMask = ChatAccountRole.isValidMask(mask) ? mask : 0;
    }

    public static synchronized int getRoleMask() {
        return roleMask;
    }

    /**
     * The server's statement of the roles apart by identity: the
     * account's own, and each of this player's own characters' own.
     */
    public static synchronized void setRoleSplit(int accountMask,
            Map<UUID, Integer> characterRoles) {
        accountRoleMask = ChatAccountRole.isValidMask(accountMask)
                ? accountMask : 0;
        CHARACTER_ROLES.clear();
        if (characterRoles != null) {
            for (Map.Entry<UUID, Integer> entry : characterRoles.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && ChatAccountRole.isValidMask(
                                entry.getValue().intValue())) {
                    CHARACTER_ROLES.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * The roles this player's account wears on its own: what an account
     * line is signed with, and what every character of the account wears
     * too.
     */
    public static synchronized int getAccountRoleMask() {
        return accountRoleMask;
    }

    /**
     * The roles one of this player's own characters wears: the account's
     * together with those assigned to that character.
     */
    public static synchronized int ownCharacterRoles(UUID characterId) {
        Integer own = characterId == null ? null
                : CHARACTER_ROLES.get(characterId);
        return accountRoleMask | (own == null ? 0 : own.intValue());
    }

    /** The server's Proximity radius, for how far speech bubbles reach. */
    public static synchronized void setProximityRadius(int radius) {
        proximityRadius = Math.max(0, radius);
    }

    /** The icons the server puts on its channels; see {@link ChatChannelIcons#install}. */
    public static void setChannelIcons(Map<String, ChatChannelIconSpec> icons) {
        ChatChannelIcons.install(icons);
    }

    /** The server's Proximity radius in blocks; zero until it says. */
    public static synchronized int getProximityRadius() {
        return proximityRadius;
    }

    /** The roles this player holds, in precedence order. */
    public static synchronized List<ChatAccountRole> localRoles() {
        return ChatAccountRole.fromMask(roleMask);
    }

    /**
     * Replaces the server's statement of who is muted; empty for anyone
     * but an operator, whose menus offer to lift a mute in force and to
     * lay one where there is none.
     */
    public static synchronized void setMutedSenders(
            java.util.Collection<UUID> senders) {
        MUTED_SENDERS.clear();
        if (senders != null) {
            for (UUID sender : senders) {
                if (sender != null) {
                    MUTED_SENDERS.add(sender);
                }
            }
        }
    }

    /** Whether the server said this sender is under a mute. */
    public static synchronized boolean isMutedSender(UUID sender) {
        return sender != null && MUTED_SENDERS.contains(sender);
    }

    /** Replaces the online role roster with the server's statement. */
    public static synchronized void setRoleHolders(
            Map<String, Integer> holders) {
        setRoleHolders(holders, null, null);
    }

    /**
     * Replaces the online role roster with the server's statement, each
     * holder's own account roles and played character with it; a holder
     * missing from {@code accountRoles} is taken to hold its roles as the
     * account.
     */
    public static synchronized void setRoleHolders(
            Map<String, Integer> holders, Map<String, Integer> accountRoles,
            Map<String, UUID> characters) {
        ROLE_HOLDER_ACCOUNT_ROLES.clear();
        ROLE_HOLDER_CHARACTERS.clear();
        if (accountRoles != null) {
            for (Map.Entry<String, Integer> entry : accountRoles.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    ROLE_HOLDER_ACCOUNT_ROLES.put(rosterKey(entry.getKey()),
                            entry.getValue());
                }
            }
        }
        if (characters != null) {
            for (Map.Entry<String, UUID> entry : characters.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    ROLE_HOLDER_CHARACTERS.put(rosterKey(entry.getKey()),
                            entry.getValue());
                }
            }
        }
        ROLE_HOLDERS.clear();
        if (holders != null) {
            for (Map.Entry<String, Integer> entry : holders.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && entry.getKey().trim().length() > 0
                        && ChatAccountRole.isValidMask(
                                entry.getValue().intValue())
                        && entry.getValue().intValue() != 0) {
                    ROLE_HOLDERS.put(entry.getKey().trim(),
                            entry.getValue());
                }
            }
        }
    }

    /** Online accounts holding the role, in the server's order. */
    public static synchronized List<String> roleHolders(
            ChatAccountRole role) {
        if (role == null || role.isNone()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : ROLE_HOLDERS.entrySet()) {
            if ((entry.getValue().intValue() & role.bit()) == 0) {
                continue;
            }
            // A role the account holds is the account's; one only the
            // played character holds is that character's, named as the
            // character when this client knows its name.
            String key = rosterKey(entry.getKey());
            Integer account = ROLE_HOLDER_ACCOUNT_ROLES.get(key);
            String character = account == null
                    || (account.intValue() & role.bit()) != 0 ? null
                    : ChatMentionColors.characterNameOf(
                            ROLE_HOLDER_CHARACTERS.get(key));
            names.add(character != null ? character : entry.getKey());
        }
        return names;
    }

    /**
     * The roles the roster says an online account holds; zero for one it
     * does not list. Case-insensitive, like every account-name match.
     */
    public static synchronized int rosterRolesOf(String account) {
        if (account == null || account.trim().length() == 0) {
            return 0;
        }
        for (Map.Entry<String, Integer> entry : ROLE_HOLDERS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(account.trim())) {
                return entry.getValue().intValue();
            }
        }
        return 0;
    }

    /**
     * The roles the roster says an online account holds on its own, apart
     * from the character it plays; zero for one it does not list.
     */
    public static synchronized int rosterAccountRolesOf(String account) {
        int worn = rosterRolesOf(account);
        if (worn == 0) {
            return 0;
        }
        Integer own = ROLE_HOLDER_ACCOUNT_ROLES.get(rosterKey(account));
        return own == null ? worn : own.intValue() & worn;
    }

    /**
     * The roles the roster says one identity of an online account wears:
     * the character it is playing wears everything the roster lists, any
     * other character the account's own roles alone — the only ones this
     * client can know for it.
     */
    public static synchronized int rosterRolesOf(String account,
                                                 UUID characterId) {
        if (characterId == null) {
            return rosterAccountRolesOf(account);
        }
        UUID played = account == null ? null
                : ROLE_HOLDER_CHARACTERS.get(rosterKey(account));
        return characterId.equals(played) ? rosterRolesOf(account)
                : rosterAccountRolesOf(account);
    }

    /** An account name as the roster's side tables key it. */
    private static String rosterKey(String account) {
        return account.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Applies the server's statement of Operator-channel access. */
    public static synchronized void setAdminAccess(boolean access) {
        adminAccess = access;
        ensureAvailable();
    }

    public static synchronized boolean hasAdminAccess() {
        return adminAccess;
    }

    /** Applies the server's statement of whether this player may moderate. */
    public static synchronized void setCanModerate(boolean allowed) {
        canModerate = allowed;
    }

    /**
     * Whether the moderation menus — mute, unmute, remove anyone's
     * message — are offered. The server said so with the access, and
     * decides again on every request.
     */
    public static synchronized boolean canModerate() {
        return canModerate;
    }

    /**
     * States which capabilities the player holds. A server that named
     * none — an older one, whose payload carried the two flags alone —
     * leaves the flags to speak for themselves.
     */
    public static synchronized void setCapabilities(
            java.util.Collection<String> held) {
        java.util.Set<String> ids = new java.util.HashSet<String>();
        if (held != null) {
            for (String id : held) {
                if (id != null && id.trim().length() > 0) {
                    ids.add(id.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        capabilities = java.util.Collections.unmodifiableSet(ids);
    }

    /** Whether the server said this player holds the capability. */
    public static synchronized boolean holds(
            com.ninuna.losttales.permission.LostTalesCapability capability) {
        return capability != null && capabilities.contains(capability.getId());
    }

    /** Applies the server's statement of whether this player may edit its settings. */
    public static synchronized void setCanEditServerConfig(boolean allowed) {
        canEditServerConfig = allowed;
    }

    /** Whether the Server Settings button is offered; the server decides again on the request. */
    public static synchronized boolean canEditServerConfig() {
        return canEditServerConfig;
    }

    /**
     * Remembers the selected tab's unsent input so closing the screen or
     * switching tabs does not lose it.
     */
    public static synchronized void setDraft(String text) {
        setDraft(selected, text);
    }

    /** Remembers a tab's unsent input; empty text forgets it. */
    public static synchronized void setDraft(ChatTab tab, String text) {
        if (tab == null) {
            return;
        }
        String value = text == null ? "" : text;
        if (value.length() == 0) {
            DRAFTS.remove(tab);
            return;
        }
        if (!DRAFTS.containsKey(tab) && DRAFTS.size() >= MAX_DRAFTS) {
            Iterator<ChatTab> oldest = DRAFTS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        DRAFTS.put(tab, value);
    }

    public static synchronized String getDraft() {
        return getDraft(selected);
    }

    /** Remembers a line sent from a tab, for the arrows to recall there and nowhere else. */
    public static synchronized void recordSent(ChatTab tab, String text) {
        SENT_HISTORY.record(tab, text);
    }

    /**
     * Walks a tab's sent lines: Up is {@code -1}, Down {@code +1}. The
     * text the field should now hold, or null when nothing changes.
     */
    public static synchronized String recallSent(ChatTab tab, int direction,
                                                 String fieldText) {
        return SENT_HISTORY.step(tab, direction, fieldText);
    }

    /** Ends a walk through sent lines: sending, or leaving the tab, does this. */
    public static synchronized void endSentBrowse() {
        SENT_HISTORY.endBrowse();
    }

    /** Drops every conversation tab's sent lines along with the conversations. */
    public static synchronized void forgetConversationHistory() {
        SENT_HISTORY.forgetConversations();
    }

    /** A tab's unsent input; empty when it has none. */
    public static synchronized String getDraft(ChatTab tab) {
        String value = tab == null ? null : DRAFTS.get(tab);
        return value == null ? "" : value;
    }

    public static synchronized void clear() {
        selected = ChatTab.of(ChatChannel.ALL);
        PARTNER_COLORS.clear();
        PARTNER_NAMES.clear();
        PARTNER_CHARACTER_IDS.clear();
        cachedFactionId = "";
        cachedFactionName = "";
        cachedFactionNanos = 0L;
        adminAccess = false;
        canModerate = false;
        canEditServerConfig = false;
        capabilities = java.util.Collections.emptySet();
        roleMask = 0;
        accountRoleMask = 0;
        CHARACTER_ROLES.clear();
        proximityRadius = 0;
        readableChannels = DEFAULT_READABLE;
        sendableChannels = DEFAULT_SENDABLE;
        ROLE_HOLDERS.clear();
        ROLE_HOLDER_ACCOUNT_ROLES.clear();
        ROLE_HOLDER_CHARACTERS.clear();
        MUTED_SENDERS.clear();
        DRAFTS.clear();
        SENT_HISTORY.clear();
    }

    /** The shared chat identity's faction or server-confirmed party. */
    public static synchronized String scopeKeyRead(ChatChannel channel) {
        if (channel == null || !channel.isScoped()) {
            return "";
        }
        if (channel.getScope() == ChatChannelScope.PARTY) {
            return ClientChatIdentitySelection.partyKey();
        }
        return scopeOfIdentity(channel, ClientChatIdentities.viewIdentityKey());
    }

    /**
     * Whom a name names, when it is the name someone is playing under
     * rather than their account: the account and the character, as the
     * server states them on every line that player sends. Null when no
     * online player is wearing the name — an account name included,
     * which the caller resolves for itself.
     */
    public static synchronized String[] playedBy(String characterName) {
        String named = characterName == null ? "" : characterName.trim();
        if (named.length() == 0) {
            return null;
        }
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance != null
                    && named.equalsIgnoreCase(appearance.getCharacterName())
                    && appearance.getAccountName().length() > 0) {
                return new String[] {appearance.getAccountName(),
                        appearance.getCharacterName()};
            }
        }
        return null;
    }

    /**
     * The conversation one of this player's identities is in on a scoped
     * channel: the faction of the identity the owner key names — the
     * character's own, or Unaligned for the account and for a character
     * created without one. Empty for a character the roster no longer
     * holds.
     */
    public static synchronized String scopeOfIdentity(ChatChannel channel,
                                                      String ownerKey) {
        if (channel == null || !channel.isScoped()
                || ownerKey == null) {
            return "";
        }
        if (channel.getScope() == ChatChannelScope.PARTY) {
            return ownerKey.equals(ClientChatIdentities.viewIdentityKey())
                    ? ClientChatIdentitySelection.partyKey() : "";
        }
        if (ownerKey.length() == 0) {
            return LotrCharacterAdapter.UNALIGNED_FACTION_ID;
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null) {
            return "";
        }
        for (CharacterSummary character : roster.getCharacters()) {
            if (character != null && ownerKey.equals(
                    ChatTab.ownerKeyOf(character.getCharacterId()))) {
                return LotrCharacterAdapter.factionIdOrUnaligned(
                        character.getStartingFactionId());
            }
        }
        return "";
    }

    /**
     * The tab of a scoped channel this player reads right now: the one
     * for the identity the chat is being read as. What the tab row, the
     * selection and the composer point at.
     */
    public static synchronized ChatTab tabRead(ChatChannel channel) {
        return channel == null || !channel.isScoped()
                ? ChatTab.of(channel)
                : ChatTab.of(channel, scopeKeyRead(channel));
    }

    /**
     * The faction of the identity the channel's tab speaks as — the
     * shared chat identity: the character's own, or Unaligned for the
     * account and for a character created without one, as the server
     * resolves it. Empty only for a character the roster no longer
     * holds. The Faction channel's label, colour and conversation all
     * read this, so they follow the worn identity as the server's
     * routing does.
     */
    public static synchronized String wornFactionId(ChatChannel channel) {
        ClientChatIdentities.Identity worn =
                ClientChatIdentities.effectiveFor(tabRead(channel));
        if (worn == null || worn.account || worn.characterId == null) {
            return LotrCharacterAdapter.UNALIGNED_FACTION_ID;
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        CharacterSummary character = roster == null ? null
                : roster.getCharacter(worn.characterId);
        return character == null ? ""
                : LotrCharacterAdapter.factionIdOrUnaligned(
                        character.getStartingFactionId());
    }

    /**
     * Without a character the player lands where they can actually talk:
     * Global when it is open and sendable, else OOC when open (account
     * conversation, always sendable), else the first open tab they can
     * send to, else the first open readable one; with nothing open at
     * all, the catalogue default.
     */
    private static ChatTab fallbackTab() {
        ChatTab global = ChatTab.of(ChatChannel.ALL);
        if (isSelectable(global) && canSend(global)) {
            return global;
        }
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        if (isSelectable(ooc)) {
            return ooc;
        }
        List<ChatTab> open = getOpenTabs();
        for (ChatTab tab : open) {
            if (canSend(tab)) {
                return tab;
            }
        }
        if (!open.isEmpty()) {
            return open.get(0);
        }
        return canSend(ChatChannel.ALL) ? global : ooc;
    }
}
