package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yasuo.YasuoRegistry;
import com.example.jjkspells.yasuo.spell.LastBreathSpell;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of "Last Breath". Phases:
 * <pre>
 *   1 .. T0               enemies are lifted LIFT blocks and frozen in the air, Yasuo hangs still
 *   T0 + STEP * k         blink k: Yasuo teleports past target k and cuts it; the log records start, end and target id
 *   F = T0 + STEP * n + 10 the mark detonates: every target is hit and slammed down, Yasuo returns to where he cast
 *   F .. F + FADE         visuals only
 * </pre>
 */
public class LastBreathEntity extends Entity {
    public static final int T0 = 12;
    public static final int STEP = LastBreathSpell.STEP;
    public static final int FADE = 26;
    public static final int MAX_END = T0 + LastBreathSpell.MAX_TARGETS * STEP + 10 + FADE;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(LastBreathEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(LastBreathEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final List<UUID> targets = new ArrayList<>();
    private final Map<UUID, Double> baseY = new HashMap<>();
    private final Map<UUID, Boolean> noGrav = new HashMap<>();
    private boolean ownerNoGrav;
    private final List<Integer> dashLog = new ArrayList<>();
    private final List<Integer> idLog = new ArrayList<>();
    private int origin[] = new int[3];
    private int finalTick = -1;
    private boolean released = false;

    private List<int[]> clientDashes = new ArrayList<>();
    private int[] clientTargets = new int[0];
    private int clientFinal = -1;
    private Vec3 clientOrigin = Vec3.ZERO;

    public LastBreathEntity(EntityType<? extends LastBreathEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    private static boolean enemy(LivingEntity t, LivingEntity owner) {
        if (t == owner || !t.isAlive() || t.isSpectator() || t instanceof ArmorStand) {
            return false;
        }
        if (owner != null) {
            if (owner.isAlliedTo(t)) {
                return false;
            }
            if (t instanceof TamableAnimal ta && ta.isOwnedBy(owner)) {
                return false;
            }
        }
        return true;
    }

    /** Collects the nearest enemies; null when there are none. */
    public static LastBreathEntity create(Level level, LivingEntity caster) {
        double r = LastBreathSpell.RANGE;
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(r, 6, r))) {
            if (enemy(t, caster) && t.distanceToSqr(caster) <= r * r) {
                found.add(t);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        found.sort(Comparator.comparingDouble(t -> t.distanceToSqr(caster)));
        LastBreathEntity e = new LastBreathEntity(YasuoRegistry.BREATH, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        int n = Math.min(found.size(), LastBreathSpell.MAX_TARGETS);
        for (int i = 0; i < n; i++) {
            LivingEntity t = found.get(i);
            e.targets.add(t.getUUID());
            e.idLog.add(t.getId());
            e.baseY.put(t.getUUID(), t.getY());
        }
        e.origin = new int[]{(int) Math.round(caster.getX() * 10), (int) Math.round(caster.getY() * 10), (int) Math.round(caster.getZ() * 10)};
        e.finalTick = T0 + STEP * n + 10;
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.sync();
        return e;
    }

    private void sync() {
        CompoundTag tag = new CompoundTag();
        int[] d = new int[dashLog.size()];
        for (int i = 0; i < d.length; i++) {
            d[i] = dashLog.get(i);
        }
        int[] t = new int[idLog.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = idLog.get(i);
        }
        tag.putIntArray("d", d);
        tag.putIntArray("t", t);
        tag.putIntArray("o", origin);
        tag.putInt("f", finalTick);
        this.entityData.set(LOG, tag);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(LOG, new CompoundTag());
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

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: blinks so far, each {sx, sy, sz, ex, ey, ez (* 10), tick, targetId}. */
    public List<int[]> dashes() {
        return clientDashes;
    }

    /** Client: ids of the marked enemies. */
    public int[] targetIds() {
        return clientTargets;
    }

    /** Client: tick the mark detonates on (known from the start). */
    public int finalTick() {
        return clientFinal;
    }

    /** Client: where Yasuo cast. */
    public Vec3 originPos() {
        return clientOrigin;
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

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            release();
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ server

    private void release() {
        if (released || !(level() instanceof ServerLevel sl)) {
            return;
        }
        released = true;
        for (UUID id : targets) {
            Entity e = sl.getEntity(id);
            if (e != null) {
                e.setNoGravity(noGrav.getOrDefault(id, false));
            }
        }
        Entity o = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        if (o != null) {
            o.setNoGravity(ownerNoGrav);
        }
    }

    private void hit(LivingEntity t, LivingEntity owner, double flat, double percent) {
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        float flatDmg = (float) (flat);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, dsrc);
        }
    }

    private boolean free(LivingEntity owner, Vec3 c) {
        AABB bb = owner.getBoundingBox().move(c.subtract(owner.position()));
        return level().noCollision(owner, bb);
    }

    private void tp(LivingEntity e, Vec3 p, float yaw) {
        if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, yaw, e.getXRot());
        } else {
            e.moveTo(p.x, p.y, p.z, yaw, e.getXRot());
        }
    }

    private void serverTick() {
        if (tickCount > finalTick + FADE || tickCount > MAX_END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (owner == null) {
            release();
            discard();
            return;
        }
        Vec3 c = owner.position();
        if (tickCount == 1) {
            ownerNoGrav = owner.isNoGravity();
            for (UUID id : targets) {
                Entity e = sl.getEntity(id);
                if (e != null) {
                    noGrav.put(id, e.isNoGravity());
                    com.example.jjkspells.vfx.sprite.SpriteFx.vortex(sl, e.position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1f, T0);
                }
            }
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 3.0f, 1.0f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 1.4f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f, T0);
            com.example.jjkspells.vfx.sprite.SpriteFx.glowOn(sl, owner, 1.0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2f, T0);
        }
        if (tickCount >= finalTick) {
            if (tickCount == finalTick) {
                detonate(sl, owner);
            }
            return;
        }
        // Yasuo hangs still, targets are lifted and held
        owner.setNoGravity(true);
        owner.fallDistance = 0;
        owner.invulnerableTime = Math.max(owner.invulnerableTime, 6);
        if (tickCount <= T0 || (tickCount - T0) % STEP != 0) {
            owner.setDeltaMovement(0, 0, 0);
            owner.hurtMarked = true;
        }
        for (UUID id : targets) {
            Entity e = sl.getEntity(id);
            if (!(e instanceof LivingEntity t) || !t.isAlive()) {
                continue;
            }
            t.setNoGravity(true);
            t.fallDistance = 0;
            double want = baseY.get(id) + LastBreathSpell.LIFT;
            double vy = t.getY() < want - 0.05 ? Math.min(0.7, (want - t.getY()) * 0.4 + 0.1) : 0.0;
            t.setDeltaMovement(0, vy, 0);
            t.hurtMarked = true;
        }
        if (tickCount < T0 || (tickCount - T0) % STEP != 0) {
            return;
        }
        int k = (tickCount - T0) / STEP;
        if (k >= targets.size()) {
            return;
        }
        Entity te = sl.getEntity(targets.get(k));
        if (!(te instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        Vec3 start = owner.position();
        Vec3 tp = target.position();
        Vec3 to = new Vec3(tp.x - start.x, 0, tp.z - start.z);
        Vec3 nd = to.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0, owner.getYRot()) : to.normalize();
        Vec3 end = start;
        Vec3[] tries = {tp.add(nd.scale(2.2)), tp.add(nd.scale(1.3)), tp.add(nd.scale(-2.0)), tp.add(nd.scale(-1.2))};
        for (Vec3 cand : tries) {
            if (free(owner, cand)) {
                end = cand;
                break;
            }
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(tp.x - end.x), tp.z - end.z));
        tp(owner, end, yaw);
        owner.setDeltaMovement(0, 0, 0);
        owner.hurtMarked = true;
        Vec3 keep = target.getDeltaMovement();
        hit(target, owner, LastBreathSpell.CUT_FLAT, LastBreathSpell.CUT_PERCENT);
        target.setDeltaMovement(0, keep.y, 0);
        target.hurtMarked = true;
        level().playSound(null, end.x, end.y + 1, end.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, 1.5f);
        level().playSound(null, end.x, end.y + 1, end.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.5f, 1.2f);
        level().playSound(null, start.x, start.y + 1, start.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 1.7f);
        Vec3 blinkSeg = end.subtract(start);
        com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, start.add(end).scale(0.5).add(0, 1.0, 0), new Vec3(-nd.z, 0, nd.x), 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) Math.max(2.5, blinkSeg.length() * 0.8));
        com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, end.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.8f);
        com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, target.position().add(0, target.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.8f);
        dashLog.add((int) Math.round(start.x * 10));
        dashLog.add((int) Math.round(start.y * 10));
        dashLog.add((int) Math.round(start.z * 10));
        dashLog.add((int) Math.round(end.x * 10));
        dashLog.add((int) Math.round(end.y * 10));
        dashLog.add((int) Math.round(end.z * 10));
        dashLog.add(tickCount);
        dashLog.add(target.getId());
        sync();
    }

    private void detonate(ServerLevel sl, LivingEntity owner) {
        Vec3 o = new Vec3(origin[0] / 10.0, origin[1] / 10.0, origin[2] / 10.0);
        // release gravity first so the slam sticks
        for (UUID id : targets) {
            Entity e = sl.getEntity(id);
            if (e != null) {
                e.setNoGravity(noGrav.getOrDefault(id, false));
            }
        }
        for (UUID id : targets) {
            Entity e = sl.getEntity(id);
            if (e instanceof LivingEntity t && t.isAlive()) {
                hit(t, owner, LastBreathSpell.FINAL_FLAT, LastBreathSpell.FINAL_PERCENT);
                t.setDeltaMovement(0, -1.4, 0);
                t.hurtMarked = true;
                t.fallDistance = 0;
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 1.5f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2.2f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, t.position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.2f);
            }
        }
        owner.setNoGravity(ownerNoGrav);
        released = true;
        Vec3 back = o;
        if (!free(owner, back)) {
            back = owner.position();
        }
        tp(owner, back, owner.getYRot());
        owner.setDeltaMovement(0, 0, 0);
        owner.hurtMarked = true;
        owner.fallDistance = 0;
        level().playSound(null, back.x, back.y + 2, back.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.8f);
        level().playSound(null, back.x, back.y + 2, back.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 1.0f);
        com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, back, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2.2f);
        com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, back.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3.5f);
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        clientTargets = tag.getIntArray("t");
        clientFinal = tag.contains("f") ? tag.getInt("f") : -1;
        int[] o = tag.getIntArray("o");
        if (o.length == 3) {
            clientOrigin = new Vec3(o[0] / 10.0, o[1] / 10.0, o[2] / 10.0);
        }
        int[] d = tag.getIntArray("d");
        if (d.length / 8 != clientDashes.size()) {
            clientDashes = new ArrayList<>();
            for (int i = 0; i + 7 < d.length; i += 8) {
                clientDashes.add(new int[]{d[i], d[i + 1], d[i + 2], d[i + 3], d[i + 4], d[i + 5], d[i + 6], d[i + 7]});
            }
            if (!clientDashes.isEmpty()) {
                int[] l = clientDashes.get(clientDashes.size() - 1);
                for (int i = 0; i < 16; i++) {
                    double f = random.nextDouble();
                    level().addParticle(ParticleTypes.CLOUD, (l[0] + (l[3] - l[0]) * f) / 10.0, (l[1] + (l[4] - l[1]) * f) / 10.0 + 1 + (random.nextDouble() - 0.5),
                            (l[2] + (l[5] - l[2]) * f) / 10.0, (random.nextDouble() - 0.5) * 0.2, 0.02, (random.nextDouble() - 0.5) * 0.2);
                }
            }
        }
    }
}
