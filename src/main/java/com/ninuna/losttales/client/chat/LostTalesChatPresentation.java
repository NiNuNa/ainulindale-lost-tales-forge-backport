package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
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
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** Builds structured legacy chat components and records entry-animation time. */
public final class LostTalesChatPresentation {
    private static volatile long lastMessageNanos;
    private static volatile int lastMessageUpdateCounter = -1;
    private static volatile ChatChannel lastMessageChannel;
    private static int nextChatLineId = Integer.MIN_VALUE;
    private static final int MAX_PINGED_LINES = 100;
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
        if (channel.getIdentityType()
                == com.ninuna.losttales.chat.ChatIdentityType.ACCOUNT) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    packet.getIdentityName());
        }
        boolean mentioned = LostTalesConfig.enableChatPings
                && isLocalPlayerMentioned(minecraft, packet.getMessage());
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                build(packet, decodeShowcases(packet)), chatLineId);
        noteLinePrinted(minecraft, chat, chatLineId, channel, mentioned);
        if (mentioned) {
            markPinged(chatLineId);
            if (minecraft.thePlayer != null
                    && LostTalesConfig.chatPingSound.length() > 0) {
                minecraft.thePlayer.playSound(
                        LostTalesConfig.chatPingSound, 0.4F, 1.0F);
            }
        }
    }

    /** Records animation timing and the line's channel for the tab views. */
    private static void noteLinePrinted(Minecraft minecraft, GuiNewChat chat,
                                        int chatLineId, ChatChannel channel,
                                        boolean mentioned) {
        lastMessageUpdateCounter = minecraft.ingameGUI.getUpdateCounter();
        lastMessageNanos = System.nanoTime();
        lastMessageChannel = channel;
        ClientChatChannelViews.record(chatLineId, channel,
                ClientChatChannelState.getSelected(), mentioned);
        ClientChatChannelViews.onLinesAdded(channel,
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

    /** The local account name plus the active character's name, if any. */
    private static boolean isLocalPlayerMentioned(
            Minecraft minecraft, String message) {
        List<String> names = new ArrayList<String>(2);
        if (minecraft.thePlayer != null) {
            names.add(minecraft.thePlayer.getCommandSenderName());
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

    static ChatChannel getLastMessageChannel() {
        return lastMessageChannel;
    }

    public static void clear() {
        lastMessageNanos = 0L;
        lastMessageUpdateCounter = -1;
        lastMessageChannel = null;
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
        return build(packet, NO_SHOWCASES);
    }

    static IChatComponent build(LostTalesChatMessagePacket packet,
                                int[] showcaseIds) {
        ChatChannel channel = packet.getChannel();
        ChatComponentText root = new ChatComponentText("");
        int channelColor = channel == ChatChannel.FACTION
                ? packet.getNameColor() : channel.getDisplayColor();
        appendChannelPrefix(root, channel, channelColor);
        appendTimestamp(root, packet.getTimestampMillis());

        root.appendSibling(ChatColorMarker.apply(
                text("<", nearestFormatting(
                        packet.getNameColor()), false),
                packet.getNameColor()));
        // Two bold spaces reserve ten pixels: enough for the raised
        // headwear layer plus the compact gap seen in the identity
        // reference, without adding a visible spacer component.
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

        if (packet.getTitle().length() > 0) {
            root.appendSibling(text(packet.getTitle() + " ",
                    nearestFormatting(packet.getTitleColor()), false));
        }
        ChatComponentText identity = text(packet.getIdentityName(),
                nearestFormatting(packet.getNameColor()), false);
        identity.setChatStyle(identity.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/msg " + packet.getAccountName() + " ")));
        root.appendSibling(identity);
        root.appendSibling(ChatColorMarker.apply(
                text("> ", nearestFormatting(
                        packet.getNameColor()), false),
                packet.getNameColor()));
        appendMessageBody(root, packet.getMessage(), showcaseIds);
        return root;
    }

    /**
     * Prints an LOTR NPC speech line styled like a player message. The
     * honey name keeps LOTR's yellow-name convention within the palette;
     * the title colour is the plain body ivory so the un-clickable NPC
     * name does not tint the message text that follows it.
     */
    public static boolean receiveNpcSpeech(ChatChannel channel, UUID npcId,
                                           String npcName,
                                           String texturePath,
                                           String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (channel == null || npcId == null || npcName == null
                || npcName.length() == 0 || message == null
                || message.length() == 0 || minecraft == null
                || minecraft.ingameGUI == null) {
            return false;
        }
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                buildNpcSpeech(channel, npcId, npcName,
                        texturePath, message,
                        System.currentTimeMillis()),
                chatLineId);
        noteLinePrinted(minecraft, chat, chatLineId, channel, false);
        return true;
    }

    static IChatComponent buildNpcSpeech(ChatChannel channel, UUID npcId,
                                         String npcName,
                                         String texturePath,
                                         String message,
                                         long timestampMillis) {
        ChatComponentText root = new ChatComponentText("");
        appendChannelPrefix(root, channel, channel.getDisplayColor());
        appendTimestamp(root, timestampMillis);
        int nameColor = LostTalesColors.rgb(LostTalesColors.HONEY);
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
        appendMessageBody(root, message, NO_SHOWCASES);
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
                                            ChatChannel channel,
                                            int channelColor) {
        root.appendSibling(ChatChannelPrefixMarker.apply(
                text(ClientChatChannelState.displayName(channel),
                        nearestFormatting(channelColor), false),
                channelColor));
        root.appendSibling(ChatChannelPrefixMarker.apply(
                text(": ", nearestFormatting(channelColor), false),
                channelColor));
    }

    /**
     * {@code [HH:mm] } in the palette's sand with the time itself — digits
     * and their colon — italic; the brackets stay upright.
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
            root.appendSibling(ChatColorMarker.apply(run, color));
            index = end;
        }
    }

    private static boolean isTimeCharacter(char character) {
        return Character.isDigit(character) || character == ':';
    }

    /**
     * Share tokens with a validated payload become an icon slot plus the
     * bracketed name; every other token stays the literal text the sender
     * typed. Emote markers replace their shortcode in the displayed
     * component only; the head marker's copy text and the wire format keep
     * the raw message, so copying and unsupported setups degrade to plain
     * text.
     */
    private static void appendMessageBody(
            ChatComponentText root, String message, int[] showcaseIds) {
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
                        literalStart, token.start));
            }
            appendShowcase(root, token.kind, showcaseIds[index]);
            literalStart = token.end;
        }
        if (literalStart < message.length()) {
            appendStyledText(root, message.substring(literalStart));
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
        float[] color = LostTalesCompassMarker.parseColor(marker.colorName);
        int rgb = (Math.round(color[0] * 255.0F) << 16)
                | (Math.round(color[1] * 255.0F) << 8)
                | Math.round(color[2] * 255.0F);
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
                                         String rawText) {
        // Player-typed &-codes become renderable formatting only here, at
        // display time; the wire and copy text keep the ampersand form.
        String displayed = ChatFormattingCodes.translateAmpersand(rawText);
        if (!LostTalesConfig.enableChatEmojis) {
            root.appendSibling(text(displayed,
                    EnumChatFormatting.WHITE, false));
            return;
        }
        for (ChatEmojiParser.Segment segment
                : ChatEmojiParser.split(displayed)) {
            if (segment.isEmoji()) {
                root.appendSibling(ChatEmojiMarker.create(
                        segment.getEmoji()));
            } else {
                root.appendSibling(text(segment.getText(),
                        EnumChatFormatting.WHITE, false));
            }
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
