package com.example.jjkspells.overlord.entity;

import com.example.jjkspells.overlord.registry.ModParticles;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericCopyOwnerTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericFollowOwnerGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericHurtByTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericOwnerHurtByTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericOwnerHurtTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.GenericProtectOwnerTargetGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Base class of every summon in this mod: owned (Iron's SummonManager), time-limited, drops nothing. */
public abstract class OverlordSummon extends PathfinderMob implements IMagicSummon {
    protected int lifeTicks = -1;
    /** Minecraft caps max health at 1024; summons stronger than that take proportionally less damage instead. */
    protected double hpDivisor = 1.0;

    protected OverlordSummon(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    /** The spell used as damage source (death messages, school resist). */
    protected abstract AbstractSpell sourceSpell();

    public void setupSummon(LivingEntity owner, double health, int lifeTicks) {
        SummonManager.setOwner(this, owner);
        this.lifeTicks = lifeTicks;
        double attr = Math.min(health, 1024.0);
        this.hpDivisor = Math.max(1.0, health / attr);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(attr);
        this.setHealth((float) attr);
    }

    /** Effective hit points (what the spell tooltip promises). */
    public double effectiveMaxHealth() {
        return this.getMaxHealth() * this.hpDivisor;
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, (float) (amount / this.hpDivisor));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(7, new GenericFollowOwnerGoal(this, this::getSummoner, 0.9f, 15, 5, false, 25));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));

        this.targetSelector.addGoal(1, new GenericOwnerHurtByTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(2, new GenericOwnerHurtTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(3, new GenericCopyOwnerTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(4, (new GenericHurtByTargetGoal(this, e -> e == getSummoner())).setAlertOthers());
        this.targetSelector.addGoal(5, new GenericProtectOwnerTargetGoal(this, this::getSummoner));
        registerExtraTargets();
    }

    protected void registerExtraTargets() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.lifeTicks > 0 && --this.lifeTicks == 0) {
            onUnSummon();
        }
    }

    @Override
    public void onUnSummon() {
        if (!this.level().isClientSide) {
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ModParticles.VOID.get(), getX(), getY() + getBbHeight() * 0.5, getZ(), 40, getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.05);
                sl.sendParticles(ModParticles.SMOKE.get(), getX(), getY() + getBbHeight() * 0.5, getZ(), 14, getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.02);
            }
            this.setRemoved(RemovalReason.DISCARDED);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (shouldIgnoreDamage(source)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return Utils.doMeleeAttack(this, target, sourceSpell().getDamageSource(this, getSummoner() == null ? this : getSummoner()));
    }

    @Override
    public void die(DamageSource source) {
        this.onDeathHelper();
        super.die(source);
    }

    @Override
    public void onRemovedFromWorld() {
        this.onRemovedHelper(this);
        super.onRemovedFromWorld();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return super.isAlliedTo(other) || this.isAlliedHelper(other);
    }

    public boolean isPreventingPlayerRest(Player p) {
        return !this.isAlliedTo(p);
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
        // summons never drop anything (also keeps their netherite gear from being duplicated)
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player p) {
        return false;
    }
}
