package com.ninuna.losttales.character.cape;

/** Stable server-owned definition for one selectable LOTR cosmetic cape. */
public final class CharacterCapeDefinition {

    private final int networkId;
    private final String id;
    private final String translationKey;

    CharacterCapeDefinition(int networkId, String id, String translationKey) {
        if (networkId <= CharacterCapeCatalog.NONE_ID
                || networkId > CharacterCapeCatalog.MAX_NETWORK_ID) {
            throw new IllegalArgumentException("networkId must fit the unsigned-short network range");
        }
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.networkId = networkId;
        this.id = id;
        this.translationKey = translationKey == null ? "" : translationKey;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public String getId() {
        return this.id;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }
}
