package com.ninuna.losttales.command;

import com.ninuna.losttales.util.EnglishWords;
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
 * the checks under test. What it is told it keeps, and reads in English.
 */
final class FakeCommandSender implements ICommandSender {

    private final String name;
    private final int operatorLevel;
    private final List<IChatComponent> told = new ArrayList<IChatComponent>();

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

    /** The lines the sender was told, as sent. */
    List<IChatComponent> heard() {
        return this.told;
    }

    /** What the sender was told, each line as a game in English shows it. */
    List<String> told() {
        List<String> lines = new ArrayList<String>(this.told.size());
        for (IChatComponent line : this.told) {
            lines.add(EnglishWords.INSTANCE.read(line));
        }
        return lines;
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
        this.told.add(message);
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
