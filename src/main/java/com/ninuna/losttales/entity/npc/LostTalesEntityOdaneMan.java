package com.ninuna.losttales.entity.npc;

import com.ninuna.losttales.faction.ELostTalesFaction;
import lotr.common.entity.npc.LOTREntityBreeMan;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import lotr.common.util.LOTRColorUtil;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class LostTalesEntityOdaneMan extends LOTREntityBreeMan {

    private static final int[] ODANE_LEATHER_COLORS = new int[] {
            0x6B3F2A,
            0x8A5A38,
            0x9A6B3F,
            0x4F5A33
    };

    public LostTalesEntityOdaneMan(World world) {
        super(world);
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);

        ItemStack meleeWeapon;
        if (this.rand.nextInt(3) == 0) {
            meleeWeapon = new ItemStack(Items.wooden_axe);
        } else {
            meleeWeapon = new ItemStack(Items.wooden_sword);
        }

        ItemStack idleItem = this.rand.nextInt(4) == 0 ? new ItemStack(Items.wooden_hoe) : null;
        this.npcItemsInv.setMeleeWeapon(meleeWeapon);
        this.npcItemsInv.setIdleItem(idleItem);
        this.setCurrentItemOrArmor(0, idleItem);
        this.equipLeatherArmor(false);

        return data;
    }

    protected void equipLeatherArmor(boolean helmet) {
        this.setCurrentItemOrArmor(1, this.createLeatherArmor(new ItemStack(Items.leather_boots)));
        this.setCurrentItemOrArmor(2, this.createLeatherArmor(new ItemStack(Items.leather_leggings)));
        this.setCurrentItemOrArmor(3, this.createLeatherArmor(new ItemStack(Items.leather_chestplate)));
        if (helmet) {
            this.setCurrentItemOrArmor(4, this.createLeatherArmor(new ItemStack(Items.leather_helmet)));
        } else {
            this.setCurrentItemOrArmor(4, null);
        }
    }

    protected ItemStack createLeatherArmor(ItemStack armor) {
        return LOTRColorUtil.dyeLeather(armor, ODANE_LEATHER_COLORS, this.rand);
    }

    @Override
    protected void onAttackModeChange(LOTREntityNPC.AttackMode mode, boolean mounted) {
        if (mode == LOTREntityNPC.AttackMode.IDLE) {
            this.setCurrentItemOrArmor(0, this.npcItemsInv.getIdleItem());
        } else {
            this.setCurrentItemOrArmor(0, this.npcItemsInv.getMeleeWeapon());
        }
    }

    @Override
    public LOTRFaction getFaction() {
        return ELostTalesFaction.ODANE.getFaction();
    }

    @Override
    public String getSpeechBank(EntityPlayer entityplayer) {
        if (this.isFriendlyAndAligned(entityplayer)) {
            return "bree/man/friendly";
        }
        return "bree/man/hostile";
    }
}
