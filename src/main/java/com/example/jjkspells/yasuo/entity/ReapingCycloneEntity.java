package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yasuo.YasuoRegistry;
import com.example.jjkspells.yasuo.spell.ReapingCycloneSpell;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Reaping Cyclone". Follows Yasuo for DURATION ticks: every tick enemies inside the radius are dragged into a spiral round him, every
 * SLICE_INTERVAL ticks they are cut, and on the last tick they are harvested and thrown into the air.
 */
public class ReapingCycloneEntity extends Entity {
    public static final int DURATION = ReapingCycloneSpell.DURATION_TICKS;
    public static final int FADE = 16;
    public static final int END = DURATION + FADE;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ReapingCycloneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(ReapingCycloneEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final Set<Integer> caught = new LinkedHashSet<>();
    private int[] clientCaught = new int[0];

    public ReapingCycloneEntity(EntityType<? extends ReapingCycloneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ReapingCycloneEntity create(Level level, LivingEntity caster) {
        ReapingCycloneEntity e = new ReapingCycloneEntity(YasuoRegistry.CYCLONE, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
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

    /** Client: enemies currently in the cyclone. */
    public int[] caughtIds() {
        return clientCaught;
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

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (owner != null && tickCount <= DURATION) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        Vec3 c = position();
        if (tickCount == 1) {
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 3.0f, 0.9f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.5f, 1.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (ReapingCycloneSpell.RADIUS * 2.0 / 7.0), 24);
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) (ReapingCycloneSpell.RADIUS * 2.0 / 7.0), 24);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.2f);
        }
        if (tickCount == DURATION - 12) {
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (ReapingCycloneSpell.RADIUS * 2.0 / 6.0));
        }
        if (tickCount > DURATION) {
            return;
        }
        double R = ReapingCycloneSpell.RADIUS;
        AABB box = new AABB(c.x - R, c.y - 3, c.z - R, c.x + R, c.y + ReapingCycloneSpell.HEIGHT, c.z + R);
        boolean last = tickCount == DURATION;
        boolean slice = tickCount % ReapingCycloneSpell.SLICE_INTERVAL == 0;
        Set<Integer> inside = new LinkedHashSet<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - c.x;
            double dz = t.getZ() - c.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > R || !isEnemy(t, owner)) {
                continue;
            }
            inside.add(t.getId());
            caught.add(t.getId());
            if (last) {
                hit(t, owner, ReapingCycloneSpell.FINAL_FLAT, ReapingCycloneSpell.FINAL_PERCENT);
                double d = Math.max(dist, 1.0E-3);
                t.setDeltaMovement(dx / d * 0.7, 1.2, dz / d * 0.7);
                t.hurtMarked = true;
                t.fallDistance = 0;
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 1.6f);
                continue;
            }
            if (slice) {
                hit(t, owner, ReapingCycloneSpell.SLICE_FLAT, ReapingCycloneSpell.SLICE_PERCENT);
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 1.4f);
                com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, new Vec3(t.getX(), t.getY() + 1.0, t.getZ()), new Vec3(0, 1, 0), 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2f);
            }
            // spiral inward and up
            double d = Math.max(dist, 0.5);
            double inward = dist > 1.8 ? Math.min(0.45, dist * 0.08 + 0.12) : 0.0;
            double tx = -dz / d * 0.30;
            double tz = dx / d * 0.30;
            double want = c.y + 1.5 + (t.getId() % 5) * 0.8;
            double vy = Mth.clamp((want - t.getY()) * 0.18, -0.2, 0.4);
            t.setDeltaMovement(tx - dx / d * inward, vy, tz - dz / d * inward);
            t.hurtMarked = true;
            t.fallDistance = 0;
        }
        if (caught.retainAll(inside) || inside.size() != clientCaught.length) {
            syncCaught();
        }
        if (tickCount % 6 == 3) {
            level().playSound(null, c.x, c.y + 3, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.5f, 0.6f);
        }
        if (last) {
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.8f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 1.0f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 1.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (ReapingCycloneSpell.RADIUS * 2.0 / 6.0));
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        clientCaught = this.entityData.get(LOG).getIntArray("t");
        if (tickCount > DURATION) {
            return;
        }
        Entity own = owner();
        double cx = own != null ? own.getX() : getX();
        double cy = own != null ? own.getY() : getY();
        double cz = own != null ? own.getZ() : getZ();
        for (int i = 0; i < 6; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double h = random.nextDouble() * 12;
            double rr = 1.5 + (ReapingCycloneSpell.RADIUS - 1.5) * Math.pow(h / ReapingCycloneSpell.HEIGHT, 1.1);
            level().addParticle(ParticleTypes.CLOUD, cx + Math.cos(ang) * rr, cy + h, cz + Math.sin(ang) * rr, -Math.sin(ang) * 0.3, 0.04, Math.cos(ang) * 0.3);
        }
    }
}
