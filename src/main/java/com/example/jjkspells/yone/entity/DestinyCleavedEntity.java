package com.example.jjkspells.yone.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yone.YoneRegistry;
import com.example.jjkspells.yone.spell.DestinyCleavedSpell;
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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Destiny Cleaved". The entity stands where the caster cast; the area centre is CENTRE_DISTANCE ahead of it.
 * <pre>
 *   1 .. WINDUP          Yone splits from his spirit; Yone rooted
 *   T1 = WINDUP          blue cut: everything in the area is hit and frozen
 *   T2 = T1 + GAP        red cut: hit again
 *   T3 = T2 + CROSS_DELAY  the cuts cross: cleave, launch
 *   T3 + 8               aftershock
 * </pre>
 */
public class DestinyCleavedEntity extends Entity {
    public static final int T1 = DestinyCleavedSpell.WINDUP;
    public static final int T2 = T1 + DestinyCleavedSpell.GAP;
    public static final int T3 = T2 + DestinyCleavedSpell.CROSS_DELAY;
    public static final int AFTER_T = T3 + 8;
    public static final int END = T3 + 34;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(DestinyCleavedEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(DestinyCleavedEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(DestinyCleavedEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final Set<Integer> caught = new LinkedHashSet<>();
    private int[] clientCaught = new int[0];

    public DestinyCleavedEntity(EntityType<? extends DestinyCleavedEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static DestinyCleavedEntity create(Level level, LivingEntity caster) {
        DestinyCleavedEntity e = new DestinyCleavedEntity(YoneRegistry.DESTINY, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        Vec3 look = Vec3.directionFromRotation(0, caster.getYRot());
        e.entityData.set(DIR, new Vector3f((float) look.x, 0f, (float) look.z));
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

    /** Client: enemies frozen by the cuts. */
    public int[] caughtIds() {
        return clientCaught;
    }

    public Vec3 centre() {
        return position().add(dir().scale(DestinyCleavedSpell.CENTRE_DISTANCE));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientCaught = this.entityData.get(LOG).getIntArray("t");
        } else {
            serverTick();
        }
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

    private void syncCaught() {
        int[] arr = new int[caught.size()];
        int i = 0;
        for (int id : caught) {
            arr[i++] = id;
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("t", arr);
        this.entityData.set(LOG, tag);
    }

    private AABB area(Vec3 c, double r) {
        return new AABB(c.x - r, c.y - 8, c.z - r, c.x + r, c.y + 40, c.z + r);
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        Vec3 c = centre();
        double R = DestinyCleavedSpell.RADIUS;

        if (owner != null && tickCount < T3) {
            owner.setDeltaMovement(0, Math.min(0.0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            owner.invulnerableTime = Math.max(owner.invulnerableTime, 6);
            owner.fallDistance = 0;
        }
        if (tickCount == 1) {
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 0.6f);
            level().playSound(null, getX(), getY() + 1, getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.5f, 0.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (R * 1.2 / 7.0), T1);
        }
        if (tickCount == T1 || tickCount == T2) {
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, area(c, R))) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                if (dx * dx + dz * dz > R * R || !isEnemy(t, owner)) {
                    continue;
                }
                hit(t, owner, DestinyCleavedSpell.CUT_FLAT, DestinyCleavedSpell.CUT_PERCENT);
                caught.add(t.getId());
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), tickCount == T1 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 1.8f);
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.5f, tickCount == T1 ? 1.2f : 0.8f);
            }
            syncCaught();
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, c.add(0, 3.0, 0), dir(), tickCount == T1 ? -45f : 45f, tickCount == T1 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (R * 0.6));
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, tickCount == T1 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (R * 2.0 / 6.0));
            level().playSound(null, c.x, c.y + 10, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 4.0f, tickCount == T1 ? 1.4f : 0.7f);
            level().playSound(null, c.x, c.y + 10, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.6f);
        }
        // frozen in time between the first cut and the cleave
        if (tickCount > T1 && tickCount < T3) {
            for (int id : caught) {
                Entity v = level().getEntity(id);
                if (v instanceof LivingEntity t && t.isAlive()) {
                    t.setDeltaMovement(0, 0.02, 0);
                    t.hurtMarked = true;
                    t.fallDistance = 0;
                }
            }
        }
        if (tickCount == T3) {
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, area(c, R))) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > R || !isEnemy(t, owner)) {
                    continue;
                }
                hit(t, owner, DestinyCleavedSpell.CLEAVE_FLAT, DestinyCleavedSpell.CLEAVE_PERCENT);
                double d = Math.max(dist, 0.01);
                t.setDeltaMovement(dx / d * 0.7, 1.1, dz / d * 0.7);
                t.hurtMarked = true;
                t.fallDistance = 0;
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2.5f);
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 3.0f, 0.8f);
            }
            caught.clear();
            syncCaught();
            level().playSound(null, c.x, c.y + 4, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.7f);
            level().playSound(null, c.x, c.y + 4, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 1.2f);
            level().playSound(null, c.x, c.y + 4, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 4.0f, 0.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, c.add(0, 3.0, 0), dir(), -45f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 0.7));
            com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, c.add(0, 3.0, 0), dir(), 45f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (R * 0.7));
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 3.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) (R * 0.5));
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) (R * 2.0 / 6.0));
            level().playSound(null, c.x, c.y + 4, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 1.5f);
        }
        if (tickCount == AFTER_T) {
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, area(c, R))) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                if (dx * dx + dz * dz > R * R || !isEnemy(t, owner)) {
                    continue;
                }
                hit(t, owner, DestinyCleavedSpell.AFTER_FLAT, DestinyCleavedSpell.AFTER_PERCENT);
                t.setDeltaMovement(t.getDeltaMovement().x, -1.2, t.getDeltaMovement().z);
                t.hurtMarked = true;
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2f);
            }
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 1.2f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 6.0));
        }
    }
}
