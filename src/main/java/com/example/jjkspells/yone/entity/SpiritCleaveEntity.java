package com.example.jjkspells.yone.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yone.YoneRegistry;
import com.example.jjkspells.yone.spell.SpiritCleaveSpell;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Spirit Cleave". The facing is fixed at cast.
 * <pre>
 *   T1   the blades tear apart: everything in the wide cone is cut and shoved outward; Yone gains a shield
 *   T2   the blades close: everything in the narrower cone is cut again and dragged toward the centre line; the shield grows
 *   .. T1 + SHIELD_TICKS   the veil of two blades circles Yone until the time is up or the shield is broken
 * </pre>
 * Hits are logged {x, y, z (* 10), tick, colour} (colour 0 for the first cut, 1 for the second); the end tick of the veil is synced once known.
 */
public class SpiritCleaveEntity extends Entity {
    public static final int T1 = SpiritCleaveSpell.T1;
    public static final int T2 = SpiritCleaveSpell.T2;
    public static final int VEIL_FADE = 8;
    public static final int END = T1 + SpiritCleaveSpell.SHIELD_TICKS + VEIL_FADE + 2;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SpiritCleaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(SpiritCleaveEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(SpiritCleaveEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final Set<Integer> caught = new HashSet<>();
    private final List<Integer> hitLog = new ArrayList<>();
    private float prevAbsorb = 0f;
    private int endTick = -1;
    private List<int[]> clientHits = new ArrayList<>();
    private int clientEnd = -1;

    public SpiritCleaveEntity(EntityType<? extends SpiritCleaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SpiritCleaveEntity create(Level level, LivingEntity caster) {
        SpiritCleaveEntity e = new SpiritCleaveEntity(YoneRegistry.CLEAVE, level);
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

    /** Client: hits so far. */
    public List<int[]> hits() {
        return clientHits;
    }

    /** Client: tick the veil ended on, or -1 while it lasts. */
    public int veilEnd() {
        return clientEnd;
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

    private void sync() {
        CompoundTag tag = new CompoundTag();
        int[] h = new int[hitLog.size()];
        for (int i = 0; i < h.length; i++) {
            h[i] = hitLog.get(i);
        }
        tag.putIntArray("h", h);
        tag.putInt("e", endTick);
        this.entityData.set(LOG, tag);
    }

    private void cut(LivingEntity owner, int phase) {
        Vec3 o = owner.position();
        Vec3 dir = dir();
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        double radius = phase == 0 ? SpiritCleaveSpell.RADIUS : SpiritCleaveSpell.CLOSE_RADIUS;
        double cosHalf = Math.cos(Math.toRadians(phase == 0 ? SpiritCleaveSpell.HALF_ANGLE_DEG : SpiritCleaveSpell.CLOSE_HALF_ANGLE_DEG));
        AABB box = new AABB(o.x - radius, o.y - 4, o.z - radius, o.x + radius, o.y + 5, o.z + radius).inflate(1.0);
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isEnemy(t, owner)) {
                continue;
            }
            Vec3 rel = new Vec3(t.getX() - o.x, 0, t.getZ() - o.z);
            double dist = rel.length();
            if (dist > radius + t.getBbWidth() * 0.5 || Math.abs(t.getY() - o.y) > 4.5) {
                continue;
            }
            if (dist > 1.4 && rel.scale(1.0 / dist).dot(dir) < cosHalf) {
                continue;
            }
            if (phase == 0) {
                hit(t, owner, SpiritCleaveSpell.CUT1_FLAT, SpiritCleaveSpell.CUT1_PERCENT);
                Vec3 out = dist < 0.01 ? dir : rel.scale(1.0 / dist);
                t.setDeltaMovement(out.x * 0.7, 0.3, out.z * 0.7);
            } else {
                hit(t, owner, SpiritCleaveSpell.CUT2_FLAT, SpiritCleaveSpell.CUT2_PERCENT);
                double lat = rel.dot(side);
                t.setDeltaMovement(dir.x * 0.3 - side.x * Math.signum(lat) * 0.4, 0.3, dir.z * 0.3 - side.z * Math.signum(lat) * 0.4);
            }
            t.hurtMarked = true;
            caught.add(t.getId());
            com.example.jjkspells.vfx.sprite.SpriteFx.impact((ServerLevel) level(), t.position().add(0, t.getBbHeight() * 0.5, 0), phase == 0 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 1.8f);
            level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, phase == 0 ? 1.4f : 1.0f);
            hitLog.add((int) Math.round(t.getX() * 10));
            hitLog.add((int) Math.round((t.getY() + t.getBbHeight() * 0.5) * 10));
            hitLog.add((int) Math.round(t.getZ() * 10));
            hitLog.add(tickCount);
            hitLog.add(phase);
            while (hitLog.size() > 150) {
                hitLog.subList(0, 5).clear();
            }
        }
        ServerLevel cutSl = (ServerLevel) level();
        Vec3 cutAt = o.add(0, 1.2, 0).add(dir.scale(2.5));
        if (phase == 0) {
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(cutSl, cutAt, dir, 55f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(cutSl, cutAt, dir, -55f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(cutSl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (radius * 2.0 / 6.0));
        } else {
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(cutSl, cutAt, dir, 35f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(cutSl, cutAt, dir, -35f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(cutSl, o.add(0, 1.2, 0).add(dir.scale(3.0)), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(cutSl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (radius * 2.0 / 6.0));
        }
        float shield = Math.min(SpiritCleaveSpell.SHIELD_MAX, SpiritCleaveSpell.SHIELD_BASE + SpiritCleaveSpell.SHIELD_PER_HIT * caught.size());
        if (shield > owner.getAbsorptionAmount()) {
            owner.setAbsorptionAmount(shield);
        }
        sync();
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
        Vec3 o = owner.position();
        if (tickCount == 1) {
            prevAbsorb = owner.getAbsorptionAmount();
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.5f, 1.6f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.8f);
        }
        if (tickCount <= T2 + 2) {
            owner.setDeltaMovement(0, Math.min(0.0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
        }
        if (tickCount == T1) {
            cut(owner, 0);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.5f, 1.2f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 1.9f);
        }
        if (tickCount == T2) {
            cut(owner, 1);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.5f, 1.6f);
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.5f, 1.3f);
        }
        if (endTick < 0 && tickCount > T2 + 1 && (owner.getAbsorptionAmount() <= 0.01f || tickCount >= T1 + SpiritCleaveSpell.SHIELD_TICKS)) {
            endTick = tickCount;
            if (owner.getAbsorptionAmount() > prevAbsorb) {
                owner.setAbsorptionAmount(prevAbsorb);
            }
            level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 0.9f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, o, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.2f);
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(sl, o.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.5f);
            sync();
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        clientEnd = tag.contains("e") ? tag.getInt("e") : -1;
        int[] h = tag.getIntArray("h");
        if (h.length / 5 != clientHits.size() || (h.length >= 5 && !clientHits.isEmpty() && clientHits.get(clientHits.size() - 1)[3] != h[h.length - 2])) {
            clientHits = new ArrayList<>();
            for (int i = 0; i + 4 < h.length; i += 5) {
                clientHits.add(new int[]{h[i], h[i + 1], h[i + 2], h[i + 3], h[i + 4]});
            }
            for (int[] l : clientHits) {
                if (l[3] >= tickCount - 1) {
                    for (int i = 0; i < 8; i++) {
                        level().addParticle(ParticleTypes.CLOUD, l[0] / 10.0, l[1] / 10.0, l[2] / 10.0, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4);
                    }
                }
            }
        }
    }
}
