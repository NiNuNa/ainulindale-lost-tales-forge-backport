package com.ninuna.losttales.entity.npc;

import com.ninuna.losttales.faction.ELostTalesFaction;
import com.ninuna.losttales.item.ELostTalesItem;
import lotr.common.LOTRMod;
import lotr.common.entity.npc.LOTREntityUtumnoOrc;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class LostTalesEntityBlueGoblinSoldier extends LOTREntityUtumnoOrc {

    public LostTalesEntityBlueGoblinSoldier(World world) {
        super(world);
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);

        this.npcItemsInv.setMeleeWeapon(new ItemStack(ELostTalesItem.ARNORIAN_LONGSWORD.getItem()));

        if (this.rand.nextInt(6) == 0) {
            this.npcItemsInv.setSpearBackup(this.npcItemsInv.getMeleeWeapon());
            this.npcItemsInv.setMeleeWeapon(new ItemStack(ELostTalesItem.ARNORIAN_SPEAR.getItem()));
        }

        this.npcItemsInv.setIdleItem(this.npcItemsInv.getMeleeWeapon());

        this.setCurrentItemOrArmor(1, new ItemStack(LOTRMod.bootsTauredainGold));
        this.setCurrentItemOrArmor(2, new ItemStack(LOTRMod.legsMithril));
        this.setCurrentItemOrArmor(3, new ItemStack(LOTRMod.bodyBlackNumenorean));
        this.setCurrentItemOrArmor(4, new ItemStack(LOTRMod.helmetTauredainGold));

        return data;
    }

    public LOTRFaction getFaction() {
        return ELostTalesFaction.BLUE_GOBLINS.getFaction();
    }
}