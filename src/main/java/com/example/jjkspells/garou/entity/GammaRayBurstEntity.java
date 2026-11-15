package com.example.jjkspells.garou.entity;

import com.example.jjkspells.garou.GarouEvents;
import com.example.jjkspells.garou.GarouRegistry;
import com.example.jjkspells.garou.spell.GarouGammaRayBurstSpell;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.Map;

/**
 * GAMMA RAY BURST controller. Non-living, no physics. Server runs the timeline + damage; clients only read the synced
 * state (phase clock, locked direction, beam length, event list) and draw it in GammaRayBurstRenderer.
 *
 * Timeline (ticks since spawn):
 *   0 .. T_FORM      dark sphere grows between the hands
 *   .. T_COMPRESS    sphere compresses, rings spin up, caster is rooted
 *   .. T_TWIN        two beams stream out of the sphere and converge into one (direction locks at the start)
 *   .. T_BEAM_END    merged catastrophic beam, damage ticks
 *   .. T_END         beam thins out and fades
 */
public class GammaRayBurstEntity extends Entity {
    public static final int T_FORM = 24;
    public static final int T_COMPRESS = T_FORM + 20;
    public static final int T_TWIN = T_COMPRESS + 10;
    public static final int T_BEAM_END = T_TWIN + GarouGammaRayBurstSpell.DURATION_TICKS;
    public static final int T_END = T_BEAM_END + 14;

    private static final EntityDataAccessor<Integer> CASTER = SynchedEntityData.defineId(GammaRayBurstEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(GammaRayBurstEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(GammaRayBurstEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(GammaRayBurstEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(GammaRayBurstEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> EVENTS = SynchedEntityData.defineId(GammaRayBurstEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** Event types written into EVENTS (client reads each new id once). */
    public static final int EV_IGNITE = 1;      // pos = origin
    public static final int EV_BLAST = 2;       // pos = secondary explosion on a block / enemy
    public static final int EV_END_BLAST = 3;   // pos = where the beam stops

    public int age;                       // ticks, on both sides
    private boolean locked;
    private int nextEventId = 1;
    private int lastSeenEvent = 0;        // client side
    private final Map<Integer, Integer> tickHits = new HashMap<>();   // entity id -> continuous hits taken
    private final java.util.Set<Integer> impacted = new java.util.HashSet<>();  // entity ids that took the opening impact

    public GammaRayBurstEntity(EntityType<? extends GammaRayBurstEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static GammaRayBurstEntity create(Level level, LivingEntity caster, GarouGammaRayBurstSpell spell) {
        GammaRayBurstEntity e = new GammaRayBurstEntity(GarouRegistry.GAMMA_RAY_BURST, level);
        e.entityData.set(CASTER, caster.getId());
        e.followCaster(caster);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(CASTER, -1);
        entityData.define(DIR_X, 0f);
        entityData.define(DIR_Y, 0f);
        entityData.define(DIR_Z, 1f);
        entityData.define(LENGTH, GarouGammaRayBurstSpell.RANGE);
        entityData.define(EVENTS, new CompoundTag());
    }

    public int getCasterId() { return entityData.get(CASTER); }
    public Vec3 getDir() { return new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z)); }
    public float getLength() { return entityData.get(LENGTH); }
    public boolean isLocked() { return locked || age >= T_TWIN; }

    /** While true the caster cannot move (server: motion zeroed; client: input zeroed by the client hook). */
    public boolean holdsCaster() { return age < T_BEAM_END + 4; }

    /** Smooth 0..1 progress helper for renderers. */
    public static float prog(float t, float a, float b) { return Mth.clamp((t - a) / (b - a), 0f, 1f); }

    private void followCaster(LivingEntity c) {
        Vec3 look = c.getLookAngle().normalize();
        // Between the hands: a little ahead of the eyes, a little below.
        Vec3 o = c.getEyePosition().add(look.scale(1.6)).add(0, -0.35, 0);
        setPos(o.x, o.y, o.z);
        entityData.set(DIR_X, (float) look.x);
        entityData.set(DIR_Y, (float) look.y);
        entityData.set(DIR_Z, (float) look.z);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        GarouEvents.ACTIVE.add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        GarouEvents.ACTIVE.remove(this);
        super.onRemovedFromWorld();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { clientTick(); age++; return; }
        serverTick();
        age++;
    }

    // ------------------------------------------------------------------ server
    private void serverTick() {
        ServerLevel sl = (ServerLevel) level();
        Entity ce = sl.getEntity(getCasterId());
        LivingEntity caster = ce instanceof LivingEntity l ? l : null;
        if (caster == null || !caster.isAlive()) { discard(); return; }

        if (holdsCaster()) {
            // Planted: no walking, no jumping, no falling drift.
            Vec3 m = caster.getDeltaMovement();
            caster.setDeltaMovement(0, Math.min(m.y, 0), 0);
            caster.hurtMarked = true;
        }

        if (age < T_TWIN) {
            followCaster(caster);
        } else if (!locked) {
            locked = true;
            addEvent(EV_IGNITE, position());
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 6f, 0.5f);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8f, 0.6f);
        }

        if (age == 2) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 4f, 0.5f);
        if (age == T_FORM) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2f, 1.6f);

        if (age >= T_TWIN && age < T_BEAM_END) {
            Vec3 dir = getDir();
            Vec3 start = position();
            float len = clipLength(sl, start, dir);
            entityData.set(LENGTH, len);
            int beamAge = age - T_TWIN;
            // The beam only "arrives" at full length over its first ticks (the merged beam travels forward).
            float reach = Math.min(len, Math.max(8f, len * Math.min(1f, (beamAge + 1) / 8f)));
            damageStep(sl, caster, start, dir, reach, beamAge);
            if (beamAge % 6 == 0) {
                Vec3 end = start.add(dir.scale(len));
                addEvent(EV_BLAST, end.add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6));
            }
            if (beamAge % 20 == 0) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6f, 0.5f);
        }
        if (age == T_BEAM_END) {
            Vec3 end = position().add(getDir().scale(getLength()));
            addEvent(EV_END_BLAST, end);
        }
        if (age >= T_END) discard();
    }

    /** Beam length: 80 blocks, shortened by the first solid block on the centre line. */
    private float clipLength(ServerLevel sl, Vec3 start, Vec3 dir) {
        float range = GarouGammaRayBurstSpell.RANGE;
        Vec3 end = start.add(dir.scale(range));
        var hit = sl.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return range;
        return (float) Math.max(2.0, hit.getLocation().distanceTo(start));
    }

    private void damageStep(ServerLevel sl, LivingEntity caster, Vec3 start, Vec3 dir, float reach, int beamAge) {
        boolean tickNow = beamAge % GarouGammaRayBurstSpell.INTERVAL_TICKS == 0;
        Vec3 end = start.add(dir.scale(reach));
        double r = GarouGammaRayBurstSpell.WIDTH * 0.5;
        AABB box = new AABB(start, end).inflate(r + 1.0);
        DamageSource src = damageSource(caster);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (t == caster || t.isAlliedTo(caster) || DamageSources.isFriendlyFireBetween(caster, t)) continue;
            Vec3 c = t.getBoundingBox().getCenter();
            double along = c.subtract(start).dot(dir);
            if (along < -1.0 || along > reach + 1.0) continue;
            Vec3 closest = start.add(dir.scale(Mth.clamp(along, 0, reach)));
            if (c.distanceTo(closest) > r + t.getBbWidth() * 0.5) continue;

            int id = t.getId();
            if (impacted.add(id)) {
                // Opening impact: once per target per cast.
                hit(t, caster, src, GarouGammaRayBurstSpell.INITIAL_FLAT, GarouGammaRayBurstSpell.INITIAL_PERCENT, dir);
                addEvent(EV_BLAST, c);
            } else if (tickNow) {
                int n = tickHits.getOrDefault(id, 0);
                if (n >= GarouGammaRayBurstSpell.MAX_TICK_HITS) continue;   // hard cap: no unlimited damage on one target
                tickHits.put(id, n + 1);
                hit(t, caster, src, GarouGammaRayBurstSpell.TICK_FLAT, GarouGammaRayBurstSpell.TICK_PERCENT, dir);
                if (n % 2 == 0) addEvent(EV_BLAST, c);
            }
        }
    }

    private static GarouGammaRayBurstSpell spellInstance;

    /** One spell instance is enough just to build the damage source (it carries the school's damage type). */
    private static DamageSource damageSource(LivingEntity caster) {
        if (spellInstance == null) spellInstance = new GarouGammaRayBurstSpell();
        return spellInstance.getDamageSource(caster);
    }

    private void hit(LivingEntity t, LivingEntity caster, DamageSource src, float flat, float percent, Vec3 dir) {
        Vec3 keep = t.getDeltaMovement();            // cancel the hurt() knockback, then push along the beam ourselves
        t.invulnerableTime = 0;
        DamageSources.applyDamage(t, flat, src);     // flat part: not scaled by max_hp_damage
        t.setDeltaMovement(keep);
        SpellDamage.percent(t, t.getMaxHealth() * percent, caster, src);   // % part: scaled/resisted by the mod stats
        if (t.isAlive()) SpellDamage.irradiate(t, 120);
        t.setDeltaMovement(keep.add(dir.scale(0.25)).add(0, 0.05, 0));
        t.hurtMarked = true;
    }

    private void addEvent(int type, Vec3 p) {
        CompoundTag all = entityData.get(EVENTS).copy();
        ListTag list = all.getList("e", Tag.TAG_COMPOUND);
        CompoundTag ev = new CompoundTag();
        ev.putInt("id", nextEventId++);
        ev.putInt("t", type);
        ev.putDouble("x", p.x); ev.putDouble("y", p.y); ev.putDouble("z", p.z);
        list.add(ev);
        while (list.size() > 24) list.remove(0);   // keep the synced tag small
        all.put("e", list);
        entityData.set(EVENTS, all);
    }

    // ------------------------------------------------------------------ client
    private void clientTick() {
        // Read each new event exactly once.
        ListTag list = entityData.get(EVENTS).getList("e", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ev = list.getCompound(i);
            int id = ev.getInt("id");
            if (id <= lastSeenEvent) continue;
            lastSeenEvent = id;
            clientEvents.add(new ClientEvent(ev.getInt("t"), new Vec3(ev.getDouble("x"), ev.getDouble("y"), ev.getDouble("z")), age));
        }
        // Sparse vanilla particle seasoning around the sphere (renderer does the real visuals).
        if (age < T_TWIN && age % 2 == 0) {
            double a = random.nextDouble() * Math.PI * 2, b = (random.nextDouble() - 0.5) * Math.PI;
            double rr = 2.5 - 1.5 * prog(age, 0, T_COMPRESS);
            level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    getX() + Math.cos(a) * Math.cos(b) * rr, getY() + Math.sin(b) * rr, getZ() + Math.sin(a) * Math.cos(b) * rr,
                    0, 0, 0);
        }
        clientEvents.removeIf(e -> age - e.tick > 40);
    }

    /** Client-only list the renderer draws (bursts / shockwaves). */
    public final java.util.List<ClientEvent> clientEvents = new java.util.ArrayList<>();
    public record ClientEvent(int type, Vec3 pos, int tick) {}

    // ------------------------------------------------------------------ boilerplate
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { }

    /** Never persisted: a half-finished burst is simply gone after a reload. */
    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 256.0 * 256.0; }
}
