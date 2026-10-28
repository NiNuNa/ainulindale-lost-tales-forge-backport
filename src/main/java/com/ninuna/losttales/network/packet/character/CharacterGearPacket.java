package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.sync.CharacterGear;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * What one of a player's own characters wears in its saved life, sent to
 * that player alone when the stage asks ({@link CharacterGearRequestPacket},
 * CHAR-42): the character's id, then its five stacks in
 * {@link CharacterGear}'s order, each as Forge writes a stack. A stack
 * whose bytes pass {@link #MAX_STACK_BYTES} goes without its tag, so the
 * whole answer stays inside one payload. A payload that does not read
 * whole is discarded, and nothing of it reaches the client.
 */
public final class CharacterGearPacket implements IMessage {

    /** One stack on the wire, its tag compressed; enchanted, named and dyed armour fits easily. */
    static final int MAX_STACK_BYTES = 6144;
    /** The id and five stacks at their longest. */
    static final int MAX_PACKET_BYTES = 16 + CharacterGear.SLOTS * MAX_STACK_BYTES;

    private UUID characterId;
    private ItemStack[] stacks = new ItemStack[CharacterGear.SLOTS];
    private boolean malformed;

    public CharacterGearPacket() {}

    private CharacterGearPacket(UUID characterId, ItemStack[] stacks) {
        this.characterId = characterId;
        this.stacks = stacks;
    }

    /** The answer for {@code characterId}: its gear, each stack held to the wire's bound. */
    public static CharacterGearPacket of(UUID characterId, CharacterGear gear) {
        if (characterId == null || gear == null) {
            throw new IllegalArgumentException("invalid gear answer");
        }
        ItemStack[] stacks = new ItemStack[CharacterGear.SLOTS];
        for (int slot = 0; slot < CharacterGear.SLOTS; slot++) {
            ItemStack stack = gear.get(slot);
            if (stack != null && !isValid(stack)) {
                throw new IllegalArgumentException("invalid gear stack in slot " + slot);
            }
            stacks[slot] = bounded(stack);
        }
        return new CharacterGearPacket(characterId, stacks);
    }

    /** The stack as it goes on the wire: itself, or without its tag where that is too long. */
    static ItemStack bounded(ItemStack stack) {
        return bounded(stack, MAX_STACK_BYTES);
    }

    /**
     * A copy of the stack for the wire: whole while Forge writes it in
     * {@code maxBytes} or fewer, else without its tag. A stack with no tag
     * is never measured.
     */
    public static ItemStack bounded(ItemStack stack, int maxBytes) {
        if (stack == null) {
            return null;
        }
        if (!stack.hasTagCompound() || encodedBytes(stack) <= maxBytes) {
            return stack.copy();
        }
        ItemStack bare = stack.copy();
        bare.setTagCompound(null);
        return bare;
    }

    private static int encodedBytes(ItemStack stack) {
        ByteBuf scratch = Unpooled.buffer();
        try {
            ByteBufUtils.writeItemStack(scratch, stack);
            return scratch.readableBytes();
        } finally {
            scratch.release();
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid gear packet size");
            }
            UUID id = LostTalesPacketCodec.readUuid(buffer);
            ItemStack[] read = new ItemStack[CharacterGear.SLOTS];
            for (int slot = 0; slot < CharacterGear.SLOTS; slot++) {
                ItemStack stack = ByteBufUtils.readItemStack(buffer);
                if (stack != null && !isValid(stack)) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid gear stack in slot " + slot);
                }
                read[slot] = stack;
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.characterId = id;
            this.stacks = read;
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.characterId = null;
            this.stacks = new ItemStack[CharacterGear.SLOTS];
            if (buffer != null && buffer.isReadable()) {
                buffer.skipBytes(buffer.readableBytes());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeUuid(buffer, this.characterId);
        for (int slot = 0; slot < CharacterGear.SLOTS; slot++) {
            ByteBufUtils.writeItemStack(buffer, this.stacks[slot]);
        }
    }

    /** A stack of a known item, at least one and no more than the item stacks to. */
    private static boolean isValid(ItemStack stack) {
        return stack.getItem() != null && stack.stackSize >= 1
                && stack.stackSize <= Math.max(1, stack.getMaxStackSize());
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    /** The five stacks as gear; nothing for a malformed payload. */
    public CharacterGear getGear() {
        return this.malformed ? CharacterGear.NONE : CharacterGear.of(this.stacks);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterGearPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterGearPacket message,
                                  MessageContext context) {
            if (message == null || message.malformed
                    || LostTalesMod.proxy == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleCharacterGear(message);
                }
            });
            return null;
        }
    }
}
