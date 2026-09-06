package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.sync.PartyMemberSnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.EnumChatFormatting;

/**
 * Session-local selected tab with deterministic availability fallback.
 * A channel can be <em>viewable</em> without being <em>sendable</em>:
 * anyone may talk anywhere with whatever appearance they choose (see
 * {@link ClientChatAppearances}), so only membership closes a channel —
 * Faction and Party need the active character to belong somewhere, and
 * the staff and Discord channels need the server's word. The server
 * enforces the same rules; this only keeps the client honest. The
 * selection is also kept to tabs that are open in the
 * {@link ChatWindowLayout}: a closed channel cannot be the input target,
 * and {@code TAB} cycles through the tabs of the selected tab's own
 * window. Whisper tabs are always available and always sendable: they
 * use the account identity.
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
     * How a conversation's tab names its partner: the appearance their
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
    /** The server's word on whether this player may moderate the chat. */
    /**
     * Every capability the server says this player holds, by id. The
     * menus ask this rather than a flag of their own, so a capability
     * the code gains later needs no new field here.
     */
    private static java.util.Set<String> capabilities =
            java.util.Collections.emptySet();
    private static boolean canModerate;
    /** The server's word on whether this player may edit its settings. */
    private static boolean canEditServerConfig;
    /** Server-stated roles of this player; what {@code @Operator} reaches. */
    private static int roleMask;
    /**
     * The gates before the server's first word: what a fresh server file
     * states, read for a player with no role — the Operator channel
     * closed, everything else open. Once the access packet arrives the
     * server's own masks replace them.
     */
    private static final int DEFAULT_READABLE = seededGateMask(true);
    private static final int DEFAULT_SENDABLE = seededGateMask(false);
    /** Server-stated channel gates for this player, one bit per channel. */
    private static int readableChannels = DEFAULT_READABLE;
    private static int sendableChannels = DEFAULT_SENDABLE;
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
    /** Unsent input kept across closing and reopening the chat screen. */
    /** Unsent text per tab, oldest first; bounded, whispers included. */
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
        int index = Math.max(0, order.indexOf(current));
        selected = order.get(
                ((index + step) % order.size() + order.size())
                        % order.size());
        return selected;
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
        if (tab.isNpc() || !tab.isWhisper()) {
            return true;
        }
        return tab.getOwnerKey().equals(
                ClientChatAppearances.viewIdentityKey());
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
     * that has not is not drawn, not offered in the placement editor,
     * and in nothing's way until one of its channels becomes available.
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
     * open channel always, a gated one while its access is held.
     */
    public static synchronized boolean isAvailable(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        if (!isGateOpen(readableChannels, channel)) {
            return false;
        }
        return channel.getAccess() == ChatChannelAccess.NONE
                || canSend(channel);
    }

    /** Whether the server's gate for this player lets the channel be used. */
    private static boolean isGateOpen(int gates, ChatChannel channel) {
        int bit = channel.ordinal();
        return bit >= 32 || (gates & (1 << bit)) != 0;
    }

    /** One bit per channel the seeded gates leave open to a player with no role. */
    private static int seededGateMask(boolean read) {
        ChatChannelGates seeded = ChatRoleConfig.parseGates(
                new String[] {ChatRoleConfig.DEFAULT_ADMIN_GATE},
                ChatRoleCatalog.builtIn(), ChatRoleConfig.SILENT);
        int mask = 0;
        ChatChannel[] channels = ChatChannel.values();
        for (int index = 0; index < channels.length && index < 32; index++) {
            boolean open = read ? seeded.canRead(0, channels[index])
                    : seeded.canSend(0, channels[index]);
            if (open) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    /** The server's word on the channels this player may read and send into. */
    public static synchronized void setChannelGates(int readable, int sendable) {
        readableChannels = readable;
        sendableChannels = sendable;
        ensureAvailable();
    }

    public static synchronized boolean canSend(ChatTab tab) {
        return tab != null && canSend(tab.getChannel());
    }

    /**
     * Whether the local player may currently send into the channel.
     * Identity does not gate a channel — any channel is spoken with
     * any appearance, the account included — so only the channel's own
     * access does: membership for Faction and Party, the server's word
     * for the staff channel.
     */
    public static synchronized boolean canSend(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        if (!isGateOpen(sendableChannels, channel)) {
            return false;
        }
        // Role gates, the Operator channel's included, are answered by the
        // sendable mask the server sent above; membership is what remains.
        ChatChannelAccess access = channel.getAccess();
        if (access == ChatChannelAccess.CHARACTER_FACTION) {
            return wornFactionId(channel).length() > 0;
        }
        if (access != ChatChannelAccess.PARTY_MEMBERSHIP) {
            return true;
        }
        PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
        return snapshot != null && snapshot.isAvailable()
                && snapshot.getActiveCharacterId() != null
                && snapshot.getParty() != null
                && snapshot.getParty().containsMember(
                        snapshot.getActiveCharacterId());
    }

    /**
     * The active character's normalized starting faction id
     * ({@code lotr:gondor}), or empty without a character or faction.
     */
    public static synchronized String activeFactionId() {
        CharacterSummary active = activeCharacter();
        return active == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(
                        active.getStartingFactionId());
    }

    /**
     * A conversation is named in the colour the other party speaks in —
     * an NPC's honey, a player's own name colour — so the tab, its icon
     * and its lines read as one. Every other tab takes its channel's
     * colour.
     */
    public static synchronized int displayColor(ChatTab tab) {
        if (tab == null) {
            return 0xFFFFFF;
        }
        Integer partner = PARTNER_COLORS.get(tab);
        if (partner != null) {
            return partner.intValue();
        }
        return displayColor(tab.getChannel());
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
        PARTNER_COLORS.put(tab, Integer.valueOf(color & 0xFFFFFF));
        while (PARTNER_COLORS.size() > MAX_PARTNER_COLORS) {
            Iterator<ChatTab> oldest = PARTNER_COLORS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Remembers the identity the partner's last line wore, so the
     * conversation's tab names them the way they speak: the appearance,
     * the account in brackets behind it when the two differ.
     */
    public static synchronized void rememberPartnerName(ChatTab tab,
                                                        String identityName,
                                                        String accountName) {
        if (tab == null || !tab.isWhisper() || identityName == null
                || accountName == null || identityName.length() == 0) {
            return;
        }
        String shown = identityName.equalsIgnoreCase(accountName)
                ? accountName
                : identityName + " (" + accountName + ")";
        PARTNER_NAMES.put(tab, shown);
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
            PARTNER_CHARACTER_IDS.remove(tab);
            return;
        }
        PARTNER_CHARACTER_IDS.put(tab, characterId);
        while (PARTNER_CHARACTER_IDS.size() > MAX_PARTNER_COLORS) {
            Iterator<ChatTab> oldest = PARTNER_CHARACTER_IDS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The id of the character a conversation is with; null for their account, or unknown. */
    public static synchronized UUID partnerCharacterIdOf(ChatTab tab) {
        return tab == null ? null : PARTNER_CHARACTER_IDS.get(tab);
    }

    public static synchronized int displayColor(ChatChannel channel) {
        if (channel == null) {
            return 0xFFFFFF;
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
            PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
            if (snapshot != null && snapshot.getParty() != null
                    && snapshot.getActiveCharacterId() != null) {
                PartyMemberSnapshot member = snapshot.getParty().getMember(
                        snapshot.getActiveCharacterId());
                if (member != null) {
                    return partyColorRgb(member.getColor());
                }
            }
        }
        return channel.getDisplayColor();
    }

    /** The chat's RGB for a party colour, as the party screens map it. */
    private static int partyColorRgb(PartyColor color) {
        if (color == PartyColor.GREEN) {
            return LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN);
        }
        if (color == PartyColor.YELLOW) {
            return LostTalesColors.rgb(LostTalesColors.HONEY);
        }
        if (color == PartyColor.PURPLE) {
            return LostTalesColors.rgb(LostTalesColors.ORCHID);
        }
        return LostTalesColors.rgb(LostTalesColors.SEAFOAM);
    }

    /** Visible label for a tab: the partner's name for a whisper —
     *  the appearance their last line wore, when one is remembered. */
    public static synchronized String displayName(ChatTab tab) {
        if (tab == null) {
            return "";
        }
        if (tab.isWhisper()) {
            String remembered = PARTNER_NAMES.get(tab);
            // The identity is what the conversation is with; the account
            // behind it only shows when the two differ.
            return remembered != null ? remembered
                    : tab.getPartnerIdentity();
        }
        return displayName(tab.getChannel());
    }

    /**
     * Visible label for a channel. Faction shows the LOTR faction name
     * ("Gondor") of the identity its tab speaks as, so the tab, indicator
     * and message prefix all agree and follow a character chosen for the
     * tab; the logical channel id is untouched. The LOTR lookup is
     * cached per faction id, and an unavailable lookup is retried on an
     * interval rather than every frame, falling back to the catalogue name.
     */
    public static synchronized String displayName(ChatChannel channel) {
        if (channel == null) {
            return "";
        }
        if (channel != ChatChannel.FACTION) {
            return channel.getDisplayName();
        }
        String factionId = wornFactionId(channel);
        if (factionId.length() == 0) {
            return channel.getDisplayName();
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
            if ((entry.getValue().intValue() & role.bit()) != 0) {
                names.add(entry.getKey());
            }
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
        readableChannels = DEFAULT_READABLE;
        sendableChannels = DEFAULT_SENDABLE;
        ROLE_HOLDERS.clear();
        MUTED_SENDERS.clear();
        DRAFTS.clear();
        SENT_HISTORY.clear();
    }

    private static CharacterSummary activeCharacter() {
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        return roster == null ? null : roster.getActiveCharacter();
    }

    /**
     * The normalized faction id of the identity the channel's tab speaks
     * as — the character chosen or locked for the tab, else the active
     * character — or empty for the account and for a character without
     * a faction. The Faction channel's label, colour and availability
     * all read this, so they follow the worn identity as the server's
     * routing does.
     */
    /**
     * Which of this player's identities reads a line said in one
     * conversation of a scoped channel: the character in that faction,
     * by its id, or the account's empty key when the channel is only
     * ever one conversation. A conversation nothing of this player's is
     * in — which the server does not send — files under the plain tab,
     * where it is counted and not shown.
     */
    public static synchronized String ownerKeyReading(ChatChannel channel,
                                                      String scopeValue) {
        if (channel == null || !channel.isIdentityScoped()
                || scopeValue == null || scopeValue.length() == 0) {
            return "";
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null) {
            return "";
        }
        for (CharacterSummary character : roster.getCharacters()) {
            if (character != null && scopeValue.equals(
                    LotrCharacterAdapter.normalizeFactionId(
                            character.getStartingFactionId()))) {
                return ChatTab.ownerKeyOf(character.getCharacterId());
            }
        }
        return "";
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
     * channel: the faction of the character the owner key names. Empty
     * for the account, which is in none, and for a character the roster
     * no longer holds.
     */
    public static synchronized String scopeOfIdentity(ChatChannel channel,
                                                      String ownerKey) {
        if (channel == null || !channel.isIdentityScoped()
                || ownerKey == null || ownerKey.length() == 0) {
            return "";
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null) {
            return "";
        }
        for (CharacterSummary character : roster.getCharacters()) {
            if (character != null && ownerKey.equals(
                    ChatTab.ownerKeyOf(character.getCharacterId()))) {
                return LotrCharacterAdapter.normalizeFactionId(
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
        return channel == null || !channel.isIdentityScoped()
                ? ChatTab.of(channel)
                : ChatTab.of(channel, ClientChatAppearances.viewIdentityKey());
    }

    public static synchronized String wornFactionId(ChatChannel channel) {
        ClientChatAppearances.Appearance worn =
                ClientChatAppearances.effectiveFor(tabRead(channel));
        if (worn == null || worn.account || worn.characterId == null) {
            return "";
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        CharacterSummary character = roster == null ? null
                : roster.getCharacter(worn.characterId);
        return character == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(character.getStartingFactionId());
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
