package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.server.PartyService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

/** Authoritative recipient resolution and presentation snapshot for player chat. */
public final class LostTalesChatService {

    private LostTalesChatService() {}

    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message) {
        if (sender == null || sender.worldObj == null
                || sender.worldObj.isRemote || channel == null
                || !ChatMessageValidator.isValid(message)) {
            return;
        }

        RoleplayCharacter character = CharacterActiveResolver.get(sender);
        Party party = null;
        String factionId = character == null ? ""
                : LotrCharacterAdapter.normalizeFactionId(
                        character.getStartingFactionId());
        if (channel.getRecipientRule() == ChatRecipientRule.PARTY) {
            party = PartyService.getInstance()
                    .getPartyForActiveCharacter(sender);
            if (party == null || character == null
                    || !party.containsMember(character.getCharacterId())) {
                sender.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.channel.party_unavailable"));
                return;
            }
        } else if (channel.getRecipientRule()
                == ChatRecipientRule.FACTION && factionId.length() == 0) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.channel.faction_unavailable"));
            return;
        }

        String accountName = sender.getGameProfile() == null
                ? sender.getCommandSenderName()
                : sender.getGameProfile().getName();
        String identityName = channel.getIdentityType()
                == ChatIdentityType.ACCOUNT
                ? accountName
                : characterNameOrFallback(character, accountName);
        LostTalesChatPresentationResolver.Presentation presentation =
                LostTalesChatPresentationResolver.resolve(sender, character);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                channel, sender.getUniqueID(), identityName,
                accountName,
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        ? presentation.title : "",
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        ? presentation.titleColor : 0xFFFFFF,
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        ? presentation.nameColor : 0xFFFFFF,
                message, System.currentTimeMillis(),
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        && character != null ? character.getSkinId() : "");

        FMLLog.info("[losttales/chat/%s] <%s (%s)> %s",
                channel.getId(), identityName, accountName, message);

        for (EntityPlayerMP recipient : resolveRecipients(
                sender, channel, party, factionId)) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, recipient);
        }
    }

    private static List<EntityPlayerMP> resolveRecipients(
            EntityPlayerMP sender, ChatChannel channel, Party party,
            String factionId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return Collections.emptyList();
        }
        List<EntityPlayerMP> result = new ArrayList<EntityPlayerMP>();
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate == null || candidate.getUniqueID() == null) {
                continue;
            }
            if (channel.getRecipientRule() == ChatRecipientRule.GLOBAL) {
                result.add(candidate);
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.PROXIMITY) {
                if (candidate.dimension == sender.dimension
                        && candidate.getDistanceSqToEntity(sender)
                        <= proximityDistanceSquared()) {
                    result.add(candidate);
                }
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.PARTY
                    && isCurrentOnlinePartyMember(candidate, party)) {
                result.add(candidate);
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.FACTION
                    && isCurrentFactionMember(candidate, factionId)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static boolean isCurrentOnlinePartyMember(
            EntityPlayerMP player, Party party) {
        if (party == null || player == null) {
            return false;
        }
        RoleplayCharacter active = CharacterActiveResolver.get(player);
        if (active == null) {
            return false;
        }
        PartyMember member = party.getMember(active.getCharacterId());
        return member != null && player.getUniqueID().equals(member.getOwnerId());
    }

    private static boolean isCurrentFactionMember(
            EntityPlayerMP player, String factionId) {
        if (player == null || factionId == null || factionId.length() == 0) {
            return false;
        }
        RoleplayCharacter active = CharacterActiveResolver.get(player);
        return active != null && factionId.equals(
                LotrCharacterAdapter.normalizeFactionId(
                        active.getStartingFactionId()));
    }

    private static double proximityDistanceSquared() {
        double radius = Math.max(1.0D, LostTalesConfig.chatProximityRadius);
        return radius * radius;
    }

    private static String characterNameOrFallback(
            RoleplayCharacter character, String accountName) {
        if (character != null && character.getName() != null
                && character.getName().trim().length() > 0) {
            return character.getName().trim();
        }
        return accountName == null || accountName.length() == 0
                ? "Unknown" : accountName;
    }
}
