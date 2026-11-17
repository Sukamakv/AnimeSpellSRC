package com.example.jjkspells.vergil.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.vergil.VergilRegistry;
import com.example.jjkspells.vergil.spell.CrescentWaveSpell;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Crescent Wave". The entity sits at the caster's feet, the horizontal travel direction is synced. Timeline (ticks since cast):
 * <pre>
 *   0 .. LAUNCH            the blade is drawn (charge)
 *   LAUNCH .. LAUNCH+STEPS the wave advances SPEED blocks per tick; every enemy it crosses is cut once and marked
 *   mark + DELAY           the cut catches up: second hit
 *   .. END                 fading scars
 * </pre>
 * The wave is a curved front: its leading edge at lateral offset x is trailing back by 0.022 x^2 blocks.
 */
public class CrescentWaveEntity extends Entity {
    public static final int LAUNCH = 10;
    public static final double SPEED = CrescentWaveSpell.SPEED;
    public static final int STEPS = (int) Math.ceil(CrescentWaveSpell.DISTANCE / SPEED);
    public static final int DELAY = CrescentWaveSpell.DELAY_TICKS;
    public static final int END = LAUNCH + STEPS + DELAY + 28;
    public static final double W = CrescentWaveSpell.HALF_WIDTH;
    public static final double CURVE = 0.022;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(CrescentWaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(CrescentWaveEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(CrescentWaveEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final Set<Integer> hitIds = new HashSet<>();
    /** Server: entity id -> tick of the wave hit, waiting for the delayed hit. */
    private final Map<Integer, Integer> pending = new HashMap<>();
    private final List<Integer> flat = new ArrayList<>();
    private List<int[]> clientHits = new ArrayList<>();
    private int clientSize = 0;

    public CrescentWaveEntity(EntityType<? extends CrescentWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static CrescentWaveEntity create(Level level, LivingEntity caster) {
        CrescentWaveEntity e = new CrescentWaveEntity(VergilRegistry.WAVE_E, level);
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

    /** Client: every enemy the wave cut, as {entity id, tick of the cut}. */
    public List<int[]> hits() {
        return clientHits;
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

    private LivingEntity owner(ServerLevel sl) {
        Entity e = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        return e instanceof LivingEntity le ? le : null;
    }

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

    /** Flat part through Iron's damage, percent part through SpellDamage.percent (respects Max Health Damage / Resistance). */
    private void hit(LivingEntity t, LivingEntity owner, double flatDmg, double percent) {
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource src = level().damageSources().indirectMagic(this, owner);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, (float) flatDmg, src);
        }
        if (t.isAlive() && percent > 0) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, src);
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
        LivingEntity owner = owner(sl);
        Vec3 o = position();
        Vec3 d = dir();
        Vec3 side = new Vec3(-d.z, 0, d.x);

        if (tickCount == 1) {
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 2.0f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 1.8f);
        }
        if (tickCount == LAUNCH) {
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2f);
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, o.add(d.scale(2.0)).add(0, 1.0, 0), new Vec3(0, 1, 0), (float) Math.toDegrees(Math.atan2(d.z, d.x)), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 6f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, 0.5f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.4f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.0f, 1.2f);
        }
        int step = tickCount - LAUNCH;
        if (step >= 1 && step <= STEPS) {
            double dPrev = SPEED * (step - 1);
            double dCur = SPEED * step;
            Vec3 mid = o.add(d.scale((dPrev + dCur) * 0.5));
            double ext = W + 12.0;
            AABB box = new AABB(mid.x - ext, o.y - 5, mid.z - ext, mid.x + ext, o.y + 18, mid.z + ext);
            boolean any = false;
            if (step % 3 == 1) {
                com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, mid.add(0, 1.2, 0), new Vec3(0, 1, 0), (float) Math.toDegrees(Math.atan2(d.z, d.x)), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 10f);
            }
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (hitIds.contains(t.getId()) || !isEnemy(t, owner)) {
                    continue;
                }
                Vec3 rel = t.position().subtract(o);
                double along = rel.x * d.x + rel.z * d.z;
                double lat = rel.x * side.x + rel.z * side.z;
                if (Math.abs(lat) > W + 1.0) {
                    continue;
                }
                double front = along + CURVE * lat * lat;
                if (front > dPrev - 1.0 && front <= dCur + 1.0) {
                    hitIds.add(t.getId());
                    pending.put(t.getId(), tickCount);
                    flat.add(t.getId());
                    flat.add(tickCount);
                    hit(t, owner, CrescentWaveSpell.WAVE_FLAT, CrescentWaveSpell.WAVE_PERCENT);
                    com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 0.35, 0));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DELAY, 2, false, false, false));
                    t.hurtMarked = true;
                    any = true;
                }
            }
            if (any) {
                int[] arr = new int[flat.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = flat.get(i);
                }
                CompoundTag tag = new CompoundTag();
                tag.putIntArray("d", arr);
                this.entityData.set(LOG, tag);
                level().playSound(null, mid.x, mid.y + 2, mid.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 3.0f, 1.3f);
                level().playSound(null, mid.x, mid.y + 2, mid.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 1.0f);
            }
            if (step % 3 == 0) {
                level().playSound(null, mid.x, mid.y + 2, mid.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, 0.7f);
            }
        }
        Iterator<Map.Entry<Integer, Integer>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> en = it.next();
            if (tickCount >= en.getValue() + DELAY) {
                it.remove();
                Entity e = sl.getEntity(en.getKey());
                if (e instanceof LivingEntity t && t.isAlive()) {
                    hit(t, owner, CrescentWaveSpell.DELAYED_FLAT, CrescentWaveSpell.DELAYED_PERCENT);
                    com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 2.5f);
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, false));
                    level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 1.0f + level().random.nextFloat() * 0.5f);
                    level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 1.6f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        int[] arr = this.entityData.get(LOG).getIntArray("d");
        if (arr.length / 2 != clientSize) {
            clientHits = new ArrayList<>();
            for (int i = 0; i + 1 < arr.length; i += 2) {
                clientHits.add(new int[]{arr[i], arr[i + 1]});
            }
            clientSize = arr.length / 2;
        }
        Vec3 o = position();
        Vec3 d = dir();
        Vec3 side = new Vec3(-d.z, 0, d.x);
        int step = tickCount - LAUNCH;
        if (step >= 1 && step <= STEPS) {
            double dist = SPEED * step;
            for (int i = 0; i < 6; i++) {
                double lat = (random.nextDouble() * 2 - 1) * W;
                Vec3 pos = o.add(d.scale(dist - CURVE * lat * lat)).add(side.scale(lat));
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.3 + random.nextDouble() * 8, pos.z, 0, 0.05, 0);
            }
        }
    }
}
