package com.example.jjkspells.entity;

import com.example.jjkspells.StormConfig;
import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModSpells;
import io.redspace.ironsspellbooks.api.entity.NoKnockbackProjectile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the two storm tornados. Its position is computed from the caster every tick on both logical sides
 * (so it needs no position packets). Tornado slot 0 also does the damage for both of them, which guarantees
 * an enemy standing in both funnels is hit only once per damage tick.
 */
public class StormTornadoEntity extends net.minecraft.world.entity.projectile.Projectile implements AntiMagicSusceptible, NoKnockbackProjectile {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(StormTornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOT = SynchedEntityData.defineId(StormTornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(StormTornadoEntity.class, EntityDataSerializers.INT);

    public static final int FADE_IN = 24;
    public static final int FADE_OUT = 24;

    private float dps = 80f;

    public StormTornadoEntity(EntityType<? extends StormTornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.noCulling = true;
    }

    public StormTornadoEntity(Level level, LivingEntity owner) {
        this(ModEntities.STORM_TORNADO.get(), level);
        this.setOwner(owner);
        this.entityData.set(DATA_OWNER, owner.getId());
    }

    public void setup(int slot, int lifetimeTicks, float damagePerSecond) {
        this.entityData.set(DATA_SLOT, slot);
        this.entityData.set(DATA_LIFETIME, lifetimeTicks);
        this.dps = damagePerSecond;
    }

    public int getSlot() {
        return this.entityData.get(DATA_SLOT);
    }

    public int getLifetime() {
        return this.entityData.get(DATA_LIFETIME);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER, -1);
        this.entityData.define(DATA_SLOT, 0);
        this.entityData.define(DATA_LIFETIME, 240);
    }

    private Entity caster() {
        Entity e = this.level().isClientSide ? this.level().getEntity(this.entityData.get(DATA_OWNER)) : getOwner();
        return e;
    }

    // ---------------------------------------------------------------- orbit

    private static double angleDeg(int slot, int age) {
        return slot * 180.0 + age * StormConfig.SPELL.orbitDegreesPerTick.get();
    }

    private static Vec3 orbitPos(Entity caster, int slot, int age) {
        double a = Math.toRadians(angleDeg(slot, age));
        double r = StormConfig.SPELL.orbitRadius.get();
        return new Vec3(caster.getX() + Math.cos(a) * r, caster.getY(), caster.getZ() + Math.sin(a) * r);
    }

    public void moveToOrbit(Entity caster) {
        Vec3 p = orbitPos(caster, getSlot(), 0);
        this.setPos(p.x, p.y, p.z);
        this.setOldPosAndRot();
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        Entity caster = caster();
        boolean client = this.level().isClientSide;
        if (caster == null || !caster.isAlive()) {
            if (!client && this.tickCount > 2) {
                this.discard();
            }
            return;
        }
        this.setOldPosAndRot();
        Vec3 p = orbitPos(caster, getSlot(), this.tickCount);
        this.setPos(p.x, p.y, p.z);

        if (client) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.client.TornadoFx.tick(this));
            return;
        }

        int life = getLifetime();
        if (this.tickCount >= life) {
            if (getSlot() == 0) {
                this.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.STORM_END.get(), SoundSource.PLAYERS, 4.0f, 1.0f);
            }
            this.discard();
            return;
        }

        float grow = Math.min(1f, this.tickCount / (float) FADE_IN) * Math.min(1f, (life - this.tickCount) / (float) FADE_OUT);
        // lift / whirl affects this tornado's own funnel every tick
        whirl(caster, grow);

        if (getSlot() == 0 && this.tickCount > 4 && grow > 0.45f) {
            int interval = Math.max(1, StormConfig.SPELL.hitIntervalTicks.get());
            if (this.tickCount % interval == 0) {
                damage(caster, interval);
            }
            if (this.tickCount % 12 == 0) {
                this.level().playSound(null, p.x, p.y + 6, p.z, ModSounds.THUNDER_CRACK.get(), SoundSource.PLAYERS, 3.0f, 0.8f + this.random.nextFloat() * 0.5f);
            }
            if (StormConfig.SPELL.lightningStrikes.get() && this.tickCount % 22 == 0) {
                strike(caster);
            }
        }
    }

    // ---------------------------------------------------------------- funnel geometry

    /** Radius of the funnel at a height above its base (matches the renderer). */
    public static double radiusAt(double h, double H) {
        double base = StormConfig.SPELL.baseRadius.get();
        double top = StormConfig.SPELL.topRadius.get();
        double f = Math.max(0.0, Math.min(1.0, h / H));
        return base + (top - base) * Math.pow(f, 1.25);
    }

    private boolean inFunnel(Vec3 base, Entity e) {
        double H = StormConfig.SPELL.tornadoHeight.get();
        double h = e.getY() + e.getBbHeight() * 0.5 - base.y;
        if (h < -1.0 || h > H + 1.0) {
            return false;
        }
        double dx = e.getX() - base.x, dz = e.getZ() - base.z;
        double reach = radiusAt(h, H) + e.getBbWidth() * 0.5;
        return dx * dx + dz * dz <= reach * reach;
    }

    private boolean canAffect(Entity caster, Entity e) {
        if (e == this || e == caster || !e.isAlive() || e.isSpectator() || e instanceof StormTornadoEntity) {
            return false;
        }
        if (!(e instanceof LivingEntity)) {
            return false;
        }
        if (e instanceof Player && !StormConfig.SPELL.hitPlayers.get()) {
            return false;
        }
        return !DamageSources.isFriendlyFireBetween(caster, e);
    }

    private List<Vec3> bases(Entity caster) {
        List<Vec3> list = new ArrayList<>(2);
        list.add(orbitPos(caster, 0, this.tickCount));
        list.add(orbitPos(caster, 1, this.tickCount));
        return list;
    }

    private AABB area(Entity caster) {
        double H = StormConfig.SPELL.tornadoHeight.get();
        double reach = StormConfig.SPELL.orbitRadius.get() + StormConfig.SPELL.topRadius.get() + 3.0;
        return new AABB(caster.getX() - reach, caster.getY() - 2, caster.getZ() - reach, caster.getX() + reach, caster.getY() + H + 2, caster.getZ() + reach);
    }

    private void whirl(Entity caster, float grow) {
        double lift = StormConfig.SPELL.liftStrength.get() * grow;
        double spin = StormConfig.SPELL.spinStrength.get() * grow;
        if (lift <= 0 && spin <= 0) {
            return;
        }
        Vec3 base = orbitPos(caster, getSlot(), this.tickCount);
        for (Entity e : this.level().getEntities(this, area(caster), x -> canAffect(caster, x))) {
            if (!inFunnel(base, e) || (e instanceof Player p && p.getAbilities().flying)) {
                continue;
            }
            Vec3 rel = new Vec3(e.getX() - base.x, 0, e.getZ() - base.z);
            Vec3 tangent = new Vec3(-rel.z, 0, rel.x).normalize();
            Vec3 m = e.getDeltaMovement().add(tangent.scale(spin)).add(0, lift, 0);
            if (m.y > 0.55) {
                m = new Vec3(m.x, 0.55, m.z);
            }
            double h = Math.sqrt(m.x * m.x + m.z * m.z);
            if (h > 0.9) {
                m = new Vec3(m.x / h * 0.9, m.y, m.z / h * 0.9);
            }
            e.setDeltaMovement(m);
            e.hurtMarked = true;
            e.hasImpulse = true;
            e.fallDistance = 0;
        }
    }

    private void damage(Entity caster, int intervalTicks) {
        float perHit = dps * intervalTicks / 20f;
        if (perHit <= 0f) {
            return;
        }
        List<Vec3> bases = bases(caster);
        double aura = StormConfig.SPELL.casterAuraRadius.get();
        for (Entity e : this.level().getEntities(this, area(caster), x -> canAffect(caster, x))) {
            boolean hit = aura > 0 && e.distanceToSqr(caster) <= aura * aura;
            for (Vec3 b : bases) {
                if (!hit && inFunnel(b, e)) {
                    hit = true;
                }
            }
            if (!hit) {
                continue;
            }
            LivingEntity living = (LivingEntity) e;
            living.invulnerableTime = 0;
            DamageSources.applyDamage(living, perHit, ModSpells.STORM_LORDS_WRATH.get().getDamageSource(this, caster));
        }
    }

    /** Harmless, visual-only vanilla lightning bolt somewhere inside the storm. */
    private void strike(Entity caster) {
        List<Vec3> bases = bases(caster);
        Vec3 b = bases.get(this.random.nextInt(bases.size()));
        double a = this.random.nextDouble() * Math.PI * 2;
        double r = (0.3 + this.random.nextDouble() * 0.9) * (StormConfig.SPELL.topRadius.get() * 0.8);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(this.level());
        if (bolt != null) {
            bolt.moveTo(b.x + Math.cos(a) * r, b.y, b.z + Math.sin(a) * r);
            bolt.setVisualOnly(true);
            this.level().addFreshEntity(bolt);
        }
    }

    @Override
    public void onAntiMagic(MagicData playerMagicData) {
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.discard(); // never persists across world reloads
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256.0 * 256.0;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }
}
