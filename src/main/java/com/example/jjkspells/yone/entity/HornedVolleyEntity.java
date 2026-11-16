package com.example.jjkspells.yone.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yone.YoneRegistry;
import com.example.jjkspells.yone.spell.HornedVolleySpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of "Horned Volley". The entity sits where Yone stands; the facing is fixed at cast.
 * <pre>
 *   1 .. FIRE_START          the mask rises and the swords slide out and form the horns
 *   FIRE_START + 2 * i       sword i leaves its slot and flies FLIGHT ticks at the enemy with the fewest swords (nearest first), or forward if nobody is near
 *   launch + FLIGHT          the sword lands and stays buried
 *   FIN                      every buried sword explodes
 * </pre>
 * Launches are logged {index, tick, targetId (-1 for none), fallback x, y, z (* 10)}.
 */
public class HornedVolleyEntity extends Entity {
    public static final int FIRE_START = HornedVolleySpell.FIRE_START;
    public static final int FLIGHT = HornedVolleySpell.FLIGHT;
    public static final int FIN = FIRE_START + (HornedVolleySpell.SWORDS - 1) * HornedVolleySpell.FIRE_INTERVAL + FLIGHT + 10;
    public static final int END = FIN + 22;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HornedVolleyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(HornedVolleyEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(HornedVolleyEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final class Sword {
        int idx;
        int launch;
        UUID target;
        Vec3 fallback;
        boolean landed;
    }

    private UUID ownerUuid;
    private final List<Sword> swords = new ArrayList<>();
    private final Map<UUID, Integer> perTarget = new HashMap<>();
    private final List<Integer> launchLog = new ArrayList<>();
    private List<int[]> clientLaunches = new ArrayList<>();

    public HornedVolleyEntity(EntityType<? extends HornedVolleyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HornedVolleyEntity create(Level level, LivingEntity caster) {
        HornedVolleyEntity e = new HornedVolleyEntity(YoneRegistry.VOLLEY, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 look = caster.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : dir.normalize();
        e.entityData.set(DIR, new Vector3f((float) dir.x, 0f, (float) dir.z));
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(DIR, new Vector3f(0, 0, 1));
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

    public Vec3 dir() {
        Vector3f v = this.entityData.get(DIR);
        return new Vec3(v.x(), 0, v.z());
    }

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: launches so far, each {index, tick, targetId, fallback x, y, z (* 10)}. */
    public List<int[]> launches() {
        return clientLaunches;
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

    // ------------------------------------------------------------------ server

    private boolean isEnemy(LivingEntity t, LivingEntity owner) {
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

    private void hit(LivingEntity t, LivingEntity owner, double flat, double percent) {
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        float flatDmg = (float) (flat);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, dsrc);
        }
        t.setDeltaMovement(keep);
        t.hurtMarked = true;
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (owner == null) {
            discard();
            return;
        }
        Vec3 o = position();
        if (tickCount == 1) {
            level().playSound(null, o.x, o.y + 2, o.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.5f, 1.4f);
            level().playSound(null, o.x, o.y + 2, o.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 0.8f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.4f, FIRE_START);
            com.example.jjkspells.vfx.sprite.SpriteFx.glowOn(sl, owner, 2.4f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2f, FIRE_START);
        }
        if (tickCount < FIN) {
            owner.setDeltaMovement(0, Math.min(0.0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
        }
        if (tickCount > 2 && tickCount < FIRE_START && tickCount % 2 == 0) {
            level().playSound(null, o.x, o.y + 3, o.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.2f + 0.05f * tickCount);
        }
        // fire the next sword
        int k = (tickCount - FIRE_START);
        if (k >= 0 && k % HornedVolleySpell.FIRE_INTERVAL == 0 && k / HornedVolleySpell.FIRE_INTERVAL < HornedVolleySpell.SWORDS) {
            fire(sl, owner, o, k / HornedVolleySpell.FIRE_INTERVAL);
        }
        // landings
        for (Sword s : swords) {
            if (!s.landed && tickCount >= s.launch + FLIGHT) {
                s.landed = true;
                if (s.target != null) {
                    Entity te = sl.getEntity(s.target);
                    if (te instanceof LivingEntity t && t.isAlive()) {
                        hit(t, owner, HornedVolleySpell.IMPACT_FLAT, HornedVolleySpell.IMPACT_PERCENT);
                        level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 1.2f);
                        level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 2.0f, 1.4f);
                        com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 1.5f);
                    }
                } else {
                    level().playSound(null, s.fallback.x, s.fallback.y, s.fallback.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.5f, 1.4f);
                    com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, s.fallback, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1f);
                }
            }
        }
        if (tickCount == FIN) {
            for (Sword s : swords) {
                if (s.target == null) {
                    continue;
                }
                Entity te = sl.getEntity(s.target);
                if (te instanceof LivingEntity t && t.isAlive()) {
                    hit(t, owner, HornedVolleySpell.BLAST_FLAT, HornedVolleySpell.BLAST_PERCENT);
                    Vec3 out = new Vec3(t.getX() - o.x, 0, t.getZ() - o.z);
                    out = out.lengthSqr() < 0.01 ? dir() : out.normalize();
                    t.setDeltaMovement(out.x * 0.25, 0.3, out.z * 0.25);
                    t.hurtMarked = true;
                    level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 1.7f);
                    com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2f);
                    com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f);
                }
            }
            level().playSound(null, o.x, o.y + 3, o.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 0.8f);
            level().playSound(null, o.x, o.y + 3, o.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 0.9f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 1.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(sl, o.add(0, 2.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);
        }
    }

    private void fire(ServerLevel sl, LivingEntity owner, Vec3 o, int idx) {
        double r = HornedVolleySpell.RANGE;
        LivingEntity best = null;
        int bestCount = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, new AABB(o.x - r, o.y - 8, o.z - r, o.x + r, o.y + 12, o.z + r))) {
            if (!isEnemy(t, owner) || t.distanceToSqr(o.x, o.y, o.z) > r * r) {
                continue;
            }
            int c = perTarget.getOrDefault(t.getUUID(), 0);
            double d = t.distanceToSqr(o.x, o.y, o.z);
            if (c < bestCount || (c == bestCount && d < bestDist)) {
                best = t;
                bestCount = c;
                bestDist = d;
            }
        }
        Sword s = new Sword();
        s.idx = idx;
        s.launch = tickCount;
        Vec3 dir = dir();
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        double spread = ((idx % 6) - 2.5) * 0.18;
        if (best != null) {
            s.target = best.getUUID();
            perTarget.merge(best.getUUID(), 1, Integer::sum);
            s.fallback = best.position().add(0, best.getBbHeight() * 0.5, 0);
        } else {
            s.fallback = o.add(0, 0.2, 0).add(dir.scale(16.0)).add(side.scale(spread * 16.0));
        }
        swords.add(s);
        launchLog.add(idx);
        launchLog.add(tickCount);
        launchLog.add(best == null ? -1 : best.getId());
        launchLog.add((int) Math.round(s.fallback.x * 10));
        launchLog.add((int) Math.round(s.fallback.y * 10));
        launchLog.add((int) Math.round(s.fallback.z * 10));
        int[] arr = new int[launchLog.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = launchLog.get(i);
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("l", arr);
        this.entityData.set(LOG, tag);
        level().playSound(null, o.x, o.y + 3, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 1.4f + 0.05f * idx);
        level().playSound(null, o.x, o.y + 3, o.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.5f, 1.2f + 0.04f * idx);
        com.example.jjkspells.vfx.sprite.SpriteFx.sparks(sl, o.add(0, 3.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 0.8f);
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        int[] l = this.entityData.get(LOG).getIntArray("l");
        if (l.length / 6 != clientLaunches.size()) {
            clientLaunches = new ArrayList<>();
            for (int i = 0; i + 5 < l.length; i += 6) {
                clientLaunches.add(new int[]{l[i], l[i + 1], l[i + 2], l[i + 3], l[i + 4], l[i + 5]});
            }
            if (!clientLaunches.isEmpty()) {
                for (int i = 0; i < 4; i++) {
                    level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5), getY() + 3.4, getZ() + (random.nextDouble() - 0.5), (random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2);
                }
            }
        }
    }
}
