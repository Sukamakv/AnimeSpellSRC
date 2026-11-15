package com.example.jjkspells.garou.entity;

import com.example.jjkspells.garou.GarouEvents;
import com.example.jjkspells.garou.GarouRegistry;
import com.example.jjkspells.garou.spell.GarouCosmicPortalSpell;
import com.example.jjkspells.garou.util.GarouDamage;
import com.example.jjkspells.garou.util.PortalPlanner;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * COSMIC PORTAL controller (non-living, no physics), parked at the caster's feet where the spell was cast.
 *
 * Timeline (ticks):
 *   0 .. T_VANISH     entry portal opens in front of the caster, he steps toward it (rooted otherwise)
 *   T_VANISH          he vanishes (invisible + invulnerable), exit portal starts opening at the destination
 *   T_EMERGE          teleport, fast punch (enemy mode: damage + knock forward), exit flash
 *   .. T_END          portals close, afterimages fade
 * The destination is planned by PortalPlanner when the spell is cast; in enemy mode it keeps following the target until the
 * emerge so the exit portal tracks it. Nothing in the world is modified.
 */
public class CosmicPortalEntity extends Entity {
    public static final int T_VANISH = 7;
    public static final int T_EMERGE = 14;
    public static final int T_END = 30;

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> EYAW = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DYAW = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HIT = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> IX = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> IY = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> IZ = SynchedEntityData.defineId(CosmicPortalEntity.class, EntityDataSerializers.FLOAT);

    public int age;
    private int targetId = -1;

    public CosmicPortalEntity(EntityType<? extends CosmicPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static CosmicPortalEntity create(Level level, LivingEntity caster, PortalPlanner.Plan plan) {
        CosmicPortalEntity e = new CosmicPortalEntity(GarouRegistry.COSMIC_PORTAL, level);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.entityData.set(CASTER, caster.getId());
        e.entityData.set(EYAW, caster.getYRot());
        e.setDest(plan.dest(), plan.yaw());
        e.targetId = plan.targetId();
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(CASTER, -1);
        entityData.define(EYAW, 0f);
        entityData.define(DX, 0f); entityData.define(DY, 0f); entityData.define(DZ, 0f);
        entityData.define(DYAW, 0f);
        entityData.define(HIT, false);
        entityData.define(IX, 0f); entityData.define(IY, 0f); entityData.define(IZ, 0f);
    }

    private void setDest(Vec3 d, float yaw) {
        entityData.set(DX, (float) d.x); entityData.set(DY, (float) d.y); entityData.set(DZ, (float) d.z);
        entityData.set(DYAW, yaw);
    }

    public int getCasterId() { return entityData.get(CASTER); }
    public Vec3 getDest() { return new Vec3(entityData.get(DX), entityData.get(DY), entityData.get(DZ)); }
    public float getDestYaw() { return entityData.get(DYAW); }
    public float getEntryYaw() { return entityData.get(EYAW); }
    public boolean wasHit() { return entityData.get(HIT); }
    public Vec3 getImpact() { return new Vec3(entityData.get(IX), entityData.get(IY), entityData.get(IZ)); }
    public boolean holdsCaster() { return age < T_EMERGE; }
    public static float prog(float t, float a, float b) { return Mth.clamp((t - a) / (b - a), 0f, 1f); }

    /** Horizontal direction for a yaw in degrees (Minecraft convention). */
    public static Vec3 dirOf(float yawDeg) {
        float r = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }
    /** Entry portal: 1.9 blocks in front of where the caster stood, facing him. */
    public Vec3 entryCenter() { return position().add(dirOf(getEntryYaw()).scale(1.9)).add(0, 1.1, 0); }
    public Vec3 entryNormal() { return dirOf(getEntryYaw()); }
    /** Exit portal: just behind the arrival spot so he comes out of it. */
    public Vec3 exitCenter() { return getDest().subtract(dirOf(getDestYaw()).scale(1.3)).add(0, 1.1, 0); }
    public Vec3 exitNormal() { return dirOf(getDestYaw()); }

    @Override public void onAddedToWorld() { super.onAddedToWorld(); GarouEvents.ACTIVE_CP.add(this); }
    @Override public void onRemovedFromWorld() { GarouEvents.ACTIVE_CP.remove(this); super.onRemovedFromWorld(); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick(); else serverTick();
        age++;
    }

    // ------------------------------------------------------------------ server
    private static GarouCosmicPortalSpell spellInstance;
    private static DamageSource damageSource(LivingEntity caster) {
        if (spellInstance == null) spellInstance = new GarouCosmicPortalSpell();
        return spellInstance.getDamageSource(caster);
    }

    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        Entity ce = sl.getEntity(getCasterId());
        if (!(ce instanceof LivingEntity caster) || !caster.isAlive()) { discard(); return; }

        if (age < T_EMERGE) {
            Vec3 m = caster.getDeltaMovement();
            Vec3 fwd = dirOf(getEntryYaw());
            if (age >= 3 && age < T_VANISH) caster.setDeltaMovement(fwd.x * 0.4, Math.min(m.y, 0), fwd.z * 0.4);   // steps into the portal
            else caster.setDeltaMovement(0, Math.min(m.y, 0), 0);
            caster.hurtMarked = true;
        }
        if (age == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 2.5f, 1.4f);
        if (age == T_VANISH) {
            // he is gone: hidden and immune while "inside" the portal (13 ticks, ends right at the emerge)
            caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, T_EMERGE - T_VANISH + 1, 0, false, false, false));
            caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, T_EMERGE - T_VANISH + 4, 4, false, false, false));
            sl.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.5f, 0.6f);
        }
        // enemy mode: keep the exit portal glued behind the (possibly moving) target
        if (targetId >= 0 && age >= T_VANISH - 3 && age < T_EMERGE) {
            Entity te = sl.getEntity(targetId);
            if (te instanceof LivingEntity tl && tl.isAlive()) {
                Vec3 d = PortalPlanner.behind(sl, caster, tl);
                if (d != null) setDest(d, PortalPlanner.yawToward(d, tl.position()));
            }
        }
        if (age == T_EMERGE) emerge(sl, caster);
        if (age >= T_END) discard();
    }

    private void emerge(ServerLevel sl, LivingEntity caster) {
        Vec3 dest = getDest();
        float yaw = getDestYaw();
        Vec3 safe = PortalPlanner.findStand(sl, caster, dest.x, dest.y, dest.z);
        if (safe == null) { safe = position(); yaw = getEntryYaw(); setDest(safe, yaw); }     // blocked in the meantime: cancel, come back out here
        else setDest(safe, yaw);

        teleport(caster, safe, yaw);
        caster.fallDistance = 0;
        Vec3 fwd = dirOf(yaw);
        caster.setDeltaMovement(fwd.x * 0.5, 0.0, fwd.z * 0.5);
        caster.hurtMarked = true;
        sl.playSound(null, safe.x, safe.y, safe.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.5f, 1.2f);

        if (targetId < 0) return;
        Entity te = sl.getEntity(targetId);
        if (!(te instanceof LivingEntity t) || !t.isAlive() || !GarouDamage.isEnemy(caster, t) || t.position().distanceTo(safe) > 7.0) return;
        caster.swing(InteractionHand.MAIN_HAND, true);
        Vec3 c = t.getBoundingBox().getCenter();
        entityData.set(HIT, true);
        entityData.set(IX, (float) c.x); entityData.set(IY, (float) c.y); entityData.set(IZ, (float) c.z);
        GarouDamage.hit(t, caster, damageSource(caster), GarouCosmicPortalSpell.PUNCH_FLAT, GarouCosmicPortalSpell.PUNCH_PERCENT);
        if (t.isAlive()) {
            // "knocked forward": along the way the punch is facing (the way the target is facing, since we are behind it)
            double resist = t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double push = 1.7 * Math.max(0.0, 1.0 - resist);
            t.setDeltaMovement(fwd.x * push, 0.35 * Math.max(0.2, 1.0 - resist), fwd.z * push);
            t.hurtMarked = true;
        }
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 3f, 0.8f);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2f, 1.3f);
    }

    private static void teleport(LivingEntity caster, Vec3 p, float yaw) {
        if (caster instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, yaw, sp.getXRot());
        } else {
            caster.moveTo(p.x, p.y, p.z, yaw, caster.getXRot());
        }
        caster.setYHeadRot(yaw);
    }

    // ------------------------------------------------------------------ client
    private void clientTick() {
        Vec3 en = entryCenter(), nn = entryNormal();
        if (age <= 8) sparks(en, nn, 1.6, 3);
        if (age == T_VANISH) burst(en, 24);
        if (age >= T_VANISH - 1 && age <= 15) sparks(exitCenter(), exitNormal(), 1.6, 3);
        if (age == T_EMERGE) {
            burst(getDest().add(0, 1.0, 0), 28);
            if (wasHit()) burst(getImpact(), 22);
        }
    }

    /** Bright particles on the rim of a portal (vanilla layer; the renderer draws the real thing). */
    private void sparks(Vec3 c, Vec3 n, double r, int count) {
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = n.cross(u).normalize();
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 d = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 p = c.add(d.scale(r));
            level().addParticle(random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y, p.z, d.x * 0.12, d.y * 0.12, d.z * 0.12);
        }
    }

    private void burst(Vec3 c, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            double s = 0.15 + random.nextDouble() * 0.35;
            level().addParticle(i % 3 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, c.x, c.y, c.z, d.x * s, d.y * s, d.z * s);
        }
    }

    // ------------------------------------------------------------------ boilerplate
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256.0 * 256.0; }
}
