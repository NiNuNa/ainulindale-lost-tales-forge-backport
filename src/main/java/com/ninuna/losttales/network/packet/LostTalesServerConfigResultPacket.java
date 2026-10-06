package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What became of an operator's changes, back to the client that sent them.
 * Its words travel as lang keys and their arguments, so the page reads them
 * in its player's language; a key outside the config's words is malformed.
 */
public final class LostTalesServerConfigResultPacket implements IMessage {

    private static final int MAX_NAMES = ServerConfigPacketCodec.MAX_ENTRIES;
    private static final int MAX_NAME_BYTES = 256;
    private static final int MAX_KEY_BYTES = 128;
    private static final int MAX_ARGUMENTS = 4;
    private static final int MAX_ARGUMENT_BYTES = 512;

    private ServerConfigApplyResult result;
    private boolean malformed;

    public LostTalesServerConfigResultPacket() {}

    public LostTalesServerConfigResultPacket(ServerConfigApplyResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        this.result = result;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            List<String> applied = readNames(buffer, "applied");
            int refusedCount = LostTalesPacketCodec.readCount(buffer, MAX_NAMES, "refused");
            List<ServerConfigApplyResult.Refusal> refused =
                    new ArrayList<ServerConfigApplyResult.Refusal>(refusedCount);
            for (int index = 0; index < refusedCount; index++) {
                String name = LostTalesPacketCodec.readUtf8String(buffer, MAX_NAME_BYTES);
                String reason = readKey(buffer);
                int argumentCount = LostTalesPacketCodec.readCount(buffer, MAX_ARGUMENTS,
                        "arguments");
                String[] arguments = new String[argumentCount];
                for (int argument = 0; argument < argumentCount; argument++) {
                    arguments[argument] = LostTalesPacketCodec.readUtf8String(buffer,
                            MAX_ARGUMENT_BYTES);
                }
                refused.add(new ServerConfigApplyResult.Refusal(name, reason, arguments));
            }
            int restartedCount = LostTalesPacketCodec.readCount(buffer, MAX_NAMES,
                    "restarted");
            List<String> restarted = new ArrayList<String>(restartedCount);
            for (int index = 0; index < restartedCount; index++) {
                restarted.add(readKey(buffer));
            }
            String message = LostTalesPacketCodec.readUtf8String(buffer, MAX_KEY_BYTES);
            if (message.length() > 0) {
                requireWords(message);
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.result = new ServerConfigApplyResult(applied, refused, restarted, message);
        } catch (RuntimeException exception) {
            this.result = null;
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        writeNames(buffer, this.result.getApplied(), "applied");
        List<ServerConfigApplyResult.Refusal> refused = this.result.getRefused();
        LostTalesPacketCodec.writeCount(buffer, refused.size(), MAX_NAMES, "refused");
        for (ServerConfigApplyResult.Refusal refusal : refused) {
            LostTalesPacketCodec.writeUtf8String(buffer, refusal.getName(), MAX_NAME_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, refusal.getReasonKey(), MAX_KEY_BYTES);
            List<String> arguments = refusal.getReasonArguments();
            LostTalesPacketCodec.writeCount(buffer, arguments.size(), MAX_ARGUMENTS,
                    "arguments");
            for (String argument : arguments) {
                LostTalesPacketCodec.writeUtf8String(buffer, argument, MAX_ARGUMENT_BYTES);
            }
        }
        List<String> restarted = this.result.getRestarted();
        LostTalesPacketCodec.writeCount(buffer, restarted.size(), MAX_NAMES, "restarted");
        for (String key : restarted) {
            LostTalesPacketCodec.writeUtf8String(buffer, key, MAX_KEY_BYTES);
        }
        LostTalesPacketCodec.writeUtf8String(buffer, this.result.getMessage(), MAX_KEY_BYTES);
    }

    /** A lang key of the config's words; anything else makes the payload malformed. */
    private static String readKey(ByteBuf buffer) {
        return requireWords(LostTalesPacketCodec.readUtf8String(buffer, MAX_KEY_BYTES));
    }

    private static String requireWords(String key) {
        if (!key.startsWith(ServerConfigApplyResult.WORDS)
                || key.length() == ServerConfigApplyResult.WORDS.length()) {
            throw new IllegalArgumentException("not a config word: " + key);
        }
        return key;
    }

    private static void writeNames(ByteBuf buffer, List<String> names, String field) {
        LostTalesPacketCodec.writeCount(buffer, names.size(), MAX_NAMES, field);
        for (String name : names) {
            LostTalesPacketCodec.writeUtf8String(buffer, name, MAX_NAME_BYTES);
        }
    }

    private static List<String> readNames(ByteBuf buffer, String field) {
        int count = LostTalesPacketCodec.readCount(buffer, MAX_NAMES, field);
        List<String> names = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            names.add(LostTalesPacketCodec.readUtf8String(buffer, MAX_NAME_BYTES));
        }
        return names;
    }

    public ServerConfigApplyResult getResult() {
        return this.result;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesServerConfigResultPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesServerConfigResultPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleServerConfigResult(message);
                }
            });
            return null;
        }
    }
}
