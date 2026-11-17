package com.example.jjkspells.yhwach.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yhwach.YhwachRegistry;
import com.example.jjkspells.yhwach.spell.AlmightySpell;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controller of "The Almighty". Timeline (ticks since cast):
 * <pre>
 *   1 .. ZONE_TICKS              darkness zone (35 block circle following the caster): 2%/s + blindness, devours the dead,
 *                                waves of 3-6 eyed pillars firing 1-3 black lasers each
 *   ZONE_TICKS .. GATHER_END     the darkness is pulled into a small orb in front of the caster
 *   .. BEAM_START                orb charges (3 s after the zone ends)
 *   BEAM_START .. BEAM_END       giant beam, trails the caster's aim at 65% of the caster's turning speed
 *   .. TOTAL                     orb fades
 * </pre>
 * The damage cap on the caster lasts for the whole cast.
 */
public class AlmightyEntity extends Entity {
    public static final int ZONE_TICKS = AlmightySpell.ZONE_TICKS;
    public static final int GATHER_END = ZONE_TICKS + 26;
    public static final int BEAM_START = ZONE_TICKS + AlmightySpell.ORB_CHARGE_TICKS;
    public static final int BEAM_END = BEAM_START + AlmightySpell.BEAM_TICKS;
    public static final int TOTAL = BEAM_END + 24;
    public static final double PILLAR_H = 18.0;
    public static final int PILLAR_LIFE = 50;
    public static final int BEAM_LIFE = 8;
    public static final int CONSUME_LIFE = 56;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(AlmightyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> BEAM_DIR = SynchedEntityData.defineId(AlmightyEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> EVENTS = SynchedEntityData.defineId(AlmightyEntity.class, EntityDataSerializers.COMPOUND_TAG);

    public static final List<AlmightyEntity> ACTIVE = new CopyOnWriteArrayList<>();

    /** Client record of an event. type 0 = pillar rising, 1 = pillar beam, 2 = corpse devoured. */
    public static final class Fx {
        public final int id;
        public final int type;
        public final int aux;
        public final Vec3 pos;
        public final Vec3 pos2;
        public final int born;

        Fx(int id, int type, int aux, Vec3 pos, Vec3 pos2, int born) {
            this.id = id;
            this.type = type;
            this.aux = aux;
            this.pos = pos;
            this.pos2 = pos2;
            this.born = born;
        }
    }

    private static final class Pending {
        final int tick;
        final Vec3 from;
        final int pillar;

        Pending(int tick, Vec3 from, int pillar) {
            this.tick = tick;
            this.from = from;
            this.pillar = pillar;
        }
    }

    private UUID ownerUuid;
    private int nextEventId = 1;
    private int nextPillarId = 1;
    private int nextWave = 24;
    private Vec3 dir = new Vec3(0, 0, 1);
    private Vec3 prevLook = new Vec3(0, 0, 1);
    private final List<CompoundTag> events = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();

    // client
    public final List<Fx> clientFx = new ArrayList<>();
    private int clientLastId = 0;
    private Vec3 prevDir;
    private Vec3 curDir;

    public AlmightyEntity(EntityType<? extends AlmightyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static AlmightyEntity create(Level level, LivingEntity caster) {
        AlmightyEntity e = new AlmightyEntity(YhwachRegistry.ALMIGHTY, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        Vec3 look = caster.getLookAngle().normalize();
        e.dir = look;
        e.prevLook = look;
        e.entityData.set(BEAM_DIR, new Vector3f((float) look.x, (float) look.y, (float) look.z));
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(BEAM_DIR, new Vector3f(0, 0, 1));
        this.entityData.define(EVENTS, new CompoundTag());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    public int getOwnerId() {
        return this.entityData.get(OWNER_ID);
    }

    public boolean isOwner(Entity e) {
        return ownerUuid != null && ownerUuid.equals(e.getUUID());
    }

    @Override
    public void remove(RemovalReason reason) {
        ACTIVE.remove(this);
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    // ------------------------------------------------------------------ client accessors

    public Vec3 ownerEye(float pt) {
        Entity o = this.level().getEntity(getOwnerId());
        return o != null ? o.getEyePosition(pt) : position().add(0, 1.6, 0);
    }

    public Vec3 beamDir(float pt) {
        if (curDir == null || prevDir == null) {
            Vector3f v = this.entityData.get(BEAM_DIR);
            return new Vec3(v.x(), v.y(), v.z());
        }
        return prevDir.lerp(curDir, pt).normalize();
    }

    // ------------------------------------------------------------------ server

    private LivingEntity owner() {
        if (ownerUuid == null || !(this.level() instanceof ServerLevel sl)) {
            return null;
        }
        Entity e = sl.getEntity(ownerUuid);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private boolean valid(LivingEntity x, LivingEntity owner) {
        return x.isAlive() && !x.isSpectator() && x != owner && !(x instanceof ArmorStand) && !x.isAlliedTo(owner);
    }

    private void addEvent(int type, int aux, Vec3 pos, Vec3 pos2) {
        CompoundTag c = new CompoundTag();
        c.putInt("id", nextEventId++);
        c.putInt("t", type);
        c.putInt("a", aux);
        c.putDouble("x", pos.x);
        c.putDouble("y", pos.y);
        c.putDouble("z", pos.z);
        c.putDouble("x2", pos2.x);
        c.putDouble("y2", pos2.y);
        c.putDouble("z2", pos2.z);
        c.putInt("b", tickCount);
        events.add(c);
        syncEvents();
    }

    private void syncEvents() {
        events.removeIf(c -> tickCount - c.getInt("b") > 70);
        while (events.size() > 60) {
            events.remove(0);
        }
        ListTag l = new ListTag();
        l.addAll(events);
        CompoundTag root = new CompoundTag();
        root.put("l", l);
        this.entityData.set(EVENTS, root);
    }

    /** Flat part through Iron's damage, percent part through SpellDamage.percent (Max Health Damage / Resistance stats). Always irradiates. */
    private void hit(LivingEntity owner, LivingEntity t, double flat, double percent, boolean keepMotion) {
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource src = level().damageSources().indirectMagic(this, owner);
        if (flat > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, (float) flat, src);
        }
        if (t.isAlive() && percent > 0) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent / 100.0), owner, src);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.irradiate(t, 120);
        }
        if (keepMotion) {
            t.setDeltaMovement(keep);
        }
    }

    /** Used by SpellDamage.percent: while the Almighty runs, its caster cannot lose more than the cap in one hit. */
    public static float capFor(LivingEntity target, float amount) {
        if (ACTIVE.isEmpty()) {
            return amount;
        }
        for (AlmightyEntity a : ACTIVE) {
            if (a.isOwner(target)) {
                return (float) Math.min(amount, target.getMaxHealth() * AlmightySpell.DAMAGE_CAP);
            }
        }
        return amount;
    }

    public void onDeathNearby(LivingEntity dead) {
        if (tickCount >= ZONE_TICKS || tickCount < 1) {
            return;
        }
        LivingEntity owner = owner();
        if (owner == null || dead == owner) {
            return;
        }
        Vec3 c = position();
        double dx = dead.getX() - c.x;
        double dz = dead.getZ() - c.z;
        if (dx * dx + dz * dz > AlmightySpell.ZONE_RADIUS * AlmightySpell.ZONE_RADIUS || Math.abs(dead.getY() - c.y) > 24) {
            return;
        }
        addEvent(2, (int) (dead.getBbHeight() * 10), dead.position(), dead.position());
        owner.heal((float) (owner.getMaxHealth() * AlmightySpell.CONSUME_HEAL));
        level().playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 3.0f, 0.5f);
        level().playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 2.0f, 0.6f);
        if (level() instanceof ServerLevel asl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(asl, dead.position().add(0, dead.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f);   // devoured
        }
    }

    private void serverTick() {
        LivingEntity owner = owner();
        if (tickCount > TOTAL || (owner == null && tickCount > 2)) {
            discard();
            return;
        }
        if (owner == null) {
            return;
        }
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        Vec3 c = position();

        if (tickCount == 1) {
            ACTIVE.add(this);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 6.0f, 0.5f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 3.0f, 0.5f);
            if (level() instanceof ServerLevel asl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(asl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) (AlmightySpell.ZONE_RADIUS / 3.0));
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(asl, c.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3.0f);
            }
        }
        if (tickCount % 50 == 0 && tickCount < ZONE_TICKS) {
            level().playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 5.0f, 0.7f);
        }
        if (tickCount % 10 == 0) {
            syncEvents();
        }

        // direction the orb / beam points to
        Vec3 look = owner.getLookAngle().normalize();
        if (tickCount < ZONE_TICKS) {
            dir = look;
        } else {
            Vec3 dl = look.subtract(prevLook);
            dir = dir.add(dl.scale(AlmightySpell.BEAM_FOLLOW)).add(look.subtract(dir).scale(0.02));
            if (dir.lengthSqr() < 1.0E-6) {
                dir = look;
            }
            dir = dir.normalize();
        }
        prevLook = look;
        this.entityData.set(BEAM_DIR, new Vector3f((float) dir.x, (float) dir.y, (float) dir.z));

        double R = AlmightySpell.ZONE_RADIUS;
        if (tickCount < ZONE_TICKS) {
            zonePhase(owner, c, R);
        } else if (tickCount == ZONE_TICKS) {
            level().playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 6.0f, 0.6f);
        }
        if (tickCount == BEAM_START) {
            level().playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 8.0f, 0.5f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 6.0f, 0.5f);
            if (level() instanceof ServerLevel asl) {
                Vec3 bo = owner.getEyePosition().add(0, -0.5, 0).add(dir.scale(2.8));
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(asl, bo, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 5.0f);   // beam released
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(asl, bo, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 4.0f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(asl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 3.0f);
            }
        }
        if (tickCount >= BEAM_START && tickCount < BEAM_END) {
            if (tickCount % 30 == 0) {
                level().playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 6.0f, 0.5f);
            }
            if (tickCount % 5 == 0) {
                beamDamage(owner);
            }
        }
    }

    private void zonePhase(LivingEntity owner, Vec3 c, double R) {
        AABB box = new AABB(c.x - R, c.y - 24, c.z - R, c.x + R, c.y + 24, c.z + R);
        if (tickCount % 10 == 0 || tickCount % 20 == 0) {
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box, x -> valid(x, owner))) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                if (dx * dx + dz * dz > R * R) {
                    continue;
                }
                if (tickCount % 10 == 0) {
                    t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, AlmightySpell.BLIND_TICKS, 0, false, false, false));
                }
                if (tickCount % 20 == 0) {
                    hit(owner, t, 0.0, AlmightySpell.ZONE_PERCENT_PER_SECOND, true);
                }
            }
        }
        if (tickCount >= nextWave && tickCount < ZONE_TICKS - 24) {
            wave(owner, c);
            nextWave = tickCount + AlmightySpell.WAVE_MIN_TICKS + random.nextInt(AlmightySpell.WAVE_MAX_TICKS - AlmightySpell.WAVE_MIN_TICKS + 1);
        }
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (tickCount >= p.tick) {
                it.remove();
                fire(owner, c, p);
            }
        }
    }

    private void wave(LivingEntity owner, Vec3 c) {
        int count = AlmightySpell.WAVE_MIN_PILLARS + random.nextInt(AlmightySpell.WAVE_MAX_PILLARS - AlmightySpell.WAVE_MIN_PILLARS + 1);
        for (int i = 0; i < count; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double rad = 8.0 + random.nextDouble() * (AlmightySpell.ZONE_RADIUS - 10.0);
            double x = c.x + Math.cos(ang) * rad;
            double z = c.z + Math.sin(ang) * rad;
            double y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - c.y) > 10) {
                y = c.y;
            }
            int id = nextPillarId++;
            addEvent(0, id, new Vec3(x, y, z), new Vec3(x, y, z));
            level().playSound(null, x, y, z, SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 3.0f, 0.6f);
            int beams = AlmightySpell.PILLAR_MIN_BEAMS + random.nextInt(AlmightySpell.PILLAR_MAX_BEAMS - AlmightySpell.PILLAR_MIN_BEAMS + 1);
            Vec3 eye = new Vec3(x, y + PILLAR_H + 1.5, z);
            for (int j = 0; j < beams; j++) {
                pending.add(new Pending(tickCount + 14 + j * 3, eye, id));
            }
        }
    }

    private void fire(LivingEntity owner, Vec3 c, Pending p) {
        double r = AlmightySpell.PILLAR_RANGE;
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        List<LivingEntity> cands = level().getEntitiesOfClass(LivingEntity.class, box, x -> valid(x, owner) && x.distanceToSqr(c.x, c.y, c.z) <= r * r);
        if (cands.isEmpty()) {
            return;
        }
        LivingEntity t = cands.get(random.nextInt(cands.size()));
        hit(owner, t, AlmightySpell.PILLAR_FLAT, AlmightySpell.PILLAR_PERCENT, true);
        Vec3 tp = t.position().add(0, t.getBbHeight() * 0.5, 0);
        addEvent(1, p.pillar, p.from, tp);
        level().playSound(null, p.from.x, p.from.y, p.from.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 3.0f, 0.7f);
        level().playSound(null, tp.x, tp.y, tp.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 1.2f);
        if (level() instanceof ServerLevel asl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.beam(asl, p.from, tp, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.0f, BEAM_LIFE);   // pillar laser
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(asl, tp, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2.0f);
        }
    }

    private void beamDamage(LivingEntity owner) {
        Vec3 o = owner.getEyePosition().add(0, -0.5, 0).add(dir.scale(2.8));
        Vec3 e = o.add(dir.scale(AlmightySpell.BEAM_LENGTH));
        double rad = AlmightySpell.BEAM_RADIUS;
        AABB box = new AABB(o, e).inflate(rad + 2.0);
        Vec3 ab = e.subtract(o);
        double len2 = ab.lengthSqr();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box, x -> valid(x, owner))) {
            Vec3 pc = t.position().add(0, t.getBbHeight() * 0.5, 0);
            double s = Mth.clamp(pc.subtract(o).dot(ab) / len2, 0.0, 1.0);
            Vec3 closest = o.add(ab.scale(s));
            double reach = rad + t.getBbWidth() * 0.5;
            if (closest.distanceToSqr(pc) <= reach * reach) {
                hit(owner, t, 0.0, AlmightySpell.BEAM_PERCENT_PER_SECOND / 4.0, true);
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        Vector3f v = this.entityData.get(BEAM_DIR);
        Vec3 d = new Vec3(v.x(), v.y(), v.z());
        if (curDir == null) {
            prevDir = d;
            curDir = d;
        } else {
            prevDir = curDir;
            curDir = d;
        }
        CompoundTag root = this.entityData.get(EVENTS);
        if (root.contains("l", Tag.TAG_LIST)) {
            ListTag l = root.getList("l", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                int id = c.getInt("id");
                if (id > clientLastId) {
                    clientLastId = id;
                    Fx fx = new Fx(id, c.getInt("t"), c.getInt("a"), new Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")),
                            new Vec3(c.getDouble("x2"), c.getDouble("y2"), c.getDouble("z2")), tickCount);
                    clientFx.add(fx);
                    if (fx.type == 1) {
                        for (int j = 0; j < 10; j++) {
                            level().addParticle(ParticleTypes.SQUID_INK, fx.pos2.x + (random.nextDouble() - 0.5), fx.pos2.y + (random.nextDouble() - 0.5), fx.pos2.z + (random.nextDouble() - 0.5),
                                    (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.3);
                        }
                    }
                }
            }
        }
        for (Iterator<Fx> it = clientFx.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            int life = f.type == 0 ? PILLAR_LIFE : f.type == 1 ? BEAM_LIFE : CONSUME_LIFE;
            if (tickCount - f.born > life + 2) {
                it.remove();
            }
        }
        if (tickCount < ZONE_TICKS) {
            Vec3 c = position();
            for (int i = 0; i < 3; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(random.nextDouble()) * AlmightySpell.ZONE_RADIUS;
                level().addParticle(ParticleTypes.SQUID_INK, c.x + Math.cos(ang) * rad, c.y + 0.3, c.z + Math.sin(ang) * rad, 0, 0.04 + random.nextDouble() * 0.06, 0);
            }
        }
    }
}
