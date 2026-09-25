package com.ninuna.losttales.network.server;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatNameSuggester;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.chat.share.ChatShareSuggester;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.server.ChatConsoleCommandHandler;
import com.ninuna.losttales.chat.server.ChatConsoleStream;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.network.packet.LostTalesChatConsoleSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatHistorySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.network.packet.character.CharacterAppearanceSyncPacket;
import org.junit.Test;

/** Common chat routing and packet classes must remain loadable without client code. */
public final class ChatDedicatedServerIsolationTest {

    @Test
    public void commonChatClassesDoNotReferenceMinecraftClient() throws Exception {
        DedicatedServerIsolation.assertServerSafe(
                ChatChannel.class,
                LostTalesChatService.class,
                LostTalesChatSendPacket.class,
                LostTalesChatSendPacket.Handler.class,
                LostTalesChatMessagePacket.class,
                LostTalesChatMessagePacket.Handler.class,
                LostTalesChatHistorySyncPacket.class,
                LostTalesChatHistorySyncPacket.Handler.class,
                ChatHistory.class,
                ChatHistory.Audience.class,
                ChatHistory.Requester.class,
                ChatConsoleEvent.class,
                ChatConsoleStream.class,
                ChatConsoleCommandHandler.class,
                LostTalesChatConsoleSyncPacket.class,
                LostTalesChatConsoleSyncPacket.Handler.class,
                ChatEmoji.class,
                ChatEmojiParser.class,
                ChatEmojiParser.Segment.class,
                ChatFormattingCodes.class,
                ChatMentions.class,
                ChatMentionCandidate.class,
                ChatNameSuggester.class,
                ChatShareTokenParser.class,
                ChatShareKind.class,
                ChatShareReference.class,
                ChatShareTokenParser.Token.class,
                ChatShareSuggester.class,
                ChatShowcase.class,
                CharacterAppearanceSyncPacket.class);
    }
}
