package com.ninuna.losttales.character.lore.ownership;

import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LoreCharacterOwnershipWorldDataTest {

    private static final String GANDALF = "losttales:gandalf";

    @Before
    public void loadDefinitions() {
        LoreCharacterRegistry.load(null);
    }

    @Test
    public void claimReleaseAndReclaimKeepOnePersistentCharacterId() {
        LoreCharacterOwnershipWorldData data = createData();
        UUID firstOwner = uuid("10000000-0000-0000-0000-000000000001");
        UUID secondOwner = uuid("20000000-0000-0000-0000-000000000002");
        UUID characterId = uuid("30000000-0000-0000-0000-000000000003");

        LoreCharacterOwnershipResult claimed = data.tryClaim(
                GANDALF, firstOwner, characterId, 0L, 100L);
        LoreCharacterOwnershipResult released = data.tryRelease(
                GANDALF, firstOwner, 1L, 200L);
        LoreCharacterOwnershipResult reclaimed = data.tryClaim(
                GANDALF, secondOwner, UUID.randomUUID(), 2L, 300L);

        assertEquals(LoreCharacterOwnershipResult.Status.CLAIMED,
                claimed.getStatus());
        assertEquals(LoreCharacterOwnershipResult.Status.RELEASED,
                released.getStatus());
        assertEquals(LoreCharacterOwnershipResult.Status.CLAIMED,
                reclaimed.getStatus());
        assertEquals(3L, reclaimed.getRecord().getRevision());
        assertEquals(characterId, reclaimed.getRecord().getCharacterId());
        assertEquals(secondOwner, reclaimed.getRecord().getOwnerId());
        assertEquals(1, data.getRecordCount());
    }

    @Test
    public void anotherOwnerCannotClaimAnOwnedIdentity() {
        LoreCharacterOwnershipWorldData data = createData();
        UUID owner = uuid("40000000-0000-0000-0000-000000000004");
        UUID attacker = uuid("50000000-0000-0000-0000-000000000005");
        UUID characterId = uuid("60000000-0000-0000-0000-000000000006");

        data.tryClaim(GANDALF, owner, characterId, 0L, 100L);
        LoreCharacterOwnershipResult result = data.tryClaim(
                GANDALF, attacker, UUID.randomUUID(), 1L, 101L);

        assertEquals(LoreCharacterOwnershipResult.Status.ALREADY_CLAIMED,
                result.getStatus());
        assertEquals(owner, data.getRecord(GANDALF).getOwnerId());
        assertEquals(1L, data.getRecord(GANDALF).getRevision());
    }

    @Test
    public void releaseRequiresCurrentOwnerAndRevision() {
        LoreCharacterOwnershipWorldData data = createData();
        UUID owner = uuid("70000000-0000-0000-0000-000000000007");
        UUID attacker = uuid("80000000-0000-0000-0000-000000000008");
        data.tryClaim(GANDALF, owner, UUID.randomUUID(), 0L, 100L);

        LoreCharacterOwnershipResult unauthorized = data.tryRelease(
                GANDALF, attacker, 1L, 200L);
        LoreCharacterOwnershipResult stale = data.tryRelease(
                GANDALF, owner, 0L, 200L);

        assertEquals(LoreCharacterOwnershipResult.Status.NOT_OWNER,
                unauthorized.getStatus());
        assertEquals(LoreCharacterOwnershipResult.Status.STALE_REVISION,
                stale.getStatus());
        assertTrue(data.getRecord(GANDALF).isClaimed());
    }

    @Test
    public void incompleteOrUnknownDefinitionsCannotBeClaimed() {
        LoreCharacterOwnershipWorldData data = createData();
        UUID owner = uuid("90000000-0000-0000-0000-000000000009");

        LoreCharacterOwnershipResult incomplete = data.tryClaim(
                "losttales:sauron", owner, UUID.randomUUID(), 0L, 100L);
        LoreCharacterOwnershipResult unknown = data.tryClaim(
                "losttales:not_registered", owner,
                UUID.randomUUID(), 0L, 100L);

        assertEquals(
                LoreCharacterOwnershipResult.Status.APPEARANCE_NOT_CONFIGURED,
                incomplete.getStatus());
        assertEquals(
                LoreCharacterOwnershipResult.Status.UNKNOWN_LORE_CHARACTER,
                unknown.getStatus());
        assertEquals(0, data.getRecordCount());
    }

    @Test
    public void nbtRoundTripPreservesReleasedOwnershipRevision() {
        LoreCharacterOwnershipWorldData source = createData();
        UUID owner = uuid("a0000000-0000-0000-0000-00000000000a");
        UUID characterId = uuid("b0000000-0000-0000-0000-00000000000b");
        source.tryClaim(GANDALF, owner, characterId, 0L, 100L);
        source.tryRelease(GANDALF, owner, 1L, 200L);

        NBTTagCompound serialized = new NBTTagCompound();
        source.writeToNBT(serialized);
        LoreCharacterOwnershipWorldData restored = createData();
        restored.readFromNBT(serialized);

        assertFalse(restored.isReadOnly());
        LoreCharacterOwnershipRecord record = restored.getRecord(GANDALF);
        assertEquals(characterId, record.getCharacterId());
        assertNull(record.getOwnerId());
        assertEquals(2L, record.getRevision());
        assertEquals(100L, record.getLastClaimedAt());
        assertEquals(200L, record.getLastReleasedAt());
    }

    @Test
    public void duplicateSavedIdentityMakesWholeStoreReadOnly() {
        LoreCharacterOwnershipWorldData source = createData();
        UUID owner = uuid("c0000000-0000-0000-0000-00000000000c");
        source.tryClaim(GANDALF, owner, UUID.randomUUID(), 0L, 100L);
        NBTTagCompound serialized = new NBTTagCompound();
        source.writeToNBT(serialized);
        NBTTagList records = serialized.getTagList(
                "Records", Constants.NBT.TAG_COMPOUND);
        records.appendTag(records.getCompoundTagAt(0).copy());

        LoreCharacterOwnershipWorldData restored = createData();
        restored.readFromNBT(serialized);

        assertTrue(restored.isReadOnly());
        assertEquals("duplicate_lore_character_id",
                restored.getReadOnlyReason());
        assertEquals(0, restored.getRecordCount());
        assertEquals(1, restored.getQuarantinedEntryCount());
        assertEquals(LoreCharacterOwnershipResult.Status.STORAGE_READ_ONLY,
                restored.tryClaim(GANDALF, owner,
                        UUID.randomUUID(), 0L, 200L).getStatus());

        NBTTagCompound preserved = new NBTTagCompound();
        restored.writeToNBT(preserved);
        assertEquals(2, preserved.getTagList(
                "Records", Constants.NBT.TAG_COMPOUND).tagCount());
    }

    @Test
    public void partialOwnerUuidMakesStoreReadOnly() {
        LoreCharacterOwnershipWorldData source = createData();
        UUID owner = uuid("d0000000-0000-0000-0000-00000000000d");
        source.tryClaim(GANDALF, owner, UUID.randomUUID(), 0L, 100L);
        NBTTagCompound serialized = new NBTTagCompound();
        source.writeToNBT(serialized);
        serialized.getTagList("Records", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0).removeTag("OwnerUUIDLeast");

        LoreCharacterOwnershipWorldData restored = createData();
        restored.readFromNBT(serialized);

        assertTrue(restored.isReadOnly());
        assertTrue(restored.getReadOnlyReason().startsWith("malformed_record_"));
        assertEquals(0, restored.getRecordCount());
    }

    @Test
    public void futureDataVersionIsPreservedWithoutDowngradeWrites() {
        NBTTagCompound future = new NBTTagCompound();
        future.setInteger("DataVersion", 99);
        future.setString("FutureField", "preserve-me");
        LoreCharacterOwnershipWorldData restored = createData();

        restored.readFromNBT(future);

        assertTrue(restored.isReadOnly());
        assertEquals(99, restored.getUnsupportedDataVersion());
        assertEquals("unsupported_data_version", restored.getReadOnlyReason());
        NBTTagCompound written = new NBTTagCompound();
        restored.writeToNBT(written);
        assertEquals(99, written.getInteger("DataVersion"));
        assertEquals("preserve-me", written.getString("FutureField"));
    }

    @Test
    public void simultaneousClaimsProduceExactlyOneOwner() throws Exception {
        final LoreCharacterOwnershipWorldData data = createData();
        final LoreCharacterOwnershipResult[] results =
                new LoreCharacterOwnershipResult[2];
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        Thread first = claimThread(data, results, 0, ready, start,
                uuid("e0000000-0000-0000-0000-00000000000e"),
                uuid("e1000000-0000-0000-0000-00000000000e"));
        Thread second = claimThread(data, results, 1, ready, start,
                uuid("f0000000-0000-0000-0000-00000000000f"),
                uuid("f1000000-0000-0000-0000-00000000000f"));
        first.start();
        second.start();
        assertTrue(ready.await(5L, TimeUnit.SECONDS));
        start.countDown();
        first.join(5000L);
        second.join(5000L);

        int claimed = 0;
        int rejected = 0;
        for (LoreCharacterOwnershipResult result : results) {
            assertTrue(result != null);
            if (result.getStatus()
                    == LoreCharacterOwnershipResult.Status.CLAIMED) {
                claimed++;
            } else if (result.getStatus()
                    == LoreCharacterOwnershipResult.Status.ALREADY_CLAIMED) {
                rejected++;
            }
        }
        assertEquals(1, claimed);
        assertEquals(1, rejected);
        assertEquals(1, data.getRecordCount());
    }

    private static Thread claimThread(
            final LoreCharacterOwnershipWorldData data,
            final LoreCharacterOwnershipResult[] results,
            final int index,
            final CountDownLatch ready,
            final CountDownLatch start,
            final UUID ownerId,
            final UUID characterId) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                ready.countDown();
                try {
                    start.await();
                    results[index] = data.tryClaim(
                            GANDALF, ownerId, characterId,
                            0L, 100L + index);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private static LoreCharacterOwnershipWorldData createData() {
        return new LoreCharacterOwnershipWorldData("test_lore_ownership");
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
