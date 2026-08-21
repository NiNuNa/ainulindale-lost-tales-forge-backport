package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** Builds structured legacy chat components and records entry-animation time. */
public final class LostTalesChatPresentation {
    private static volatile long lastMessageNanos;
    private static volatile int lastMessageUpdateCounter = -1;
    private static int nextChatLineId = Integer.MIN_VALUE;
    private static final int MAX_PINGED_LINES = 100;
    private static final LinkedHashSet<Integer> pingedChatLineIds =
            new LinkedHashSet<Integer>();

    private LostTalesChatPresentation() {}

    public static void receive(LostTalesChatMessagePacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (packet == null || packet.isMalformed() || minecraft == null
                || minecraft.ingameGUI == null) {
            return;
        }
        if (packet.getChannel().getIdentityType()
                == com.ninuna.losttales.chat.ChatIdentityType.ACCOUNT) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    packet.getIdentityName());
        }
        int chatLineId = allocateChatLineId();
        minecraft.ingameGUI.getChatGUI()
                .printChatMessageWithOptionalDeletion(
                        build(packet), chatLineId);
        lastMessageUpdateCounter =
                minecraft.ingameGUI.getUpdateCounter();
        lastMessageNanos = System.nanoTime();
        if (LostTalesConfig.enableChatPings
                && isLocalPlayerMentioned(minecraft, packet.getMessage())) {
            markPinged(chatLineId);
            if (minecraft.thePlayer != null
                    && LostTalesConfig.chatPingSound.length() > 0) {
                minecraft.thePlayer.playSound(
                        LostTalesConfig.chatPingSound, 0.4F, 1.0F);
            }
        }
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

    public static void clear() {
        lastMessageNanos = 0L;
        lastMessageUpdateCounter = -1;
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
        ChatChannel channel = packet.getChannel();
        ChatComponentText root = new ChatComponentText("");
        int channelColor = channel == ChatChannel.FACTION
                ? packet.getNameColor() : channel.getDisplayColor();
        appendChannelPrefix(root, channel, channelColor,
                packet.getTimestampMillis());

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
        appendMessageBody(root, packet.getMessage());
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
        minecraft.ingameGUI.getChatGUI()
                .printChatMessageWithOptionalDeletion(
                        buildNpcSpeech(channel, npcId, npcName,
                                texturePath, message,
                                System.currentTimeMillis()),
                        allocateChatLineId());
        lastMessageUpdateCounter =
                minecraft.ingameGUI.getUpdateCounter();
        lastMessageNanos = System.nanoTime();
        return true;
    }

    static IChatComponent buildNpcSpeech(ChatChannel channel, UUID npcId,
                                         String npcName,
                                         String texturePath,
                                         String message,
                                         long timestampMillis) {
        ChatComponentText root = new ChatComponentText("");
        appendChannelPrefix(root, channel, channel.getDisplayColor(),
                timestampMillis);
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
        appendMessageBody(root, message);
        return root;
    }

    private static void appendChannelPrefix(ChatComponentText root,
                                            ChatChannel channel,
                                            int channelColor,
                                            long timestampMillis) {
        root.appendSibling(ChatColorMarker.apply(
                text(channel.getDisplayName(),
                        nearestFormatting(channelColor), false),
                channelColor));
        root.appendSibling(ChatColorMarker.apply(
                text(": ", nearestFormatting(channelColor), false),
                channelColor));
        if (LostTalesConfig.showChatTimestamps) {
            appendTimestamp(root, "[" + ChatTimestampFormatter.format(
                    timestampMillis) + "] | ");
        }
    }

    /**
     * Timestamp in plum gray with the time itself — digits and their
     * colon — italic; the brackets and separator stay upright.
     */
    private static void appendTimestamp(ChatComponentText root,
                                        String formatted) {
        int color = LostTalesColors.rgb(LostTalesColors.PLUM_GRAY);
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
     * Emote markers replace their shortcode text in the displayed component
     * only; the head marker's copy text and the wire format keep the raw
     * message, so copying and unsupported setups degrade to plain shortcodes.
     */
    private static void appendMessageBody(
            ChatComponentText root, String message) {
        // Player-typed &-codes become renderable formatting only here, at
        // display time; the wire and copy text keep the ampersand form.
        String displayed = ChatFormattingCodes.translateAmpersand(message);
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
