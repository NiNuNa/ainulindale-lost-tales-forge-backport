package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatAccessPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import com.ninuna.losttales.party.server.PartyService;
import com.ninuna.losttales.world.map.waypoint.LostTalesWaypointFastTravelPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

/** Authoritative recipient resolution and presentation snapshot for player chat. */
public final class LostTalesChatService {

    private LostTalesChatService() {}

    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message) {
        send(sender, channel, message, null);
    }

    /**
     * Validates and distributes one message. {@code references} pair, in
     * order, with the share tokens found in the message; an item slot is
     * re-read from the sender's live inventory and a marker id from world
     * data under the sender's own visibility, and only a match whose real
     * name agrees with the token is attached. Tokens that cannot be
     * verified are delivered as the literal text the sender typed.
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references) {
        send(sender, channel, message, references, "");
    }

    /**
     * As above, with the account name a whisper is for. A whisper goes
     * to the sender and that one online player, each told who the other
     * party is; an unknown name is refused with a notice.
     */
    public static void send(EntityPlayerMP sender,
                            ChatChannel channel, String message,
                            List<ChatShareReference> references,
                            String target) {
        if (sender == null || sender.worldObj == null
                || sender.worldObj.isRemote || channel == null
                || !ChatMessageValidator.isValid(message)) {
            return;
        }
        EntityPlayerMP whisperTarget = null;
        if (channel.getRecipientRule() == ChatRecipientRule.WHISPER) {
            whisperTarget = findOnlinePlayer(target);
            if (whisperTarget == null || whisperTarget == sender) {
                sender.addChatMessage(new ChatComponentTranslation(
                        whisperTarget == sender
                                ? "chat.losttales.whisper.self"
                                : "chat.losttales.whisper.unavailable"));
                return;
            }
        }

        RoleplayCharacter character = CharacterActiveResolver.get(sender);
        if (channel.getIdentityType() == ChatIdentityType.CHARACTER
                && character == null) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.channel.character_required"));
            return;
        }
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
        } else if (channel.getRecipientRule()
                == ChatRecipientRule.OPERATORS
                && !LostTalesWaystonePermissionPolicy.isOperator(sender)) {
            // The client only offers the tab while it believes the player
            // is an operator; tell it again so a revoked op loses the tab.
            sendAccess(sender);
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.channel.admin_unavailable"));
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
        List<ChatShowcase> showcases =
                resolveShowcases(sender, message, references);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                channel, sender.getUniqueID(), identityName,
                accountName,
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        ? presentation.title : "",
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        ? presentation.titleColor : LostTalesColors.rgb(
                        LostTalesColors.HUD_LABEL),
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        ? presentation.nameColor : LostTalesColors.rgb(
                        LostTalesColors.HUD_LABEL),
                message, System.currentTimeMillis(),
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        && character != null ? character.getSkinId() : "",
                showcases,
                channel.getIdentityType() == ChatIdentityType.CHARACTER
                        ? presentation.factionName : "");

        FMLLog.info("[losttales/chat/%s] <%s (%s)> %s%s%s",
                channel.getId(), identityName, accountName, message,
                whisperTarget == null ? ""
                        : " -> " + whisperTarget.getCommandSenderName(),
                showcases.isEmpty() ? ""
                        : " [shared: " + showcases.size() + "]");

        if (whisperTarget != null) {
            // Each side is told who the other party is.
            LostTalesNetworkHandler.CHANNEL.sendTo(withPartner(packet,
                    whisperTarget.getCommandSenderName()), sender);
            LostTalesNetworkHandler.CHANNEL.sendTo(withPartner(packet,
                    accountName), whisperTarget);
            return;
        }
        for (EntityPlayerMP recipient : resolveRecipients(
                sender, channel, party, factionId)) {
            LostTalesNetworkHandler.CHANNEL.sendTo(packet, recipient);
        }
    }

    private static LostTalesChatMessagePacket withPartner(
            LostTalesChatMessagePacket packet, String partner) {
        return new LostTalesChatMessagePacket(packet.getChannel(),
                packet.getSenderId(), packet.getIdentityName(),
                packet.getAccountName(), packet.getTitle(),
                packet.getTitleColor(), packet.getNameColor(),
                packet.getMessage(), packet.getTimestampMillis(),
                packet.getSkinId(), packet.getShowcases(),
                packet.getFactionName(), partner);
    }

    /** The online player with that account name, case-insensitively. */
    private static EntityPlayerMP findOnlinePlayer(String name) {
        MinecraftServer server = MinecraftServer.getServer();
        String wanted = name == null ? "" : name.trim();
        if (wanted.length() == 0 || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate != null && wanted.equalsIgnoreCase(
                    candidate.getCommandSenderName())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Tells one client which channels its operator status unlocks. Sent
     * on login and whenever a staff-channel message is refused, so the
     * Admin tab follows the server's view of op status without the client
     * ever deciding it.
     */
    public static void sendAccess(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesChatAccessPacket(
                        LostTalesWaystonePermissionPolicy.isOperator(player)),
                player);
    }

    /**
     * Pairs tokens with references by position and keeps only the things
     * that exist, match the typed name, and fit the wire bound. The sender
     * is told once per message and kind when something was dropped; nothing
     * here trusts a reference beyond using it as a lookup key.
     */
    private static List<ChatShowcase> resolveShowcases(
            EntityPlayerMP sender, String message,
            List<ChatShareReference> references) {
        if (references == null || references.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(message);
        int count = Math.min(tokens.size(), Math.min(
                references.size(), ChatShareTokenParser.MAX_TOKENS));
        List<ChatShowcase> result = new ArrayList<ChatShowcase>(count);
        boolean itemUnavailable = false;
        boolean itemTooLarge = false;
        boolean markerUnavailable = false;
        for (int index = 0; index < count; index++) {
            ChatShareTokenParser.Token token = tokens.get(index);
            ChatShareReference reference = references.get(index);
            if (reference == null || reference.getKind() != token.kind) {
                if (token.kind == ChatShareKind.MARKER) {
                    markerUnavailable = true;
                } else {
                    itemUnavailable = true;
                }
                continue;
            }
            if (token.kind == ChatShareKind.ITEM) {
                ItemStack stack = resolveItem(sender, reference, token);
                if (stack == null) {
                    itemUnavailable = true;
                    continue;
                }
                byte[] encoded = ChatShowcase.encodeStack(stack.copy());
                if (encoded == null) {
                    itemTooLarge = true;
                    continue;
                }
                result.add(ChatShowcase.item(index, encoded));
            } else {
                ChatShowcase marker = resolveMarker(
                        sender, reference, token, index);
                if (marker == null) {
                    markerUnavailable = true;
                    continue;
                }
                result.add(marker);
            }
        }
        if (itemUnavailable) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.item.unavailable"));
        }
        if (itemTooLarge) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.item.too_large"));
        }
        if (markerUnavailable) {
            sender.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.marker.unavailable"));
        }
        return result;
    }

    private static ItemStack resolveItem(EntityPlayerMP sender,
                                         ChatShareReference reference,
                                         ChatShareTokenParser.Token token) {
        if (sender.inventory == null || !reference.isResolved()) {
            return null;
        }
        ItemStack stack = sender.inventory.getStackInSlot(reference.getSlot());
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0
                || !token.normalizedName().equals(
                        ChatShareTokenParser.normalizeName(
                                stack.getDisplayName()))) {
            return null;
        }
        return stack;
    }

    /**
     * A marker the sender may actually see, by the id the client supplied,
     * with the typed name checked against the record. The public fields go
     * out; ownership, sharing lists, and settings never do.
     */
    private static ChatShowcase resolveMarker(EntityPlayerMP sender,
                                              ChatShareReference reference,
                                              ChatShareTokenParser.Token token,
                                              int tokenIndex) {
        if (!reference.isResolved()) {
            return null;
        }
        try {
            LostTalesMapMarkerRecord record = LostTalesMapMarkerStorage
                    .get(sender.worldObj).getRecord(reference.getMarkerId());
            if (record == null
                    || !LostTalesMapMarkerVisibilityPolicy.canView(
                            record, sender)
                    || !token.normalizedName().equals(
                            ChatShareTokenParser.normalizeName(
                                    record.getName()))) {
                return null;
            }
            if (!LostTalesWaypointFastTravelPolicy.hasVisited(sender, record)) {
                // Only places the sender has actually reached are shared;
                // an undiscovered or region-locked marker stays plain text.
                return null;
            }
            return ChatShowcase.marker(tokenIndex, record.getId(),
                    record.getName(), record.getIconName(),
                    record.getColorName(), record.getDimensionId(),
                    record.getX(), record.getZ());
        } catch (RuntimeException exception) {
            FMLLog.warning("[losttales/chat] Could not resolve shared marker "
                    + "%s for %s: %s", reference.getMarkerId(),
                    sender.getUniqueID(), exception.toString());
            return null;
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
            } else if (channel.getRecipientRule() == ChatRecipientRule.SELF
                    || channel.getRecipientRule()
                    == ChatRecipientRule.WHISPER) {
                if (candidate == sender) {
                    result.add(candidate);
                }
            } else if (channel.getRecipientRule()
                    == ChatRecipientRule.OPERATORS) {
                if (LostTalesWaystonePermissionPolicy.isOperator(candidate)) {
                    result.add(candidate);
                }
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
