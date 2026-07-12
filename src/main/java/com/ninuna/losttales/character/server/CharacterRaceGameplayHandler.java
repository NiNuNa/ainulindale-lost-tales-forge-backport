package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.physics.CharacterEntitySizeHelper;
import com.ninuna.losttales.character.physics.CharacterRaceDimensions;
import com.ninuna.losttales.character.physics.CharacterRaceEntityData;
import com.ninuna.losttales.character.physics.CharacterPlayerEyeHeightHelper;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.compat.lotr.LotrHalfTrollArmorAdapter;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.ServerChatEvent;

import java.util.UUID;

/**
 * Applies race gameplay rules on the logical server. The active character is
 * always resolved from world storage; clients cannot submit attribute values.
 */
public final class CharacterRaceGameplayHandler {

    private static final UUID MAX_HEALTH_MODIFIER_ID =
            UUID.fromString("eb539d1d-6fe0-4fe2-8f44-c22962186c91");
    private static final UUID MOVEMENT_SPEED_MODIFIER_ID =
            UUID.fromString("219c68ec-f18f-482f-9424-62341d0d8605");
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID =
            UUID.fromString("c077b9d5-9073-4598-8b81-b2b955a2bf47");

    private static final String HALF_TROLL_ARMOR_NOTICE_TIME =
            "LostTalesHalfTrollArmorNoticeTime";
    private static final long ARMOR_NOTICE_COOLDOWN_TICKS = 40L;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP)event.player;
        RoleplayCharacter character = CharacterActiveResolver.get(player);

        // Equipment is checked every tick so inventory clicks, dispensers,
        // commands, and other mods cannot leave invalid armor equipped.
        enforceRaceArmor(player, character);

        // Legacy EntityPlayer code and other transformation mods can restore
        // vanilla dimensions during ordinary ticks. apply() is idempotent and
        // therefore runs every END tick; stable modifier UUIDs prevent stacking.
        apply(player, character);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (event == null || event.player == null) {
            return;
        }
        RoleplayCharacter character = CharacterActiveResolver.get(event.player);
        if (character == null || character.getName().length() == 0) {
            return;
        }

        // Keep the vanilla translation component shape so chat mods can still
        // inspect and style the two ordinary chat arguments.
        event.component = new ChatComponentTranslation(
                "chat.type.text",
                new ChatComponentText(character.getName()),
                new ChatComponentText(event.message)
        );
    }

    public static void apply(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        RoleplayCharacter character = CharacterActiveResolver.get(player);
        enforceRaceArmor(player, character);
        apply(player, character);
    }

    private static void apply(
            EntityPlayerMP player, RoleplayCharacter character) {
        if (character == null) {
            restoreVanillaPlayerState(player);
            return;
        }

        CharacterRaceGameplayProfile profile =
                LotrRaceProfileAdapter.getInstance().resolve(
                        player.worldObj, character.getRaceId());
        CharacterRaceDimensions dimensions =
                CharacterRaceDimensions.fromProfile(
                        character.getRaceId(), profile);

        applyTargetAttribute(
                player.getEntityAttribute(SharedMonsterAttributes.maxHealth),
                MAX_HEALTH_MODIFIER_ID,
                "Lost Tales LOTR race health",
                profile.getMaxHealth(),
                0);
        applyMultiplierAttribute(
                player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
                MOVEMENT_SPEED_MODIFIER_ID,
                "Lost Tales LOTR race speed",
                profile.getMovementSpeedMultiplier());
        applyTargetAttribute(
                player.getEntityAttribute(SharedMonsterAttributes.attackDamage),
                ATTACK_DAMAGE_MODIFIER_ID,
                "Lost Tales LOTR race damage",
                profile.getAttackDamage(),
                0);

        capHealthToMaximum(player);
        if (!player.isPlayerSleeping()) {
            CharacterEntitySizeHelper.apply(player, dimensions);
        }
        CharacterRaceEntityData.write(player, dimensions);
        CharacterPlayerEyeHeightHelper.apply(player, dimensions, true);
    }

    private static void enforceRaceArmor(
            EntityPlayerMP player, RoleplayCharacter character) {
        if (player == null || character == null
                || !CharacterRaceRegistry.HALF_TROLL.equals(
                        CharacterRaceRegistry.canonicalizeIdentifier(
                                character.getRaceId()))) {
            return;
        }

        boolean rejectedAny = false;
        for (int equipmentSlot = LotrHalfTrollArmorAdapter.SLOT_BOOTS;
             equipmentSlot <= LotrHalfTrollArmorAdapter.SLOT_HELMET;
             equipmentSlot++) {
            ItemStack equipped = player.getEquipmentInSlot(equipmentSlot);
            if (LotrHalfTrollArmorAdapter.isAllowedInEquipmentSlot(
                    equipped, equipmentSlot)) {
                continue;
            }

            // Clear the armor slot before returning the same stack. This is
            // authoritative on the server and the normal container sync then
            // corrects the client's inventory view.
            player.setCurrentItemOrArmor(equipmentSlot, null);
            if (!player.inventory.addItemStackToInventory(equipped)
                    && equipped.stackSize > 0) {
                player.dropPlayerItemWithRandomChoice(equipped, false);
            }
            rejectedAny = true;
        }

        if (!rejectedAny) {
            return;
        }

        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        sendHalfTrollArmorNotice(player);
    }

    private static void sendHalfTrollArmorNotice(EntityPlayerMP player) {
        long now = player.worldObj.getTotalWorldTime();
        long last = player.getEntityData().getLong(HALF_TROLL_ARMOR_NOTICE_TIME);
        if (last > 0L && now - last < ARMOR_NOTICE_COOLDOWN_TICKS) {
            return;
        }
        player.getEntityData().setLong(HALF_TROLL_ARMOR_NOTICE_TIME, now);
        player.addChatMessage(new ChatComponentTranslation(
                "losttales.character.halfTrollArmorOnly"));
    }

    private static void restoreVanillaPlayerState(EntityPlayerMP player) {
        removeModifier(player.getEntityAttribute(SharedMonsterAttributes.maxHealth),
                MAX_HEALTH_MODIFIER_ID);
        removeModifier(player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
                MOVEMENT_SPEED_MODIFIER_ID);
        removeModifier(player.getEntityAttribute(SharedMonsterAttributes.attackDamage),
                ATTACK_DAMAGE_MODIFIER_ID);

        capHealthToMaximum(player);
        CharacterRaceGameplayProfile fallback = CharacterRaceGameplayRegistry.DEFAULT;
        CharacterRaceDimensions dimensions =
                CharacterRaceDimensions.fromProfile("", fallback);
        if (!player.isPlayerSleeping()) {
            CharacterEntitySizeHelper.apply(player, dimensions);
        }
        CharacterRaceEntityData.write(player, dimensions);
        CharacterPlayerEyeHeightHelper.restoreVanilla(player);
    }

    /**
     * Produces the requested final base value while preserving modifiers added
     * by other mods. The Lost Tales modifier is recalculated against the
     * attribute's current base value.
     */
    private static void applyTargetAttribute(
            IAttributeInstance attribute,
            UUID id,
            String name,
            double targetValue,
            int operation) {
        if (attribute == null) {
            return;
        }
        double amount = targetValue - attribute.getBaseValue();
        applyAttribute(attribute, id, name, amount, operation);
    }

    private static void applyMultiplierAttribute(
            IAttributeInstance attribute,
            UUID id,
            String name,
            double multiplier) {
        applyAttribute(attribute, id, name, multiplier - 1.0D, 1);
    }

    private static void applyAttribute(
            IAttributeInstance attribute,
            UUID id,
            String name,
            double amount,
            int operation) {
        if (attribute == null) {
            return;
        }
        AttributeModifier old = attribute.getModifier(id);
        if (old != null) {
            if (old.getOperation() == operation
                    && Math.abs(old.getAmount() - amount) < 0.000001D) {
                return;
            }
            attribute.removeModifier(old);
        }
        if (Math.abs(amount) > 0.000001D) {
            attribute.applyModifier(
                    new AttributeModifier(id, name, amount, operation));
        }
    }

    private static void removeModifier(IAttributeInstance attribute, UUID id) {
        if (attribute == null) {
            return;
        }
        AttributeModifier modifier = attribute.getModifier(id);
        if (modifier != null) {
            attribute.removeModifier(modifier);
        }
    }

    private static void capHealthToMaximum(EntityPlayerMP player) {
        float maximumHealth = player.getMaxHealth();
        if (player.getHealth() > maximumHealth) {
            player.setHealth(maximumHealth);
        }
    }
}
