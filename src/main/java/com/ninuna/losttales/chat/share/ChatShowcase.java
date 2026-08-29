package com.ninuna.losttales.chat.share;

import java.io.IOException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

/**
 * One server-validated shared thing attached to a chat message, keyed by
 * the index of the token it replaces. An item travels as compressed NBT the
 * server produced from the sender's real inventory; a marker travels as the
 * public fields of the record the server looked up and confirmed the sender
 * may see. Clients decode each once on arrival. Bounds are deliberately
 * tight: a message may show a few ordinary things, not ferry data.
 */
public final class ChatShowcase {
    /** Compressed NBT bytes per stack; enchanted and named gear fits easily. */
    public static final int MAX_STACK_BYTES = 1024;
    /**
     * Every showcase one message carries, together. A message may share
     * as many things as it has tokens for, but the set of them is what
     * the server sends to every recipient of the line, so the bytes are
     * what is bounded rather than the count: a line of markers costs
     * almost nothing, a line of fully enchanted stacks stops being
     * attached once it reaches this. The server drops the ones past it
     * and leaves their tokens as the text they were typed as.
     */
    public static final int MAX_TOTAL_BYTES = 8192;
    /** Wire overhead of one showcase: its token index, kind and lengths. */
    private static final int SHOWCASE_OVERHEAD_BYTES = 16;
    /** Decompressed NBT budget in bits, the unit {@link NBTSizeTracker} counts. */
    private static final long MAX_DECOMPRESSED_BITS = 16384L * 8L;
    public static final int MAX_MARKER_ID_BYTES =
            ChatShareReference.MAX_MARKER_ID_BYTES;
    public static final int MAX_MARKER_NAME_BYTES = 256;
    public static final int MAX_MARKER_STYLE_BYTES = 64;
    /** Matches {@code LostTalesMapMarkerRecord.MAX_ABSOLUTE_COORDINATE}. */
    public static final double MAX_MARKER_COORDINATE = 30000000.0D;

    private final ChatShareKind kind;
    private final int tokenIndex;
    private final byte[] stackData;
    private final String markerId;
    private final String markerName;
    private final String markerIcon;
    private final String markerColor;
    private final int markerDimension;
    private final double markerX;
    private final double markerZ;

    private ChatShowcase(ChatShareKind kind, int tokenIndex,
                         byte[] stackData, String markerId,
                         String markerName, String markerIcon,
                         String markerColor, int markerDimension,
                         double markerX, double markerZ) {
        if (kind == null || tokenIndex < 0
                || tokenIndex >= ChatShareTokenParser.MAX_TOKENS) {
            throw new IllegalArgumentException("invalid chat showcase");
        }
        this.kind = kind;
        this.tokenIndex = tokenIndex;
        this.stackData = stackData == null ? new byte[0] : stackData.clone();
        this.markerId = markerId == null ? "" : markerId;
        this.markerName = markerName == null ? "" : markerName;
        this.markerIcon = markerIcon == null ? "" : markerIcon;
        this.markerColor = markerColor == null ? "" : markerColor;
        this.markerDimension = markerDimension;
        this.markerX = markerX;
        this.markerZ = markerZ;
    }

    public static ChatShowcase item(int tokenIndex, byte[] stackData) {
        if (stackData == null || stackData.length == 0
                || stackData.length > MAX_STACK_BYTES) {
            throw new IllegalArgumentException("invalid chat item showcase");
        }
        return new ChatShowcase(ChatShareKind.ITEM, tokenIndex, stackData,
                "", "", "", "", 0, 0.0D, 0.0D);
    }

    public static ChatShowcase marker(int tokenIndex, String markerId,
                                      String markerName, String markerIcon,
                                      String markerColor,
                                      int markerDimension,
                                      double markerX, double markerZ) {
        if (markerId == null || markerId.length() == 0
                || utf8Length(markerId) > MAX_MARKER_ID_BYTES
                || markerName == null || markerName.length() == 0
                || utf8Length(markerName) > MAX_MARKER_NAME_BYTES
                || utf8Length(markerIcon) > MAX_MARKER_STYLE_BYTES
                || utf8Length(markerColor) > MAX_MARKER_STYLE_BYTES
                || !isFiniteCoordinate(markerX)
                || !isFiniteCoordinate(markerZ)) {
            throw new IllegalArgumentException(
                    "invalid chat marker showcase");
        }
        return new ChatShowcase(ChatShareKind.MARKER, tokenIndex, null,
                markerId, markerName, markerIcon, markerColor,
                markerDimension, markerX, markerZ);
    }

    public static boolean isFiniteCoordinate(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && Math.abs(value) <= MAX_MARKER_COORDINATE;
    }

    private static int utf8Length(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return value.getBytes("UTF-8").length;
        } catch (java.io.UnsupportedEncodingException exception) {
            return value.length() * 3;
        }
    }

    /**
     * What this showcase costs on the wire, its framing included: what
     * {@link #MAX_TOTAL_BYTES} is counted in.
     */
    public int serializedBytes() {
        if (this.kind == ChatShareKind.ITEM) {
            return SHOWCASE_OVERHEAD_BYTES + this.stackData.length;
        }
        return SHOWCASE_OVERHEAD_BYTES + utf8Length(this.markerId)
                + utf8Length(this.markerName) + utf8Length(this.markerIcon)
                + utf8Length(this.markerColor) + 4 + 8 + 8;
    }

    /** The wire cost of a whole set of showcases. */
    public static int serializedBytes(
            java.util.List<ChatShowcase> showcases) {
        int total = 0;
        if (showcases != null) {
            for (ChatShowcase showcase : showcases) {
                if (showcase != null) {
                    total += showcase.serializedBytes();
                }
            }
        }
        return total;
    }

    public ChatShareKind getKind() { return this.kind; }
    public int getTokenIndex() { return this.tokenIndex; }
    public byte[] getStackData() { return this.stackData.clone(); }
    public String getMarkerId() { return this.markerId; }
    public String getMarkerName() { return this.markerName; }
    public String getMarkerIcon() { return this.markerIcon; }
    public String getMarkerColor() { return this.markerColor; }
    public int getMarkerDimension() { return this.markerDimension; }
    public double getMarkerX() { return this.markerX; }
    public double getMarkerZ() { return this.markerZ; }

    /**
     * Serializes a stack for the wire, or returns null when the stack is
     * empty, fails to serialize, or exceeds {@link #MAX_STACK_BYTES}.
     */
    public static byte[] encodeStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return null;
        }
        try {
            byte[] encoded = CompressedStreamTools.compress(
                    stack.writeToNBT(new NBTTagCompound()));
            return encoded == null || encoded.length == 0
                    || encoded.length > MAX_STACK_BYTES ? null : encoded;
        } catch (IOException exception) {
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Decodes bounded stack data; null when absent, oversized, or corrupt. */
    public static ItemStack decodeStack(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_STACK_BYTES) {
            return null;
        }
        try {
            NBTTagCompound nbt = CompressedStreamTools.func_152457_a(
                    data, new NBTSizeTracker(MAX_DECOMPRESSED_BITS));
            ItemStack stack = nbt == null ? null
                    : ItemStack.loadItemStackFromNBT(nbt);
            return stack == null || stack.getItem() == null
                    || stack.stackSize <= 0 ? null : stack;
        } catch (IOException exception) {
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
