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
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
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
        applyInternal(player, character, true);
    }

    /**
     * Applies attributes and dimensions before transaction commit without moving
     * or dropping equipment. Equipment enforcement is allowed only post-commit.
     */
    public static void applyProvisional(
            EntityPlayerMP player, RoleplayCharacter character) {
        applyInternal(player, character, false);
    }

    private static void applyInternal(
            EntityPlayerMP player, RoleplayCharacter character,
            boolean enforceEquipment) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        if (enforceEquipment) {
            enforceRaceArmor(player, character);
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


    /**
     * Transaction-safe armor normalization. Unlike ordinary enforcement this
     * method never drops an item into the world: it either moves every rejected
     * armor stack into empty main-inventory slots or leaves the player unchanged.
     */
    public static boolean prepareEquipmentForCharacterSwitch(
            EntityPlayerMP player, RoleplayCharacter character) {
        if (player == null || character == null
                || !CharacterRaceRegistry.HALF_TROLL.equals(
                        CharacterRaceRegistry.canonicalizeIdentifier(
                                character.getRaceId()))) {
            return true;
        }
        int rejected = 0;
        for (int equipmentSlot = LotrHalfTrollArmorAdapter.SLOT_BOOTS;
             equipmentSlot <= LotrHalfTrollArmorAdapter.SLOT_HELMET;
             equipmentSlot++) {
            ItemStack equipped = player.getEquipmentInSlot(equipmentSlot);
            if (!LotrHalfTrollArmorAdapter.isAllowedInEquipmentSlot(
                    equipped, equipmentSlot)) {
                rejected++;
            }
        }
        if (rejected == 0) {
            return true;
        }
        int empty = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack == null) {
                empty++;
            }
        }
        if (empty < rejected) {
            return false;
        }
        for (int equipmentSlot = LotrHalfTrollArmorAdapter.SLOT_BOOTS;
             equipmentSlot <= LotrHalfTrollArmorAdapter.SLOT_HELMET;
             equipmentSlot++) {
            ItemStack equipped = player.getEquipmentInSlot(equipmentSlot);
            if (LotrHalfTrollArmorAdapter.isAllowedInEquipmentSlot(
                    equipped, equipmentSlot)) {
                continue;
            }
            int emptySlot = player.inventory.getFirstEmptyStack();
            if (emptySlot < 0) {
                return false;
            }
            player.setCurrentItemOrArmor(equipmentSlot, null);
            player.inventory.mainInventory[emptySlot] = equipped;
        }
        player.inventory.markDirty();
        return true;
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
            if (!LotrHalfTrollArmorAdapter.isAllowedInEquipmentSlot(
                    player.getEquipmentInSlot(equipmentSlot), equipmentSlot)) {
                rejectedAny = true;
                break;
            }
        }
        if (!rejectedAny) {
            return;
        }

        // Never drop equipment as a side effect of login, recovery, or a race
        // refresh. A world drop followed by a crash could be replayed from an
        // older snapshot. Move all rejected pieces atomically or leave them in
        // place until enough inventory space is available.
        if (prepareEquipmentForCharacterSwitch(player, character)) {
            player.inventoryContainer.detectAndSendChanges();
        }
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
