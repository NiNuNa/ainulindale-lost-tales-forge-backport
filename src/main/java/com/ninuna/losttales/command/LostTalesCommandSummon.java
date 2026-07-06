package com.ninuna.losttales.command;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.entity.ELostTalesEntity;
import net.minecraft.command.server.CommandSummon;

public class LostTalesCommandSummon extends CommandSummon {
    private final String commandName;

    public LostTalesCommandSummon() {
        this(LostTalesMetaData.MOD_ID + "_summon");
    }

    public LostTalesCommandSummon(String commandName) {
        this.commandName = commandName;
    }

    @Override
    public String getCommandName() {
        return commandName;
    }

    @Override
    protected String[] func_147182_d() {
        return ELostTalesEntity.getAllEntityNames().toArray(new String[0]);
    }
}