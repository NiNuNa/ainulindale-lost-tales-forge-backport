package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
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
        minecraft.ingameGUI.getChatGUI()
                .printChatMessageWithOptionalDeletion(
                        build(packet), allocateChatLineId());
        lastMessageUpdateCounter =
                minecraft.ingameGUI.getUpdateCounter();
        lastMessageNanos = System.nanoTime();
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
        root.appendSibling(ChatColorMarker.apply(
                text(channel.getDisplayName(),
                        nearestFormatting(channelColor), false),
                channelColor));
        root.appendSibling(text(": ", EnumChatFormatting.GRAY, false));
        if (LostTalesConfig.showChatTimestamps) {
            root.appendSibling(text("[" + ChatTimestampFormatter.format(
                    packet.getTimestampMillis()) + "] | ",
                    EnumChatFormatting.DARK_GRAY, false));
        }

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
        root.appendSibling(text(packet.getMessage(),
                EnumChatFormatting.WHITE, false));
        return root;
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
