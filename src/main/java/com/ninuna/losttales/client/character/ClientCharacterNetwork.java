package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterCapeUpdateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterDeleteRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterLookUpdateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterProfileUpdateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterRestoreRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterSelectRequestPacket;
import com.ninuna.losttales.network.packet.character.LoreCharacterClaimRequestPacket;
import com.ninuna.losttales.network.packet.character.LoreCharacterReleaseRequestPacket;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every character request the client sends, each under a request id of its
 * own that the roster cache follows until the server answers it.
 */
public final class ClientCharacterNetwork {

    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);

    private ClientCharacterNetwork() {}

    public static int requestRoster() {
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.REQUEST_ROSTER, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterRosterRequestPacket(requestId));
            }
        });
    }

    public static int createCharacter(final CharacterCreationRequest request) {
        if (request == null || request.getExpectedRosterRevision() < 0L) {
            throw new IllegalArgumentException("request and roster revision must be valid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.CREATE, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterCreateRequestPacket(requestId, request));
            }
        });
    }

    public static int selectCharacter(final long expectedRosterRevision,
                                      final UUID characterId) {
        if (expectedRosterRevision < 0L || characterId == null) {
            throw new IllegalArgumentException("revision and characterId must be valid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.SELECT, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterSelectRequestPacket(
                                requestId, expectedRosterRevision, characterId));
            }
        });
    }


    public static int deleteCharacter(final long expectedRosterRevision,
                                      final UUID characterId) {
        if (expectedRosterRevision < 0L || characterId == null) {
            throw new IllegalArgumentException("revision and characterId must be valid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.DELETE, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterDeleteRequestPacket(
                                requestId, expectedRosterRevision, characterId));
            }
        });
    }

    public static int updateCapeSettings(final long expectedRosterRevision,
                                         final UUID characterId,
                                         final boolean showMinecraftCape,
                                         final int cosmeticCapeId) {
        if (expectedRosterRevision < 0L || characterId == null) {
            throw new IllegalArgumentException("revision and characterId must be valid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.CAPE_UPDATE, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterCapeUpdateRequestPacket(
                                requestId,
                                expectedRosterRevision,
                                characterId,
                                showMinecraftCape,
                                cosmeticCapeId));
            }
        });
    }

    public static int updateProfile(final long expectedRosterRevision,
                                    final UUID characterId,
                                    final CharacterProfile profile,
                                    final int age) {
        if (expectedRosterRevision < 0L || characterId == null) {
            throw new IllegalArgumentException("revision and character must be valid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.PROFILE_UPDATE, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterProfileUpdateRequestPacket(
                                requestId,
                                expectedRosterRevision,
                                characterId,
                                profile,
                                age));
            }
        });
    }

    /** A new look for one character: its skin, arm width and chest. */
    public static int updateLook(final long expectedRosterRevision,
                                 final UUID characterId,
                                 final String skinId,
                                 final String bodyTypeId,
                                 final String chestTypeId) {
        if (expectedRosterRevision < 0L || characterId == null) {
            throw new IllegalArgumentException("revision and character must be valid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.LOOK_UPDATE, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterLookUpdateRequestPacket(
                                requestId,
                                expectedRosterRevision,
                                characterId,
                                skinId,
                                bodyTypeId,
                                chestTypeId));
            }
        });
    }

    /** Brings back one of the player's own deleted characters. */
    public static int restoreCharacter(final long expectedRosterRevision,
                                       final UUID characterId) {
        if (expectedRosterRevision < 0L || characterId == null) {
            throw new IllegalArgumentException("revision and character must be valid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.RESTORE, new Runnable() {
            @Override
            public void run() {
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new CharacterRestoreRequestPacket(
                                requestId, expectedRosterRevision, characterId));
            }
        });
    }

    public static int claimLoreCharacter(
            final long expectedRosterRevision,
            final long expectedOwnershipRevision,
            final int slotIndex,
            final String loreCharacterId) {
        if (expectedRosterRevision < 0L || expectedOwnershipRevision < 0L
                || loreCharacterId == null || loreCharacterId.length() == 0) {
            throw new IllegalArgumentException("Lore claim fields are invalid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.LORE_CLAIM,
                new Runnable() {
                    @Override public void run() {
                        LostTalesNetworkHandler.CHANNEL.sendToServer(
                                new LoreCharacterClaimRequestPacket(
                                        requestId, expectedRosterRevision,
                                        expectedOwnershipRevision, slotIndex,
                                        loreCharacterId));
                    }
                });
    }

    public static int releaseLoreCharacter(
            final long expectedRosterRevision,
            final long expectedOwnershipRevision,
            final String loreCharacterId) {
        if (expectedRosterRevision < 0L || expectedOwnershipRevision < 0L
                || loreCharacterId == null || loreCharacterId.length() == 0) {
            throw new IllegalArgumentException("Lore release fields are invalid");
        }
        final int requestId = nextRequestId();
        return send(requestId, CharacterOperationType.LORE_RELEASE,
                new Runnable() {
                    @Override public void run() {
                        LostTalesNetworkHandler.CHANNEL.sendToServer(
                                new LoreCharacterReleaseRequestPacket(
                                        requestId, expectedRosterRevision,
                                        expectedOwnershipRevision,
                                        loreCharacterId));
                    }
                });
    }

    private static int send(int requestId,
                            CharacterOperationType operationType,
                            Runnable sendAction) {
        ClientCharacterRosterCache.beginRequest(requestId, operationType);
        try {
            sendAction.run();
        } catch (RuntimeException exception) {
            ClientCharacterRosterCache.failLocalRequest(requestId, operationType);
        }
        return requestId;
    }

    private static int nextRequestId() {
        while (true) {
            int current = NEXT_REQUEST_ID.getAndIncrement();
            if (current > 0) {
                return current;
            }
            NEXT_REQUEST_ID.compareAndSet(current + 1, 1);
        }
    }
}
