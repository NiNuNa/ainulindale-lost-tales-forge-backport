package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.HashMap;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatCodeNames;
import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelIconSpec;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatSpeechRange;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.lotr.LotrFactionColors;
import com.ninuna.losttales.faction.FactionDemonyms;
import com.ninuna.losttales.util.LostTalesWords;
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
 * Remembers the selected page for this session. Server gates control visibility;
 * membership channels remain visible but read-only without a membership.
 * All roleplaying conversations follow the shared chat identity.
 */
public final class ClientChatChannelState {
    /** How often an unavailable faction-name lookup is retried. */
    private static final long FACTION_NAME_RETRY_NANOS = 5000L * 1000000L;

    /** The conversation the input is in. */
    private static ConversationPage selected = ConversationPage.of(ChatChannel.GLOBAL);
    /**
     * The conversation the player last picked or typed in, never a
     * console: the chat key brings it back, and the consoles have a key of
     * their own. It stays when a page covers it and the input is lent to
     * another window.
     */
    private static ConversationPage lastUsed = selected;
    /**
     * Whether the player has picked or typed in a conversation since
     * joining; until then the chat key brings the one in front of the top
     * window, as the layout file left it.
     */
    private static boolean lastUsedKnown;
    /** Conversations remembered for their partner's colour; oldest go first. */
    private static final int MAX_PARTNER_COLORS = 64;
    private static final LinkedHashMap<ConversationPage, Integer> PARTNER_COLORS =
            new LinkedHashMap<ConversationPage, Integer>();
    /**
     * How a conversation's page names its partner: the identity their
     * last line wore, the account in brackets behind it when the two
     * differ. Remembered like the colours, from their lines alone.
     */
    private static final LinkedHashMap<ConversationPage, String> PARTNER_NAMES =
            new LinkedHashMap<ConversationPage, String>();
    /**
     * The id of the character a conversation is with, when the server
     * has said; a reply is then addressed by it. Remembered like the
     * names, from the conversation's lines alone.
     */
    private static final LinkedHashMap<ConversationPage, UUID> PARTNER_CHARACTER_IDS =
            new LinkedHashMap<ConversationPage, UUID>();
    /**
     * The lang key LOTR names each faction asked about by, by faction id,
     * read in this game's language at every ask, so a language chosen
     * now names the factions at once. An empty key is one LOTR could not
     * give; it is asked again after a while ({@link #FACTION_NAMES_ASKED})
     * rather than every frame.
     */
    private static final HashMap<String, String> FACTION_NAMES =
            new HashMap<String, String>();
    private static final HashMap<String, Long> FACTION_NAMES_ASKED =
            new HashMap<String, Long>();
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
    /** The server's talk range in blocks; zero until it says. */
    private static int proximityRadius;
    /** The server's whisper and shout ranges in blocks; zero until it says. */
    private static int whisperRange;
    private static int shoutRange;
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
    /** The commands the server keeps private ({@code chat.privateCommands}). */
    private static final java.util.Set<String> PRIVATE_COMMANDS =
            new java.util.LinkedHashSet<String>();
    /** The channels linked to Discord, by link key, as the server said. */
    private static final java.util.Set<String> DISCORD_LINKS =
            new java.util.HashSet<String>();
    /** Whether the server keeps a log of whispers, as it said. */
    private static boolean whispersLogged;
    /** Server-stated muted senders; filled for operators only. */
    private static final java.util.Set<UUID> MUTED_SENDERS =
            new java.util.HashSet<UUID>();
    /**
     * Server-stated online role holders, account name to mask, in the
     * order the server listed them: what the role card names its
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
    /** What was sent from each page, for the arrows to recall there. */
    private static final ChatSentHistory SENT_HISTORY = new ChatSentHistory();

    private ClientChatChannelState() {}

    public static synchronized ConversationPage getSelected() {
        ensureAvailable();
        return selected;
    }

    /**
     * The player's own pick: the input goes there, and it is the last
     * used. Asked for a conversation no window holds as it is, the copy of
     * it used last takes the input.
     */
    public static synchronized void select(ConversationPage page) {
        ConversationPage held = heldCopyOf(page);
        choose(isSelectable(held) ? held : fallbackPage());
    }

    /**
     * Moves the input to a conversation while a page covers the one last
     * used, which is kept for the chat key.
     */
    public static synchronized void lendInput(ConversationPage page) {
        ConversationPage held = heldCopyOf(page);
        if (isSelectable(held)) {
            selected = held;
        }
    }

    /**
     * The copy of the page's row entry a window holds: the page itself while
     * a window holds it, else the copy of it used last; the page as it came
     * when no copy is open.
     */
    private static ConversationPage heldCopyOf(ConversationPage page) {
        ConversationPage row = ConversationPage.row(page);
        if (row == null || WindowLayout.holds(row)) {
            return page;
        }
        ConversationPage held = ConversationPage.from(WindowLayout.lastUsed(row));
        return held == null ? page : held;
    }

    /** Something was typed where the input is: that conversation is the last used, a console aside. */
    public static synchronized void markUsed() {
        if (isSelectable(selected) && !selected.isConsole()) {
            lastUsed = selected;
            lastUsedKnown = true;
        }
    }

    /**
     * The conversation last picked or typed in, while it is still open;
     * before any since joining, the one in front of the top window that
     * shows one. Never a console. Null when there is none.
     */
    public static synchronized ConversationPage lastUsed() {
        if (!lastUsedKnown) {
            List<Window> stacked = WindowLayout.stacked();
            for (int index = stacked.size() - 1; index >= 0; index--) {
                ConversationPage front = ConversationPage.from(stacked.get(index).getActiveTab());
                if (isSelectable(front) && !front.isConsole()) {
                    return front;
                }
            }
        }
        return isSelectable(lastUsed) ? lastUsed : null;
    }

    private static void choose(ConversationPage page) {
        selected = page;
        if (!page.isConsole()) {
            lastUsed = page;
            lastUsedKnown = true;
        }
    }

    /**
     * Available conversations open in some window, pages left out, in
     * window and page order.
     */
    public static synchronized List<ConversationPage> getOpenPages() {
        ArrayList<ConversationPage> result = new ArrayList<ConversationPage>();
        for (WindowPage each : WindowLayout.order()) {
            ConversationPage page = ConversationPage.from(each);
            if (page != null && isAvailable(page)) {
                result.add(page);
            }
        }
        return result;
    }

    public static synchronized void ensureAvailable() {
        if (!isSelectable(selected)) {
            selected = fallbackPage();
        }
    }

    /**
     * Available to this player and open in a window as this very copy, and
     * a conversation: a page is never the page typed into.
     */
    public static synchronized boolean isSelectable(ConversationPage page) {
        return page != null && isAvailable(page)
                && WindowLayout.holds(ConversationPage.row(page));
    }

    /**
     * Whether the player may close the page: it is open and its window is
     * unlocked. Nothing is held back — the last page of the last window
     * closes like any other, and the screen shows its empty state.
     */
    public static synchronized boolean isClosable(ConversationPage page) {
        return WindowLayout.isClosable(page);
    }

    /**
     * Closes the page under {@link #isClosable} and moves the selection
     * off it if it was selected: onto the page its window brings forward
     * in its place, the one to its right or else to its left, so the
     * input stays where the player was working and no other window comes
     * forward for it. A page brought forward keeps the front, and the
     * input waits in another conversation of that window; only a window
     * emptied by the close hands the selection elsewhere. Closing never
     * mutes: the channel keeps receiving and keeps its own choice.
     */
    public static synchronized boolean close(ConversationPage page) {
        Window window = WindowLayout.windowOf(page);
        boolean wasSelected = page != null && page.equals(selected);
        if (!isClosable(page) || !ChatLayout.close(page)) {
            return false;
        }
        if (wasSelected) {
            ConversationPage neighbour = neighbourIn(window);
            choose(neighbour != null ? neighbour : fallbackPage());
        }
        ensureAvailable();
        return true;
    }

    /**
     * The conversation that takes a closed page's place in its window: the
     * one the window brought forward, else any selectable one it holds;
     * null when the window is gone or holds nothing selectable.
     */
    private static ConversationPage neighbourIn(Window window) {
        if (window == null) {
            return null;
        }
        ConversationPage front = ConversationPage.from(window.getActiveTab());
        if (isSelectable(front)) {
            return front;
        }
        for (WindowPage each : window.getTabs()) {
            ConversationPage candidate = ConversationPage.from(each);
            if (isSelectable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Whether the page's history is readable and its page shown. A
     * conversation is shown only while some copy reads as the identity it
     * is held as, or the character played is it: what the player said as
     * one character is not on screen while nothing reads as it. Its lines
     * are still filed and counted, and it shows again the moment a copy
     * reads as that identity again. An NPC conversation belongs to nobody
     * in particular and is always shown; so is every plain channel, a
     * scoped one included — one row entry, each copy showing the
     * conversation it reads. A fellowship's conversation is its own row
     * entry, shown while the character played is in it; the Fellowship
     * channel has no plain page.
     */
    public static synchronized boolean isAvailable(ConversationPage page) {
        if (page == null || !isAvailable(page.getChannel())) {
            return false;
        }
        if (page.getChannel() == ChatChannel.FELLOWSHIP) {
            return ClientChatIdentitySelection.fellowship(page.getOwnerKey()) != null;
        }
        if (page.isNpc() || page.getOwnerKey().length() == 0) {
            // A row entry stands for whichever conversation is read.
            return true;
        }
        // A conversation held as one identity shows while that identity
        // is read: a whisper by the identity itself, a scoped channel by
        // the conversation one of the identities read is in.
        return page.isWhisper()
                ? ClientChatIdentities.isRead(page.getOwnerKey())
                : readsScope(page.getChannel(), page.getOwnerKey());
    }

    /** Whether the character played or a copy reads the conversation {@code scope} of a scoped channel. */
    private static boolean readsScope(ChatChannel channel, String scope) {
        for (ClientChatIdentities.Identity identity : ClientChatIdentities.inUse()) {
            if (scope.equals(scopeOfIdentity(channel,
                    ConversationPage.ownerKeyOf(identity.characterId)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the channel's page is shown and its history readable: every
     * open channel always, a gated one while its access is held, and
     * Fellowship only while the character played is in a fellowship.
     */
    public static synchronized boolean isAvailable(ChatChannel channel) {
        if (channel == null || !isGateOpen(readableChannels, channel)) {
            return false;
        }
        return channel.getAccess() != ChatChannelAccess.FELLOWSHIP_MEMBERSHIP
                || !ClientChatIdentitySelection.fellowships().isEmpty();
    }

    /** Whether the server's gate for this player lets the channel be used. */
    private static boolean isGateOpen(java.util.Set<String> gates,
                                      ChatChannel channel) {
        return gates.contains(channel.getId());
    }

    /**
     * The channels the seeded gates leave open to a player with no role.
     * The Server Log is left out whatever the gates say: a
     * capability opens it, and only the server knows who holds one, so
     * its page waits for the server's word rather than showing for a
     * frame to everybody.
     */
    private static java.util.Set<String> seededGates(boolean read) {
        ChatChannelGates seeded = ChatRoleConfig.parseGates(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_GATE},
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

    public static synchronized boolean canSend(ConversationPage page) {
        return isAvailable(page) && canSend(page.getChannel());
    }

    /**
     * Sending requires the server's send gate on a channel that is shown:
     * role gates, the Operator channel's included, are answered by the
     * sendable mask the server sent, and membership by the page being
     * there at all. Every identity is in a faction, Unaligned by default,
     * so the Faction channel asks nothing more.
     */
    public static synchronized boolean canSend(ChatChannel channel) {
        return channel != null && isGateOpen(sendableChannels, channel)
                && isAvailable(channel);
    }

    /**
     * A conversation is named in the colour the other party speaks in —
     * an NPC's faction colour, a player's own name colour — so the page,
     * its icon and its lines read as one. The colour their last line wore
     * is the surest; before they have said anything this session a
     * player's conversation takes the colour their name wears in
     * character — the faction's of the character it is with, as the
     * client knows it, or the plain ivory of an account — never a colour
     * of the channel's own: a whisper is the colour of the person it is
     * with. Every other page
     * takes its channel's colour.
     */
    public static synchronized int displayColor(ConversationPage page) {
        if (page == null) {
            return LostTalesUiInk.IVORY;
        }
        Integer partner = PARTNER_COLORS.get(partnerKey(page));
        if (partner != null) {
            return partner.intValue();
        }
        if (page.isWhisper() && !page.isNpc()) {
            return partnerNameColor(page);
        }
        return displayColor(page.getChannel(), page.getOwnerKey());
    }

    /**
     * The colour of one conversation: for a faction's chat that faction's
     * own, whichever faction is read now, so a link or a line names the
     * faction it was said in; for a fellowship's the colour the player
     * wears in it, the one its HUD and page show them in; the channel's
     * colour for every other.
     */
    public static synchronized int displayColor(ChatChannel channel,
                                                String scope) {
        if (isFaction(channel, scope)) {
            return LotrFactionColors.forFactionId(scope,
                    channel.getDisplayColor());
        }
        ChatFellowship fellowship = fellowshipOf(channel, scope);
        return fellowship != null ? fellowship.getColor() : displayColor(channel);
    }

    /** The fellowship a channel and scope name; null for anything else, and for one the character played is not in. */
    private static ChatFellowship fellowshipOf(ChatChannel channel, String scope) {
        return channel == ChatChannel.FELLOWSHIP
                ? ClientChatIdentitySelection.fellowship(scope) : null;
    }

    /** Whether a channel and scope name one faction's chat rather than the Faction page. */
    private static boolean isFaction(ChatChannel channel, String scope) {
        return channel == ChatChannel.FACTION && scope != null
                && scope.length() > 0;
    }

    /**
     * The colour a player's name wears in character, for the identity a
     * conversation is with: the faction colour of that character where
     * the client holds its appearance — by its id, or else by its name
     * among the partner's — and the chat's plain ivory for an account, or
     * for a character the client cannot place.
     */
    private static int partnerNameColor(ConversationPage page) {
        int plain = com.ninuna.losttales.chat.ChatRolePresentation
                .unassignedColor();
        UUID characterId = PARTNER_CHARACTER_IDS.get(partnerKey(page));
        String identity = page.getPartnerIdentity();
        for (CharacterAppearance appearance
                : ClientCharacterAppearanceCache.snapshot().values()) {
            if (appearance == null || !appearance.hasCharacter()) {
                continue;
            }
            boolean same = characterId != null
                    ? characterId.equals(appearance.getCharacterId())
                    : page.getPartner().equalsIgnoreCase(
                            appearance.getAccountName())
                            && identity != null
                            && identity.equalsIgnoreCase(
                                    appearance.getCharacterName());
            if (same) {
                return LotrFactionColors.forFactionId(
                        appearance.getFactionId(), plain);
            }
        }
        return plain;
    }

    /**
     * Remembers the colour the other party's name is drawn in, for the
     * conversation's own page. Only their lines say it: the player's own
     * copy of a whisper carries the player's colour, not theirs.
     */
    public static synchronized void rememberPartnerColor(ConversationPage page,
                                                         int color) {
        if (page == null || (!page.isWhisper() && !page.isNpc())) {
            return;
        }
        PARTNER_COLORS.put(partnerKey(page), Integer.valueOf(color & 0xFFFFFF));
        while (PARTNER_COLORS.size() > MAX_PARTNER_COLORS) {
            Iterator<ConversationPage> oldest = PARTNER_COLORS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Remembers the identity the partner's last line wore, so the
     * person's page names them the way they speak: the character's name
     * alone, never the account behind it.
     */
    public static synchronized void rememberPartnerName(ConversationPage page,
                                                        String identityName) {
        if (page == null || !page.isWhisper() || identityName == null
                || identityName.length() == 0) {
            return;
        }
        PARTNER_NAMES.put(partnerKey(page), identityName);
        while (PARTNER_NAMES.size() > MAX_PARTNER_COLORS) {
            Iterator<ConversationPage> oldest = PARTNER_NAMES.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * Remembers which character of the other party a conversation is
     * with; null forgets, for a conversation with their account.
     */
    public static synchronized void rememberPartnerCharacterId(ConversationPage page,
                                                               UUID characterId) {
        if (page == null || !page.isWhisper() || page.isNpc()) {
            return;
        }
        if (characterId == null) {
            PARTNER_CHARACTER_IDS.remove(partnerKey(page));
            return;
        }
        PARTNER_CHARACTER_IDS.put(partnerKey(page), characterId);
        while (PARTNER_CHARACTER_IDS.size() > MAX_PARTNER_COLORS) {
            Iterator<ConversationPage> oldest = PARTNER_CHARACTER_IDS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The id of the character a conversation is with; null for their account, or unknown. */
    public static synchronized UUID partnerCharacterIdOf(ConversationPage page) {
        return page == null ? null : PARTNER_CHARACTER_IDS.get(partnerKey(page));
    }

    public static synchronized int displayColor(ChatChannel channel) {
        if (channel == null) {
            return LostTalesUiInk.IVORY;
        }
        if (channel == ChatChannel.FACTION) {
            String factionId = playedFactionId();
            return factionId.length() == 0 ? channel.getDisplayColor()
                    : LotrFactionColors.forFactionId(factionId,
                            channel.getDisplayColor());
        }
        return channel.getDisplayColor();
    }

    /**
     * Visible label for a page: the partner's name for a whisper — the
     * identity their last line wore, when one is remembered — and for
     * one faction's conversation that faction's name.
     */
    public static synchronized String displayName(ConversationPage page) {
        if (page == null) {
            return "";
        }
        if (page.isWhisper()) {
            String remembered = PARTNER_NAMES.get(partnerKey(page));
            // The identity is what the conversation is with; the account
            // behind it is never shown beside it.
            return remembered != null ? remembered
                    : page.getPartnerIdentity();
        }
        return displayName(page.getChannel(), page.getOwnerKey());
    }

    /**
     * The name of one conversation, in this game's language: for a
     * faction's chat that faction's chat ("Gondor Chat"), whichever
     * faction is read now; for a fellowship's its name ("The Grey Company
     * Chat"); the channel's name ({@link #displayName(ChatChannel)}) for
     * every other.
     */
    public static synchronized String displayName(ChatChannel channel,
                                                  String scope) {
        if (isFaction(channel, scope)) {
            return factionChatName(scope, channel);
        }
        ChatFellowship fellowship = fellowshipOf(channel, scope);
        return fellowship != null ? StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.fellowship.titled", fellowship.getName())
                : displayName(channel);
    }

    /**
     * Visible label for a channel, in this game's language
     * ({@link ChatNames#channel}). Faction shows the chat of the LOTR
     * faction ("Gondor Chat") of the character played; a Faction copy
     * names its own conversation ({@link #displayName(ConversationPage)}).
     * A fellowship's conversation is named by its own name
     * ({@link #displayName(ChatChannel, String)}).
     */
    public static synchronized String displayName(ChatChannel channel) {
        if (channel == null) {
            return "";
        }
        if (channel != ChatChannel.FACTION) {
            return ChatNames.channel(LostTalesWords.LANG, channel);
        }
        return factionChatName(playedFactionId(), channel);
    }

    /**
     * A faction's chat by its faction ("Gondor Chat"), LOTR naming the
     * faction in this game's language; the Faction channel's own name
     * while LOTR cannot name it.
     */
    private static String factionChatName(String factionId, ChatChannel channel) {
        String faction = factionName(factionId, "");
        return faction.length() == 0 ? ChatNames.channel(LostTalesWords.LANG, channel)
                : ChatNames.factionChat(LostTalesWords.LANG, faction);
    }

    /**
     * A faction's name as LOTR gives it, in this game's language
     * ("Gondor"), or {@code fallback} while LOTR cannot say. Unaligned's
     * name is this mod's own lang entry, since LOTR ships none.
     */
    public static synchronized String factionName(String factionId,
                                                  String fallback) {
        if (factionId == null || factionId.length() == 0) {
            return fallback;
        }
        if (LotrCharacterAdapter.UNALIGNED_FACTION_ID.equals(factionId)) {
            return StatCollector.translateToLocal("lotr.faction.UNALIGNED.name");
        }
        long now = System.nanoTime();
        String key = FACTION_NAMES.get(factionId);
        Long asked = FACTION_NAMES_ASKED.get(factionId);
        if (key == null || (key.length() == 0 && asked != null
                && now - asked.longValue() > FACTION_NAME_RETRY_NANOS)) {
            String named = LotrCharacterAdapter.getInstance()
                    .getFactionNameKey(factionId);
            key = named == null ? "" : named.trim();
            FACTION_NAMES.put(factionId, key);
            FACTION_NAMES_ASKED.put(factionId, Long.valueOf(now));
        }
        if (key.length() == 0) {
            return fallback;
        }
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(
                StatCollector.translateToLocal(key));
        return plain == null || plain.trim().length() == 0 ? fallback
                : plain.trim();
    }

    /**
     * The people of a faction as a title names them, in this game's
     * language: a Lothlórien character is a Galadhrim Miner
     * ({@link FactionDemonyms}). Empty for no faction, and while LOTR
     * cannot name it.
     */
    public static String factionPeople(String factionId) {
        String normalized = LotrCharacterAdapter.normalizeFactionId(factionId);
        if (normalized.length() == 0) {
            return "";
        }
        return FactionDemonyms.of(normalized, factionName(normalized, ""));
    }

    /**
     * The roles the server says this player holds. Only used to notice
     * that a role mention was addressed to this client: nothing here
     * grants anything, and the server never reads it back.
     */
    public static synchronized void setRoleMask(int mask) {
        roleMask = ChatAccountRole.isValidMask(mask) ? mask : 0;
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

    /** The server's talk range alone, the others alike: how far speech bubbles reach. */
    public static synchronized void setProximityRadius(int radius) {
        setSpeechRanges(radius, radius, radius);
    }

    /** The server's speech ranges (CHAT-37): how far speech carries and its bubbles reach. */
    public static synchronized void setSpeechRanges(int whisper, int talk,
                                                    int shout) {
        whisperRange = Math.max(0, whisper);
        proximityRadius = Math.max(0, talk);
        shoutRange = Math.max(0, shout);
    }

    /** How far a line said at {@code range} carries, in blocks; zero until the server says. */
    public static synchronized int rangeBlocks(ChatSpeechRange range) {
        return range == ChatSpeechRange.WHISPER ? whisperRange
                : range == ChatSpeechRange.SHOUT ? shoutRange : proximityRadius;
    }

    /** The icons the server puts on its channels; see {@link ChatChannelIcons#install}. */
    public static void setChannelIcons(Map<String, ChatChannelIconSpec> icons) {
        ChatChannelIcons.install(icons);
    }

    /**
     * Replaces the server's statement of which channels are linked to
     * Discord, by code name ({@code global}, {@code gondor}).
     */

    /**
     * The commands the server keeps private: their echo here keeps only
     * their name, as the Server Log does.
     */
    public static synchronized void setPrivateCommands(java.util.Collection<String> names) {
        PRIVATE_COMMANDS.clear();
        if (names != null) {
            PRIVATE_COMMANDS.addAll(names);
        }
    }

    /** The commands the server keeps private, by name. */
    public static synchronized java.util.List<String> getPrivateCommands() {
        return new java.util.ArrayList<String>(PRIVATE_COMMANDS);
    }

    /** What the server says of its log of whispers, with the chat access. */
    public static synchronized void setWhispersLogged(boolean logged) {
        whispersLogged = logged;
    }

    /**
     * Whether the server keeps a log of whispers: a whisper's help and
     * the faint line at its top say so (CHAT-85).
     */
    public static synchronized boolean whispersLogged() {
        return whispersLogged;
    }

    public static synchronized void setDiscordLinks(java.util.Collection<String> keys) {
        DISCORD_LINKS.clear();
        if (keys != null) {
            DISCORD_LINKS.addAll(keys);
        }
    }

    /**
     * The conversations the server links to Discord, by the names their
     * pages read ({@code Global Chat}, {@code Gondor Chat}), in the order
     * the server stated them; empty for none.
     */
    public static synchronized List<String> discordLinkedNames() {
        List<String> names = new ArrayList<String>();
        for (String key : DISCORD_LINKS) {
            ChatCodeNames.Named named = ChatCodeNames.parse(key);
            if (named != null) {
                names.add(displayName(named.channel, named.scope));
            }
        }
        return names;
    }

    /**
     * Whether what is said in a page crosses to Discord: its channel is
     * linked, and for Faction chat the faction the page shows now.
     */
    public static synchronized boolean isLinkedToDiscord(ConversationPage page) {
        if (page == null || page.getChannel() == null || DISCORD_LINKS.isEmpty()) {
            return false;
        }
        ChatChannel channel = page.getChannel();
        String name = ChatCodeNames.of(channel,
                channel != ChatChannel.FACTION ? ""
                        : page.getOwnerKey().length() > 0 ? page.getOwnerKey()
                        : scopeKeyRead(page));
        return name != null && DISCORD_LINKS.contains(name);
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

    /** States which capabilities the player holds, as the access payload lists them. */
    public static synchronized void setCapabilities(
            java.util.Collection<String> held) {
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (String id : held) {
            if (id != null && id.trim().length() > 0) {
                ids.add(id.trim().toLowerCase(java.util.Locale.ROOT));
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
     * Remembers the selected page's unsent input so closing the screen,
     * switching pages or losing the connection does not lose it.
     */
    public static synchronized void setDraft(String text) {
        setDraft(selected, text);
    }

    /**
     * Remembers a page's unsent input where the client is now
     * ({@link ClientChatDrafts}); empty text forgets it.
     */
    public static synchronized void setDraft(ConversationPage page, String text) {
        ClientChatDrafts.set(ClientChatSession.currentKey(), page, text);
    }

    public static synchronized String getDraft() {
        return getDraft(selected);
    }

    /**
     * What a whisper partner's colour, name and character are kept by: the
     * person's row entry, the same for every copy of the conversation.
     */
    private static ConversationPage partnerKey(ConversationPage page) {
        ConversationPage row = ConversationPage.row(page);
        return row == null ? null : row.conversation();
    }

    /**
     * Remembers a line sent from a copy of a conversation, for the arrows
     * to recall there and nowhere else.
     */
    public static synchronized void recordSent(ConversationPage page, String text) {
        SENT_HISTORY.record(ConversationPage.row(page), text);
    }

    /** A copy opening anew has no draft and has sent nothing. */
    static synchronized void forgetCopy(ConversationPage copy) {
        setDraft(copy, "");
        SENT_HISTORY.forget(ConversationPage.row(copy));
    }

    /**
     * Walks a page's sent lines: Up is {@code -1}, Down {@code +1}. The
     * text the field should now hold, or null when nothing changes.
     */
    public static synchronized String recallSent(ConversationPage page, int direction,
                                                 String fieldText) {
        return SENT_HISTORY.step(ConversationPage.row(page), direction,
                fieldText);
    }

    /** Ends a walk through sent lines: sending, or leaving the page, does this. */
    public static synchronized void endSentBrowse() {
        SENT_HISTORY.endBrowse();
    }

    /** Drops every conversation page's sent lines along with the conversations. */
    public static synchronized void forgetConversationHistory() {
        SENT_HISTORY.forgetConversations();
    }

    /** A page's unsent input where the client is now; empty when it has none. */
    public static synchronized String getDraft(ConversationPage page) {
        return ClientChatDrafts.get(ClientChatSession.currentKey(), page);
    }

    public static synchronized void clear() {
        choose(ConversationPage.of(ChatChannel.GLOBAL));
        lastUsedKnown = false;
        PARTNER_COLORS.clear();
        PARTNER_NAMES.clear();
        PARTNER_CHARACTER_IDS.clear();
        FACTION_NAMES.clear();
        FACTION_NAMES_ASKED.clear();
        canModerate = false;
        canEditServerConfig = false;
        capabilities = java.util.Collections.emptySet();
        roleMask = 0;
        accountRoleMask = 0;
        CHARACTER_ROLES.clear();
        proximityRadius = 0;
        whisperRange = 0;
        shoutRange = 0;
        readableChannels = DEFAULT_READABLE;
        sendableChannels = DEFAULT_SENDABLE;
        ROLE_HOLDERS.clear();
        ROLE_HOLDER_ACCOUNT_ROLES.clear();
        ROLE_HOLDER_CHARACTERS.clear();
        MUTED_SENDERS.clear();
        ClientChatDrafts.endSession();
        SENT_HISTORY.clear();
        DISCORD_LINKS.clear();
        PRIVATE_COMMANDS.clear();
        whispersLogged = false;
    }

    /**
     * The conversation a copy reads on its own channel: the faction of the
     * identity it speaks as, or the fellowship the character played
     * travels with.
     */
    public static synchronized String scopeKeyRead(ConversationPage page) {
        return page == null ? "" : scopeKeyRead(page.getChannel(), page);
    }

    /**
     * The conversation {@code copy} reads on {@code channel}: what a
     * channel's link typed in that copy names, the faction of the
     * identity it speaks as.
     */
    public static synchronized String scopeKeyRead(ChatChannel channel,
                                                   ConversationPage copy) {
        if (channel == null || !channel.isScoped()) {
            return "";
        }
        if (channel.getScope() == ChatChannelScope.FELLOWSHIP) {
            return ClientChatIdentitySelection.travellingKey();
        }
        return scopeOfIdentity(channel, ClientChatIdentities.viewIdentityKey(copy));
    }

    /** The conversation the character played reads on the channel: what the feed shows. */
    public static synchronized String playedScopeKey(ChatChannel channel) {
        if (channel == null || !channel.isScoped()) {
            return "";
        }
        if (channel.getScope() == ChatChannelScope.FELLOWSHIP) {
            return ClientChatIdentitySelection.travellingKey();
        }
        return scopeOfIdentity(channel, ClientChatIdentities.activeIdentityKey());
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
        if (channel.getScope() == ChatChannelScope.FELLOWSHIP) {
            return ownerKey.equals(ClientChatIdentities.activeIdentityKey())
                    ? ClientChatIdentitySelection.travellingKey() : "";
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
                    ConversationPage.ownerKeyOf(character.getCharacterId()))) {
                return LotrCharacterAdapter.factionIdOrUnaligned(
                        character.getFactionId());
            }
        }
        return "";
    }

    /**
     * The faction of the character played: the character's own, or
     * Unaligned for the account and for a character created without one,
     * as the server resolves it. Empty only for a character the roster no
     * longer holds.
     */
    public static synchronized String playedFactionId() {
        return factionOf(ClientChatIdentities.played());
    }

    private static String factionOf(ClientChatIdentities.Identity worn) {
        if (worn == null || worn.account || worn.characterId == null) {
            return LotrCharacterAdapter.UNALIGNED_FACTION_ID;
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        CharacterSummary character = roster == null ? null
                : roster.getCharacter(worn.characterId);
        return character == null ? ""
                : LotrCharacterAdapter.factionIdOrUnaligned(
                        character.getFactionId());
    }

    /**
     * Without a character the player lands where they can actually talk:
     * Global when it is open and sendable, else OOC when open (account
     * conversation, always sendable), else the first open page they can
     * send to, else the first open readable one; with nothing open at
     * all, the catalogue default.
     */
    private static ConversationPage fallbackPage() {
        ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
        if (isSelectable(global) && canSend(global)) {
            return global;
        }
        ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);
        if (isSelectable(ooc)) {
            return ooc;
        }
        List<ConversationPage> open = getOpenPages();
        for (ConversationPage page : open) {
            if (canSend(page)) {
                return page;
            }
        }
        if (!open.isEmpty()) {
            return open.get(0);
        }
        return canSend(ChatChannel.GLOBAL) ? global : ooc;
    }
}
