package com.ninuna.losttales.network.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFormattingCodes;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/** Common chat routing and packet classes must remain loadable without client code. */
public final class ChatDedicatedServerIsolationTest {
    private static final Charset CLASS_FILE_TEXT =
            Charset.forName("ISO-8859-1");

    @Test
    public void commonChatClassesDoNotReferenceMinecraftClient() throws Exception {
        assertNoClientReference(ChatChannel.class);
        assertNoClientReference(LostTalesChatService.class);
        assertNoClientReference(LostTalesChatSendPacket.class);
        assertNoClientReference(LostTalesChatSendPacket.Handler.class);
        assertNoClientReference(LostTalesChatMessagePacket.class);
        assertNoClientReference(LostTalesChatMessagePacket.Handler.class);
        assertNoClientReference(ChatEmoji.class);
        assertNoClientReference(ChatEmojiParser.class);
        assertNoClientReference(ChatEmojiParser.Segment.class);
        assertNoClientReference(ChatFormattingCodes.class);
        assertNoClientReference(ChatMentions.class);
    }

    private static void assertNoClientReference(Class<?> type)
            throws IOException {
        String path = '/' + type.getName().replace('.', '/') + ".class";
        InputStream input = type.getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing class resource " + path);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            String constants = new String(
                    output.toByteArray(), CLASS_FILE_TEXT);
            assertFalse(type.getName() + " references client classes",
                    constants.contains("net/minecraft/client"));
        } finally {
            input.close();
        }
    }
}
