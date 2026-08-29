package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatMarkdown;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
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
import com.ninuna.losttales.network.packet.LostTalesChatUpdatePacket;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
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
    /**
     * The newest message's own line id. What the entry animation picks
     * its lines out by: several messages can share an update counter
     * (they arrive in one tick), and the feed hands a run's lines the
     * counter they fade on rather than the one they arrived on, so the
     * counter identifies nothing on its own.
     */
    private static volatile int lastMessageChatLineId;
    private static volatile boolean hasLastMessage;
    private static volatile ChatTab lastMessageTab;
    private static int nextChatLineId = Integer.MIN_VALUE;
    /** Pinged line ids remembered: as many as the history can hold. */
    private static final int MAX_PINGED_LINES =
            LostTalesChatHistoryHooks.MAX_CAPACITY;
    private static final LinkedHashSet<Integer> pingedChatLineIds =
            new LinkedHashSet<Integer>();
    private static final int[] NO_SHOWCASES = new int[0];
    /** What opens a reply's quote row: a turned arrow, as Discord's is. */
    private static final String REPLY_MARK = "\u21b3 ";
    /**
     * One cue stands for every ping inside this window. A burst of
     * mentions — an achievement naming half the server, two lines
     * arriving in one tick — reads as one notification, so it sounds as
     * one; the window is short enough that pings a player would perceive
     * as separate events stay separately audible.
     */
    private static final long PING_SOUND_WINDOW_NANOS = 200L * 1000000L;
    private static long lastPingSoundNanos;
    private LostTalesChatPresentation() {}

    public static void receive(LostTalesChatMessagePacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (packet == null || packet.isMalformed() || minecraft == null
                || minecraft.ingameGUI == null) {
            return;
        }
        ChatChannel channel = packet.getChannel();
        // A Discord sender has no Minecraft account to look a skin up
        // for. Whether the line wears the account is the line's own
        // word, not the channel's: appearances let a character speak in
        // OOC and the account in Global.
        if (packet.isAccountLine() && channel != ChatChannel.DISCORD) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    packet.getIdentityName());
        }
        // The name this line was signed with, and what the server says
        // it wears: that is where a mention of it takes its colour from.
        ClientChatAccountRoles.remember(packet.getIdentityName(),
                packet.getRoles());
        boolean mentioned = LostTalesConfig.enableChatPings
                && isLocalPlayerMentioned(minecraft, packet.getMessage());
        // A whisper lands in the tab of its conversation, opened on the
        // first message in the window the player is typing in; a plain
        // channel the player closed reopens the same way. A hidden tab
        // stays closed: its lines are still filed and counted unread,
        // and the tab shows them all once it is restored.
        ChatTab tab;
        if (channel == ChatChannel.WHISPER) {
            ChatTab conversation = ChatTab.whisper(packet.getPartner(),
                    packet.getPartnerIdentity());
            tab = conversation != null
                    && !ChatWindowLayout.isOpen(conversation)
                    && ChatWindowLayout.isHidden(conversation)
                    ? conversation
                    : ChatWindowLayout.openWhisper(packet.getPartner(),
                            packet.getPartnerIdentity(),
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
            // Their half of the conversation says what colour it is in,
            // and which appearance the tab names them by.
            ClientChatChannelState.rememberPartnerColor(tab,
                    packet.getNameColor());
            ClientChatChannelState.rememberPartnerName(tab,
                    packet.getIdentityName(), packet.getAccountName());
        }
        // A message this client already showed is not printed again:
        // the line it is standing on becomes the real one, in place.
        int confirmed = confirmPendingEcho(minecraft, packet, tab);
        int chatLineId = confirmed != 0 ? confirmed
                : print(minecraft, packet, tab, mentioned);
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

    /**
     * Applies what the server says has happened to a message already on
     * screen: it now reads differently, or it is gone.
     *
     * <p>An edited message is rebuilt from everything it was built
     * from and put back <em>where it stands</em>. Printing it again
     * would file it as the newest line, which is not what happened —
     * the message was said when it was said, and a conversation that
     * reordered itself around a typo would be worse than the typo. A
     * removed one is taken out of the history entirely, and the run it
     * was part of closes over the gap.</p>
     *
     * <p>Both leave the mod's own scroll alone: it belongs to the view,
     * not to vanilla's history, so a correction does not throw a reader
     * back to the present.</p>
     */
    public static void applyUpdate(LostTalesChatUpdatePacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        Integer chatLineId = ClientChatMessageIds.chatLineIdOf(
                packet.getMessageId());
        if (packet.isRemoved()) {
            ClientChatMessages.forget(packet.getMessageId());
            if (chatLineId != null
                    && ChatWindowLines.removeMessage(chat,
                            chatLineId.intValue())) {
                ChatGroupRuns.forget(chatLineId.intValue());
                chat.refreshChat();
            }
            return;
        }
        ClientChatMessages.Remembered remembered =
                ClientChatMessages.get(packet.getMessageId());
        if (remembered == null || chatLineId == null) {
            // Nothing left to correct: the message has already fallen
            // out of the history this client keeps.
            return;
        }
        LostTalesChatMessagePacket edited;
        try {
            edited = remembered.packet.withMessage(packet.getMessage());
        } catch (RuntimeException refused) {
            // The server validates before it sends; a payload this
            // client cannot rebuild is dropped rather than half-applied.
            return;
        }
        boolean grouped = !edited.getReply().exists();
        IChatComponent full = markEdited(build(edited, remembered.tab,
                remembered.showcaseIds, false));
        IChatComponent groupedLine = markEdited(build(edited,
                remembered.tab, remembered.showcaseIds, grouped));
        if (!ChatWindowLines.replaceMessage(chat, chatLineId.intValue(),
                full)) {
            return;
        }
        ChatGroupRuns.replaceGroupedLine(chatLineId.intValue(), groupedLine);
        ClientChatMessages.rewrite(packet.getMessageId(), edited);
        chat.refreshChat();
    }

    /**
     * Adds the quiet note that a line is not what was first said. It
     * goes on the end of the body, in the timestamp's own muted colour,
     * so it reads as something the chat is saying about the message
     * rather than something the sender wrote.
     */
    private static IChatComponent markEdited(IChatComponent line) {
        int color = LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
        ChatComponentText mark = text(
                StatCollector.translateToLocal("gui.losttales.chat.edited"),
                nearestFormatting(color), false);
        mark.getChatStyle().setItalic(Boolean.TRUE);
        return line.appendSibling(mark);
    }

    /**
     * Prints a message in full and records what a view needs to show it
     * as a continuation instead. Which views do is theirs to decide:
     * the history keeps one message, and {@link ChatGroupRuns} keeps
     * the identity it was signed with beside the grouped form of the
     * same line.
     */
    private static int print(Minecraft minecraft,
                             LostTalesChatMessagePacket packet, ChatTab tab,
                             boolean mentioned) {
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        // Decoded once: both forms of the line show the same showcases.
        int[] showcaseIds = decodeShowcases(packet);
        chat.printChatMessageWithOptionalDeletion(
                build(packet, tab, showcaseIds, false), chatLineId);
        ChatGroupRuns.remember(chatLineId, tab, packet.getSenderId(),
                packet.getIdentityName(), packet.isAccountLine(),
                packet.getTimestampMillis(),
                // A reply keeps its header: the quote above it answers
                // for a sender the grouped form would not name.
                !packet.getReply().exists(),
                build(packet, tab, showcaseIds,
                        !packet.getReply().exists()));
        ClientChatMessageIds.remember(chatLineId, packet.getMessageId());
        // Kept so the same line can be built again if it is edited.
        ClientChatMessages.remember(packet, tab, showcaseIds);
        noteLinePrinted(minecraft, chat, chatLineId, tab, mentioned);
        return chatLineId;
    }

    /**
     * The player's own line in an NPC conversation: nobody is on the
     * other end, so nothing is sent; the line is signed the way the
     * server signs a whisper of theirs — the appearance the tab speaks
     * as, its roles and its colour — and filed under the NPC's tab.
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
        return echoToNpc(tab, message, showcases, ChatReplyReference.NONE);
    }

    /**
     * As above, answering something already said in the conversation.
     * No server sees an NPC's conversation, so the quote is the
     * client's own: it took the author and the text off the line it was
     * answering, which is the only record either of them has.
     */
    public static boolean echoToNpc(ChatTab tab, String message,
                                    List<ChatShowcase> showcases,
                                    ChatReplyReference reply) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || !tab.isNpc() || message == null
                || !ChatMessageValidator.isValid(message)
                || minecraft == null || minecraft.ingameGUI == null
                || minecraft.thePlayer == null) {
            return false;
        }
        ClientChatIdentity.Signature signature = ClientChatIdentity.of(tab);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, minecraft.thePlayer.getUniqueID(),
                signature.identityName, signature.accountName, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                signature.nameColor,
                message, System.currentTimeMillis(), signature.skinId,
                showcases, "", tab.getPartner(), signature.roles,
                signature.accountLine, ClientChatMessageIds.nextLocal(),
                reply);
        if (signature.accountLine) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    signature.accountName);
        }
        if (ChatWindowLayout.openTab(tab, windowIdOfSelection()) == null) {
            return false;
        }
        // An NPC conversation never passes through the server, so the
        // mention check the served channels get on receipt runs here:
        // naming yourself pings you in this tab exactly as anywhere
        // else.
        boolean mentioned = LostTalesConfig.enableChatPings
                && isLocalPlayerMentioned(minecraft, message);
        int chatLineId = print(minecraft, packet, tab, mentioned);
        if (mentioned) {
            markPinged(chatLineId);
            if (ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
        return true;
    }

    /**
     * Shows a message the moment it is typed, before any server has
     * seen it, and answers with the name it was remembered under — or
     * zero when nothing was shown, which is when the caller should
     * simply send and wait.
     *
     * <p>The line is this client's own work, signed the way the server
     * would sign it, and it is faint until the server's copy arrives to
     * take its place. It carries no message id worth the name: replies,
     * edits and quotes all name a message by what the server stamped on
     * it, and until that comes back there is nothing to name. Nothing
     * is shown early unless the history can actually be reached, since
     * the promise is only kept by rewriting the line where it stands.</p>
     */
    public static long echoPending(ChatTab tab, String message,
                                   List<ChatShowcase> showcases,
                                   ChatReplyReference reply) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || tab.isNpc() || message == null
                || !ChatMessageValidator.isValid(message)
                || minecraft == null || minecraft.ingameGUI == null
                || minecraft.thePlayer == null
                || ChatWindowLines.messageHistory(
                        minecraft.ingameGUI.getChatGUI()) == null) {
            return 0L;
        }
        ClientChatIdentity.Signature signature = ClientChatIdentity.of(tab);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                tab.getChannel(), minecraft.thePlayer.getUniqueID(),
                signature.identityName, signature.accountName, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                signature.nameColor,
                message, System.currentTimeMillis(), signature.skinId,
                showcases, "", tab.isWhisper() ? tab.getPartner() : "",
                signature.roles, signature.accountLine,
                ClientChatMessageIds.nextLocal(), reply,
                tab.isWhisper() ? tab.getPartnerIdentity() : "");
        if (signature.accountLine) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    signature.accountName);
        }
        long nonce = ClientChatPendingEchoes.nextNonce();
        // Never pinged and never sounded: naming yourself in your own
        // message is answered for by the copy that comes back, and
        // answering it twice would ring twice.
        int chatLineId = print(minecraft, packet, tab, false);
        ClientChatPendingEchoes.remember(nonce, chatLineId, packet, tab,
                decodeShowcases(packet), System.currentTimeMillis());
        return nonce;
    }

    /**
     * Turns the line a message was promised on into the message itself,
     * answering with that line, or zero when this was not a message
     * this client had already shown.
     *
     * <p>Only a line this player signed can be confirmed, and only by a
     * copy carrying the name they gave it. The line keeps its place in
     * the conversation and gains everything a delivered message has:
     * the server's id, its quote, its showcases as the server resolved
     * them.</p>
     */
    private static int confirmPendingEcho(Minecraft minecraft,
                                          LostTalesChatMessagePacket packet,
                                          ChatTab tab) {
        if (packet.getEchoNonce() == 0L || minecraft.thePlayer == null
                || !minecraft.thePlayer.getUniqueID().equals(
                        packet.getSenderId())) {
            return 0;
        }
        ClientChatPendingEchoes.Pending pending =
                ClientChatPendingEchoes.take(packet.getEchoNonce());
        if (pending == null) {
            return 0;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        int chatLineId = pending.chatLineId;
        int[] showcaseIds = decodeShowcases(packet);
        if (!ChatWindowLines.replaceMessage(chat, chatLineId,
                build(packet, tab, showcaseIds, false))) {
            // The promised line is no longer in the history to rewrite.
            // Take it out if it is anywhere at all and print the real
            // message afresh, rather than leaving the two side by side.
            ChatWindowLines.removeMessage(chat, chatLineId);
            ChatGroupRuns.forget(chatLineId);
            chat.refreshChat();
            return 0;
        }
        ChatGroupRuns.remember(chatLineId, tab, packet.getSenderId(),
                packet.getIdentityName(), packet.isAccountLine(),
                packet.getTimestampMillis(),
                !packet.getReply().exists(),
                build(packet, tab, showcaseIds,
                        !packet.getReply().exists()));
        ClientChatMessageIds.remember(chatLineId, packet.getMessageId());
        ClientChatMessages.remember(packet, tab, showcaseIds);
        chat.refreshChat();
        return chatLineId;
    }

    /**
     * Gives up on messages the server never answered for, marking each
     * line undelivered where it stands. A dropped message is visibly
     * dropped: leaving it faint in the history would leave it looking
     * like it was still on its way, and leaving it plain would leave it
     * looking sent.
     */
    public static void expirePendingEchoes() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }
        List<ClientChatPendingEchoes.Pending> gone =
                ClientChatPendingEchoes.expired(System.currentTimeMillis());
        if (gone.isEmpty()) {
            return;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        boolean changed = false;
        for (ClientChatPendingEchoes.Pending pending : gone) {
            IChatComponent line = markUndelivered(build(pending.packet,
                    pending.tab, pending.showcaseIds, false));
            if (ChatWindowLines.replaceMessage(chat, pending.chatLineId,
                    line)) {
                ChatGroupRuns.replaceGroupedLine(pending.chatLineId,
                        markUndelivered(build(pending.packet, pending.tab,
                                pending.showcaseIds, true)));
                changed = true;
            }
        }
        if (changed) {
            chat.refreshChat();
        }
    }

    /** The note that a message never arrived, in the palette's alarm red. */
    private static IChatComponent markUndelivered(IChatComponent line) {
        int color = LostTalesColors.rgb(LostTalesColors.CRIMSON);
        ChatComponentText mark = text(
                StatCollector.translateToLocal(
                        "gui.losttales.chat.undelivered"),
                nearestFormatting(color), false);
        mark.getChatStyle().setItalic(Boolean.TRUE);
        return line.appendSibling(mark);
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
        // Audio only: every caller has already recorded its visual ping.
        // This is the one place the cue sounds, so the collapse of
        // near-simultaneous copies needs no cooldown in any producer.
        long now = System.nanoTime();
        if (lastPingSoundNanos != 0L
                && now - lastPingSoundNanos < PING_SOUND_WINDOW_NANOS) {
            return;
        }
        lastPingSoundNanos = now;
        minecraft.getSoundHandler().playSound(
                new LostTalesChatPingSound(new ResourceLocation(sound)));
    }

    /** Records animation timing and the line's tab for the tab views. */
    private static void noteLinePrinted(Minecraft minecraft, GuiNewChat chat,
                                        int chatLineId, ChatTab tab,
                                        boolean mentioned) {
        lastMessageChatLineId = chatLineId;
        hasLastMessage = true;
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
     * Whether the message names this player: any of their own names
     * ({@link #localMentionNames}) in every channel alike, plus the
     * name of every mentionable role they hold — in every channel too,
     * since an operator is worth calling wherever the call is made — so
     * {@code @Operator} reaches the operators and nobody else. Which
     * roles the player holds is the server's word, sent with the chat
     * access; nothing here is decided from the message.
     */
    private static boolean isLocalPlayerMentioned(
            Minecraft minecraft, String message) {
        List<String> names = localMentionNames(minecraft);
        for (ChatAccountRole role : ClientChatChannelState.localRoles()) {
            if (role.isMentionable()) {
                names.add(StatCollector.translateToLocal(
                        role.getNameKey()));
            }
        }
        return ChatMentions.mentionsAny(message, names);
    }

    /**
     * The message the pointer is resting on, by chat line id, or zero.
     * Every line of it lifts a shade while it is, the way a row does in
     * any messenger — a message is what the pointer is on, not the one
     * wrapped line under it, so the whole of it answers together. Set
     * from the screen each frame it draws.
     */
    private static int hoveredChatLineId;

    static void setHoveredLine(int chatLineId) {
        hoveredChatLineId = chatLineId;
    }

    /**
     * Whether the message on this line can be answered: it has a name at
     * all, and the tab it lives in takes messages. A console notice and
     * an adopted stray have no name and so are nobody's to answer; a
     * line this client wrote itself has one of its own, and an NPC's
     * conversation is answered exactly as a player's is — locally, since
     * nobody else ever sees either half of it. One rule, asked by the
     * message menu and by the toolbar.
     */
    static boolean isRepliable(int chatLineId) {
        return ClientChatMessageIds.messageIdOf(chatLineId)
                        != ChatMessageIds.NONE
                && ClientChatChannelState.canSend(
                        ClientChatChannelViews.tabOf(chatLineId));
    }

    /** Whether the line belongs to the message the pointer is on. */
    static boolean isHoveredLine(int chatLineId) {
        return hoveredChatLineId != 0 && chatLineId == hoveredChatLineId;
    }

    /**
     * How long a jumped-to line stays lit: long enough to find with the
     * eye, short enough not to be mistaken for a state the line is in.
     */
    private static final long FLASH_NANOS = 1200L * 1000000L;
    private static int flashedChatLineId;
    private static long flashedNanos;

    /**
     * Lights a line for a moment: what a jump to a quoted message leaves
     * behind, so the message the view landed on is the one the eye finds.
     */
    static void flashLine(int chatLineId) {
        flashedChatLineId = chatLineId;
        flashedNanos = System.nanoTime();
    }

    /** How lit the line is right now, 1 to 0 as the moment passes. */
    static float flashStrength(int chatLineId) {
        if (flashedNanos == 0L || chatLineId != flashedChatLineId) {
            return 0.0F;
        }
        long elapsed = System.nanoTime() - flashedNanos;
        if (elapsed < 0L || elapsed >= FLASH_NANOS) {
            return 0.0F;
        }
        return 1.0F - elapsed / (float)FLASH_NANOS;
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

    /** Whether {@code chatLineId} is the newest message printed. */
    static boolean isLastMessage(int chatLineId) {
        return hasLastMessage && chatLineId == lastMessageChatLineId;
    }

    static ChatTab getLastMessageTab() {
        return lastMessageTab;
    }

    public static void clear() {
        lastMessageNanos = 0L;
        lastMessageChatLineId = 0;
        hasLastMessage = false;
        lastMessageTab = null;
        nextChatLineId = Integer.MIN_VALUE;
        pingedChatLineIds.clear();
        lastPingSoundNanos = 0L;
        flashedChatLineId = 0;
        flashedNanos = 0L;
        hoveredChatLineId = 0;
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
                ? ChatTab.whisper(packet.getPartner(),
                        packet.getPartnerIdentity())
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
        return build(packet, tab, showcaseIds, false);
    }

    /**
     * As above; a <em>grouped</em> line continues its sender's run and
     * drops the repeated header — the channel prefix, tags, brackets,
     * head, name and title — keeping only the timestamp and the body,
     * which starts on the anchor exactly where a wrapped continuation
     * line starts, so a run reads as one voice speaking in paragraphs.
     * The prefix goes with the rest: in the closed feed the run's
     * header line already named the channel, so its continuations do
     * not say it again.
     */
    static IChatComponent build(LostTalesChatMessagePacket packet,
                                ChatTab tab, int[] showcaseIds,
                                boolean grouped) {
        ChatChannel channel = packet.getChannel();
        ChatComponentText root = new ChatComponentText("");
        ChatTab named = tab == null ? tabOf(packet) : tab;
        if (grouped) {
            appendTimestamp(root, packet.getTimestampMillis());
            root.appendSibling(ChatLayoutMarker.anchor());
            appendMessageBody(root, packet.getMessage(), showcaseIds,
                    channel);
            return root;
        }
        // A reply opens with the message it answers, on a row of its
        // own above the line.
        if (packet.getReply().exists()) {
            appendReplyQuote(root, packet.getReply());
            root.appendSibling(ChatLayoutMarker.lineBreak());
        }
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
        // line, so nothing here decides what a role looks like. The tag
        // carries the role mention marker, so hovering it shows the
        // role's card exactly as hovering @Operator does.
        for (ChatAccountRole role : ChatAccountRole.fromMask(
                packet.getRoles())) {
            root.appendSibling(ChatMentionMarker.applyRole(
                    text(StatCollector.translateToLocal(role.getTagKey())
                            + " ", nearestFormatting(role.getColor()),
                            false),
                    role.getColor(), role));
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
        // The head is the line's identity's, not the channel's: an
        // account line wears the account head wherever it was said.
        marker.setChatStyle(marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        ChatHeadMarker.encode(packet.getSenderId(),
                                packet.isAccountLine()
                                        ? ChatIdentityType.ACCOUNT
                                        : ChatIdentityType.CHARACTER,
                                packet.getSkinId(), packet.getMessage(),
                                packet.getTitleColor(),
                                packet.getNameColor()))));
        root.appendSibling(marker);

        root.appendSibling(reply(text(packet.getIdentityName(),
                nearestFormatting(packet.getNameColor()), false), whisper));
        if (channel == ChatChannel.WHISPER && !packet.getIdentityName()
                .equalsIgnoreCase(packet.getAccountName())) {
            // A whisper spoken as a character still says who is behind
            // it: the account in brackets, part of the name — it answers
            // to the pointer the way the name does.
            root.appendSibling(reply(text(
                    " (" + packet.getAccountName() + ")",
                    nearestFormatting(packet.getNameColor()), false),
                    whisper));
        }
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
     * The row a reply opens with: the message it answers, quoted in the
     * timestamps' quiet grey so it reads as context rather than as
     * something said. The author keeps their own name colour, the way
     * they are named everywhere else. The wrapper cuts the row to one
     * line, so a long quote never pushes the answer down the window.
     */
    private static void appendReplyQuote(ChatComponentText root,
                                         ChatReplyReference reply) {
        int quiet = LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
        int name = ClientChatAccountRoles.colorOf(reply.getAuthor());
        long id = reply.getMessageId();
        root.appendSibling(ChatReplyMarker.apply(
                text(REPLY_MARK, nearestFormatting(quiet), false),
                quiet, id));
        root.appendSibling(ChatReplyMarker.apply(
                text(reply.getAuthor(),
                        nearestFormatting(name < 0 ? quiet : name), false),
                name < 0 ? quiet : name, id));
        root.appendSibling(ChatReplyMarker.apply(
                text(": " + reply.getExcerpt(),
                        nearestFormatting(quiet), false),
                quiet, id));
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
     *
     * <p>{@code audibleMentionCue} says whether a mention of this player
     * inside the line may sound as well as highlight: an announcement —
     * an achievement above all — names its player without addressing
     * them, so its mention stays visual. The highlight, the unread ping
     * count and the line's tint are the same either way.</p>
     */
    public static boolean receiveSystemLine(IChatComponent message,
                                            ChatChannel channel,
                                            boolean audibleMentionCue) {
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
        // A system line naming a player — an achievement, a death, a
        // join — names them the way a typed mention does: @Name in the
        // mention's colour, the active character's name on the
        // character channels, answering to the pointer. A line naming
        // this player highlights, and the cue sounds. The rewrite may
        // hand back a fresh component; the fresh one is what is shown.
        IChatComponent shown = message;
        boolean mentioned = false;
        if (LostTalesConfig.enableChatPings) {
            boolean[] localMentioned = new boolean[1];
            shown = rewritePlayerNames(message, channel,
                    localMentionNames(minecraft), localMentioned);
            mentioned = localMentioned[0];
        }
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                buildSystemLine(shown, channel,
                        System.currentTimeMillis()), chatLineId);
        ClientChatChannelViews.record(chatLineId, tab,
                ClientChatChannelState.getSelected(), mentioned);
        ClientChatChannelViews.onLinesAdded(tab,
                LostTalesChatOverlayRenderer.countLeadingLines(
                        chat, chatLineId));
        if (mentioned) {
            markPinged(chatLineId);
            if (audibleMentionCue && ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
        if (channel == ChatChannel.CONSOLE && chat.getChatOpen()) {
            frontConsole();
        }
        return true;
    }

    /**
     * Adopts a line printed straight into the chat without passing
     * through the received-chat event — a game-mode change notice, a
     * saved-screenshot line, another mod's local print. The entry is
     * rebuilt in place as a console system line, so it carries the
     * channel prefix, the timestamp and a tracked line id exactly like
     * everything else the console shows; the caller lays the history
     * out again once it has adopted what it found. Only a plain print
     * (chat line id zero) is adopted: a line printed under an id of its
     * own may be replaced or deleted by that id later and must keep it.
     */
    static boolean adoptStrayLine(List<ChatLine> messages, int index) {
        if (messages == null || index < 0 || index >= messages.size()) {
            return false;
        }
        ChatLine line = messages.get(index);
        if (line == null || line.getChatLineID() != 0) {
            return false;
        }
        int chatLineId = allocateChatLineId();
        messages.set(index, new ChatLine(line.getUpdatedCounter(),
                buildSystemLine(line.func_151461_a(), ChatChannel.CONSOLE,
                        System.currentTimeMillis()), chatLineId));
        ClientChatChannelViews.record(chatLineId,
                ChatTab.of(ChatChannel.CONSOLE),
                ClientChatChannelState.getSelected(), false);
        return true;
    }

    /**
     * The names a line may address this client by, the same set in every
     * channel: the account, and every character of the roster — whoever
     * this player is currently speaking as, every other name they own is
     * an alias of them, so a mention of any of their names reaches them
     * wherever it is said.
     */
    private static List<String> localMentionNames(Minecraft minecraft) {
        List<String> names = new ArrayList<String>(4);
        if (minecraft != null && minecraft.thePlayer != null) {
            names.add(minecraft.thePlayer.getCommandSenderName());
        }
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        if (snapshot != null) {
            for (CharacterSummary summary : snapshot.getCharacters()) {
                if (summary != null && summary.getName() != null
                        && summary.getName().trim().length() > 0) {
                    names.add(summary.getName().trim());
                }
            }
        }
        return names;
    }

    /**
     * Rewrites player names inside a system line into mentions: a
     * component (or a translation's bare string argument) whose whole
     * text is a name this client can place — the way an achievement, a
     * join line or a death names its player — becomes {@code @Name},
     * carrying the mention marker so it answers to the pointer exactly
     * as a typed mention does. On the character channels the account's
     * active role-playing character is named instead of the account,
     * which is the identity every ordinary line there is signed with.
     *
     * <p>Returns the component to show, which may be a fresh one: a
     * translation caches the children it renders the first time
     * anything walks it — which another handler may already have done
     * by the time this runs last — so a translation with a replaced
     * argument is rebuilt as a new instance rather than edited in
     * place. Sibling lists are walked live at render time and are
     * edited in place. {@code localMentioned[0]} is set when one of
     * this client's own names was among the replaced.</p>
     */
    private static IChatComponent rewritePlayerNames(
            IChatComponent component, ChatChannel channel,
            List<String> localNames, boolean[] localMentioned) {
        if (component == null) {
            return null;
        }
        IChatComponent mention = asMention(component, channel, localNames,
                localMentioned);
        if (mention != null) {
            return mention;
        }
        List<?> siblings = component.getSiblings();
        for (int index = 0; siblings != null
                && index < siblings.size(); index++) {
            Object value = siblings.get(index);
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent sibling = (IChatComponent)value;
            IChatComponent replaced = rewritePlayerNames(sibling, channel,
                    localNames, localMentioned);
            if (replaced != sibling) {
                @SuppressWarnings("unchecked")
                List<Object> mutable = (List<Object>)siblings;
                mutable.set(index, replaced);
            }
        }
        if (!(component instanceof ChatComponentTranslation)) {
            return component;
        }
        ChatComponentTranslation translation =
                (ChatComponentTranslation)component;
        Object[] arguments = translation.getFormatArgs();
        if (arguments == null) {
            return component;
        }
        Object[] rewritten = new Object[arguments.length];
        boolean changed = false;
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (argument instanceof IChatComponent) {
                rewritten[index] = rewritePlayerNames(
                        (IChatComponent)argument, channel, localNames,
                        localMentioned);
            } else if (argument instanceof String) {
                IChatComponent named = asMentionName((String)argument,
                        channel, localNames, localMentioned);
                rewritten[index] = named != null ? named : argument;
            } else {
                rewritten[index] = argument;
            }
            changed |= rewritten[index] != argument;
        }
        if (!changed) {
            return component;
        }
        ChatComponentTranslation fresh = new ChatComponentTranslation(
                translation.getKey(), rewritten);
        fresh.setChatStyle(component.getChatStyle());
        // setChatStyle re-parents the siblings but not the arguments,
        // whose parent is still the fresh instance's discarded default
        // style; without this they would lose the line's inheritance.
        for (int index = 0; index < rewritten.length; index++) {
            if (rewritten[index] instanceof IChatComponent) {
                ((IChatComponent)rewritten[index]).getChatStyle()
                        .setParentStyle(fresh.getChatStyle());
            }
        }
        for (int index = 0; siblings != null
                && index < siblings.size(); index++) {
            Object value = siblings.get(index);
            if (value instanceof IChatComponent) {
                fresh.appendSibling((IChatComponent)value);
            }
        }
        return fresh;
    }

    /**
     * The mention a leaf component becomes when its whole text is a
     * placeable player name (or one of this client's own names), or
     * null. Vanilla and LOTR put the player's name in a component of
     * its own, so whole-text matching reaches exactly them without
     * splitting anybody's prose.
     */
    private static IChatComponent asMention(IChatComponent component,
                                            ChatChannel channel,
                                            List<String> localNames,
                                            boolean[] localMentioned) {
        if (!(component instanceof ChatComponentText)
                || !component.getSiblings().isEmpty()) {
            return null;
        }
        return asMentionName(component.getUnformattedTextForChat(),
                channel, localNames, localMentioned);
    }

    /** As above from a bare name, for a translation's string argument. */
    private static IChatComponent asMentionName(String rawText,
                                                ChatChannel channel,
                                                List<String> localNames,
                                                boolean[] localMentioned) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.length() == 0) {
            return null;
        }
        boolean local = matchesAny(text, localNames);
        String account = ChatMentionColors.accountFor(text);
        if (!local && account == null) {
            return null;
        }
        // The character channels sign every line with the sender's
        // active role-playing character; a system line naming the
        // account shows the same identity when the client knows it —
        // the character's name, in the colour the mention resolution
        // below gives every mention of that identity, so an achievement
        // names its player exactly as their lines do.
        String shown = text;
        if (account != null && channel != null
                && channel.getIdentityType() == ChatIdentityType.CHARACTER) {
            String characterName =
                    ChatMentionColors.characterNameFor(account);
            if (characterName != null) {
                shown = characterName;
            }
        }
        int color = ChatMentionColors.colorOf(text, channel);
        if (color < 0) {
            // One of this client's own names the public caches cannot
            // place; the shared accent stands in.
            color = LostTalesColors.rgb(LostTalesColors.HONEY);
        }
        if (local) {
            localMentioned[0] = true;
        }
        ChatComponentText piece = text("@" + shown,
                nearestFormatting(color), false);
        return account != null
                ? ChatMentionMarker.apply(piece, color, account)
                : ChatColorMarker.apply(piece, color);
    }

    private static boolean matchesAny(String text, List<String> names) {
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (name != null && text.equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
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
                                           int nameColor,
                                           String factionName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || npcId == null || npcName == null
                || npcName.length() == 0 || message == null
                || message.length() == 0 || minecraft == null
                || minecraft.ingameGUI == null) {
            return false;
        }
        // The tab wears the same portrait the line is drawn with, and
        // is named in the same colour the NPC's own name is; the
        // faction is kept for the NPC's hover card.
        ChatChannelIcons.rememberNpcPortrait(tab, texturePath);
        ChatChannelIcons.rememberNpcFaction(npcId, factionName);
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
        // cue sounds. An NPC keeps a run exactly as a player does: its
        // next line inside the window drops the repeated header.
        List<String> localNames = localMentionNames(minecraft);
        boolean mentioned = LostTalesConfig.enableChatPings
                && ChatMentions.mentionsAny(ChatMentions.mentionNames(
                        message, localNames), localNames);
        int chatLineId = allocateChatLineId();
        long now = System.currentTimeMillis();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                buildNpcSpeech(tab, npcId, npcName,
                        texturePath, message, now, nameColor, false),
                chatLineId);
        ChatGroupRuns.remember(chatLineId, tab, npcId, npcName, false, now,
                true, buildNpcSpeech(tab, npcId, npcName, texturePath,
                        message, now, nameColor, true));
        // An NPC's speech is this client's own line too: nobody else
        // sees it, so it is named locally like the player's replies.
        ClientChatMessageIds.remember(chatLineId,
                ClientChatMessageIds.nextLocal());
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
        return buildNpcSpeech(tab, npcId, npcName, texturePath, message,
                timestampMillis, nameColor, false);
    }

    /**
     * As above; a <em>grouped</em> line continues the NPC's run and
     * drops the repeated header, exactly as a player's grouped line
     * does.
     */
    static IChatComponent buildNpcSpeech(ChatTab tab, UUID npcId,
                                         String npcName,
                                         String texturePath,
                                         String message,
                                         long timestampMillis,
                                         int nameColor, boolean grouped) {
        ChatComponentText root = new ChatComponentText("");
        if (grouped) {
            // A grouped line drops the channel prefix with the rest of
            // the header, so the feed's run names its channel once.
            appendTimestamp(root, timestampMillis);
            root.appendSibling(ChatLayoutMarker.anchor());
            appendMessageBody(root, ChatMentions.mentionNames(message,
                            localMentionNames(Minecraft.getMinecraft())),
                    NO_SHOWCASES, ChatChannel.WHISPER);
            return root;
        }
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
     * routes faction chat to members. A conversation names its
     * <em>channel</em>, not its partner: the line itself already opens
     * with the sender, so the feed reads {@code Whisper: <Name> ...}
     * instead of saying the name twice.
     */
    private static void appendChannelPrefix(ChatComponentText root,
                                            ChatTab tab,
                                            int channelColor) {
        String label = tab != null && tab.isWhisper()
                ? ChatChannel.WHISPER.getDisplayName()
                : ClientChatChannelState.displayName(tab);
        root.appendSibling(ChatPrefixMarker.channel(
                text(label, nearestFormatting(channelColor), false),
                channelColor));
        root.appendSibling(ChatPrefixMarker.channel(
                text(": ", nearestFormatting(channelColor), false),
                channelColor));
    }

    /**
     * {@code [HH:mm] } in the palette's rose beige — a quiet grey a step
     * below the sand body text — with the time itself — digits and their
     * colon — italic; the brackets stay upright. Marked as a timestamp
     * run, so the closed feed leaves it out: the feed is a glance at
     * what was just said, not a log to read times off.
     */
    private static void appendTimestamp(ChatComponentText root,
                                        long timestampMillis) {
        if (!LostTalesConfig.showChatTimestamps) {
            return;
        }
        String formatted = "[" + ChatTimestampFormatter.format(
                timestampMillis) + "] ";
        int color = LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
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
        // The brackets and the name read in the marker's text colour —
        // white becomes the chat's ivory — while the icon itself keeps
        // the marker's exact artwork colour.
        int rgb = ChatInlineIcons.markerTextRgb(marker.colorName);
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
        if (!ChatMarkdown.hasMarkup(displayed)) {
            appendEmojiRuns(root, displayed, channel);
            return;
        }
        for (ChatMarkdown.Span span : ChatMarkdown.parse(displayed)) {
            if (span.isPlain()) {
                appendEmojiRuns(root, span.getText(), channel);
                continue;
            }
            if (span.isCode()) {
                // Quoted text is shown as typed: no emoji, no link, no
                // mention, since reading it again is the one thing
                // quoting it says not to do.
                root.appendSibling(marked(text(span.getText(),
                        EnumChatFormatting.GRAY, false), span));
                continue;
            }
            // The marks ride the runs the ordinary passes produce, so a
            // mention or an emoji inside a bold span is still a mention
            // or an emoji, and bold besides.
            ChatComponentText held = new ChatComponentText("");
            appendEmojiRuns(held, span.getText(), channel);
            List<IChatComponent> parts = new ArrayList<IChatComponent>(
                    held.getSiblings());
            for (int index = 0; index < parts.size(); index++) {
                root.appendSibling(marked(parts.get(index), span));
            }
        }
    }

    /** The emoji, link and mention passes over one run of body text. */
    private static void appendEmojiRuns(ChatComponentText root,
                                        String text, ChatChannel channel) {
        if (!LostTalesConfig.enableChatEmojis) {
            appendLinksAndMentions(root, text, channel);
            return;
        }
        for (ChatEmojiParser.Segment segment
                : ChatEmojiParser.split(text)) {
            if (segment.isEmoji()) {
                root.appendSibling(ChatEmojiMarker.create(
                        segment.getEmoji()));
            } else {
                appendLinksAndMentions(root, segment.getText(), channel);
            }
        }
    }

    /**
     * Puts a span's marks on one of its runs. Vanilla's own decorations
     * carry them, so the wrapper measures and the renderer draws them
     * without knowing markup exists at all. A spoiler is drawn
     * obfuscated — Minecraft's own way of showing text that is there but
     * not to be read.
     */
    private static IChatComponent marked(IChatComponent part,
                                         ChatMarkdown.Span span) {
        ChatStyle style = part.getChatStyle();
        if (span.isBold()) {
            style.setBold(Boolean.TRUE);
        }
        if (span.isItalic()) {
            style.setItalic(Boolean.TRUE);
        }
        if (span.isStrikethrough()) {
            style.setStrikethrough(Boolean.TRUE);
        }
        if (span.isSpoiler()) {
            style.setObfuscated(Boolean.TRUE);
        }
        return part;
    }

    /**
     * Splits a run of message text so every web address in it becomes a
     * clickable link — underlined, in the palette's blue, opening
     * through the same confirm dialog every other chat link opens
     * through — and hands everything around the addresses to the
     * mention pass. Only whole {@code http(s)://} words that open at a
     * word boundary count, trailing punctuation stays ordinary text,
     * and with Minecraft's own chat-links option off nothing is touched
     * at all, exactly as vanilla leaves links inert then.
     */
    private static void appendLinksAndMentions(ChatComponentText root,
                                               String text,
                                               ChatChannel channel) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null
                || !minecraft.gameSettings.chatLinks
                || text.indexOf(':') < 0) {
            appendMentions(root, text, channel);
            return;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int at = indexOfLink(lowered, cursor);
            if (at < 0) {
                break;
            }
            int end = at;
            while (end < text.length()
                    && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            while (end > at && isTrailingPunctuation(text.charAt(end - 1))) {
                end--;
            }
            String url = text.substring(at, end);
            int scheme = lowered.startsWith("https", at)
                    ? "https://".length() : "http://".length();
            if (url.length() <= scheme) {
                // A bare scheme is only text.
                cursor = at + 1;
                continue;
            }
            if (literalStart < at) {
                appendMentions(root, text.substring(literalStart, at),
                        channel);
            }
            ChatComponentText link = new ChatComponentText(url);
            ChatStyle style = link.getChatStyle()
                    .setColor(EnumChatFormatting.BLUE)
                    .setUnderlined(Boolean.TRUE);
            style.setChatClickEvent(new ClickEvent(
                    ClickEvent.Action.OPEN_URL, url));
            link.setChatStyle(style);
            root.appendSibling(link);
            literalStart = end;
            cursor = end;
        }
        if (literalStart < text.length()) {
            appendMentions(root, text.substring(literalStart), channel);
        }
    }

    /**
     * The next {@code http://}/{@code https://} that opens a word, in
     * the already-lowercased text.
     */
    private static int indexOfLink(String lowered, int from) {
        int cursor = from;
        while (cursor < lowered.length()) {
            int at = lowered.indexOf("http", cursor);
            if (at < 0) {
                return -1;
            }
            boolean opensWord = at == 0
                    || Character.isWhitespace(lowered.charAt(at - 1));
            int schemeEnd = at + 4;
            if (schemeEnd < lowered.length()
                    && lowered.charAt(schemeEnd) == 's') {
                schemeEnd++;
            }
            if (opensWord && lowered.regionMatches(schemeEnd, "://", 0, 3)) {
                return at;
            }
            cursor = at + 1;
        }
        return -1;
    }

    private static boolean isTrailingPunctuation(char character) {
        return character == '.' || character == ',' || character == ';'
                || character == ':' || character == '!' || character == '?'
                || character == ')' || character == '"' || character == '\'';
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
                // A mention carries whom it reaches, so it answers to
                // the pointer: a player mention with their card on
                // hover and the conversation on a click, a role mention
                // with the role's card naming everyone holding it.
                String name = text.substring(at + 1, end);
                String account = ChatMentionColors.accountFor(name);
                ChatAccountRole role = account == null
                        ? ChatMentionColors.roleFor(name) : null;
                ChatComponentText piece = text(text.substring(at, end),
                        nearestFormatting(color), false);
                if (account != null) {
                    root.appendSibling(ChatMentionMarker.apply(
                            piece, color, account));
                } else if (role != null) {
                    root.appendSibling(ChatMentionMarker.applyRole(
                            piece, color, role));
                } else {
                    root.appendSibling(ChatColorMarker.apply(piece, color));
                }
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
