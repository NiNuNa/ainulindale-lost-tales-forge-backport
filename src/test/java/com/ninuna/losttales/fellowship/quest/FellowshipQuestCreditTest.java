package com.ninuna.losttales.fellowship.quest;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipFixtures;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A kill or an arrival counts for a member only when its maker and the
 * member both travel with that fellowship (FEL-28).
 */
public final class FellowshipQuestCreditTest {
    private static final UUID COMPANY = new UUID(5L, 5L);
    private static final UUID OTHER_COMPANY = new UUID(6L, 6L);
    private static final FellowshipMember MAKER = new FellowshipMember(
            new UUID(10L, 10L), new UUID(1L, 1L), "Aldric", 1L, FellowshipColor.GREEN);
    private static final FellowshipMember BEREN = new FellowshipMember(
            new UUID(20L, 20L), new UUID(2L, 2L), "Beren", 2L, FellowshipColor.BLUE);
    private static final FellowshipMember HURIN = new FellowshipMember(
            new UUID(30L, 30L), new UUID(3L, 3L), "Hurin", 3L, FellowshipColor.RED);

    @Test
    public void aMemberTravellingWithTheMakersFellowshipShares() {
        assertEquals(Arrays.asList(BEREN, HURIN),
                FellowshipQuestProgressCoordinator.sharingCredit(company(), MAKER.getIdentityId(),
                        travelling(COMPANY, COMPANY, COMPANY)));
    }

    @Test
    public void aMemberTravellingWithAnotherOrNoneDoesNot() {
        assertEquals(Collections.singletonList(BEREN),
                FellowshipQuestProgressCoordinator.sharingCredit(company(), MAKER.getIdentityId(),
                        travelling(COMPANY, COMPANY, OTHER_COMPANY)));
        assertEquals(Collections.singletonList(HURIN),
                FellowshipQuestProgressCoordinator.sharingCredit(company(), MAKER.getIdentityId(),
                        travelling(COMPANY, null, COMPANY)));
    }

    @Test
    public void aMakerTravellingWithNoneOrAnotherSharesWithNobody() {
        assertTrue(FellowshipQuestProgressCoordinator.sharingCredit(null, MAKER.getIdentityId(),
                travelling(null, COMPANY, COMPANY)).isEmpty());
        assertTrue(FellowshipQuestProgressCoordinator.sharingCredit(company(),
                MAKER.getIdentityId(), travelling(null, COMPANY, COMPANY)).isEmpty());
        assertTrue(FellowshipQuestProgressCoordinator.sharingCredit(company(),
                MAKER.getIdentityId(), travelling(OTHER_COMPANY, COMPANY, COMPANY)).isEmpty());
    }

    @Test
    public void aMakerOutsideTheFellowshipSharesWithNobody() {
        assertTrue(FellowshipQuestProgressCoordinator.sharingCredit(company(),
                new UUID(99L, 99L), travelling(COMPANY, COMPANY, COMPANY)).isEmpty());
    }

    private static Fellowship company() {
        return FellowshipFixtures.of(COMPANY, MAKER.getIdentityId(),
                Arrays.asList(MAKER, BEREN, HURIN));
    }

    /** The fellowships the maker, Beren and Hurin travel with; null for none. */
    private static FellowshipQuestProgressCoordinator.TravelledWith travelling(
            UUID maker, UUID beren, UUID hurin) {
        final Map<UUID, UUID> travels = new HashMap<UUID, UUID>();
        travels.put(MAKER.getIdentityId(), maker);
        travels.put(BEREN.getIdentityId(), beren);
        travels.put(HURIN.getIdentityId(), hurin);
        return new FellowshipQuestProgressCoordinator.TravelledWith() {
            @Override
            public UUID of(UUID identityId) {
                return travels.get(identityId);
            }
        };
    }
}
