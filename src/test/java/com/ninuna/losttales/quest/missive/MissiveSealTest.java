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

    private static LostTalesMissiveData missive(int coins, long posted) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("group", "zombie");
        params.put("count", "5");
        return LostTalesMissiveData.builder(QUEST, "kill")
                .titleId("clear_the_paths")
                .issuerId("road_warden")
                .descriptionId("kill")
                .flavorId("roads_open")
                .target("Zombie")
                .generationWorldTime(posted)
                .timeLimitTicks(48000L)
                .context("board", "dim0_4_65_9")
                .objective(new LostTalesMissiveObjectiveData("kill_zombies",
                        "kill", false, params))
                .rewardData(LostTalesMissiveRewardData.experienceAndCoins(40, coins))
                .build();
    }

    @Test
    public void aLetterTheServerSealedIsGenuine() {
        LostTalesMissiveData letter = missive(12, 1200L);
        byte[] seal = MissiveSeal.sign(key(1), letter);
        assertNotNull(seal);
        assertEquals(MissiveSeal.LENGTH, seal.length);
        assertTrue(MissiveSeal.verifies(key(1), letter, seal));
    }

    @Test
    public void aChangedRewardOrPostingTimeBreaksTheSeal() {
        byte[] seal = MissiveSeal.sign(key(1), missive(12, 1200L));
        assertFalse(MissiveSeal.verifies(key(1), missive(999, 1200L), seal));
        assertFalse(MissiveSeal.verifies(key(1), missive(12, 999999L), seal));
    }

    /** A server's letter is sealed over its words as written, and over its being written (QUEST-42). */
    @Test
    public void aWrittenLettersWordsAreSealed() {
        LostTalesMissiveData letter = wolves(true, "Wanted: Wolf Pelts");
        byte[] seal = MissiveSeal.sign(key(1), letter);
        assertNotNull(seal);
        assertTrue(MissiveSeal.verifies(key(1), letter, seal));
        assertFalse(MissiveSeal.verifies(key(1), wolves(true, "Wanted: Wolf Pelts!"), seal));
        byte[] asWords = MissiveSeal.sign(key(1), wolves(true, "clear_the_paths"));
        assertFalse("the same words as ids are another letter",
                MissiveSeal.verifies(key(1), wolves(false, "clear_the_paths"), asWords));
    }

    private static LostTalesMissiveData wolves(boolean written, String title) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("entity", "lotr.Wolf");
        params.put("count", "5");
        return LostTalesMissiveData.builder(QUEST, "kill")
                .written(written)
                .titleId(title)
                .issuerId("road_warden")
                .descriptionId("kill")
                .target("lotr.Wolf")
                .generationWorldTime(1200L)
                .objective(new LostTalesMissiveObjectiveData("kill_lotr_wolf",
                        "kill", false, params))
                .build();
    }

    /** A gather notice hands its items over; a letter changed by hand to keep them has no seal that fits. */
    @Test
    public void aGatherLettersHandOverIsSealed() {
        LostTalesMissiveData taking = gather("true");
        byte[] seal = MissiveSeal.sign(key(1), taking);
        assertTrue(MissiveSeal.verifies(key(1), taking, seal));
        assertFalse(MissiveSeal.verifies(key(1), gather("false"), seal));
        assertFalse(MissiveSeal.verifies(key(1), gather(null), seal));
    }

    private static LostTalesMissiveData gather(String take) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("item", "minecraft:coal");
        params.put("count", "10");
        if (take != null) {
            params.put("take", take);
        }
        return LostTalesMissiveData.builder(QUEST, "gather")
                .titleId("materials_wanted")
                .issuerId("quartermaster")
                .descriptionId("gather")
                .flavorId("plain_notice")
                .target("minecraft:coal")
                .generationWorldTime(1200L)
                .objective(new LostTalesMissiveObjectiveData("gather_coal",
                        "gather", false, params))
                .rewardData(LostTalesMissiveRewardData.experienceAndCoins(30, 20))
                .build();
    }

    @Test
    public void anotherWorldsKeyFindsNoSeal() {
        LostTalesMissiveData letter = missive(12, 1200L);
        assertFalse(MissiveSeal.verifies(key(2), letter, MissiveSeal.sign(key(1), letter)));
    }

    @Test
    public void noKeyOrNoSealIsNeverGenuine() {
        LostTalesMissiveData letter = missive(12, 1200L);
        assertNull(MissiveSeal.sign(null, letter));
        assertFalse(MissiveSeal.verifies(null, letter, MissiveSeal.sign(key(1), letter)));
        assertFalse(MissiveSeal.verifies(key(1), letter, null));
        assertFalse(MissiveSeal.verifies(key(1), letter, new byte[3]));
    }

    @Test
    public void theSealSurvivesTheLettersOwnSave() {
        LostTalesMissiveData letter = missive(12, 1200L);
        byte[] seal = MissiveSeal.sign(key(7), letter);
        LostTalesMissiveData reread = LostTalesMissiveNbt.readFromNBT(
                LostTalesMissiveNbt.writeToNBT(letter));
        assertTrue(MissiveSeal.verifies(key(7), reread, seal));
    }

    /**
     * A letter's days run from its first posting (QUEST-34), and the seal
     * covers that time: a letter whose posting time was moved by hand, to
     * buy it fresh days, no longer carries a seal that fits.
     */
    @Test
    public void theSealCoversTheFirstPosting() {
        LostTalesMissiveData letter = LostTalesMissiveData.builder(
                "losttales:missive/generated/b/1_0", "kill").titleId("dangerous_work")
                .generationWorldTime(1000L)
                .objective(new LostTalesMissiveObjectiveData("o", "kill", false, null))
                .build();
        LostTalesMissiveData moved = LostTalesMissiveData.builder(
                "losttales:missive/generated/b/1_0", "kill").titleId("dangerous_work")
                .generationWorldTime(50000L)
                .objective(new LostTalesMissiveObjectiveData("o", "kill", false, null))
                .build();
        byte[] seal = MissiveSeal.sign(key(7), letter);
        assertTrue(MissiveSeal.verifies(key(7), letter, seal));
        assertFalse(MissiveSeal.verifies(key(7), moved, seal));
    }

    @Test
    public void theKeyIsMadeOnceAndKept() {
        MissiveSealWorldData data = new MissiveSealWorldData(
                MissiveSealWorldData.DATA_NAME);
        byte[] first = data.key();
        assertEquals(MissiveSeal.LENGTH, first.length);
        assertArrayEquals(first, data.key());

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        MissiveSealWorldData loaded = new MissiveSealWorldData(
                MissiveSealWorldData.DATA_NAME);
        loaded.readFromNBT(saved);
        assertArrayEquals(first, loaded.key());
    }

    @Test
    public void aKeyOfAnotherVersionIsKeptAndSealsNothing() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger(MissiveSealWorldData.TAG_VERSION, MissiveSealWorldData.DATA_VERSION + 1);
        newer.setByteArray(MissiveSealWorldData.TAG_KEY, key(3));
        MissiveSealWorldData data = new MissiveSealWorldData(
                MissiveSealWorldData.DATA_NAME);
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
