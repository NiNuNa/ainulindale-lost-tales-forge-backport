package com.ninuna.losttales.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A command sender that answers only what a permission check asks it:
 * its name, and the operator level it holds. Everything else a sender
 * can be asked for belongs to a running game and is never reached by
 * the checks under test.
 */
final class FakeCommandSender implements ICommandSender {

    private final String name;
    private final int operatorLevel;
    private final List<String> told = new ArrayList<String>();

    FakeCommandSender(String name, int operatorLevel) {
        this.name = name;
        this.operatorLevel = operatorLevel;
    }

    /** Someone with no operator level at all: what a plain player is. */
    static FakeCommandSender player(String name) {
        return new FakeCommandSender(name, 0);
    }

    /** An operator at the level the capabilities name. */
    static FakeCommandSender operator(String name) {
        return new FakeCommandSender(name, 2);
    }

    /** What the sender was told, for asserting a refusal was explained. */
    List<String> told() {
        return this.told;
    }

    @Override
    public String getCommandSenderName() {
        return this.name;
    }

    @Override
    public IChatComponent func_145748_c_() {
        throw new UnsupportedOperationException("not asked by a permission check");
    }

    @Override
    public void addChatMessage(IChatComponent message) {
        this.told.add(message == null ? "" : message.getUnformattedText());
    }

    @Override
    public boolean canCommandSenderUseCommand(int level, String node) {
        return this.operatorLevel >= level;
    }

    @Override
    public ChunkCoordinates getPlayerCoordinates() {
        throw new UnsupportedOperationException("not asked by a permission check");
    }

    @Override
    public World getEntityWorld() {
        throw new UnsupportedOperationException("not asked by a permission check");
    }
}
