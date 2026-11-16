package com.example.jjkspells.vergil.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.vergil.VergilRegistry;
import com.example.jjkspells.vergil.spell.DimensionSeverSpell;
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
 * Controller of "Dimension Sever". The entity sits on the target point P. Timeline (ticks since cast):
 * <pre>
 *   SLASH (event 0)        the opening cut: hit + knock up, the cage closes, the sky starts cracking
 *   PULL .. FINAL          enemies are dragged towards P
 *   PULSE0 + 10 j          sky pulse j (events 1..8)
 *   VOID                   a rift opens at P
 *   FINAL (event 9)        the sky shatters: huge hit
 *   .. END                 fading glass
 * </pre>
 */
public class DimensionSeverEntity extends Entity {
    public static final int SLASH = 12;
    public static final int PULL = 40;
    public static final int PULSE0 = 44;
    public static final int STEP = 10;
    public static final int PULSES = 8;
    public static final int VOID = 100;
    public static final int FINAL = 150;
    public static final int END = 205;
    public static final int EVENTS = PULSES + 2;
    public static final double R = DimensionSeverSpell.RADIUS;
    public static final double BELOW = 10.0;
    public static final double ABOVE = 60.0;

    /** Tick at which event k happens: 0 the opening cut, 1..8 sky pulses, 9 the final shatter. */
    public static int eventTick(int k) {
        return k == 0 ? SLASH : (k <= PULSES ? PULSE0 + STEP * (k - 1) : FINAL);
    }

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(DimensionSeverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(DimensionSeverEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final CompoundTag log = new CompoundTag();
    private final List<int[]> clientEvents = new ArrayList<>();

    public DimensionSeverEntity(EntityType<? extends DimensionSeverEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static DimensionSeverEntity create(Level level, LivingEntity caster) {
        DimensionSeverEntity e = new DimensionSeverEntity(VergilRegistry.SEVER, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().normalize().scale(DimensionSeverSpell.RANGE));
        BlockHitResult bhit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(level, caster, eye, stop, new AABB(eye, stop).inflate(1.5),
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en != caster);
        Vec3 at = stop;
        if (ehit != null) {
            at = ehit.getEntity().position();
        } else if (bhit.getType() == HitResult.Type.MISS) {
            BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -80, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
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

    public Entity owner() {
        int id = this.entityData.get(OWNER_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Client: ids of the enemies each event hit (index = event, see eventTick). */
    public List<int[]> events() {
        return clientEvents;
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
            if (owner != null) {
                owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, END, 2, false, false, true));
            }
            level().playSound(null, x, y + 2, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 5.0f, 1.9f);
            level().playSound(null, x, y + 2, z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4.0f, 0.8f);
            level().playSound(null, x, y + 2, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, 0.5f);
        }
        if (tickCount == 1) {
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 7.0), SLASH - 1);
        }
        if (tickCount > 4 && tickCount < SLASH + 30 && tickCount % 5 == 0) {
            level().playSound(null, x, y + 20, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 4.0f, 0.5f + 0.02f * tickCount);
        }
        if (tickCount >= PULL && tickCount < FINAL) {
            for (LivingEntity t : inZone(owner)) {
                Vec3 to = new Vec3(x - t.getX(), 0, z - t.getZ());
                double d = to.length();
                if (d > 1.5) {
                    Vec3 push = to.scale(1.0 / d).scale(Math.min(0.22, 0.05 + d * 0.005));
                    t.setDeltaMovement(t.getDeltaMovement().multiply(0.75, 1.0, 0.75).add(push));
                    t.hurtMarked = true;
                }
            }
        }
        if (tickCount == VOID) {
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, 3f, FINAL - VOID);
            level().playSound(null, x, y + 10, z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 5.0f, 0.6f);
            level().playSound(null, x, y + 10, z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 3.0f, 0.6f);
        }
        int k = -1;
        for (int i = 0; i < EVENTS; i++) {
            if (tickCount == eventTick(i)) {
                k = i;
            }
        }
        if (k >= 0) {
            List<LivingEntity> in = inZone(owner);
            int[] ids = new int[in.size()];
            for (int i = 0; i < ids.length; i++) {
                LivingEntity t = in.get(i);
                ids[i] = t.getId();
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, k == 0 || k > PULSES ? 3f : 1.5f);
                if (k == 0) {
                    hit(t, owner, DimensionSeverSpell.SLASH_FLAT, DimensionSeverSpell.SLASH_PERCENT);
                    t.setDeltaMovement(t.getDeltaMovement().add(0, 0.5, 0));
                    t.hurtMarked = true;
                } else if (k <= PULSES) {
                    hit(t, owner, DimensionSeverSpell.PULSE_FLAT, DimensionSeverSpell.PULSE_PERCENT);
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 3, false, false, false));
                } else {
                    hit(t, owner, DimensionSeverSpell.FINAL_FLAT, DimensionSeverSpell.FINAL_PERCENT);
                    Vec3 out = new Vec3(t.getX() - x, 0, t.getZ() - z);
                    out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(0.8);
                    t.setDeltaMovement(t.getDeltaMovement().add(out.x, 0.7, out.z));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true, true));
                    t.hurtMarked = true;
                }
            }
            log.putIntArray("c" + k, ids);
            log.putInt("n", k + 1);
            this.entityData.set(LOG, log.copy());
            if (k == 0) {
                com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, new Vec3(x, y + 4, z), new Vec3(0, 0, 1), 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 12f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 6.0));
                com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, (float) (R * 2.0 / 7.0), FINAL - SLASH);
                level().playSound(null, x, y + 4, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 1.2f);
                level().playSound(null, x, y + 4, z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 5.0f, 0.7f);
                level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 6.0f, 0.5f);
            } else if (k <= PULSES) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, (float) (R * 2.0 / 6.0) * (0.3f + 0.08f * k));
                com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, new Vec3(x, y + 8, z), new Vec3(0, 0, 1), 40f * k, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 8f);
                level().playSound(null, x, y + 4, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.8f);
                level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.7f + 0.05f * k);
                level().playSound(null, x, y + 4, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 4.0f, 1.0f);
            } else {
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, new Vec3(x, y + 6, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 12f);
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (R * 2.0 / 6.0));
                level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 8.0f, 0.5f);
                level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 8.0f, 1.0f);
                level().playSound(null, x, y + 4, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0f, 0.5f);
                level().playSound(null, x, y + 4, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 0.8f);
                level().playSound(null, x, y + 4, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0f, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int n = tag.getInt("n");
        while (clientEvents.size() < n) {
            clientEvents.add(tag.getIntArray("c" + clientEvents.size()));
        }
        double x = getX();
        double y = getY();
        double z = getZ();
        if (tickCount >= PULL && tickCount < FINAL && tickCount % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * R;
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r, 0, 0.2 + random.nextDouble() * 0.3, 0);
            }
        }
        if (tickCount == SLASH || tickCount == FINAL) {
            for (int i = 0; i < 120; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.5 + random.nextDouble() * 2.2;
                level().addParticle(ParticleTypes.END_ROD, x, y + 0.5, z, Math.cos(a) * s, 0.1 + random.nextDouble() * 0.9, Math.sin(a) * s);
            }
        }
    }
}
