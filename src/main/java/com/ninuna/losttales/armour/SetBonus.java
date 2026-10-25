package com.ninuna.losttales.armour;

/**
 * One effect of a named set's bonus (CONTENT-07): the effect, its number,
 * and for {@link SetEffect#EXPERIENCE} the skill it feeds.
 */
public final class SetBonus {
    private final SetEffect effect;
    private final double amount;
    private final String skill;

    public SetBonus(SetEffect effect, double amount, String skill) {
        this.effect = effect;
        this.amount = amount;
        this.skill = skill == null ? "" : skill;
    }

    public SetEffect getEffect() {
        return this.effect;
    }

    /** The share or chance, written 0.05 for 5%. */
    public double getAmount() {
        return this.amount;
    }

    /** The skill an experience bonus feeds; empty for every other effect. */
    public String getSkill() {
        return this.skill;
    }
}
