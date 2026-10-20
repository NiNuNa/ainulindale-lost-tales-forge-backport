package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.physics.CharacterEntitySizeHelper;
import com.ninuna.losttales.character.physics.CharacterRaceDimensions;
import com.ninuna.losttales.character.physics.CharacterRaceEntityData;
import com.ninuna.losttales.character.physics.CharacterPlayerEyeHeightHelper;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayRegistry;
import com.ninuna.losttales.compat.lotr.LotrRaceProfileAdapter;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.event.ServerChatEvent;

import java.util.List;
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

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP)event.player;
        PlayableIdentityResolver.Resolution identity =
                PlayableIdentityResolver.resolve(player);
        if (!identity.isAvailable()) {
            // The roster cannot be read: what was last applied stands,
            // since taking the race away would be a guess about a
            // player who may well still be playing one.
            return;
        }
        RoleplayCharacter character = identity.getCharacter();

        // Vanilla's EntityPlayer and mods that change a player's size can put
        // the vanilla dimensions back during ordinary ticks. apply() is
        // idempotent and so runs every END tick; stable modifier UUIDs keep
        // the attributes from stacking.
        apply(player, character);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerChat(ServerChatEvent event) {
        if (event == null || event.player == null || event.component == null) {
            return;
        }
        RoleplayCharacter character = CharacterActiveResolver.get(event.player);
        if (character == null || character.getName().length() == 0) {
            return;
        }

        // Run after LOTR's title handler and replace only the account-name
        // component. This preserves LOTR titles, chat translations, styles,
        // hover events, drunk-text processing, and any other compatible
        // decoration already attached to the message.
        ReplacementState replacementState = new ReplacementState();
        IChatComponent replaced = replaceFirstExactText(
                event.component,
                event.username,
                character.getName(),
                replacementState);
        if (replacementState.replaced
                && replaced instanceof ChatComponentTranslation) {
            event.component = (ChatComponentTranslation) replaced;
        }
    }

    private static IChatComponent replaceFirstExactText(
            IChatComponent component,
            String expected,
            String replacement,
            ReplacementState state) {
        if (component == null || expected == null || replacement == null
                || state == null || state.replaced) {
            return component;
        }
        if (component instanceof ChatComponentText
                && expected.equals(component.getUnformattedTextForChat())) {
            IChatComponent result = new ChatComponentText(replacement);
            result.setChatStyle(component.getChatStyle().createShallowCopy());
            // Minecraft 1.7.10 exposes getSiblings() as a raw List in the
            // mapped API, so iterating it as IChatComponent does not compile.
            for (Object sibling : component.getSiblings()) {
                if (sibling instanceof IChatComponent) {
                    result.appendSibling((IChatComponent) sibling);
                }
            }
            state.replaced = true;
            return result;
        }

        if (component instanceof ChatComponentTranslation) {
            Object[] arguments = ((ChatComponentTranslation) component)
                    .getFormatArgs();
            for (int index = 0;
                 index < arguments.length && !state.replaced;
                 index++) {
                Object argument = arguments[index];
                if (argument instanceof IChatComponent) {
                    arguments[index] = replaceFirstExactText(
                            (IChatComponent) argument,
                            expected, replacement, state);
                } else if (expected.equals(argument)) {
                    arguments[index] = replacement;
                    state.replaced = true;
                }
            }
        }

        List siblings = component.getSiblings();
        for (int index = 0;
             index < siblings.size() && !state.replaced;
             index++) {
            Object sibling = siblings.get(index);
            if (sibling instanceof IChatComponent) {
                siblings.set(index, replaceFirstExactText(
                        (IChatComponent) sibling,
                        expected, replacement, state));
            }
        }
        return component;
    }

    private static final class ReplacementState {
        private boolean replaced;
    }

    public static void apply(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        apply(player, CharacterActiveResolver.get(player));
    }

    public static void apply(
            EntityPlayerMP player, RoleplayCharacter character) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
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
