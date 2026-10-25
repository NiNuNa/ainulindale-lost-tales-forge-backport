package com.ninuna.losttales.util;

import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.armour.ArmourCatalogue;
import com.ninuna.losttales.armour.ArmourSettings;
import com.ninuna.losttales.armour.ArmourSlot;
import com.ninuna.losttales.armour.ArmourTooltip;
import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.armour.ArmourWords;
import com.ninuna.losttales.armour.NamedSet;
import com.ninuna.losttales.armour.Relics;
import com.ninuna.losttales.armour.SetBonus;
import com.ninuna.losttales.armour.WornArmour;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.custom.LostTalesBlockPlushie;
import com.ninuna.losttales.block.vessel.VesselTooltip;
import com.ninuna.losttales.client.armour.SetBonusWords;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipIcons;
import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSlots;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.user.ELostTalesUser;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.progression.PerkWords;
import com.ninuna.losttales.progression.skill.PerkEffect;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.LOTRTextures;
import lotr.client.gui.LOTRMapLabels;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.EnumHelper;
import org.lwjgl.input.Keyboard;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Physical-client-only helpers: item tooltips and the map image. They stand
 * apart from LostTalesUtil so common LOTR setup never loads Minecraft or LOTR
 * client classes on a dedicated server.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesClientUtil {
    /** What the lang keys of the item tooltips' words begin with. */
    private static final String TOOLTIP = "item.losttales.tooltip.";
    /** The keyboard as it is now. */
    private static final LostTalesTooltipKeys.Held KEYBOARD = new LostTalesTooltipKeys.Held() {
        @Override
        public boolean isDown(int keyCode) {
            return Keyboard.isCreated() && Keyboard.isKeyDown(keyCode);
        }
    };

    private LostTalesClientUtil() {}

    public static void addItemInformation(List list, ItemStack itemStack,
                                          ELostTalesItemMaterial material, String credits,
                                          EntityPlayer player, ELostTalesItem.Type itemType) {
        addItemLore(list, getItemLore(itemStack));
        addItemDetails(list, itemStack, credits, material, itemType);
    }

    public static void addItemBlockInformation(List list, ItemStack itemStack,
                                               Block block, ELostTalesUser credits) {
        addItemLore(list, getItemLore(itemStack));
        addItemBlockDetails(list, itemStack, block, credits);
    }

    /**
     * What a vessel item holds (CONTENT-09), from its stacks slot by slot
     * (null for an empty slot): its slots with the items drawn in them and
     * their counts, as a shulker box's tooltip shows them, and a line
     * counting any items past the slots shown; where tooltips cannot draw
     * them, each item's name and count instead.
     */
    public static void addVesselContents(List list, ItemStack[] slots) {
        List<VesselTooltip.Entry> entries = new ArrayList<VesselTooltip.Entry>();
        int[] counts = new int[slots.length];
        for (int slot = 0; slot < slots.length; slot++) {
            ItemStack stack = slots[slot];
            if (stack != null) {
                entries.add(new VesselTooltip.Entry(stack.getDisplayName(), stack.stackSize));
                counts[slot] = stack.stackSize;
            }
        }
        List<String> words = VesselTooltip.lines(entries, new VesselTooltip.Words() {
            @Override
            public String item(String name, int count) {
                return "§7" + word("vessel.item", name, Integer.valueOf(count));
            }

            @Override
            public String more(int count) {
                return vesselMore(count);
            }
        });
        int shown = VesselTooltip.shownSlots(slots.length);
        int hidden = VesselTooltip.hiddenItems(counts, shown);
        ItemStack[] shownSlots = new ItemStack[shown];
        System.arraycopy(slots, 0, shownSlots, 0, shown);
        list.addAll(LostTalesTooltipSlots.mark(shownSlots,
                hidden > 0 ? vesselMore(hidden) : null, words));
    }

    /** The line saying how many more items a vessel holds than it shows. */
    private static String vesselMore(int count) {
        return "§7§o" + word("vessel.more", Integer.valueOf(count));
    }

    /**
     * The item's lore lines the lang file holds, line 1 then line 2; none
     * for an item without lore.
     */
    public static String[] getItemLore(ItemStack itemStack) {
        List<String> lore = new ArrayList<String>(2);
        for (String line : new String[] {".lore.line1", ".lore.line2"}) {
            String key = itemStack.getUnlocalizedName() + line;
            if (StatCollector.canTranslate(key)) {
                lore.add(I18n.format(key));
            }
        }
        return lore.toArray(new String[lore.size()]);
    }

    /**
     * The lore with either Shift held, else the line naming Shift; nothing
     * for an item without lore.
     */
    public static void addItemLore(List list, String[] itemLore) {
        if (itemLore.length == 0) {
            return;
        }
        if (LostTalesTooltipKeys.showsLore(KEYBOARD)) {
            for (String lore : itemLore) {
                list.add("§7§o" + lore);
            }
        } else {
            list.add(hold("lore", LostTalesTooltipIcons.key(
                    Keyboard.KEY_LSHIFT, "§f[" + word("key.shift") + "]")));
        }
    }

    /**
     * A tooltip's word under {@code key}: the line under
     * {@code item.losttales.tooltip.<key>} in the game's language.
     */
    private static String word(String key, Object... arguments) {
        return I18n.format(TOOLTIP + key, arguments);
    }

    /**
     * The line that names a key to hold for more ({@code Hold [SHIFT] to
     * view item lore.}), grey again after the key.
     */
    private static String hold(String what, String key) {
        return word("hold." + what, key + "§r§7");
    }

    /** A label and its value, the value white and in italics. */
    private static String labelled(String key, String value) {
        return word(key, "§f§o" + value);
    }

    /**
     * A rarity's name in the game's language; one the lang file does not
     * name, as vanilla or the mod that added it calls it.
     */
    static String rarityName(EnumRarity rarity) {
        String key = TOOLTIP + "rarity." + rarity.name().toLowerCase(Locale.ROOT);
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : rarity.rarityName;
    }

    public static void addItemDetails(List list, ItemStack itemStack, String credits,
                                      ELostTalesItemMaterial material,
                                      ELostTalesItem.Type itemType) {
        if (LostTalesTooltipKeys.showsDetails(KEYBOARD)) {
            list.add("");
            list.add(labelled("faction", I18n.format("lotr.faction."
                    + material.getFaction().codeName() + ".name")));
            if (material.getMaterial() != null) {
                list.add(labelled("repair", material.getMaterial()
                        .getRepairItem().getDisplayName()));
            }
            list.add(labelled("type", itemType.getName()));
            if (credits != null) {
                list.add("§8" + word("created_by", "§o" + credits));
            }
        } else {
            list.add("");
            list.add(hold("details", LostTalesTooltipIcons.key(
                    Keyboard.KEY_LCONTROL, "§f[" + word("key.ctrl") + "]")));
        }
    }

    public static void addItemBlockDetails(List list, ItemStack itemStack,
                                           Block block, ELostTalesUser credits) {
        if (LostTalesTooltipKeys.showsDetails(KEYBOARD)) {
            list.add("");
            if (block instanceof LostTalesBlockPlushie) {
                LostTalesBlockPlushie plushie = (LostTalesBlockPlushie)block;
                list.add(word("rarity", plushie.getRarity().rarityColor
                        + "§o" + rarityName(plushie.getRarity())));
            }
            ELostTalesItem.Type type = ELostTalesBlock.typeOf(block);
            if (type != null) {
                list.add(labelled("type", type.getName()));
            }
            if (credits != ELostTalesUser.NULL) {
                list.add("§8" + word("created_by", "§o" + credits.getName()));
            }
        } else {
            list.add("");
            list.add(hold("details", LostTalesTooltipIcons.key(
                    Keyboard.KEY_LCONTROL, "§f[" + word("key.ctrl") + "]")));
        }
    }

    /**
     * For any armour piece, the mod's, LOTR's, vanilla's or another mod's
     * (CONTENT-06, CONTENT-07, CONTENT-11): a relic piece's own name and
     * lore line, then, with the Modifier held, its weight set and named
     * sets as {@link ArmourTooltip} gives them; else the line naming the
     * Modifier. Nothing for a piece of neither weight in no named set.
     */
    public static void addArmourInformation(List<String> list, ItemStack itemStack,
                                            final EntityPlayer player) {
        if (list == null || itemStack == null || !(itemStack.getItem() instanceof ItemArmor)) {
            return;
        }
        ItemArmor item = (ItemArmor) itemStack.getItem();
        ArmourCatalogue catalogue = Armour.clientCatalogue();
        ArmourSlot slot = ArmourSlot.ofArmourType(item.armorType);
        String itemId = Armour.itemId(item);
        String relicId = Relics.relicOf(itemStack);
        NamedSet relic = relicId.length() == 0 ? null : catalogue.set(relicId);
        if (relic != null && relic.isRelic() && slot != null
                && relic.items(slot).contains(itemId)) {
            String name = ArmourWords.pieceName(relic, slot);
            if (name.length() > 0 && !list.isEmpty() && !itemStack.hasDisplayName()) {
                list.set(0, "§6" + name);
            }
            String lore = ArmourWords.pieceLore(relic, slot);
            if (lore.length() > 0) {
                list.add(Math.min(1, list.size()), "§5§o" + lore);
            }
        }
        ArmourWeight weight = catalogue.weightOf(itemId, Armour.material(item));
        if (!ArmourTooltip.tells(catalogue, weight, slot, itemId, relicId)) {
            return;
        }
        list.add("");
        if (!LostTalesKeyBindings.isModifierKeyDown()) {
            list.add(hold("set", LostTalesTooltipIcons.key(
                    LostTalesKeyBindings.getModifierKeyBinding(),
                    "§e[" + LostTalesKeyBindings.getModifierKeyDisplayName() + "]")));
            return;
        }
        WornArmour worn = player == null ? WornArmour.NONE : Armour.worn(player);
        ArmourSettings settings = Armour.settings(player);
        list.addAll(ArmourTooltip.lines(new ArmourTooltip.Words() {
            @Override
            public String weightSet(ArmourWeight setWeight) {
                return SetBonusWords.weightSetName(setWeight);
            }

            @Override
            public String setName(NamedSet set) {
                return ArmourWords.setName(set);
            }

            @Override
            public String bonus(SetBonus bonus) {
                return SetBonusWords.effect(bonus);
            }

            @Override
            public String weightBonus(ArmourWeight setWeight, double amount) {
                return SetBonusWords.weightBonus(setWeight, amount);
            }

            @Override
            public String slow(double share) {
                return word("set.slow", PerkWords.written(PerkEffect.Unit.PERCENT, share));
            }

            @Override
            public String slotItem(ArmourSlot each) {
                ItemStack worn = player == null ? null
                        : player.getCurrentArmor(each.getInventoryIndex());
                return worn == null ? word("set.empty") : worn.getDisplayName();
            }
        }, worn, catalogue, settings, weight, slot, itemId, relicId));
    }

    public static void setClientMapImage(ResourceLocation mapTexture) {
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null, mapTexture, "mapTexture");
        ResourceLocation sepiaMapTexture;
        try {
            BufferedImage mapImage = LostTalesUtil.getImage(Minecraft.getMinecraft()
                    .getResourceManager().getResource(mapTexture).getInputStream());
            sepiaMapTexture = invokeStatic(new Object[]{mapImage,
                            new ResourceLocation("lotr:map_sepia")},
                    LOTRTextures.class, "convertToSepia",
                    BufferedImage.class, ResourceLocation.class);
        } catch (IOException exception) {
            FMLLog.severe("Failed to generate LOTR sepia map: %s", exception.toString());
            sepiaMapTexture = mapTexture;
        }
        if (sepiaMapTexture == null) {
            sepiaMapTexture = mapTexture;
        }
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null,
                sepiaMapTexture, "sepiaMapTexture");
    }

    public static void setClientMapImageWithOverlay(ResourceLocation baseMap,
                                                    ResourceLocation overlayMap) {
        BufferedImage mergedMapImage = getMergedClientMapImage(baseMap, overlayMap);
        if (mergedMapImage == null) {
            setClientMapImage(baseMap);
            return;
        }
        ResourceLocation mergedMapTexture = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("losttales_lotr_map", new DynamicTexture(mergedMapImage));
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null,
                mergedMapTexture, "mapTexture");
        ResourceLocation sepiaMapTexture = invokeStatic(new Object[]{mergedMapImage,
                        new ResourceLocation("losttales:map_sepia")},
                LOTRTextures.class, "convertToSepia",
                BufferedImage.class, ResourceLocation.class);
        if (sepiaMapTexture == null) {
            sepiaMapTexture = mergedMapTexture;
        }
        ReflectionHelper.setPrivateValue(LOTRTextures.class, null,
                sepiaMapTexture, "sepiaMapTexture");
    }

    private static BufferedImage getMergedClientMapImage(ResourceLocation baseMap,
                                                          ResourceLocation overlayMap) {
        try {
            BufferedImage baseImage = LostTalesUtil.getImage(Minecraft.getMinecraft()
                    .getResourceManager().getResource(baseMap).getInputStream());
            BufferedImage overlayImage = LostTalesUtil.getImage(Minecraft.getMinecraft()
                    .getResourceManager().getResource(overlayMap).getInputStream());
            return mergeMapImages(baseImage, overlayImage);
        } catch (IOException exception) {
            FMLLog.severe("Failed to load Lost Tales map overlay: %s", exception.toString());
            return null;
        }
    }

    private static BufferedImage mergeMapImages(BufferedImage baseImage,
                                                 BufferedImage overlayImage) {
        if (baseImage == null) {
            return null;
        }
        if (overlayImage == null) {
            return baseImage;
        }
        int width = baseImage.getWidth();
        int height = baseImage.getHeight();
        if (overlayImage.getWidth() != width || overlayImage.getHeight() != height) {
            FMLLog.warning("Lost Tales map overlay size does not match the LOTR map size. Overlay will be ignored.");
            return baseImage;
        }
        int[] baseColors = baseImage.getRGB(0, 0, width, height, null, 0, width);
        int[] overlayColors = overlayImage.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < baseColors.length; i++) {
            baseColors[i] = blendOver(baseColors[i], overlayColors[i]);
        }
        BufferedImage merged = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        merged.setRGB(0, 0, width, height, baseColors, 0, width);
        return merged;
    }

    private static int blendOver(int baseColor, int overlayColor) {
        int alpha = overlayColor >>> 24;
        if (alpha <= 0) {
            return baseColor;
        }
        if (alpha >= 255) {
            return 0xFF000000 | overlayColor & 0x00FFFFFF;
        }
        int inverse = 255 - alpha;
        int red = (((overlayColor >> 16) & 255) * alpha
                + ((baseColor >> 16) & 255) * inverse) / 255;
        int green = (((overlayColor >> 8) & 255) * alpha
                + ((baseColor >> 8) & 255) * inverse) / 255;
        int blue = ((overlayColor & 255) * alpha
                + (baseColor & 255) * inverse) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public static LOTRMapLabels addMapLabel(String enumName, String stringLabel,
                                            int x, int y, float scale, int angle,
                                            float zoomMin, float zoomMax) {
        return addMapLabel(enumName, (Object)stringLabel, x, y, scale, angle, zoomMin, zoomMax);
    }

    private static LOTRMapLabels addMapLabel(String enumName, Object label,
                                             int x, int y, float scale, int angle,
                                             float zoomMin, float zoomMax) {
        Class<?>[] types = {Object.class, int.class, int.class, float.class,
                int.class, float.class, float.class};
        Object[] arguments = {label, x, y, scale, angle, zoomMin, zoomMax};
        return EnumHelper.addEnum(LOTRMapLabels.class, enumName, types, arguments);
    }

    @SuppressWarnings("unchecked")
    private static <T, E> T invokeStatic(Object[] arguments, Class<E> owner,
                                         String methodName, Class<?>... parameterTypes) {
        try {
            Method method = ReflectionHelper.findMethod(owner, (E)null,
                    new String[]{methodName}, parameterTypes);
            return (T)method.invoke(null, arguments);
        } catch (RuntimeException exception) {
            FMLLog.severe("Failed to resolve client compatibility method %s.%s: %s",
                    owner.getName(), methodName, exception.toString());
        } catch (IllegalAccessException exception) {
            FMLLog.severe("Cannot access client compatibility method %s.%s: %s",
                    owner.getName(), methodName, exception.toString());
        } catch (InvocationTargetException exception) {
            FMLLog.severe("Client compatibility method %s.%s failed: %s",
                    owner.getName(), methodName, exception.getCause());
        }
        return null;
    }
}
