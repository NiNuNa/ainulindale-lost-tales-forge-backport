package com.ninuna.losttales.network;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerDiscoveryPacket;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import com.ninuna.losttales.network.packet.LostTalesChargeTierSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesMissiveAcceptPacket;
import com.ninuna.losttales.network.packet.LostTalesMobAggroSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootContainerSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootDropItemPacket;
import com.ninuna.losttales.network.packet.LostTalesQuickLootRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonEntityActionPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonBlockActionPacket;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonAimPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneSettingsRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneTravelRequestPacket;
import com.ninuna.losttales.network.packet.AccessoryInventorySyncPacket;
import com.ninuna.losttales.network.packet.AccessoryEffectSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterAppearanceSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterCapeUpdateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreateRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterCreationCatalogSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterDeleteRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterOperationResultPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterRequestPacket;
import com.ninuna.losttales.network.packet.character.CharacterRosterSyncPacket;
import com.ninuna.losttales.network.packet.character.CharacterSelectRequestPacket;
import com.ninuna.losttales.network.packet.character.LoreCharacterClaimRequestPacket;
import com.ninuna.losttales.network.packet.character.LoreCharacterReleaseRequestPacket;
import com.ninuna.losttales.network.packet.character.LoreCharacterSyncPacket;
import com.ninuna.losttales.network.packet.party.PartyActionRequestPacket;
import com.ninuna.losttales.network.packet.party.PartyMemberStatusSyncPacket;
import com.ninuna.losttales.network.packet.party.PartyOperationResultPacket;
import com.ninuna.losttales.network.packet.party.PartyStateSyncPacket;
import com.ninuna.losttales.network.packet.party.PartyTrackingSyncPacket;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class LostTalesNetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(LostTalesMetaData.MOD_ID);

    private LostTalesNetworkHandler() {}

    public static void registerCommonPackets() {
        // Client -> server requests. These handlers are common/server-safe and validate
        // every request on the logical server before touching world state.
        CHANNEL.registerMessage(LostTalesQuickLootRequestPacket.Handler.class, LostTalesQuickLootRequestPacket.class, 0, Side.SERVER);
        CHANNEL.registerMessage(LostTalesQuickLootDropItemPacket.Handler.class, LostTalesQuickLootDropItemPacket.class, 1, Side.SERVER);
        CHANNEL.registerMessage(LostTalesQuestActionPacket.Handler.class, LostTalesQuestActionPacket.class, 4, Side.SERVER);
        CHANNEL.registerMessage(LostTalesMissiveAcceptPacket.Handler.class, LostTalesMissiveAcceptPacket.class, 7, Side.SERVER);
        CHANNEL.registerMessage(CharacterRosterRequestPacket.Handler.class, CharacterRosterRequestPacket.class, 8, Side.SERVER);
        CHANNEL.registerMessage(CharacterCreateRequestPacket.Handler.class, CharacterCreateRequestPacket.class, 9, Side.SERVER);
        CHANNEL.registerMessage(CharacterSelectRequestPacket.Handler.class, CharacterSelectRequestPacket.class, 10, Side.SERVER);
        CHANNEL.registerMessage(CharacterDeleteRequestPacket.Handler.class, CharacterDeleteRequestPacket.class, 11, Side.SERVER);
        CHANNEL.registerMessage(CharacterCapeUpdateRequestPacket.Handler.class, CharacterCapeUpdateRequestPacket.class, 16, Side.SERVER);
        CHANNEL.registerMessage(PartyActionRequestPacket.Handler.class, PartyActionRequestPacket.class, 17, Side.SERVER);
        CHANNEL.registerMessage(LoreCharacterClaimRequestPacket.Handler.class, LoreCharacterClaimRequestPacket.class, 22, Side.SERVER);
        CHANNEL.registerMessage(LoreCharacterReleaseRequestPacket.Handler.class, LoreCharacterReleaseRequestPacket.class, 23, Side.SERVER);
        CHANNEL.registerMessage(LostTalesThirdPersonEntityActionPacket.Handler.class, LostTalesThirdPersonEntityActionPacket.class, 25, Side.SERVER);
        CHANNEL.registerMessage(LostTalesThirdPersonBlockActionPacket.Handler.class, LostTalesThirdPersonBlockActionPacket.class, 26, Side.SERVER);
        CHANNEL.registerMessage(LostTalesThirdPersonAimPacket.Handler.class, LostTalesThirdPersonAimPacket.class, 27, Side.SERVER);
        CHANNEL.registerMessage(LostTalesWaystoneSettingsRequestPacket.Handler.class, LostTalesWaystoneSettingsRequestPacket.class, 32, Side.SERVER);
        CHANNEL.registerMessage(LostTalesWaystoneTravelRequestPacket.Handler.class, LostTalesWaystoneTravelRequestPacket.class, 34, Side.SERVER);

        // Server -> client snapshots. These are registered from the common proxy so a
        // dedicated server also knows the packet discriminators when it sends them.
        // The handlers route through the sided proxy instead of importing client-only
        // cache/renderer classes here, keeping dedicated-server class loading safe.
        CHANNEL.registerMessage(LostTalesQuickLootContainerSyncPacket.Handler.class, LostTalesQuickLootContainerSyncPacket.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesQuestSyncPacket.Handler.class, LostTalesQuestSyncPacket.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesMobAggroSyncPacket.Handler.class, LostTalesMobAggroSyncPacket.class, 5, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesMapMarkerDiscoveryPacket.Handler.class, LostTalesMapMarkerDiscoveryPacket.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(CharacterRosterSyncPacket.Handler.class, CharacterRosterSyncPacket.class, 12, Side.CLIENT);
        CHANNEL.registerMessage(CharacterOperationResultPacket.Handler.class, CharacterOperationResultPacket.class, 13, Side.CLIENT);
        CHANNEL.registerMessage(CharacterAppearanceSyncPacket.Handler.class, CharacterAppearanceSyncPacket.class, 14, Side.CLIENT);
        CHANNEL.registerMessage(CharacterCreationCatalogSyncPacket.Handler.class, CharacterCreationCatalogSyncPacket.class, 15, Side.CLIENT);
        CHANNEL.registerMessage(PartyStateSyncPacket.Handler.class, PartyStateSyncPacket.class, 18, Side.CLIENT);
        CHANNEL.registerMessage(PartyOperationResultPacket.Handler.class, PartyOperationResultPacket.class, 19, Side.CLIENT);
        CHANNEL.registerMessage(PartyMemberStatusSyncPacket.Handler.class, PartyMemberStatusSyncPacket.class, 20, Side.CLIENT);
        CHANNEL.registerMessage(PartyTrackingSyncPacket.Handler.class, PartyTrackingSyncPacket.class, 21, Side.CLIENT);
        CHANNEL.registerMessage(LoreCharacterSyncPacket.Handler.class, LoreCharacterSyncPacket.class, 24, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesChargeTierSyncPacket.Handler.class, LostTalesChargeTierSyncPacket.class, 28, Side.CLIENT);
        CHANNEL.registerMessage(AccessoryInventorySyncPacket.Handler.class, AccessoryInventorySyncPacket.class, 29, Side.CLIENT);
        CHANNEL.registerMessage(AccessoryEffectSyncPacket.Handler.class, AccessoryEffectSyncPacket.class, 30, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesMapMarkerSnapshotPacket.Handler.class, LostTalesMapMarkerSnapshotPacket.class, 31, Side.CLIENT);
        CHANNEL.registerMessage(LostTalesWaystoneStatePacket.Handler.class, LostTalesWaystoneStatePacket.class, 33, Side.CLIENT);
    }
}
