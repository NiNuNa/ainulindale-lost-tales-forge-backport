package com.ninuna.losttales.quest.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who a won world quest pays: each account once. A part is counted per
 * identity, the character played or the account's own; among one
 * account's identities that added at least the quest's {@code least}, the
 * one that added most is paid, and on a tie the one that joined the run
 * first. Pure logic; the server says which account an identity is.
 */
public final class WorldQuestPayees {

    private WorldQuestPayees() {}

    /** Which account an identity belongs to. */
    public interface Accounts {
        /** The identity's account; the identity itself for an account's own identity or one nobody owns. */
        UUID accountOf(UUID identity);
    }

    /** Every identity its own account: what a caller with no characters to ask uses. */
    public static final Accounts EACH_ITS_OWN = new Accounts() {
        @Override
        public UUID accountOf(UUID identity) {
            return identity;
        }
    };

    /**
     * The identities to pay, in the order they joined the run.
     * {@code helpers} holds each identity's part in that order.
     */
    public static List<UUID> of(Map<UUID, Integer> helpers, int least,
                                Accounts accounts) {
        Map<UUID, UUID> bestOfAccount = new LinkedHashMap<UUID, UUID>();
        Map<UUID, Integer> bestPart = new LinkedHashMap<UUID, Integer>();
        for (Map.Entry<UUID, Integer> helper : helpers.entrySet()) {
            int part = helper.getValue() == null ? 0 : helper.getValue().intValue();
            if (helper.getKey() == null || part < least) {
                continue;
            }
            UUID account = accounts == null ? null
                    : accounts.accountOf(helper.getKey());
            if (account == null) {
                account = helper.getKey();
            }
            Integer best = bestPart.get(account);
            if (best == null || part > best.intValue()) {
                bestOfAccount.put(account, helper.getKey());
                bestPart.put(account, Integer.valueOf(part));
            }
        }
        Set<UUID> chosen = new HashSet<UUID>(bestOfAccount.values());
        List<UUID> paid = new ArrayList<UUID>();
        for (UUID identity : helpers.keySet()) {
            if (chosen.contains(identity)) {
                paid.add(identity);
            }
        }
        return paid;
    }
}
