package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

/** Builds structured legacy chat components and records entry-animation time. */
public final class LostTalesChatPresentation {
    private static volatile long lastMessageNanos;
    private static volatile int lastMessageUpdateCounter = -1;
    private static volatile ChatTab lastMessageTab;
    private static int nextChatLineId = Integer.MIN_VALUE;
    /** Pinged line ids remembered: as many as the history can hold. */
    private static final int MAX_PINGED_LINES =
            LostTalesChatHistoryHooks.MAX_CAPACITY;
    private static final LinkedHashSet<Integer> pingedChatLineIds =
            new LinkedHashSet<Integer>();
    private static final int[] NO_SHOWCASES = new int[0];

    private LostTalesChatPresentation() {}

    public static void receive(LostTalesChatMessagePacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (packet == null || packet.isMalformed() || minecraft == null
                || minecraft.ingameGUI == null) {
            return;
        }
        ChatChannel channel = packet.getChannel();
        // A Discord sender has no Minecraft account to look a skin up for.
        if (channel.getIdentityType()
                == com.ninuna.losttales.chat.ChatIdentityType.ACCOUNT
                && channel != ChatChannel.DISCORD) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    packet.getIdentityName());
        }
        // The name this line was signed with, and what the server says
        // it wears: that is where a mention of it takes its colour from.
        ClientChatAccountRoles.remember(packet.getIdentityName(),
                packet.getRoles());
        boolean mentioned = LostTalesConfig.enableChatPings
                && isLocalPlayerMentioned(minecraft, channel,
                        packet.getMessage());
        // A whisper lands in the tab of its conversation, opened on the
        // first message in the window the player is typing in; a plain
        // channel the player closed reopens the same way. A hidden tab
        // stays closed: its lines are still filed and counted unread,
        // and the tab shows them all once it is restored.
        ChatTab tab;
        if (channel == ChatChannel.WHISPER) {
            ChatTab conversation = ChatTab.whisper(packet.getPartner());
            tab = conversation != null
                    && !ChatWindowLayout.isOpen(conversation)
                    && ChatWindowLayout.isHidden(conversation)
                    ? conversation
                    : ChatWindowLayout.openWhisper(packet.getPartner(),
                            windowIdOfSelection());
        } else {
            tab = ChatTab.of(channel);
            if (tab != null && !ChatWindowLayout.isOpen(tab)
                    && !ChatWindowLayout.isHidden(tab)) {
                ChatWindowLayout.openTab(tab, windowIdOfSelection());
            }
        }
        if (tab == null) {
            return;
        }
        if (tab.isWhisper() && minecraft.thePlayer != null
                && !minecraft.thePlayer.getUniqueID().equals(
                        packet.getSenderId())) {
            // Their half of the conversation says what colour it is in.
            ClientChatChannelState.rememberPartnerColor(tab,
                    packet.getNameColor());
        }
        int chatLineId = print(minecraft, packet, tab, mentioned);
        if (mentioned || tab.isWhisper()) {
            if (mentioned) {
                markPinged(chatLineId);
            }
            // The highlight stays for when the tab is read; the cue is
            // silenced by the tab's own preference alone — a closed tab
            // still receives — and a whisper is always a cue.
            if (ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
    }

    private static int print(Minecraft minecraft,
                             LostTalesChatMessagePacket packet, ChatTab tab,
                             boolean mentioned) {
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                build(packet, tab, decodeShowcases(packet)), chatLineId);
        noteLinePrinted(minecraft, chat, chatLineId, tab, mentioned);
        return chatLineId;
    }

    /**
     * The player's own line in an NPC conversation: nobody is on the other
     * end, so nothing is sent; the line is shown here exactly as a
     * whisper of theirs would be, filed under the NPC's tab.
     */
    public static boolean echoToNpc(ChatTab tab, String message) {
        return echoToNpc(tab, message, null);
    }

    /**
     * As above with the things the player shared. Nobody is on the other
     * end of an NPC's conversation, so no server ever validates them:
     * the client resolved them from its own inventory and marker cache,
     * and they are shown to the player who shared them and to nobody
     * else.
     */
    public static boolean echoToNpc(ChatTab tab, String message,
                                    List<ChatShowcase> showcases) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || !tab.isNpc() || message == null
                || !ChatMessageValidator.isValid(message)
                || minecraft == null || minecraft.ingameGUI == null
                || minecraft.thePlayer == null) {
            return false;
        }
        String account = minecraft.thePlayer.getCommandSenderName();
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, minecraft.thePlayer.getUniqueID(),
                account, account, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                message, System.currentTimeMillis(), "", showcases, "",
                tab.getPartner());
        LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                minecraft, packet.getSenderId(), account);
        if (ChatWindowLayout.openTab(tab, windowIdOfSelection()) == null) {
            return false;
        }
        print(minecraft, packet, tab, false);
        return true;
    }

    private static String windowIdOfSelection() {
        ChatWindow window = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        return window == null ? null : window.getId();
    }

    /**
     * The mention cue, only on this client and only because this client's
     * own names matched: the server never triggers it and no other client
     * can hear it. Played as a UI sound so the player's position, the
     * dimension, or a respawn in progress cannot swallow or duplicate it.
     */
    private static void playPingSound(Minecraft minecraft) {
        String sound = LostTalesConfig.chatPingSound == null
                ? "" : LostTalesConfig.chatPingSound.trim();
        if (sound.length() == 0 || minecraft.getSoundHandler() == null) {
            return;
        }
        minecraft.getSoundHandler().playSound(
                new LostTalesChatPingSound(new ResourceLocation(sound)));
    }

    /** Records animation timing and the line's tab for the tab views. */
    private static void noteLinePrinted(Minecraft minecraft, GuiNewChat chat,
                                        int chatLineId, ChatTab tab,
                                        boolean mentioned) {
        lastMessageUpdateCounter = minecraft.ingameGUI.getUpdateCounter();
        lastMessageNanos = System.nanoTime();
        lastMessageTab = tab;
        ClientChatChannelViews.record(chatLineId, tab,
                ClientChatChannelState.getSelected(), mentioned);
        ClientChatChannelViews.onLinesAdded(tab,
                LostTalesChatOverlayRenderer.countLeadingLines(
                        chat, chatLineId));
    }

    /**
     * Decodes each validated showcase exactly once and registers it for the
     * renderer; the result maps token index to store key (-1 when the
     * server attached nothing for that token).
     */
    private static int[] decodeShowcases(LostTalesChatMessagePacket packet) {
        List<ChatShowcase> showcases = packet.getShowcases();
        if (showcases == null || showcases.isEmpty()) {
            return NO_SHOWCASES;
        }
        int[] ids = new int[ChatShareTokenParser.MAX_TOKENS];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = -1;
        }
        for (ChatShowcase showcase : showcases) {
            if (showcase.getKind() == ChatShareKind.ITEM) {
                ItemStack stack = ChatShowcase.decodeStack(
                        showcase.getStackData());
                if (stack != null) {
                    ids[showcase.getTokenIndex()] =
                            ClientChatShowcaseStore.registerItem(stack);
                }
            } else {
                ids[showcase.getTokenIndex()] =
                        ClientChatShowcaseStore.registerMarker(showcase);
            }
        }
        return ids;
    }

    /**
     * The local account name, the active character's name, and — on the
     * account channels only — the name of every role this player holds,
     * so {@code @Operator} reaches the operators and nobody else. A role
     * is an account fact and means nothing in character, so it is not
     * addressable in the role-playing channels. Which roles the player
     * holds is the server's word, sent with the chat access; nothing
     * here is decided from the message.
     */
    private static boolean isLocalPlayerMentioned(
            Minecraft minecraft, ChatChannel channel, String message) {
        List<String> names = new ArrayList<String>(2);
        if (minecraft.thePlayer != null) {
            names.add(minecraft.thePlayer.getCommandSenderName());
        }
        if (channel != null
                && channel.getIdentityType() == ChatIdentityType.ACCOUNT) {
            for (ChatAccountRole role
                    : ClientChatChannelState.localRoles()) {
                if (role.isMentionable()) {
                    names.add(StatCollector.translateToLocal(
                            role.getNameKey()));
                }
            }
        }
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = snapshot == null
                ? null : snapshot.getActiveCharacter();
        if (active != null) {
            names.add(active.getName());
        }
        return ChatMentions.mentionsAny(message, names);
    }

    /** Remembers a mention so every wrapped line of it stays highlighted. */
    static void markPinged(int chatLineId) {
        pingedChatLineIds.add(Integer.valueOf(chatLineId));
        while (pingedChatLineIds.size() > MAX_PINGED_LINES) {
            Iterator<Integer> iterator = pingedChatLineIds.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    static boolean isPingedLine(int chatLineId) {
        return pingedChatLineIds.contains(Integer.valueOf(chatLineId));
    }

    static long getLastMessageNanos() {
        return lastMessageNanos;
    }

    static int getLastMessageUpdateCounter() {
        return lastMessageUpdateCounter;
    }

    static ChatTab getLastMessageTab() {
        return lastMessageTab;
    }

    public static void clear() {
        lastMessageNanos = 0L;
        lastMessageUpdateCounter = -1;
        lastMessageTab = null;
        nextChatLineId = Integer.MIN_VALUE;
        pingedChatLineIds.clear();
    }

    private static int allocateChatLineId() {
        int allocated = nextChatLineId;
        nextChatLineId = nextChatLineId == -1
                ? Integer.MIN_VALUE : nextChatLineId + 1;
        return allocated;
    }

    static IChatComponent build(LostTalesChatMessagePacket packet) {
        return build(packet, tabOf(packet), NO_SHOWCASES);
    }

    /** The tab a packet would be filed under, read from the packet alone. */
    private static ChatTab tabOf(LostTalesChatMessagePacket packet) {
        return packet.getChannel() == ChatChannel.WHISPER
                ? ChatTab.whisper(packet.getPartner())
                : ChatTab.of(packet.getChannel());
    }

    /**
     * The line as it is shown, filed under {@code tab}. The tab is the
     * caller's, not the packet's: an NPC conversation and a whisper with
     * a player of the same name are two different tabs, and only the
     * caller knows which one this line belongs to.
     */
    static IChatComponent build(LostTalesChatMessagePacket packet,
                                ChatTab tab, int[] showcaseIds) {
        ChatChannel channel = packet.getChannel();
        ChatComponentText root = new ChatComponentText("");
        ChatTab named = tab == null ? tabOf(packet) : tab;
        // The prefix names the tab, so it is the colour the tab is drawn
        // in: a conversation speaks in the other party's colour, every
        // other channel in its own. Nothing in the feed may name a
        // channel in a colour its tab does not use. Faction takes the
        // sender's own faction colour, which the server put on the line
        // and which is the receiver's too — the server routes faction
        // chat to members only.
        appendChannelPrefix(root, named, channel == ChatChannel.FACTION
                ? packet.getNameColor()
                : ClientChatChannelState.displayColor(named));
        // (An NPC conversation names the same partner, so its prefix
        // reads the same.)
        appendTimestamp(root, packet.getTimestampMillis());
        // Continuation lines of a wrapped message align here, under the
        // sender's opening bracket; see ChatLineWrapper.
        root.appendSibling(ChatLayoutMarker.anchor());

        // The server's word on an account line's sender: every role it
        // holds, tagged ahead of the name in the role's own colour. The
        // name's colour is the primary role's too, but that is already
        // the packet's name colour — the server set it when it built the
        // line, so nothing here decides what a role looks like.
        for (ChatAccountRole role : ChatAccountRole.fromMask(
                packet.getRoles())) {
            root.appendSibling(ChatColorMarker.apply(
                    text(StatCollector.translateToLocal(role.getTagKey())
                            + " ", nearestFormatting(role.getColor()),
                            false),
                    role.getColor()));
        }
        // The brackets are part of the name: they answer to a hover
        // and a click exactly as it does, so the card comes up wherever
        // the pointer is over the sender. Their colour comes from the
        // head marker, which is where every part of the name takes it.
        String whisper = "/msg " + packet.getAccountName() + " ";
        root.appendSibling(reply(text("<", nearestFormatting(
                packet.getNameColor()), false), whisper));
        // Two bold spaces stand in for the head; what the line actually
        // advances by is the slot ChatInlineIcons declares, which every
        // walk over the line — drawing, wrapping, hit testing — reads.
        // The spaces are only so the raw text has something there.
        ChatComponentText marker = text("  ",
                EnumChatFormatting.WHITE, true);
        marker.setChatStyle(marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        ChatHeadMarker.encode(packet.getSenderId(),
                                channel.getIdentityType(),
                                packet.getSkinId(), packet.getMessage(),
                                packet.getTitleColor(),
                                packet.getNameColor()))));
        root.appendSibling(marker);

        root.appendSibling(reply(text(packet.getIdentityName(),
                nearestFormatting(packet.getNameColor()), false), whisper));
        if (packet.getTitle().length() > 0) {
            // LOTR's NPC naming, "Name, the Gondor Farmer": the epithet is
            // the sender's faction and title; an untitled sender gets no
            // comma, no "the", nothing.
            String epithet = epithet(packet.getFactionName(),
                    packet.getTitle());
            root.appendSibling(ChatTitleMarker.apply(
                    text(translate("chat.losttales.title.suffix",
                            ", the %s", epithet),
                            nearestFormatting(packet.getTitleColor()),
                            false),
                    packet.getTitleColor(), epithet));
        }
        // The closing bracket keeps the same clear space from what
        // precedes it that the opening one keeps from the head; a glyph
        // carries one pixel of that as its own trailing space.
        root.appendSibling(ChatSpacerMarker.of(
                ChatInlineIcons.NAME_GAP - 1));
        root.appendSibling(reply(text("> ", nearestFormatting(
                packet.getNameColor()), false), whisper));
        appendMessageBody(root, packet.getMessage(), showcaseIds,
                channel);
        return root;
    }

    /**
     * A component that answers to the pointer as the sender's name
     * does: the same whisper on a click, and so the same card on a
     * hover. Its colour comes from the line's head marker, which is
     * where every part of a sender's name takes it.
     */
    private static ChatComponentText reply(ChatComponentText part,
                                           String whisper) {
        part.setChatStyle(part.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, whisper)));
        return part;
    }

    /**
     * {@code Gondor Farmer}: the faction name before the title, or the
     * bare title when the sender's faction is unknown.
     */
    static String epithet(String factionName, String title) {
        String faction = factionName == null ? "" : factionName.trim();
        String bare = title == null ? "" : title.trim();
        if (faction.length() == 0) {
            return bare;
        }
        return translate("chat.losttales.title.epithet", "%s %s",
                faction, bare);
    }

    /**
     * A localized format with an English fallback, so the line is still
     * right when the language file does not carry the key.
     */
    private static String translate(String key, String fallback,
                                    Object... arguments) {
        String format = StatCollector.translateToLocal(key);
        if (format == null || format.length() == 0 || format.equals(key)) {
            format = fallback;
        }
        try {
            return String.format(format, arguments);
        } catch (IllegalArgumentException ignored) {
            return String.format(fallback, arguments);
        }
    }

    /**
     * Prints a vanilla or third-party line that Lost Tales classified
     * into a channel — an achievement, a death message, command output,
     * a fast-travel countdown — with the channel prefix and timestamp
     * every other line carries and a tracked line id, so the feed names
     * its channel and the tabs can file it. The component itself is the
     * server's, untouched; no head, no mention check.
     */
    public static boolean receiveSystemLine(IChatComponent message,
                                            ChatChannel channel) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (message == null || channel == null || minecraft == null
                || minecraft.ingameGUI == null) {
            return false;
        }
        // A system line reopens its closed channel exactly as a player
        // message does — an achievement brings Global back, a command's
        // answer the console — unless the channel is hidden.
        ChatTab tab = ChatTab.of(channel);
        if (!ChatWindowLayout.isOpen(tab)
                && !ChatWindowLayout.isHidden(tab)) {
            ChatWindowLayout.openTab(tab, windowIdOfSelection());
        }
        // A system line naming this player — their achievement, an NPC
        // notice — is a mention like any other: the name becomes
        // @Name, the line highlights, and the cue sounds.
        boolean mentioned = LostTalesConfig.enableChatPings
                && mentionLocalNames(message, localMentionNames(minecraft));
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                buildSystemLine(message, channel,
                        System.currentTimeMillis()), chatLineId);
        ClientChatChannelViews.record(chatLineId, tab,
                ClientChatChannelState.getSelected(), mentioned);
        ClientChatChannelViews.onLinesAdded(tab,
                LostTalesChatOverlayRenderer.countLeadingLines(
                        chat, chatLineId));
        if (mentioned) {
            markPinged(chatLineId);
            if (ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
        if (channel == ChatChannel.CONSOLE && chat.getChatOpen()) {
            frontConsole();
        }
        return true;
    }

    /** The names a line may address this client by: account, character. */
    private static List<String> localMentionNames(Minecraft minecraft) {
        List<String> names = new ArrayList<String>(2);
        if (minecraft != null && minecraft.thePlayer != null) {
            names.add(minecraft.thePlayer.getCommandSenderName());
        }
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        CharacterSummary active = snapshot == null
                ? null : snapshot.getActiveCharacter();
        if (active != null && active.getName() != null
                && active.getName().trim().length() > 0) {
            names.add(active.getName().trim());
        }
        return names;
    }

    /**
     * Rewrites this player's name inside a system line into a mention:
     * a component whose whole text is one of the names — the way an
     * achievement, a join line or a death names its player — becomes
     * {@code @Name} in the mention's colour, carrying the mention
     * marker so it answers to the pointer. Edited in place, before the
     * line is printed; true when anything matched.
     */
    private static boolean mentionLocalNames(IChatComponent root,
                                             List<String> names) {
        if (root == null || names.isEmpty()) {
            return false;
        }
        boolean replaced = false;
        if (root instanceof ChatComponentTranslation) {
            Object[] arguments =
                    ((ChatComponentTranslation)root).getFormatArgs();
            for (int index = 0; arguments != null
                    && index < arguments.length; index++) {
                if (!(arguments[index] instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent argument = (IChatComponent)arguments[index];
                IChatComponent mention = asMention(argument, names);
                if (mention != null) {
                    arguments[index] = mention;
                    replaced = true;
                } else {
                    replaced |= mentionLocalNames(argument, names);
                }
            }
        }
        List<?> siblings = root.getSiblings();
        for (int index = 0; siblings != null
                && index < siblings.size(); index++) {
            Object value = siblings.get(index);
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent sibling = (IChatComponent)value;
            IChatComponent mention = asMention(sibling, names);
            if (mention != null) {
                @SuppressWarnings("unchecked")
                List<Object> mutable = (List<Object>)siblings;
                mutable.set(index, mention);
                replaced = true;
            } else {
                replaced |= mentionLocalNames(sibling, names);
            }
        }
        return replaced;
    }

    /**
     * The mention a leaf component becomes when its whole text is one of
     * the names, or null. Vanilla and LOTR put the player's name in a
     * component of its own, so whole-text matching reaches exactly them
     * without splitting anybody's prose.
     */
    private static IChatComponent asMention(IChatComponent component,
                                            List<String> names) {
        if (!(component instanceof ChatComponentText)
                || !component.getSiblings().isEmpty()) {
            return null;
        }
        String text = component.getUnformattedTextForChat().trim();
        if (text.length() == 0) {
            return null;
        }
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (name == null || !text.equalsIgnoreCase(name.trim())) {
                continue;
            }
            int color = ChatMentionColors.colorOf(text, null);
            if (color < 0) {
                color = LostTalesColors.rgb(LostTalesColors.HONEY);
            }
            String account = ChatMentionColors.accountFor(text);
            ChatComponentText piece = text("@" + text,
                    nearestFormatting(color), false);
            return account != null
                    ? ChatMentionMarker.apply(piece, color, account)
                    : ChatColorMarker.apply(piece, color);
        }
        return null;
    }

    /**
     * Console output while the screen is open brings the console tab to
     * the front of its window, so the player sees it. In the window the
     * player is typing in that means selecting it; in another window the
     * input stays where it is and only the tab comes forward.
     */
    private static void frontConsole() {
        ChatTab console = ChatTab.of(ChatChannel.CONSOLE);
        ChatWindow window = ChatWindowLayout.windowOf(console);
        if (window == null) {
            return;
        }
        ChatWindow current = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        if (window == current) {
            ClientChatChannelState.select(console);
        } else {
            ChatWindowLayout.setActiveTab(console);
        }
    }

    /**
     * {@code Channel: [HH:mm] } ahead of the server's own component, with
     * the anchor that lets continuation lines indent under the text.
     */
    static IChatComponent buildSystemLine(IChatComponent message,
                                          ChatChannel channel,
                                          long timestampMillis) {
        ChatComponentText root = new ChatComponentText("");
        appendChannelPrefix(root, ChatTab.of(channel),
                ClientChatChannelState.displayColor(channel));
        appendTimestamp(root, timestampMillis);
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(message);
        return root;
    }

    /**
     * Prints an LOTR NPC speech line styled like a player message, the
     * name in the colour the caller resolved — the NPC's faction colour,
     * like a role-playing character's — so the line, the tab and the
     * conversation read as one; the body stays the plain ivory.
     */
    public static boolean receiveNpcSpeech(ChatTab tab, UUID npcId,
                                           String npcName,
                                           String texturePath,
                                           String message,
                                           int nameColor) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || npcId == null || npcName == null
                || npcName.length() == 0 || message == null
                || message.length() == 0 || minecraft == null
                || minecraft.ingameGUI == null) {
            return false;
        }
        // The tab wears the same portrait the line is drawn with, and
        // is named in the same colour the NPC's own name is.
        ChatChannelIcons.rememberNpcPortrait(tab, texturePath);
        ClientChatChannelState.rememberPartnerColor(tab,
                nameColor & 0xFFFFFF);
        if (tab.isWhisper()) {
            if (!ChatWindowLayout.isOpen(tab)
                    && ChatWindowLayout.isHidden(tab)) {
                // A hidden conversation stays closed; the speech falls
                // back to LOTR's own chat line rather than vanishing.
                return false;
            }
            if (ChatWindowLayout.openTab(tab, windowIdOfSelection())
                    == null) {
                return false;
            }
        }
        // An NPC speaking this player's name is addressing them: the
        // name reads as the mention it is, the line highlights, and the
        // cue sounds.
        List<String> localNames = localMentionNames(minecraft);
        boolean mentioned = LostTalesConfig.enableChatPings
                && ChatMentions.mentionsAny(ChatMentions.mentionNames(
                        message, localNames), localNames);
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                buildNpcSpeech(tab, npcId, npcName,
                        texturePath, message,
                        System.currentTimeMillis(), nameColor),
                chatLineId);
        noteLinePrinted(minecraft, chat, chatLineId, tab, mentioned);
        if (mentioned) {
            markPinged(chatLineId);
            if (ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
        return true;
    }

    static IChatComponent buildNpcSpeech(ChatTab tab, UUID npcId,
                                         String npcName,
                                         String texturePath,
                                         String message,
                                         long timestampMillis,
                                         int nameColor) {
        ChatComponentText root = new ChatComponentText("");
        appendChannelPrefix(root, tab,
                ClientChatChannelState.displayColor(tab));
        appendTimestamp(root, timestampMillis);
        root.appendSibling(ChatLayoutMarker.anchor());
        nameColor &= 0xFFFFFF;
        int bodyColor = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        root.appendSibling(ChatColorMarker.apply(
                text("<", nearestFormatting(nameColor), false),
                nameColor));
        ChatComponentText marker = text("  ",
                EnumChatFormatting.WHITE, true);
        marker.setChatStyle(marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        ChatHeadMarker.encodeNpc(npcId, texturePath,
                                message, bodyColor, nameColor))));
        root.appendSibling(marker);
        root.appendSibling(ChatColorMarker.apply(
                text(npcName, nearestFormatting(nameColor), false),
                nameColor));
        root.appendSibling(ChatColorMarker.apply(
                text("> ", nearestFormatting(nameColor), false),
                nameColor));
        // The player's bare name in the speech is shown as the mention
        // it is; the head marker above keeps the words as spoken, so a
        // copy is untouched.
        appendMessageBody(root, ChatMentions.mentionNames(message,
                        localMentionNames(Minecraft.getMinecraft())),
                NO_SHOWCASES, ChatChannel.WHISPER);
        return root;
    }

    /**
     * Channel label as the receiving client names it, carried on prefix
     * markers so the renderer can drop it while the chat screen is open
     * (the tabs separate channels there) and show it in the closed HUD's
     * combined feed. Faction shows the local active character's faction
     * name, which is the sender's faction too because the server only
     * routes faction chat to members.
     */
    private static void appendChannelPrefix(ChatComponentText root,
                                            ChatTab tab,
                                            int channelColor) {
        root.appendSibling(ChatPrefixMarker.channel(
                text(ClientChatChannelState.displayName(tab),
                        nearestFormatting(channelColor), false),
                channelColor));
        root.appendSibling(ChatPrefixMarker.channel(
                text(": ", nearestFormatting(channelColor), false),
                channelColor));
    }

    /**
     * {@code [HH:mm] } in the palette's sand with the time itself — digits
     * and their colon — italic; the brackets stay upright. Marked as a
     * timestamp run, so the closed feed leaves it out: the feed is a
     * glance at what was just said, not a log to read times off.
     */
    private static void appendTimestamp(ChatComponentText root,
                                        long timestampMillis) {
        if (!LostTalesConfig.showChatTimestamps) {
            return;
        }
        String formatted = "[" + ChatTimestampFormatter.format(
                timestampMillis) + "] ";
        int color = LostTalesColors.rgb(LostTalesColors.SAND);
        int index = 0;
        while (index < formatted.length()) {
            boolean time = isTimeCharacter(formatted.charAt(index));
            int end = index;
            while (end < formatted.length() && isTimeCharacter(
                    formatted.charAt(end)) == time) {
                end++;
            }
            ChatComponentText run = text(formatted.substring(index, end),
                    nearestFormatting(color), false);
            if (time) {
                run.getChatStyle().setItalic(Boolean.TRUE);
            }
            root.appendSibling(ChatPrefixMarker.timestamp(run, color));
            index = end;
        }
    }

    private static boolean isTimeCharacter(char character) {
        return Character.isDigit(character) || character == ':';
    }

    /**
     * Share tokens with a validated payload become an icon slot plus the
     * bracketed name; every other token stays the literal text the sender
     * typed. Emoji markers replace their shortcode in the displayed
     * component only; the head marker's copy text and the wire format keep
     * the raw message, so copying and unsupported setups degrade to plain
     * text.
     */
    private static void appendMessageBody(
            ChatComponentText root, String message, int[] showcaseIds,
            ChatChannel channel) {
        List<ChatShareTokenParser.Token> tokens = showcaseIds.length == 0
                ? Collections.<ChatShareTokenParser.Token>emptyList()
                : ChatShareTokenParser.parse(message);
        int literalStart = 0;
        for (int index = 0; index < tokens.size()
                && index < showcaseIds.length; index++) {
            ChatShareTokenParser.Token token = tokens.get(index);
            if (!hasPayload(token.kind, showcaseIds[index])) {
                continue;
            }
            if (literalStart < token.start) {
                appendStyledText(root, message.substring(
                        literalStart, token.start), channel);
            }
            appendShowcase(root, token.kind, showcaseIds[index]);
            literalStart = token.end;
        }
        if (literalStart < message.length()) {
            appendStyledText(root, message.substring(literalStart),
                    channel);
        }
    }

    private static boolean hasPayload(ChatShareKind kind, int showcaseId) {
        return kind == ChatShareKind.ITEM
                ? ClientChatShowcaseStore.getItem(showcaseId) != null
                : ClientChatShowcaseStore.getMarker(showcaseId) != null;
    }

    private static boolean appendShowcase(ChatComponentText root,
                                          ChatShareKind kind,
                                          int showcaseId) {
        if (kind == ChatShareKind.ITEM) {
            ItemStack stack = ClientChatShowcaseStore.getItem(showcaseId);
            if (stack == null) {
                return false;
            }
            EnumRarity rarity = stack.getRarity();
            EnumChatFormatting rarityFormatting = rarity == null
                    || rarity.rarityColor == null
                    ? EnumChatFormatting.WHITE : rarity.rarityColor;
            int rgb = rarityRgb(rarityFormatting);
            String name = ChatShareTokenParser.plainName(
                    stack.getDisplayName());
            appendShowcaseParts(root, kind, showcaseId, name,
                    rarityFormatting, rgb);
            return true;
        }
        ClientChatShowcaseStore.Marker marker =
                ClientChatShowcaseStore.getMarker(showcaseId);
        if (marker == null) {
            return false;
        }
        int rgb = ChatInlineIcons.markerRgb(marker.colorName);
        appendShowcaseParts(root, kind, showcaseId,
                ChatShareTokenParser.plainName(marker.name),
                nearestFormatting(rgb), rgb);
        return true;
    }

    private static void appendShowcaseParts(ChatComponentText root,
                                            ChatShareKind kind,
                                            int showcaseId, String name,
                                            EnumChatFormatting nearest,
                                            int rgb) {
        root.appendSibling(ChatShowcaseMarker.createText(
                kind, showcaseId, "[", nearest, rgb));
        root.appendSibling(ChatShowcaseMarker.createIcon(kind, showcaseId));
        root.appendSibling(ChatShowcaseMarker.createText(
                kind, showcaseId, " " + name + "]", nearest, rgb));
    }

    private static void appendStyledText(ChatComponentText root,
                                         String rawText,
                                         ChatChannel channel) {
        // Player-typed &-codes become renderable formatting only here, at
        // display time; the wire and copy text keep the ampersand form.
        String displayed = ChatFormattingCodes.translateAmpersand(rawText);
        if (!LostTalesConfig.enableChatEmojis) {
            appendMentions(root, displayed, channel);
            return;
        }
        for (ChatEmojiParser.Segment segment
                : ChatEmojiParser.split(displayed)) {
            if (segment.isEmoji()) {
                root.appendSibling(ChatEmojiMarker.create(
                        segment.getEmoji()));
            } else {
                appendMentions(root, segment.getText(), channel);
            }
        }
    }

    /**
     * Splits a run of message text so that every {@code @name} reaching
     * somebody is a piece of its own in that somebody's colour, and the
     * words around it stay as they were typed. A name that reaches
     * nobody is left alone: it is only text with an at-sign in front.
     */
    private static void appendMentions(ChatComponentText root, String text,
                                       ChatChannel channel) {
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int at = text.indexOf('@', cursor);
            if (at < 0) {
                break;
            }
            int end = at + 1;
            while (end < text.length() && ChatMentionColors
                    .isMentionCharacter(text.charAt(end))) {
                end++;
            }
            // The at-sign must open a word, so an address never becomes
            // a mention of whoever is named after it.
            boolean opensWord = at == 0 || !ChatMentionColors
                    .isMentionCharacter(text.charAt(at - 1));
            int color = opensWord && end > at + 1
                    ? ChatMentionColors.colorOf(
                            text.substring(at + 1, end), channel)
                    : -1;
            if (color >= 0) {
                if (at > literalStart) {
                    root.appendSibling(text(
                            text.substring(literalStart, at),
                            EnumChatFormatting.WHITE, false));
                }
                // A player mention carries who it reaches, so it answers
                // to the pointer — card on hover, conversation on click
                // — the way the sender's name does; a role mention is
                // colour alone, since a role is nobody in particular.
                String account = ChatMentionColors.accountFor(
                        text.substring(at + 1, end));
                ChatComponentText piece = text(text.substring(at, end),
                        nearestFormatting(color), false);
                root.appendSibling(account != null
                        ? ChatMentionMarker.apply(piece, color, account)
                        : ChatColorMarker.apply(piece, color));
                literalStart = end;
            }
            cursor = Math.max(end, at + 1);
        }
        if (literalStart < text.length()) {
            root.appendSibling(text(text.substring(literalStart),
                    EnumChatFormatting.WHITE, false));
        }
    }

    /** Palette stand-ins for vanilla rarity colours. */
    static int rarityRgb(EnumChatFormatting formatting) {
        if (formatting == EnumChatFormatting.YELLOW) {
            return LostTalesColors.rgb(LostTalesColors.HONEY);
        }
        if (formatting == EnumChatFormatting.AQUA) {
            return LostTalesColors.rgb(LostTalesColors.SEAFOAM);
        }
        if (formatting == EnumChatFormatting.LIGHT_PURPLE) {
            return LostTalesColors.rgb(LostTalesColors.ORCHID);
        }
        if (formatting == EnumChatFormatting.GREEN) {
            return LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN);
        }
        if (formatting == EnumChatFormatting.RED) {
            return LostTalesColors.rgb(LostTalesColors.SALMON);
        }
        if (formatting == EnumChatFormatting.GOLD) {
            return LostTalesColors.rgb(LostTalesColors.APRICOT);
        }
        if (formatting == EnumChatFormatting.BLUE) {
            return LostTalesColors.rgb(LostTalesColors.STEEL_BLUE);
        }
        return LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
    }

    private static ChatComponentText text(
            String value, EnumChatFormatting color, boolean bold) {
        ChatComponentText component = new ChatComponentText(
                value == null ? "" : value);
        ChatStyle style = component.getChatStyle().setColor(color);
        if (bold) {
            style.setBold(Boolean.TRUE);
        }
        component.setChatStyle(style);
        return component;
    }

    static EnumChatFormatting nearestFormatting(int rgb) {
        EnumChatFormatting[] colors = new EnumChatFormatting[] {
                EnumChatFormatting.DARK_BLUE,
                EnumChatFormatting.DARK_GREEN,
                EnumChatFormatting.DARK_AQUA,
                EnumChatFormatting.DARK_RED,
                EnumChatFormatting.DARK_PURPLE,
                EnumChatFormatting.GOLD,
                EnumChatFormatting.GRAY,
                EnumChatFormatting.DARK_GRAY,
                EnumChatFormatting.BLUE,
                EnumChatFormatting.GREEN,
                EnumChatFormatting.AQUA,
                EnumChatFormatting.RED,
                EnumChatFormatting.LIGHT_PURPLE,
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.WHITE
        };
        int[] values = new int[] {
                0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
                0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55,
                0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
        };
        int red = rgb >> 16 & 255;
        int green = rgb >> 8 & 255;
        int blue = rgb & 255;
        long bestDistance = Long.MAX_VALUE;
        EnumChatFormatting best = EnumChatFormatting.WHITE;
        for (int index = 0; index < values.length; index++) {
            int candidate = values[index];
            long dr = red - (candidate >> 16 & 255);
            long dg = green - (candidate >> 8 & 255);
            long db = blue - (candidate & 255);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = colors[index];
            }
        }
        return best;
    }
}
