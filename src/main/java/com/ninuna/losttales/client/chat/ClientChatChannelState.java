package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.EnumChatFormatting;

/**
 * Session-local selected tab with deterministic availability fallback.
 * A channel can be <em>viewable</em> without being <em>sendable</em>: Global
 * is always readable because achievements and other vanilla lines live in
 * its tab, but only an active role-playing character may talk there. The
 * server enforces the same rule; this only keeps the client honest. The
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
    private static String cachedFactionId = "";
    private static String cachedFactionName = "";
    private static long cachedFactionNanos;
    /** Server-stated operator status; the Admin tab exists only with it. */
    private static boolean adminAccess;
    /** Server-stated Discord bridge; the Discord tab exists only with it. */
    private static boolean discordAccess;
    /** Unsent input kept across closing and reopening the chat screen. */
    /** Unsent text per tab, oldest first; bounded, whispers included. */
    private static final Map<ChatTab, String> DRAFTS =
            new LinkedHashMap<ChatTab, String>();
    private static final int MAX_DRAFTS = 64;

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

    /**
     * Next available tab of the selected tab's window, in row order; a
     * selection without a window cycles every open tab.
     */
    public static synchronized ChatTab cycle() {
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
        int index = order.indexOf(current);
        selected = order.get((index + 1) % order.size());
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
     * Whether the player may close the tab: the layout must keep one
     * open tab, and the player must keep one they can actually see, so
     * a tab hidden by availability (Admin without operator status) does
     * not count as the remaining one.
     */
    public static synchronized boolean isClosable(ChatTab tab) {
        return ChatWindowLayout.isClosable(tab) && getOpenTabs().size() > 1;
    }

    /**
     * Closes the tab under {@link #isClosable} and moves the selection
     * off it if it was selected. Closing never mutes: the channel keeps
     * receiving and keeps its own mute setting.
     */
    public static synchronized boolean close(ChatTab tab) {
        if (!isClosable(tab) || !ChatWindowLayout.close(tab)) {
            return false;
        }
        ensureAvailable();
        return true;
    }

    /** Whether the tab's history is readable and its tab shown. */
    public static synchronized boolean isAvailable(ChatTab tab) {
        return tab != null && isAvailable(tab.getChannel());
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

    /** Whether the channel's tab is shown and its history readable. */
    public static synchronized boolean isAvailable(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        if (channel == ChatChannel.ALL || channel == ChatChannel.OOC
                || channel == ChatChannel.CONSOLE
                || channel == ChatChannel.WHISPER) {
            return true;
        }
        return canSend(channel);
    }

    public static synchronized boolean canSend(ChatTab tab) {
        return tab != null && canSend(tab.getChannel());
    }

    /** Whether the local player may currently send into the channel. */
    public static synchronized boolean canSend(ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        if (channel == ChatChannel.ADMIN) {
            return adminAccess;
        }
        if (channel == ChatChannel.DISCORD) {
            return discordAccess;
        }
        if (channel == ChatChannel.WHISPER) {
            return true;
        }
        CharacterSummary active = activeCharacter();
        if (channel.getIdentityType() == ChatIdentityType.CHARACTER
                && active == null) {
            return false;
        }
        if (channel == ChatChannel.FACTION) {
            return LotrCharacterAdapter.normalizeFactionId(
                    active.getStartingFactionId()).length() > 0;
        }
        if (channel != ChatChannel.PARTY) {
            return true;
        }
        PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
        return snapshot != null && snapshot.isAvailable()
                && snapshot.getActiveCharacterId() != null
                && snapshot.getParty() != null
                && snapshot.getParty().containsMember(
                        snapshot.getActiveCharacterId());
    }

    public static synchronized int displayColor(ChatTab tab) {
        return tab == null ? 0xFFFFFF : displayColor(tab.getChannel());
    }

    public static synchronized int displayColor(ChatChannel channel) {
        if (channel != ChatChannel.FACTION) {
            return channel == null ? 0xFFFFFF : channel.getDisplayColor();
        }
        CharacterSummary active = activeCharacter();
        return active == null ? channel.getDisplayColor()
                : LotrCharacterAdapter.getInstance().getFactionColor(
                        active.getStartingFactionId(),
                        channel.getDisplayColor());
    }

    /** Visible label for a tab: the partner's name for a whisper. */
    public static synchronized String displayName(ChatTab tab) {
        if (tab == null) {
            return "";
        }
        return tab.isWhisper() ? tab.getPartner()
                : displayName(tab.getChannel());
    }

    /**
     * Visible label for a channel. Faction shows the active character's
     * LOTR faction name ("Gondor") so the tab, indicator, and message prefix
     * all agree; the logical channel id is untouched. The LOTR lookup is
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
        CharacterSummary active = activeCharacter();
        String factionId = active == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(
                        active.getStartingFactionId());
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

    /** Applies the server's statement of operator status. */
    public static synchronized void setAdminAccess(boolean access) {
        adminAccess = access;
        ensureAvailable();
    }

    public static synchronized boolean hasAdminAccess() {
        return adminAccess;
    }

    /** Applies the server's statement of whether Discord is bridged. */
    public static synchronized void setDiscordAccess(boolean access) {
        discordAccess = access;
        ensureAvailable();
    }

    public static synchronized boolean hasDiscordAccess() {
        return discordAccess;
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

    /** A tab's unsent input; empty when it has none. */
    public static synchronized String getDraft(ChatTab tab) {
        String value = tab == null ? null : DRAFTS.get(tab);
        return value == null ? "" : value;
    }

    public static synchronized void clear() {
        selected = ChatTab.of(ChatChannel.ALL);
        cachedFactionId = "";
        cachedFactionName = "";
        cachedFactionNanos = 0L;
        adminAccess = false;
        discordAccess = false;
        DRAFTS.clear();
    }

    private static CharacterSummary activeCharacter() {
        CharacterRosterSnapshot roster =
                ClientCharacterRosterCache.getSnapshot();
        return roster == null ? null : roster.getActiveCharacter();
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
