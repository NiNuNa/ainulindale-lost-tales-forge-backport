package com.ninuna.losttales.quest.missive;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MissiveSealTest {
    private static final String QUEST = "losttales:missive/generated/dim0_4_65_9/1200_0";

    private static byte[] key(int seed) {
        byte[] key = new byte[MissiveSeal.LENGTH];
        Arrays.fill(key, (byte) seed);
        return key;
    }

    private static LostTalesMissiveData missive(String reward, long posted) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("group", "zombie");
        params.put("count", "5");
        return LostTalesMissiveData.builder(QUEST, "kill")
                .title("Missive: Clear the Paths")
                .issuer("A Road Warden")
                .description("Travellers have reported zombies.")
                .flavorText("The roads must remain open.")
                .repeatable(true)
                .firstComeFirstServed(true)
                .generationWorldTime(posted)
                .timeLimitTicks(48000L)
                .context("board", "dim0_4_65_9")
                .objective(new LostTalesMissiveObjectiveData("kill_zombies",
                        "kill", "Defeat 5 zombies.", false, params))
                .rewardData(LostTalesMissiveRewardData.experienceAndItems(40, reward))
                .build();
    }

    @Test
    public void aLetterTheServerSealedIsGenuine() {
        LostTalesMissiveData letter = missive("minecraft:emerald*2", 1200L);
        byte[] seal = MissiveSeal.sign(key(1), letter);
        assertNotNull(seal);
        assertEquals(MissiveSeal.LENGTH, seal.length);
        assertTrue(MissiveSeal.verifies(key(1), letter, seal));
    }

    @Test
    public void aChangedRewardOrPostingTimeBreaksTheSeal() {
        byte[] seal = MissiveSeal.sign(key(1), missive("minecraft:emerald*2", 1200L));
        assertFalse(MissiveSeal.verifies(key(1), missive("minecraft:diamond*64", 1200L), seal));
        assertFalse(MissiveSeal.verifies(key(1), missive("minecraft:emerald*2", 999999L), seal));
    }

    @Test
    public void anotherWorldsKeyFindsNoSeal() {
        LostTalesMissiveData letter = missive("minecraft:emerald*2", 1200L);
        assertFalse(MissiveSeal.verifies(key(2), letter, MissiveSeal.sign(key(1), letter)));
    }

    @Test
    public void noKeyOrNoSealIsNeverGenuine() {
        LostTalesMissiveData letter = missive("minecraft:emerald*2", 1200L);
        assertNull(MissiveSeal.sign(null, letter));
        assertFalse(MissiveSeal.verifies(null, letter, MissiveSeal.sign(key(1), letter)));
        assertFalse(MissiveSeal.verifies(key(1), letter, null));
        assertFalse(MissiveSeal.verifies(key(1), letter, new byte[3]));
    }

    @Test
    public void theSealSurvivesTheLettersOwnSave() {
        LostTalesMissiveData letter = missive("minecraft:emerald*2", 1200L);
        byte[] seal = MissiveSeal.sign(key(7), letter);
        LostTalesMissiveData reread = LostTalesMissiveNbt.readFromNBT(
                LostTalesMissiveNbt.writeToNBT(letter));
        assertTrue(MissiveSeal.verifies(key(7), reread, seal));
    }

    @Test
    public void theKeyIsMadeOnceAndKept() {
        MissiveSealWorldData data = new MissiveSealWorldData();
        byte[] first = data.key();
        assertEquals(MissiveSeal.LENGTH, first.length);
        assertArrayEquals(first, data.key());

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        MissiveSealWorldData loaded = new MissiveSealWorldData();
        loaded.readFromNBT(saved);
        assertArrayEquals(first, loaded.key());
    }

    @Test
    public void aKeyOfAnotherVersionIsKeptAndSealsNothing() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger(MissiveSealWorldData.TAG_VERSION, MissiveSealWorldData.DATA_VERSION + 1);
        newer.setByteArray(MissiveSealWorldData.TAG_KEY, key(3));
        MissiveSealWorldData data = new MissiveSealWorldData();
        data.readFromNBT(newer);
        assertTrue(data.isUnusable());
        assertNull(data.key());

        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(MissiveSealWorldData.DATA_VERSION + 1,
                written.getInteger(MissiveSealWorldData.TAG_VERSION));
        assertArrayEquals(key(3), written.getByteArray(MissiveSealWorldData.TAG_KEY));
    }
}
