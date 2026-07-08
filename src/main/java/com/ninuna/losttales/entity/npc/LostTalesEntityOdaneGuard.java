package com.ninuna.losttales.entity.npc;

import lotr.common.entity.ai.LOTREntityAIAttackOnCollide;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class LostTalesEntityOdaneGuard extends LostTalesEntityOdaneMan {

    public LostTalesEntityOdaneGuard(World world) {
        super(world);
        this.addTargetTasks(true);
    }

    @Override
    protected int addBreeAttackAI(int prio) {
        this.tasks.addTask(prio, new LOTREntityAIAttackOnCollide(this, 1.45D, false));
        return prio;
    }

    @Override
    protected void addBreeAvoidAI(int prio) {}

    @Override
    public void setupNPCGender() {
        this.familyInfo.setMale(true);
    }

    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);

        ItemStack weapon;
        if (this.rand.nextBoolean()) {
            weapon = new ItemStack(Items.wooden_sword);
        } else {
            weapon = new ItemStack(Items.wooden_axe);
        }

        this.npcItemsInv.setMeleeWeapon(weapon);
        this.npcItemsInv.setIdleItem(weapon);
        this.setCurrentItemOrArmor(0, weapon);
        this.equipLeatherArmor(true);

        return data;
    }

    @Override
    public float getAlignmentBonus() {
        return 2.0F;
    }

    @Override
    public String getSpeechBank(net.minecraft.entity.player.EntityPlayer entityplayer) {
        if (this.isFriendlyAndAligned(entityplayer)) {
            if (this.hiredNPCInfo.getHiringPlayer() == entityplayer) {
                return "bree/guard/hired";
            }
            return "bree/guard/friendly";
        }
        return "bree/guard/hostile";
    }
}
