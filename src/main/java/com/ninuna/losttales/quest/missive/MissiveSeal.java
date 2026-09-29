package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.network.packet.LostTalesMissiveCodec;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The server's seal on a missive letter: an HMAC-SHA256 of everything the
 * letter says, made with the world's own secret key.
 *
 * <p>A letter is item data, and a player in creative mode can give an item
 * any data at all. The seal is how the server knows a letter is one it wrote:
 * a letter whose words, work, reward, time limit or id were changed, or that
 * was made by hand, has no seal that matches, and cannot be accepted or
 * pinned. Pure logic, no Minecraft.</p>
 */
public final class MissiveSeal {
    /** The length of a key and of a seal, in bytes. */
    public static final int LENGTH = 32;

    private static final String ALGORITHM = "HmacSHA256";

    private MissiveSeal() {}

    /** The seal of the missive under {@code key}; null for a missive a letter cannot carry. */
    public static byte[] sign(byte[] key, LostTalesMissiveData missive) {
        byte[] content = canonicalBytes(missive);
        if (key == null || key.length != LENGTH || content == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(content);
        } catch (GeneralSecurityException unavailable) {
            return null;
        }
    }

    /** Whether {@code seal} is the missive's seal under {@code key}, compared in constant time. */
    public static boolean verifies(byte[] key, LostTalesMissiveData missive, byte[] seal) {
        if (seal == null || seal.length != LENGTH) {
            return false;
        }
        byte[] expected = sign(key, missive);
        return expected != null && MessageDigest.isEqual(expected, seal);
    }

    /**
     * Everything the missive says, in one fixed order: the letter as the
     * page is sent it, then what the page is not sent (who may take it, when
     * it was posted, where it came from). Null for a missive that does not
     * fit a letter.
     */
    static byte[] canonicalBytes(LostTalesMissiveData missive) {
        if (missive == null || !missive.isValid() || !LostTalesMissiveCodec.fits(missive)) {
            return null;
        }
        ByteBuf buffer = Unpooled.buffer();
        try {
            LostTalesMissiveCodec.write(buffer, missive);
            buffer.writeBoolean(missive.isRepeatable());
            buffer.writeBoolean(missive.isFirstComeFirstServed());
            buffer.writeLong(missive.getGenerationWorldTime());
            Map<String, String> context = missive.getGenerationContext();
            buffer.writeInt(context.size());
            for (Map.Entry<String, String> entry : context.entrySet()) {
                LostTalesPacketCodec.writeUtf8String(buffer, entry.getKey(),
                        LostTalesMissiveCodec.MAX_KEY_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer, entry.getValue(),
                        LostTalesMissiveCodec.MAX_VALUE_BYTES);
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } catch (RuntimeException unwritable) {
            return null;
        } finally {
            buffer.release();
        }
    }
}
