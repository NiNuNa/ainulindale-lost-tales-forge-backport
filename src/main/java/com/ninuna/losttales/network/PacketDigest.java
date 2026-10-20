package com.ninuna.losttales.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A SHA-256 digest of a packet's bytes as they would cross the wire: what
 * tells a send of the same thing again from a change, without keeping the
 * bytes themselves.
 */
public final class PacketDigest {
    private PacketDigest() {}

    public static byte[] of(IMessage packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is not available", missing);
        } finally {
            buffer.release();
        }
    }
}
