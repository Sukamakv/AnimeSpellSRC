package com.example.jjkspells.vergil.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.vergil.VergilRegistry;
import com.example.jjkspells.vergil.spell.SpiralSwordsSpell;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
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
import java.util.UUID;

/**
 * Controller of "Spiral Swords". Timeline (ticks since cast):
 * <pre>
 *   0 .. SUMMON            swords appear one after another in three rings
 *   PULL .. FINAL          enemies are dragged towards the centre
 *   VOLLEY0 + k * STEP     volley k launches (8 swords); it lands FLIGHT ticks later (damage on landing)
 *   FINAL                  every embedded sword bursts: heavy hit, outward knock
 *   .. END                 fading glass
 * </pre>
 */
public class SpiralSwordsEntity extends Entity {
    public static final int SUMMON = 30;
    public static final int PULL = 24;
    public static final int VOLLEY0 = 70;
    public static final int STEP = 5;
    public static final int VOLLEYS = 6;
    public static final int PER_VOLLEY = 8;
    public static final int FLIGHT = 6;
    public static final int FINAL = 108;
    public static final int END = 150;
    public static final double R = SpiralSwordsSpell.RADIUS;
    public static final double BELOW = 6.0;
    public static final double ABOVE = 24.0;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SpiralSwordsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(SpiralSwordsEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final CompoundTag log = new CompoundTag();
    private final int[][] pending = new int[VOLLEYS][];
    private final List<int[]> clientVolleys = new ArrayList<>();

    public SpiralSwordsEntity(EntityType<? extends SpiralSwordsEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SpiralSwordsEntity create(Level level, LivingEntity caster) {
        SpiralSwordsEntity e = new SpiralSwordsEntity(VergilRegistry.SWORDS, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().normalize().scale(SpiralSwordsSpell.RANGE));
        BlockHitResult bhit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(level, caster, eye, stop, new AABB(eye, stop).inflate(1.5),
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en != caster);
        Vec3 at = stop;
        if (ehit != null) {
            at = ehit.getEntity().position();
        } else if (bhit.getType() == HitResult.Type.MISS) {
            BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -56, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (down.getType() != HitResult.Type.MISS) {
                at = down.getLocation();
            }
        }
        e.setPos(at.x, at.y, at.z);
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

    /** Client: the caster (null when not loaded). */
    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: ids of the enemies each volley was aimed at (index = volley). */
    public List<int[]> volleys() {
        return clientVolleys;
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

    private List<LivingEntity> inZone(LivingEntity owner) {
        double x = getX();
        double y = getY();
        double z = getZ();
        AABB box = new AABB(x - R, y - BELOW, z - R, x + R, y + ABOVE, z + R);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - x;
            double dz = t.getZ() - z;
            if (dx * dx + dz * dz <= R * R && isEnemy(t, owner)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Flat part through Iron's damage, percent part through SpellDamage.percent (respects Max Health Damage / Resistance). */
    private void hit(LivingEntity t, LivingEntity owner, double flat, double percent) {
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource src = level().damageSources().indirectMagic(this, owner);
        if (flat > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, (float) flat, src);
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
        double x = getX();
        double y = getY();
        double z = getZ();

        if (tickCount == 1) {
            level().playSound(null, x, y + 4, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0f, 1.9f);
            level().playSound(null, x, y + 4, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.5f, 0.6f);
        }
        if (tickCount == PULL) {
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, (float) (R * 2.0 / 7.0), FINAL - PULL);
        }
        if (tickCount < SUMMON && tickCount % 3 == 0) {
            level().playSound(null, x, y + 6, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.5f, 1.4f + tickCount * 0.02f);
        }
        if (tickCount >= PULL && tickCount < FINAL) {
            for (LivingEntity t : inZone(owner)) {
                Vec3 to = new Vec3(x - t.getX(), 0, z - t.getZ());
                double d = to.length();
                if (d > 1.5) {
                    Vec3 push = to.scale(1.0 / d).scale(Math.min(0.16, 0.04 + d * 0.01));
                    t.setDeltaMovement(t.getDeltaMovement().multiply(0.7, 1.0, 0.7).add(push));
                    t.hurtMarked = true;
                }
            }
        }
        int k = (tickCount - VOLLEY0) / STEP;
        if (tickCount >= VOLLEY0 && (tickCount - VOLLEY0) % STEP == 0 && k < VOLLEYS) {
            List<LivingEntity> in = inZone(owner);
            int[] ids = new int[in.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = in.get(i).getId();
            }
            pending[k] = ids;
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(sl, new Vec3(x, y + 8, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 3f);
            log.putIntArray("c" + k, ids);
            log.putInt("n", k + 1);
            this.entityData.set(LOG, log.copy());
            level().playSound(null, x, y + 8, z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 3.0f, 1.3f);
            level().playSound(null, x, y + 8, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.5f, 1.2f);
        }
        int hk = (tickCount - VOLLEY0 - FLIGHT + 1) / STEP;
        if (tickCount >= VOLLEY0 + FLIGHT - 1 && (tickCount - VOLLEY0 - FLIGHT + 1) % STEP == 0 && hk < VOLLEYS && pending[hk] != null) {
            for (int id : pending[hk]) {
                Entity e = sl.getEntity(id);
                if (e instanceof LivingEntity t && t.isAlive()) {
                    hit(t, owner, SpiralSwordsSpell.VOLLEY_FLAT, SpiralSwordsSpell.VOLLEY_PERCENT);
                    com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.5f);
                }
            }
            level().playSound(null, x, y + 1, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 3.0f, 0.8f);
            level().playSound(null, x, y + 1, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 3.0f, 1.1f);
        }
        if (tickCount == FINAL) {
            level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.6f);
            level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 1.1f);
            level().playSound(null, x, y + 4, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.2f);
            level().playSound(null, x, y + 4, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, new Vec3(x, y + 2, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 6.0));
            for (LivingEntity t : inZone(owner)) {
                hit(t, owner, SpiralSwordsSpell.FINAL_FLAT, SpiralSwordsSpell.FINAL_PERCENT);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 2.5f);
                Vec3 out = new Vec3(t.getX() - x, 0, t.getZ() - z);
                out = out.lengthSqr() < 0.01 ? new Vec3(0, 0, 0) : out.normalize().scale(0.6);
                t.setDeltaMovement(t.getDeltaMovement().add(out.x, 0.5, out.z));
                t.hurtMarked = true;
            }
            if (owner != null) {
                owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false, true));
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int n = tag.getInt("n");
        while (clientVolleys.size() < n) {
            clientVolleys.add(tag.getIntArray("c" + clientVolleys.size()));
        }
        double x = getX();
        double y = getY();
        double z = getZ();
        if (tickCount > 2 && tickCount < FINAL && tickCount % 2 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = (0.3 + random.nextDouble() * 0.7) * R;
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r, 0, 0.12, 0);
        }
        if (tickCount == FINAL) {
            for (int i = 0; i < 70; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.5 + random.nextDouble() * 1.4;
                level().addParticle(ParticleTypes.END_ROD, x, y + 1.5, z, Math.cos(a) * s, 0.1 + random.nextDouble() * 0.5, Math.sin(a) * s);
            }
        }
    }
}
