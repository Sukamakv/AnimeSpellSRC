package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.frieren.FrierenRegistry;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.jjkspells.frieren.spell.FrierenVollzanbelSpell.*;

/**
 * Controller of Vollzanbel.
 *   0..START          circle in front of the caster, runes, small flame (client visuals)
 *   START..FLAME_END  flamethrower: every 4 ticks everything inside the flame cone (with line of sight) is set burning
 *   FLAME_END         the aimed point is frozen (phase 1), flames converge into it for CONVERGE ticks
 *   EXPLODE           explosion (phase 2): EXPLOSION_RADIUS, initial damage, burning refreshed on everybody hit
 * Burning: BURN_FLAT + BURN_PERCENT of max health every BURN_PERIOD ticks, lasts BURN_TICKS after the last exposure.
 * Nothing here places fire or touches blocks.
 */
public class VollzanbelEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(VollzanbelEntity.class, EntityDataSerializers.INT);
    /** 0 = flamethrower, 1 = converging, 2 = exploded. The client starts the matching visuals when it changes. */
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VollzanbelEntity.class, EntityDataSerializers.INT);
    /** {x, y, z} absolute explosion point (valid from phase 1). */
    private static final EntityDataAccessor<CompoundTag> POINT = SynchedEntityData.defineId(VollzanbelEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** {"ids": int[]} entity ids that are burning right now. */
    private static final EntityDataAccessor<CompoundTag> BURNING = SynchedEntityData.defineId(VollzanbelEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    /** Server: burning entity -> {expiry tick, next damage tick}. */
    private final Map<UUID, int[]> burning = new HashMap<>();
    private int[] lastIds = new int[0];
    private Vec3 point = Vec3.ZERO;

    // client caches
    public int clientPhase;
    public double[] clientPoint;
    public int[] clientBurning = new int[0];
    public Object fx;

    public VollzanbelEntity(EntityType<? extends VollzanbelEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static VollzanbelEntity create(Level level, LivingEntity owner) {
        VollzanbelEntity e = new VollzanbelEntity(FrierenRegistry.VOLLZANBEL_ENTITY, level);
        e.ownerUuid = owner.getUUID();
        e.entityData.set(OWNER_ID, owner.getId());
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        return e;
    }

    /** Where the flame leaves the circle: just in front of the caster's hand, slightly below eye level. Same on both sides. */
    public static Vec3 origin(Vec3 eye, Vec3 look) {
        return eye.add(look.scale(2.6)).add(0, -0.35, 0);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(PHASE, 0);
        this.entityData.define(POINT, new CompoundTag());
        this.entityData.define(BURNING, new CompoundTag());
    }

    public int getOwnerId() { return entityData.get(OWNER_ID); }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == PHASE) clientPhase = entityData.get(PHASE);
        else if (key == POINT) {
            CompoundTag t = entityData.get(POINT);
            if (t.contains("x")) clientPoint = new double[]{t.getDouble("x"), t.getDouble("y"), t.getDouble("z")};
        } else if (key == BURNING) clientBurning = entityData.get(BURNING).getIntArray("ids");
    }

    // ------------------------------------------------------------------------------------------ server timeline

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            com.example.jjkspells.frieren.client.VollzanbelFx.tick(this);
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity o = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        if (!(o instanceof LivingEntity owner) || !owner.isAlive() || tickCount >= END) {
            discard();
            return;
        }
        int t = tickCount;
        int phase = entityData.get(PHASE);
        if (phase == 0) setPos(owner.getX(), owner.getY(), owner.getZ());
        entityData.set(OWNER_ID, owner.getId());

        if (t == 1) sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0f, 0.5f);
        if (t == START) sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3.0f, 0.5f);
        if (t >= START && t < FLAME_END && t % 10 == 0) {
            sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 3.0f, 0.6f);
        }

        // 1. flamethrower
        if (t >= START && t < FLAME_END && t % 4 == 0) {
            Vec3 look = owner.getLookAngle();
            Vec3 origin = origin(owner.getEyePosition(), look);
            double len = RANGE * Math.min(1.0, (t - START) / 8.0);
            AABB box = new AABB(origin, origin.add(look.scale(len))).inflate(CONE_BASE + CONE_SPREAD * len + 2.0);
            for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(owner, e))) {
                if (inFlame(sl, owner, origin, look, len, target)) ignite(target, t);
            }
        }

        // 2. freeze the aimed point and start converging
        if (t == FLAME_END) {
            Vec3 eye = owner.getEyePosition(), look = owner.getLookAngle();
            Vec3 end = eye.add(look.scale(RANGE));
            BlockHitResult hit = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            point = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(look.scale(1.5));
            CompoundTag p = new CompoundTag();
            p.putDouble("x", point.x);
            p.putDouble("y", point.y);
            p.putDouble("z", point.z);
            entityData.set(POINT, p);
            entityData.set(PHASE, 1);
            sl.playSound(null, point.x, point.y, point.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0f, 0.5f);
        }

        // 3. explosion
        if (t == EXPLODE) {
            entityData.set(PHASE, 2);
            explode(sl, owner);
        }

        // 4. burning
        for (Iterator<Map.Entry<UUID, int[]>> it = burning.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, int[]> en = it.next();
            Entity target = sl.getEntity(en.getKey());
            int[] st = en.getValue();
            if (!(target instanceof LivingEntity le) || !le.isAlive() || t >= st[0]) {
                it.remove();
                continue;
            }
            if (t >= st[1]) {
                st[1] += BURN_PERIOD;
                try {
                    damage(owner, le, BURN_FLAT, BURN_PERCENT);
                } catch (Throwable ex) {
                    org.slf4j.LoggerFactory.getLogger("jjk_spells").error("vollzanbel burn failed", ex);
                }
            }
        }
        if (t % 4 == 0) syncBurning(sl);
    }

    /** Inside the flame cone (grows with distance) and not behind a wall. */
    private boolean inFlame(ServerLevel sl, LivingEntity owner, Vec3 origin, Vec3 look, double len, LivingEntity target) {
        Vec3 c = target.getBoundingBox().getCenter();
        Vec3 v = c.subtract(origin);
        double d = v.dot(look);
        if (d < -0.5 || d > len + target.getBbWidth()) return false;
        double lateral = v.subtract(look.scale(d)).length();
        double allowed = CONE_BASE + CONE_SPREAD * Math.max(d, 0) + target.getBbWidth() * 0.5 + target.getBbHeight() * 0.25;
        if (lateral > allowed) return false;
        BlockHitResult wall = sl.clip(new ClipContext(origin, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return wall.getType() == HitResult.Type.MISS;
    }

    private void ignite(LivingEntity t, int now) {
        int[] st = burning.get(t.getUUID());
        if (st == null) burning.put(t.getUUID(), new int[]{now + BURN_TICKS, now + BURN_PERIOD});
        else st[0] = now + BURN_TICKS;
    }

    private void explode(ServerLevel sl, LivingEntity owner) {
        sl.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0f, 0.5f);
        sl.playSound(null, point.x, point.y, point.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8.0f, 0.6f);
        sl.playSound(null, point.x, point.y, point.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 6.0f, 0.5f);
        double r = EXPLOSION_RADIUS;
        AABB box = new AABB(point.x - r, point.y - r, point.z - r, point.x + r, point.y + r, point.z + r);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(owner, e))) {
            if (distanceToBox(point, t.getBoundingBox()) > r) continue;
            try {
                damage(owner, t, INITIAL_FLAT, INITIAL_PERCENT);
            } catch (Throwable ex) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("vollzanbel explosion failed", ex);
            }
            if (t.isAlive()) ignite(t, tickCount);
        }
    }

    /** One spell hit: flat (knockback cancelled, hurt-frames reset) then percent of max health through SpellDamage.percent. */
    private static void damage(LivingEntity owner, LivingEntity t, float flat, float pct) {
        DamageSource src = FrierenRegistry.VOLLZANBEL.getDamageSource(owner, owner);
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        DamageSources.applyDamage(t, flat, src);
        t.setDeltaMovement(keep);
        if (t.isAlive()) SpellDamage.percent(t, t.getMaxHealth() * pct, owner, src);
    }

    private void syncBurning(ServerLevel sl) {
        List<Integer> ids = new ArrayList<>();
        for (UUID u : burning.keySet()) {
            Entity e = sl.getEntity(u);
            if (e != null) ids.add(e.getId());
        }
        int[] a = ids.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(a);
        if (!Arrays.equals(a, lastIds)) {
            lastIds = a;
            CompoundTag c = new CompoundTag();
            c.putIntArray("ids", a);
            entityData.set(BURNING, c);
        }
    }

    private static double distanceToBox(Vec3 p, AABB b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Never the caster, allies, owned pets, the caster's summons, creative/spectator players, armor stands; PvP rules respected. */
    public static boolean isValidTarget(LivingEntity owner, Entity e) {
        if (!(e instanceof LivingEntity le) || e == owner || !le.isAlive() || le.isSpectator()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof Player p) {
            if (p.isCreative() || p.getAbilities().invulnerable) return false;
            if (owner instanceof Player op && !op.canHarmPlayer(p)) return false;
        }
        if (e instanceof OwnableEntity oe && owner.getUUID().equals(oe.getOwnerUUID())) return false;
        if (e instanceof IMagicSummon s && s.getSummoner() == owner) return false;
        if (owner.isAlliedTo(e)) return false;
        return !DamageSources.isFriendlyFireBetween(owner, e);
    }

    // ------------------------------------------------------------------------------------------ boilerplate

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 224.0 * 224.0; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(90.0); }
}
