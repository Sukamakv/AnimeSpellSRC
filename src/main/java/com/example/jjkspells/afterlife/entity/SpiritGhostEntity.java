package com.example.jjkspells.afterlife.entity;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModEntities;
import com.example.jjkspells.afterlife.registry.ModParticles;
import com.example.jjkspells.afterlife.registry.ModSounds;
import com.example.jjkspells.afterlife.util.Util;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The ghost of Spirit Soother: a short scripted flight (summon, rush, explode, return) driven entirely by tickCount, so
 * the client can derive its animation from the same clock. No pathfinding, not a permanent companion.
 */
public class SpiritGhostEntity extends Entity {
    public static final int T_RUSH = 12, RUSH = 16, T_EXP = 28, T_RET = 38, RETURN = 20, T_END = 58;

    private UUID ownerId;
    private Vec3 p0 = Vec3.ZERO, dest = Vec3.ZERO, retFrom = Vec3.ZERO;
    private double a0;
    private boolean lowHp, killed;
    private int enemiesHit;
    private final Set<UUID> contacted = new HashSet<>();

    public SpiritGhostEntity(EntityType<? extends SpiritGhostEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static void launch(Level level, LivingEntity caster) {
        var c = AfterlifeConfig.SOOTHER;
        double range = c.range.get();
        Vec3 aim = Util.aimPoint(level, caster, range);
        Vec3 from = caster.position();
        Vec3 d = aim.subtract(from);
        if (d.length() > range) {
            aim = from.add(d.normalize().scale(range));
        }
        if (aim.subtract(from).horizontalDistance() < 3.0) {
            Vec3 look = caster.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
            aim = new Vec3(from.x + flat.x * 3.0, aim.y, from.z + flat.z * 3.0);
        }
        SpiritGhostEntity g = new SpiritGhostEntity(ModEntities.GHOST.get(), level);
        g.ownerId = caster.getUUID();
        g.dest = aim.add(0, 1.0, 0);
        g.lowHp = caster.getHealth() < caster.getMaxHealth() * c.lowHpThreshold.get() / 100.0;
        g.a0 = Math.toRadians(caster.getYRot() + 90.0);
        g.setPos(from.x, from.y + 1.6, from.z);
        level.addFreshEntity(g);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        if (!(sl.getEntity(ownerId) instanceof LivingEntity owner) || !owner.isAlive()) {
            discard();
            return;
        }
        int age = tickCount - 1;
        if (age == 0) {
            Util.sound(level(), getX(), getY(), getZ(), ModSounds.GHOST_SUMMON.get(), 1.6f, 1.0f);
        }
        if (age < T_RUSH) {
            double a = a0 + Math.PI * 2 * (age + 1) / T_RUSH;
            Vec3 o = owner.position();
            setPos(o.x + Math.cos(a) * 1.5, o.y + 1.6 + 0.15 * Math.sin(age * 0.6), o.z + Math.sin(a) * 1.5);
            Util.fx(level(), ModParticles.WISP.get(), getX(), getY(), getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        } else if (age < T_EXP) {
            if (age == T_RUSH) {
                p0 = position();
                Util.sound(level(), getX(), getY(), getZ(), ModSounds.GHOST_RUSH.get(), 1.8f, 1.0f);
            }
            double s = (age - T_RUSH + 1) / (double) RUSH;
            Vec3 p = p0.lerp(dest, Math.pow(s, 1.7));
            setPos(p.x, p.y, p.z);
            double ang = age * 0.9;
            for (int k = 0; k < 3; k++) {
                double a = ang + k * Math.PI * 2 / 3;
                Util.fxDir(level(), ModParticles.SPIRIT_FLAME.get(), p.x + Math.cos(a) * 0.7, p.y + Math.sin(a) * 0.7, p.z + Math.sin(a * 0.5) * 0.3, 0, 0.01, 0);
            }
            Util.fx(level(), ModParticles.BUTTERFLY_RED.get(), p.x, p.y, p.z, 1, 0.5, 0.5, 0.5, 0.02);
            Util.fx(level(), ModParticles.WISP.get(), p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
            var c = AfterlifeConfig.SOOTHER;
            for (LivingEntity e : Util.enemies(level(), new Vec3(p.x, p.y - 1.0, p.z), 1.8, owner)) {
                if (contacted.add(e.getUUID())) {
                    Util.hurt(owner, e, c.contactFlat.get(), c.contactPercent.get(), 1.0);
                    Vec3 q = e.getBoundingBox().getCenter();
                    Util.fx(level(), ModParticles.FLASH.get(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    Util.fx(level(), ModParticles.EMBER.get(), q.x, q.y, q.z, 10, 0.3, 0.4, 0.3, 0.12);
                }
            }
        } else if (age == T_EXP) {
            setPos(dest.x, dest.y, dest.z);
            explode(owner);
        } else if (age >= T_RET && age < T_END) {
            if (age == T_RET) {
                retFrom = dest;
                Util.fx(level(), ModParticles.FLASH.get(), dest.x, dest.y, dest.z, 1, 0, 0, 0, 0);
            }
            double s = (age - T_RET + 1) / (double) RETURN;
            double e = s * s * (3 - 2 * s);
            Vec3 target = owner.position().add(0, 1.9, 0);
            Vec3 p = retFrom.lerp(target, e);
            setPos(p.x, p.y, p.z);
            // golden-red butterflies spiral into the caster
            double a = age * 0.8;
            double r = (1.0 - e) * 1.6 + 0.3;
            Vec3 o = owner.position();
            Util.fxDir(level(), ModParticles.BUTTERFLY_GOLD.get(), o.x + Math.cos(a) * r, o.y + 0.4 + e * 1.0, o.z + Math.sin(a) * r, -Math.cos(a) * 0.02, 0.02, -Math.sin(a) * 0.02);
            Util.fxDir(level(), ModParticles.BUTTERFLY_RED.get(), o.x - Math.cos(a) * r, o.y + 0.9 + e * 0.6, o.z - Math.sin(a) * r, Math.cos(a) * 0.02, 0.02, Math.sin(a) * 0.02);
        } else if (age == T_END) {
            finish(owner);
        }
    }

    private void explode(LivingEntity owner) {
        var c = AfterlifeConfig.SOOTHER;
        Vec3 ce = dest;
        double mult = lowHp ? 1.0 + c.lowHpBonusPercent.get() / 100.0 : 1.0;
        for (LivingEntity e : Util.enemies(level(), ce, c.radius.get(), owner)) {
            double dx = e.getX() - ce.x, dz = e.getZ() - ce.z;
            boolean core = Math.sqrt(dx * dx + dz * dz) <= c.coreRadius.get() + e.getBbWidth() / 2;
            if (Util.hurt(owner, e, c.explFlat.get() + (core ? c.coreBonus.get() : 0.0), c.explPercent.get(), mult)) {
                killed = true;
            } else {
                e.setSecondsOnFire((int) Math.round(c.fireSeconds.get()));
            }
            enemiesHit++;
        }
        var l = level();
        Util.sound(l, ce.x, ce.y, ce.z, ModSounds.GHOST_BOOM.get(), 3.0f, 1.0f);
        double r = c.radius.get();
        // layered blast: white-hot core, crimson ring, spectral flames, butterflies and a ground shockwave
        Util.fx(l, ModParticles.FLASH.get(), ce.x, ce.y, ce.z, 3, 0.3, 0.3, 0.3, 0);
        Util.fx(l, ModParticles.EMBER.get(), ce.x, ce.y, ce.z, 140, r * 0.3, r * 0.2, r * 0.3, 0.35);
        Util.fx(l, ModParticles.SPIRIT_FLAME.get(), ce.x, ce.y, ce.z, 90, r * 0.25, r * 0.15, r * 0.25, 0.25);
        Util.fx(l, ModParticles.WISP.get(), ce.x, ce.y, ce.z, 40, r * 0.3, 0.3, r * 0.3, 0.12);
        Util.fx(l, ModParticles.BUTTERFLY_RED.get(), ce.x, ce.y, ce.z, 40, 0.5, 0.5, 0.5, 0.3);
        Util.fx(l, ModParticles.BUTTERFLY_ORANGE.get(), ce.x, ce.y, ce.z, 40, 0.5, 0.5, 0.5, 0.3);
        Util.fx(l, ModParticles.BUTTERFLY_GOLD.get(), ce.x, ce.y, ce.z, 25, 0.5, 0.5, 0.5, 0.3);
        double gy = ce.y - 1.0;
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            var t = i % 2 == 0 ? ModParticles.BUTTERFLY_RED.get() : ModParticles.BUTTERFLY_ORANGE.get();
            Util.fxDir(l, t, ce.x, gy + 0.3, ce.z, Math.cos(a) * 0.55, 0.02, Math.sin(a) * 0.55);
            Util.fxDir(l, ModParticles.SPIRIT_FLAME.get(), ce.x, gy + 0.2, ce.z, Math.cos(a) * 0.4, 0.01, Math.sin(a) * 0.4);
        }
    }

    private void finish(LivingEntity owner) {
        var c = AfterlifeConfig.SOOTHER;
        double pct = Math.min(enemiesHit * c.healPercent.get(), c.healCapPercent.get());
        if (pct > 0) {
            owner.heal((float) (owner.getMaxHealth() * pct / 100.0));
        }
        if (killed) {
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) Math.round(c.farewellRegenSeconds.get() * 20), 1, false, true, true));
            Util.fx(level(), ModParticles.FLASH.get(), owner.getX(), owner.getY() + 1.0, owner.getZ(), 1, 0, 0, 0, 0);
        }
        Vec3 o = owner.position();
        Util.fx(level(), ModParticles.BUTTERFLY_GOLD.get(), o.x, o.y + 1.0, o.z, 14, 0.5, 0.7, 0.5, 0.08);
        Util.fx(level(), ParticleTypes.HAPPY_VILLAGER, o.x, o.y + 1.0, o.z, 24, 0.5, 0.8, 0.5, 0.05);
        Util.sound(level(), o.x, o.y, o.z, ModSounds.HEAL.get(), 1.4f, 1.0f);
        discard();
    }

    // ------------------------------------------------------------ client animation helpers (pure functions of time)

    /** Visual scale of the ghost at time t (ticks since spawn, with partial tick). */
    public static float scaleAt(float t) {
        if (t < T_RUSH) {
            return 0.45f + 0.55f * (t / T_RUSH);
        }
        if (t < T_EXP) {
            float s = (t - T_RUSH) / RUSH;
            return 1.0f + 0.55f * s;
        }
        if (t < T_RET) {
            return 1.55f + 1.1f * Math.min(1f, (t - T_EXP) / 10f);
        }
        return 0.55f;
    }

    public static float alphaAt(float t) {
        if (t < T_EXP) {
            return Math.min(1f, t / 4f);
        }
        if (t < T_RET) {
            return Math.max(0f, 1f - (t - T_EXP) / 9f);
        }
        return Math.min(1f, (t - T_RET) / 4f);
    }

    public static boolean mouthOpenAt(float t) {
        return (t > T_EXP - 6 && t < T_RET) ;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128.0 * 128.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
