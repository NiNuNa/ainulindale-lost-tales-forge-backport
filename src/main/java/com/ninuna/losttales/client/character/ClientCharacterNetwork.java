package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterCapeUpdateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterDeleteRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterSelectRequestPacket;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Client-side request facade used by the later profile and management GUIs. */
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
