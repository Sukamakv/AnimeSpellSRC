package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.frieren.FrierenRegistry;
import com.example.jjkspells.frieren.spell.FrierenFlammesUltimateMagicSpell;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static com.example.jjkspells.frieren.spell.FrierenFlammesUltimateMagicSpell.*;

/**
 * Controller of Flamme's Ultimate Magic. Sits at the target point (its own position never changes) and runs the timeline:
 *   0..WINDUP        circle beneath the caster, circles in the sky, the sphere forms (client visuals only)
 *   launch(k)        lance k leaves the sphere (event synced to the client)       k = 0..4 every LANCE_INTERVAL, 5 = final
 *   launch(k)+FLIGHT lance k lands: its own damage pass; the final one is the big explosion
 * Every lance and the explosion run their OWN damage pass (own target list, hurt-frames reset), so a target in reach of
 * several of them is hit by each one separately.
 */
public class FlammeMagicEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(FlammeMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(FlammeMagicEntity.class, EntityDataSerializers.INT);
    /** {"ev": [ {k, x, y, z} ... ]} one entry per launched lance, x/y/z = impact point relative to this entity. */
    private static final EntityDataAccessor<CompoundTag> EVENTS = SynchedEntityData.defineId(FlammeMagicEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** Firing order of the five outer lances (a star pattern around the area, not a sweep). */
    public static final int[] ORDER = {0, 2, 4, 1, 3};

    private UUID ownerUuid;
    private final ListTag events = new ListTag();
    private final double[][] impact = new double[LANCES][3];

    /** Client cache of EVENTS: each {k, dx, dy, dz}. */
    public List<float[]> clientEvents = new ArrayList<>();
    public Object fx;

    public FlammeMagicEntity(EntityType<? extends FlammeMagicEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static FlammeMagicEntity create(Level level, LivingEntity owner) {
        FlammeMagicEntity e = new FlammeMagicEntity(FrierenRegistry.FLAMME_MAGIC, level);
        e.ownerUuid = owner.getUUID();
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(SEED, level.random.nextInt());
        Vec3 p = aim(level, owner, RANGE);
        e.setPos(p.x, p.y, p.z);
        return e;
    }

    // ------------------------------------------------------------------------------------------ geometry (shared with the client)

    /** Horizontal offset (dx, dz) from the target point of lance slot i (0..4); the same on server and client. */
    public static double[] lanceOffset(int seed, int i) {
        Random r = new Random(seed);
        double base = r.nextDouble() * Math.PI * 2;
        double[] out = null;
        for (int j = 0; j <= i; j++) {
            double ang = base + j * Math.PI * 2 / 5 + (r.nextDouble() - 0.5) * 0.5;
            double rad = 13 + r.nextDouble() * 10;
            out = new double[]{Math.cos(ang) * rad, Math.sin(ang) * rad};
        }
        return out;
    }

    /** Offset of lance k in firing order (k = 5 is the final lance, straight below the sphere). */
    public static double[] offsetOf(int seed, int k) {
        return k >= LANCES - 1 ? new double[]{0, 0} : lanceOffset(seed, ORDER[k]);
    }

    public static int launchTick(int k) {
        return k < LANCES - 1 ? WINDUP + k * LANCE_INTERVAL : WINDUP + (LANCES - 2) * LANCE_INTERVAL + FINAL_DELAY;
    }

    /** Where the caster is aiming (an entity or a block within range), brought down to the standing surface. */
    private static Vec3 aim(Level level, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult bhr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double blockDist = bhr.getType() == HitResult.Type.MISS ? range : bhr.getLocation().distanceTo(eye);
        HitResult hr = Utils.raycastForEntity(level, caster, (float) Math.min(range, blockDist), false, 0.3f);
        Vec3 p;
        if (hr instanceof EntityHitResult ehr) p = ehr.getEntity().position();
        else p = bhr.getType() == HitResult.Type.MISS ? end : bhr.getLocation();
        return new Vec3(p.x, groundAt(level, p.x, p.z, p.y + 1.0, 64), p.z);
    }

    /** Height of the standing surface below (x, refY): scans down up to maxDown blocks; refY itself when there is none. */
    public static double groundAt(Level level, double x, double z, double refY, int maxDown) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunkAt(new BlockPos(bx, Mth.floor(refY), bz))) return refY;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = Mth.floor(refY);
        for (int y = top; y > top - maxDown && y > level.getMinBuildHeight(); y--) {
            p.set(bx, y, bz);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
            p.set(bx, y - 1, bz);
            var shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty()) return (y - 1) + shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        return refY;
    }

    // ------------------------------------------------------------------------------------------ synched data

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(SEED, 0);
        this.entityData.define(EVENTS, new CompoundTag());
    }

    public int getOwnerId() { return entityData.get(OWNER_ID); }
    public int getSeed() { return entityData.get(SEED); }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == EVENTS) {
            ListTag l = entityData.get(EVENTS).getList("ev", 10);
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                out.add(new float[]{c.getInt("k"), c.getFloat("x"), c.getFloat("y"), c.getFloat("z")});
            }
            clientEvents = out;
        }
    }

    // ------------------------------------------------------------------------------------------ server timeline

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            com.example.jjkspells.frieren.client.FlammeFx.tick(this);
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity o = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        if (!(o instanceof LivingEntity owner) || !owner.isAlive() || tickCount >= END) {
            discard();
            return;
        }
        this.entityData.set(OWNER_ID, owner.getId());
        if (tickCount == 1) {
            sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6f, 1.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, owner.position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, 1.6f, WINDUP);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, position(), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, 0.8f, WINDUP);
        }
        if (tickCount == WINDUP - 10) {
            sl.playSound(null, getX(), getY() + SPHERE_HEIGHT, getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 3.0f, 1.8f);
            com.example.jjkspells.vfx.sprite.SpriteFx.glowOn(sl, this, SPHERE_HEIGHT, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, 6.0f, 10 + 10);
        }
        for (int k = 0; k < LANCES; k++) {
            if (tickCount == launchTick(k)) launch(sl, k);
            if (tickCount == launchTick(k) + FLIGHT) land(sl, owner, k);
        }
    }

    private void launch(ServerLevel sl, int k) {
        double[] off = offsetOf(getSeed(), k);
        double x = getX() + off[0], z = getZ() + off[1];
        double y = groundAt(sl, x, z, getY() + 12, 48);
        impact[k] = new double[]{x, y, z};
        CompoundTag c = new CompoundTag();
        c.putInt("k", k);
        c.putFloat("x", (float) (x - getX()));
        c.putFloat("y", (float) (y - getY()));
        c.putFloat("z", (float) (z - getZ()));
        events.add(c);
        CompoundTag all = new CompoundTag();
        all.put("ev", events.copy());
        entityData.set(EVENTS, all);
        sl.playSound(null, getX(), getY() + SPHERE_HEIGHT, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 4.0f, 0.6f);
        // telegraph: danger zone of this lance (life = flight time until damage)
        com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, k >= LANCES - 1 ? (float) (AREA_RADIUS * 2.0 / 7.0) : (float) (LANCE_RADIUS * 2.0 / 7.0), FLIGHT);
        com.example.jjkspells.vfx.sprite.SpriteFx.sparks(sl, new Vec3(getX(), getY() + SPHERE_HEIGHT, getZ()), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, 1.5f);
    }

    private void land(ServerLevel sl, LivingEntity owner, int k) {
        double[] p = impact[k];
        boolean last = k == LANCES - 1;
        Vec3 at = new Vec3(p[0], p[1], p[2]);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, last ? 5.0f : 2.2f, last ? 0.7f : 1.5f);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, last ? 5.0f : 2.5f, last ? 0.5f : 1.2f);
        if (last) sl.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 4.0f, 0.55f);
        com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, at, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, last ? 8.0f : 2.5f);
        com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, at, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, last ? 6.0f : 2.0f);
        com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, at, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.FIRE, last ? 4.5f : 1.3f);
        try {
            strike(sl, owner, at, last ? AREA_RADIUS : LANCE_RADIUS, last ? 12 : 8, last ? 28 : 16,
                    last ? FINAL_FLAT : LANCE_FLAT, last ? FINAL_PERCENT : LANCE_PERCENT);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("jjk_spells").error("flammes_ultimate_magic strike failed", t);
        }
    }

    /** One independent damage pass: flat (normal spell hit, knockback cancelled) then percent of max health (SpellDamage.percent). */
    private static void strike(ServerLevel sl, LivingEntity owner, Vec3 c, double r, double down, double up, float flat, float pct) {
        AABB box = new AABB(c.x - r, c.y - down, c.z - r, c.x + r, c.y + up, c.z + r).inflate(2.0);
        DamageSource src = FrierenRegistry.FLAMMES_ULTIMATE_MAGIC.getDamageSource(owner, owner);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(owner, e))) {
            AABB b = t.getBoundingBox();
            double dx = Math.max(Math.max(b.minX - c.x, 0), c.x - b.maxX), dz = Math.max(Math.max(b.minZ - c.z, 0), c.z - b.maxZ);
            if (dx * dx + dz * dz > r * r || b.maxY < c.y - down || b.minY > c.y + up) continue;
            Vec3 keep = t.getDeltaMovement();
            t.invulnerableTime = 0;
            DamageSources.applyDamage(t, flat, src);
            t.setDeltaMovement(keep);
            if (t.isAlive()) SpellDamage.percent(t, t.getMaxHealth() * pct, owner, src);
        }
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
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 192.0 * 192.0; }
    @Override public AABB getBoundingBoxForCulling() { return new AABB(getX() - 40, getY() - 10, getZ() - 40, getX() + 40, getY() + 70, getZ() + 40); }
}
