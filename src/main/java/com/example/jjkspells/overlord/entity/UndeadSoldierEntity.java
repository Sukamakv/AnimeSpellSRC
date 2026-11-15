package com.example.jjkspells.overlord.entity;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.registry.ModSpells;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Wither skeleton soldier. Built exactly like the Black Goat summons (plain OverlordSummon, no custom tick,
 * no custom effects): 450 HP, 20 armour (full netherite), 75 damage, Speed I.
 */
public class UndeadSoldierEntity extends OverlordSummon {
    public static final double HEALTH = 450.0;
    public static final double ATTACK = 75.0;
    /** Netherite sword gives +7 on top of the base value. */
    private static final double SWORD_BONUS = 7.0;
    /** Speed I = +20%. */
    public static final double SPEED_MULT = 1.2;
    public static final int WITHER_TICKS = 100;

    public UndeadSoldierEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 450.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ATTACK_DAMAGE, ATTACK)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerExtraTargets() {
        this.targetSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && !(e instanceof io.redspace.ironsspellbooks.entity.mobs.IMagicSummon)
                        && !io.redspace.ironsspellbooks.damage.DamageSources.isFriendlyFireBetween(this, e)));
    }

    @Override
    protected AbstractSpell sourceSpell() {
        return ModSpells.SUMMON_UNDEAD.get();
    }

    public void configure(LivingEntity owner, float healthScale, int spellLevel) {
        int life = OverlordConfig.UNDEAD.durationSeconds.get() * 20;
        setupSummon(owner, HEALTH * healthScale, life);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ATTACK);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.30 * SPEED_MULT);
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        for (EquipmentSlot s : EquipmentSlot.values()) {
            this.setDropChance(s, 0f);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.level().isClientSide || !(target instanceof LivingEntity le) || !le.isAlive()) {
            return false;
        }
        boolean hit = false;
        try {
            Entity owner = getSummoner() == null ? this : getSummoner();
            hit = io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(le,
                    (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), sourceSpell().getDamageSource(this, owner));
            this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (hit && le.isAlive()) {
                le.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0));
            }
        } catch (RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().error("Undead soldier melee failed", ex);
        }
        return hit;
    }
}
