package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.chat.ChatTabIds;
import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.client.window.PageHelp;
import com.ninuna.losttales.client.window.PageOption;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.TabMark;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowTab;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * A conversation's tab: a channel, and for a whisper the <em>identity</em>
 * the conversation is with, the identity of this player's own it is held
 * as — or the NPC, since LOTR speech is addressed to one player and reads
 * as a whisper from the NPC. It is the chat's kind of {@link WindowTab}.
 *
 * <p>A conversation is between two people as they present themselves,
 * not between the accounts behind them: whispering someone speaking as
 * Aldric and whispering the same player speaking as Beren are two
 * conversations, and neither is the one with their account; and what
 * this player says as Aldric is Aldric's conversation, not Beren's, so
 * switching to Beren shows none of it. The accounts are still carried —
 * they are who the message is routed to, always reachable whatever
 * either happens to be playing — but it is the two identities that name
 * the tab and keep the threads apart.</p>
 *
 * <p>Every plain channel is one tab; every whisper conversation is one
 * more, all of them on the {@link ChatChannel#WHISPER} channel, NPCs
 * kept apart from players of the same name. Tabs are values — equal when
 * channel, account, identity, own identity and kind agree, names
 * compared case-insensitively — and are what windows hold, lines are
 * filed under, and the selection points at.</p>
 */
public final class ChatTab extends WindowTab {
    /** A conversation's options, behind its tab's three dots and on its tool strip. */
    private static final String MENU_MARK_READ = "mark_read";
    private static final String MENU_JUMP_UNREAD = "jump_unread";
    private static final String MENU_NOTIFY = "notify";
    private static final String MENU_FEED = "feed";
    /** The options' two groups: reading, and the conversation's own settings. */
    private static final String GROUP_READING = "reading";
    private static final String GROUP_SETTINGS = "settings";
    /** A tick: Mark as Read. A pattern until its artwork is painted. */
    private static final OptionGlyph READ_GLYPH = OptionGlyph.pattern(
            "....#",
            "...#.",
            "#.#..",
            ".#...");
    private static final OptionGlyph JUMP_GLYPH = OptionGlyph.sprite(
            LostTalesUiSheet.CHEVRON_5, LostTalesUiSheet.CHEVRON_5_HOVER);
    /**
     * The bell of Notifications and the bubble of Show in Feed, one for
     * each word in {@link ChatLineChoice}'s order: green for Everything,
     * ivory for Only Mentions, crimson for Nothing, each lighting to
     * honey.
     */
    private static final OptionGlyph[] BELL_GLYPHS = {
            OptionGlyph.sprite(LostTalesUiSheet.BELL_EVERYTHING,
                    LostTalesUiSheet.BELL_LIT),
            OptionGlyph.sprite(LostTalesUiSheet.BELL, LostTalesUiSheet.BELL_LIT),
            OptionGlyph.sprite(LostTalesUiSheet.BELL_NOTHING,
                    LostTalesUiSheet.BELL_LIT)};
    private static final OptionGlyph[] FEED_GLYPHS = {
            OptionGlyph.sprite(LostTalesUiSheet.FEED_EVERYTHING,
                    LostTalesUiSheet.FEED_LIT),
            OptionGlyph.sprite(LostTalesUiSheet.FEED, LostTalesUiSheet.FEED_LIT),
            OptionGlyph.sprite(LostTalesUiSheet.FEED_NOTHING,
                    LostTalesUiSheet.FEED_LIT)};
    /** Between a pick's id and one of its words in a choice's id: {@code notify:mentions}. */
    private static final String WORD_SEPARATOR = ":";
    /** The timestamp area's button: the person, for the heads the area holds. */
    private static final ToolStrip.Panel AREA_PANEL = new ToolStrip.Panel(
            LostTalesUiSheet.AREA, LostTalesUiSheet.AREA_HOVER,
            "gui.losttales.chat.area.show", "gui.losttales.chat.area.hide");

    private static final String WHISPER_ID_PREFIX = ChatTabIds.WHISPER_PREFIX;
    private static final String NPC_ID_PREFIX = ChatTabIds.NPC_PREFIX;
    /**
     * Between the account and the identity in a tab's id. A Minecraft
     * account name cannot hold one, so the account is always the part
     * before the first of them and an identity may hold as many as it
     * likes. An id without one is the account's own conversation, which
     * is also what every id stored before identities existed reads as.
     */
    private static final char IDENTITY_SEPARATOR = ChatTabIds.SEPARATOR;
    private static final String OWNER_MARK = ChatTabIds.OWNER_MARK;
    /**
     * The last segment of a scoped plain channel's id, after a
     * separator: the conversation the tab is, a faction or a fellowship.
     * A conversation and not a character — two characters in one faction
     * are in the same conversation, and it outlives either of them.
     */
    private static final String SCOPE_MARK = ChatTabIds.SCOPE_MARK;
    /**
     * One interned tab per channel, by the channel itself. A map rather
     * than a position: the set of channels is open, so there is no fixed
     * length to index into.
     */
    private static final java.util.Map<String, ChatTab> PLAIN =
            new java.util.concurrent.ConcurrentHashMap<String, ChatTab>();

    private final ChatChannel channel;
    private final String partner;
    private final String partnerKey;
    private final String identity;
    private final String identityKey;
    /** This player's identity the conversation is held as; empty for the account. */
    private final String ownerKey;
    private final boolean npc;

    private ChatTab(ChatChannel channel, String partner, String identity,
                    String ownerKey, boolean npc) {
        this.channel = channel;
        this.partner = partner == null ? "" : partner.trim();
        this.partnerKey = this.partner.toLowerCase(Locale.ROOT);
        String named = identity == null ? "" : identity.trim();
        this.identity = named.length() == 0 ? this.partner : named;
        this.identityKey = this.identity.toLowerCase(Locale.ROOT);
        this.ownerKey = ownerKey == null ? "" : ownerKey.trim().toLowerCase(Locale.ROOT);
        this.npc = npc;
    }

    /**
     * The tab of a plain channel; null for the whisper channel, whose
     * tabs each name a partner and come from {@link #whisper}. This is
     * the entry the row and the layout hold, one per channel. A channel
     * that is more than one conversation
     * ({@link ChatChannel#isScoped}) files its lines under a tab
     * per identity — see {@link #of(ChatChannel, String)} — and the one
     * being read is {@link #viewed}.
     */
    public static ChatTab of(ChatChannel channel) {
        // A whisper is always with someone: it has no plain tab.
        if (channel == null || channel == ChatChannel.WHISPER) {
            return null;
        }
        // Made when first asked for rather than all at once, because the
        // set of channels is open: a server names its own, and they are
        // registered long after this class is first read. Kept by id and
        // rebuilt when the registry hands out a new object for that id,
        // so the tab always names the channel in force.
        String key = channel.getId();
        ChatTab cached = PLAIN.get(key);
        if (cached == null || cached.channel != channel) {
            cached = new ChatTab(channel, "", "", "", false);
            PLAIN.put(key, cached);
        }
        return cached;
    }

    /** A conversation's tab, or null for any other kind of tab. */
    public static ChatTab from(WindowTab tab) {
        return tab instanceof ChatTab ? (ChatTab)tab : null;
    }

    /** The conversation in front of a window; null for a page in front, or no window. */
    public static ChatTab frontOf(Window window) {
        return window == null ? null : from(window.getActiveTab());
    }

    /**
     * The tab whose lines are shown while this one is on screen. A
     * channel that is one conversation is its own; a scoped channel's
     * row entry stands for whichever of its conversations the chat is
     * being read as, so the lines shown under it are that identity's;
     * and a person's row entry stands for the conversation that person
     * has with the identity being read. The row holds one Faction tab
     * and one tab per person; which conversation each shows follows
     * the identity, and nothing else in the layout has to know.
     */
    public static ChatTab viewed(ChatTab tab) {
        if (tab == null || tab.npc || tab.ownerKey.length() > 0) {
            return tab;
        }
        if (tab.isWhisper()) {
            return whisper(tab.partner, tab.identity,
                    ClientChatIdentities.viewIdentityKey());
        }
        if (tab.channel == null || !tab.channel.isScoped()) {
            return tab;
        }
        return of(tab.channel,
                ClientChatChannelState.scopeKeyRead(tab.channel));
    }

    /**
     * The row entry a tab belongs to: the plain channel tab for a
     * faction's conversation, the person's tab for a whisper conversation
     * held as one identity, and the tab itself for everything else, a
     * fellowship's conversation among them. The row holds one entry per
     * channel, per person and per fellowship, so a line's own tab is not
     * always a tab a window can hold; anything asking the layout about a
     * line asks about this.
     */
    public static ChatTab row(ChatTab tab) {
        if (tab == null || tab.npc || tab.ownerKey.length() == 0
                || tab.isFellowship()) {
            return tab;
        }
        return tab.isWhisper() ? whisper(tab.partner, tab.identity)
                : of(tab.channel);
    }

    /**
     * One conversation of a scoped channel, named by the conversation
     * itself: a Gondor line and a Rohan line are two conversations, and
     * every character of this player in Gondor reads the same one. A
     * channel that is only ever one conversation ignores the scope and
     * answers with its one tab, and so does an empty scope — the
     * account is in no faction, so it is in no conversation.
     */
    public static ChatTab of(ChatChannel channel, String scopeKey) {
        if (channel == null || !channel.isScoped()
                || scopeKey == null || scopeKey.trim().length() == 0) {
            return of(channel);
        }
        return new ChatTab(channel, "", "", scopeKey, false);
    }

    /**
     * The row entry for whispers with one identity of an account: the
     * person as they were speaking, kept apart from their other
     * characters and from their account. An empty identity is the
     * account's own. Which conversation the entry shows — held as which
     * of this player's identities — follows the identity being read
     * ({@link #viewed}); the entry is also the conversation held as the
     * account itself, which has no identity of its own to name.
     */
    public static ChatTab whisper(String partner, String identity) {
        return whisper(partner, identity, "");
    }

    /**
     * The whisper tab with one identity of an account, held as one of
     * this player's own identities: {@code ownerKey} is the character's
     * id ({@link #ownerKeyOf}), or empty for the account.
     */
    public static ChatTab whisper(String partner, String identity, String ownerKey) {
        String name = partner == null ? "" : partner.trim();
        return name.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, name, identity, ownerKey, false);
    }

    /** The owner key of a character id; empty for null, the account. */
    public static String ownerKeyOf(UUID characterId) {
        return characterId == null ? "" : characterId.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * The conversation tab with a named NPC: a whisper nobody is on the
     * other end of, so replies are echoed locally rather than sent.
     */
    public static ChatTab npc(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() == 0 ? null
                : new ChatTab(ChatChannel.WHISPER, trimmed, trimmed, "", true);
    }

    public ChatChannel getChannel() { return this.channel; }
    /** The account a whisper is routed to; empty otherwise. */
    public String getPartner() { return this.partner; }
    /**
     * The identity the conversation is with — a character's name, or the
     * account's own. Never empty for a whisper.
     */
    public String getPartnerIdentity() { return this.identity; }
    /**
     * This player's identity the tab is read and spoken as: a
     * character's id, lower-cased, or empty for the account. A whisper
     * conversation carries the identity it is held as, and a person's
     * row entry none; a plain tab carries one only on a channel that is
     * more than one conversation.
     */
    public String getOwnerKey() { return this.ownerKey; }
    public boolean isWhisper() { return this.channel == ChatChannel.WHISPER; }
    /** Whether the tab is one fellowship's conversation, named by the fellowship's id. */
    public boolean isFellowship() {
        return this.channel == ChatChannel.FELLOWSHIP && this.ownerKey.length() > 0;
    }
    /**
     * Whether the tab is a conversation the server it is held on keeps
     * apart: a whisper with a player, or a fellowship's. Its tab is
     * remembered for that server, not in the layout, and goes when the
     * session ends.
     */
    public boolean isPlaceConversation() {
        return (isWhisper() && !this.npc) || isFellowship();
    }
    /**
     * The other end a line typed here is sent to: a whisper's partner, a
     * fellowship's id; empty for every other tab.
     */
    public String target() {
        return isWhisper() ? this.partner : isFellowship() ? this.ownerKey : "";
    }
    /** Whether the partner is an NPC rather than a player. */
    public boolean isNpc() { return this.npc; }

    /**
     * The id an NPC's lines in this conversation are signed with, one for
     * its name as its tab is; null for any other tab. The creature cannot
     * give one: a client of this game version makes a new random id up for
     * a creature every time it comes into view, so its lines would stop
     * running on, and its card lose its faction, after every return.
     */
    public UUID npcSpeakerId() {
        return this.npc ? UUID.nameUUIDFromBytes(
                id().getBytes(StandardCharsets.UTF_8)) : null;
    }

    /**
     * Stable id: the channel id, {@code channel|own:<character id>} for a
     * channel read as one identity, {@code whisper:Name},
     * {@code whisper:Name|Identity|own:<character id>} or {@code npc:Name}.
     */
    @Override
    public String id() {
        if (this.npc) {
            return NPC_ID_PREFIX + this.partner;
        }
        if (!isWhisper()) {
            return this.ownerKey.length() == 0 ? this.channel.getId()
                    : this.channel.getId() + IDENTITY_SEPARATOR
                            + SCOPE_MARK + this.ownerKey;
        }
        return ChatTabIds.whisperConversationId(this.partner, this.identity,
                this.ownerKey);
    }

    /** The inverse of {@link #id()}; null for anything unknown. */
    public static ChatTab fromId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(WHISPER_ID_PREFIX)) {
            String rest = trimmed.substring(WHISPER_ID_PREFIX.length());
            String owner = "";
            int lastSeparator = rest.lastIndexOf(IDENTITY_SEPARATOR);
            if (lastSeparator >= 0 && rest.substring(lastSeparator + 1)
                    .toLowerCase(Locale.ROOT).startsWith(OWNER_MARK)) {
                owner = rest.substring(lastSeparator + 1 + OWNER_MARK.length());
                rest = rest.substring(0, lastSeparator);
            }
            int separator = rest.indexOf(IDENTITY_SEPARATOR);
            return separator < 0 ? whisper(rest, "", owner)
                    : whisper(rest.substring(0, separator),
                            rest.substring(separator + 1), owner);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(NPC_ID_PREFIX)) {
            return npc(trimmed.substring(NPC_ID_PREFIX.length()));
        }
        String scope = "";
        String channelPart = trimmed;
        int separator = trimmed.indexOf(IDENTITY_SEPARATOR);
        if (separator >= 0) {
            String rest = trimmed.substring(separator + 1);
            // Only a conversation, and only one written as a conversation:
            // an id naming a character here names no conversation, so it
            // reads as unknown.
            if (!rest.toLowerCase(Locale.ROOT).startsWith(SCOPE_MARK)) {
                return null;
            }
            scope = rest.substring(SCOPE_MARK.length());
            channelPart = trimmed.substring(0, separator);
        }
        ChatChannel channel = ChatChannel.fromId(channelPart);
        if (channel == null || channel == ChatChannel.WHISPER) {
            return null;
        }
        return scope.length() == 0 ? of(channel) : of(channel, scope);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatTab)) {
            return false;
        }
        ChatTab tab = (ChatTab)other;
        // By the channel's id, never the object: a channel a server
        // defines is registered again with every access broadcast, and
        // two tabs naming the same channel must stay the same tab across
        // that. It is also what keeps this in step with hashCode.
        return tab.channel.getId().equals(this.channel.getId())
                && tab.npc == this.npc
                && tab.partnerKey.equals(this.partnerKey)
                && tab.identityKey.equals(this.identityKey)
                && tab.ownerKey.equals(this.ownerKey);
    }

    @Override
    public int hashCode() {
        return (((this.channel.getId().hashCode() * 31 + this.partnerKey.hashCode())
                * 31 + this.identityKey.hashCode()) * 31 + this.ownerKey.hashCode()) * 2
                + (this.npc ? 1 : 0);
    }

    /** The channel's shown name; for a whisper the partner's. */
    @Override
    public String title() {
        return ClientChatChannelState.displayName(this);
    }

    @Override
    public int tone() {
        return ClientChatChannelState.displayColor(this);
    }

    @Override
    public boolean hasIcon() {
        return ChatChannelIcons.iconOf(this) != null;
    }

    @Override
    public void drawIcon(Minecraft minecraft, float x, float y, int alpha,
                         TabMark mark) {
        ChatChannelIcons.draw(minecraft, this, x, y, alpha, mark);
    }

    /**
     * Its pings on the tile — every unread line, for a conversation with
     * one person — else the sphere while anything is unread.
     */
    @Override
    public TabMark mark() {
        if (isWhisper()) {
            return TabMark.pings(ClientChatChannelViews.unreadCount(this));
        }
        int pings = ClientChatChannelViews.unreadPingCount(this);
        if (pings > 0) {
            return TabMark.pings(pings);
        }
        return ClientChatChannelViews.unreadOtherCount(this) > 0
                ? TabMark.UNREAD : TabMark.NONE;
    }

    /** Unsent words, unless it is the tab being typed in: those are in the field. */
    @Override
    public boolean hasDraft() {
        return !equals(ClientChatChannelState.getSelected())
                && ClientChatChannelState.getDraft(this).length() > 0;
    }

    /** None of its lines chime: its Notifications is Nothing. */
    @Override
    public boolean isMuted() {
        return ChatLayout.isMuted(this);
    }

    @Override
    public boolean isReadOnly() {
        return !ClientChatChannelState.canSend(this);
    }

    /** A conversation shows while its channel is open to the player. */
    @Override
    public boolean isAvailable() {
        return ClientChatChannelState.isAvailable(this);
    }

    /**
     * Only a plain channel's own tab: a whisper and one conversation of
     * a scoped channel end with the session. The row holds the channel;
     * which conversation it shows follows the identity being read.
     */
    @Override
    public ToolStrip.Panel panel() {
        return AREA_PANEL;
    }

    /** The timestamp area is a conversation's panel. */
    @Override
    public boolean isPanelOut(Window window) {
        return !ChatLayout.isAreaHidden(window);
    }

    @Override
    public void togglePanel(Window window) {
        ChatLayout.setAreaHidden(window.getId(),
                !ChatLayout.isAreaHidden(window));
    }

    @Override
    public boolean hasMemberList() {
        return true;
    }

    @Override
    public boolean isMemberListOut(Window window) {
        return !ChatLayout.isMembersHidden(window);
    }

    @Override
    public void toggleMemberList(Window window) {
        ChatLayout.setMembersHidden(window.getId(),
                !ChatLayout.isMembersHidden(window));
    }

    /** A conversation always has its four options. */
    @Override
    public boolean hasOptions() {
        return true;
    }

    /** Every conversation's cog opens the one Chat Settings. */
    @Override
    public Settings.Place settingsPlace() {
        return Settings.Place.CHAT;
    }

    /**
     * A conversation's help: who reads it and who you speak as there, the
     * chat's guide, and the chat's keys.
     */
    @Override
    public PageHelp help() {
        List<String> guide = new ArrayList<String>();
        guide.add(StatCollector.translateToLocalFormatted(readersKey(),
                title()));
        String speaking = speakingKey();
        if (speaking != null) {
            guide.add(StatCollector.translateToLocal(speaking));
        }
        guide.addAll(PageHelp.paragraphs("gui.losttales.help.chat"));
        return new PageHelp(guide, ChatShortcuts.areas());
    }

    /** The line that says who reads the conversation, by who its lines reach. */
    private String readersKey() {
        String prefix = "gui.losttales.help.chat.readers.";
        if (isWhisper()) {
            return prefix + (this.npc ? "npc" : "whisper");
        }
        if (this.channel.getAccess() == ChatChannelAccess.FELLOWSHIP_MEMBERSHIP) {
            return prefix + "fellowship";
        }
        switch (this.channel.getRecipientRule()) {
            case PROXIMITY:
                return prefix + "proximity";
            case FACTION:
                return prefix + "faction";
            case SELF:
                return prefix + "self";
            case CONSOLE_READERS:
                return prefix + "console";
            case OPERATORS:
                return prefix + "operators";
            default:
                return prefix + "everyone";
        }
    }

    /**
     * The line that says who you speak as: in character as the character
     * you play where the channel says so, else as your chat identity; out
     * of character as your account. None where nobody else reads it.
     */
    private String speakingKey() {
        String prefix = "gui.losttales.help.chat.speaking.";
        if (this.channel.getRecipientRule() == ChatRecipientRule.SELF) {
            return null;
        }
        if (!ChatRolePresentation.isInCharacter(this.channel)) {
            return prefix + "account";
        }
        return prefix + (ChatRolePresentation.speaksAsPlayedCharacter(
                this.channel) ? "played" : "identity");
    }

    /**
     * A messenger's channel options. Mark as Read, the counters and the
     * divider gone at once, and Jump to First Unread, the tab brought
     * forward and its history taken to where the unread run begins; each
     * greyed while nothing waits unread. Then Notifications and Show in
     * Feed (Everything, Only Mentions or Nothing: which lines chime, and
     * which reach the closed feed), each opening its three words in a
     * sub-window of its own: the channel's own settings. Their glyphs wear
     * the colour of the word chosen.
     */
    @Override
    public List<PageOption> options() {
        List<PageOption> options = new ArrayList<PageOption>(4);
        boolean divided = ClientChatChannelViews.unreadDividerLine(this) != null;
        boolean unread = ClientChatChannelViews.hasUnread(this) || divided;
        options.add(PageOption.action(MENU_MARK_READ,
                word("gui.losttales.chat.tab.mark_read"), READ_GLYPH)
                .unavailable(unread ? "" : word("gui.losttales.chat.tab.nothing_unread"))
                .inGroup(GROUP_READING, ""));
        options.add(PageOption.action(MENU_JUMP_UNREAD,
                word("gui.losttales.chat.tab.jump_unread"), JUMP_GLYPH)
                .unavailable(divided ? "" : word("gui.losttales.chat.tab.nothing_unread"))
                .inGroup(GROUP_READING, ""));
        options.add(pick(MENU_NOTIFY, "gui.losttales.chat.tab.notify",
                ChatLayout.notification(this), BELL_GLYPHS));
        options.add(pick(MENU_FEED, "gui.losttales.chat.tab.feed",
                ChatLayout.feedChoice(this), FEED_GLYPHS));
        return options;
    }

    /**
     * One of the conversation's two settings: its three words, each with
     * its glyph and what it does under the pointer, the one {@code chosen}
     * marked; the option wears the chosen word's glyph.
     */
    private static PageOption pick(String id, String labelKey,
                                   ChatLineChoice chosen,
                                   OptionGlyph[] glyphs) {
        List<PageOption> words = new ArrayList<PageOption>(3);
        for (ChatLineChoice choice : ChatLineChoice.values()) {
            words.add(PageOption.choice(id + WORD_SEPARATOR + choice.id(),
                    word(choice.labelKey()), choice == chosen,
                    glyphs[choice.ordinal()]).explained(word(
                    "gui.losttales.chat.choice." + id + "." + choice.id())));
        }
        return PageOption.pick(id, word(labelKey), word(chosen.labelKey()),
                glyphs[chosen.ordinal()], words).inGroup(GROUP_SETTINGS, "");
    }

    /** The word a choice's id picks of {@code pick}'s three, or null for an id of another option. */
    private static ChatLineChoice wordOf(String pick, String id) {
        String prefix = pick + WORD_SEPARATOR;
        return id.startsWith(prefix)
                ? ChatLineChoice.fromId(id.substring(prefix.length())) : null;
    }

    private static String word(String key) {
        return StatCollector.translateToLocal(key);
    }

    /**
     * A word picked for one of the settings stays, and so does its
     * sub-window; reading and jumping are done with the menu.
     */
    @Override
    public boolean takeOption(String id) {
        ChatLineChoice notify = wordOf(MENU_NOTIFY, id);
        if (notify != null) {
            ChatLayout.setNotification(this, notify);
            return true;
        }
        ChatLineChoice feed = wordOf(MENU_FEED, id);
        if (feed != null) {
            ChatLayout.setFeedChoice(this, feed);
            return true;
        }
        if (MENU_MARK_READ.equals(id)) {
            ClientChatChannelViews.markViewed(this);
            ClientChatChannelViews.dismissDivider(this);
        } else if (MENU_JUMP_UNREAD.equals(id)) {
            Integer first = ClientChatChannelViews.unreadDividerLine(this);
            WindowScreen screen = WindowScreen.current();
            if (screen != null) {
                screen.jumpToTab(this);
            }
            if (first != null) {
                // The rows exist once the tab has been drawn; the next
                // draw lands on the run's first line.
                LostTalesChatPresentation.requestJump(first.intValue());
            }
        }
        return false;
    }

    @Override
    public String searchPrompt() {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.message_search.prompt", title());
    }

    /** A conversation's search is walked, the match stood on of how many. */
    @Override
    public boolean walksSearch() {
        return true;
    }

    @Override
    public int searchFound() {
        return ChatSearch.matchCount();
    }

    @Override
    public String searchCount() {
        return ChatSearch.position() + "/" + ChatSearch.matchCount();
    }

    @Override
    public boolean isKeptInLayout() {
        return !isWhisper() && this.ownerKey.length() == 0;
    }
}
