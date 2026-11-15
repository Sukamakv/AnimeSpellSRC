package com.example.jjkspells.vergil.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.vergil.VergilRegistry;
import com.example.jjkspells.vergil.spell.JudgementCutSpell;
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
 * Controller of "Judgement Cut End". Timeline (ticks since cast):
 * <pre>
 *   0 .. RING            the caster's slash flies to the point, a ground seal draws itself
 *   RING .. CUT0         lines of cut space draw a cage around the zone, enemies are rooted
 *   CUT0 + k * STEP      cut number k (k = 0..CUTS-1): damage + slice lines through every enemy inside
 *   FINAL                space shatters: heavy hit, glass bursts outward
 *   .. END               fading shards
 * </pre>
 */
public class JudgementCutEntity extends Entity {
    public static final int RING = 14;
    public static final int CUT0 = 40;
    public static final int STEP = 5;
    public static final int CUTS = 8;
    public static final int FINAL = 84;
    public static final int END = 126;
    public static final double R = JudgementCutSpell.RADIUS;
    public static final double BELOW = 4.0;
    public static final double ABOVE = 26.0;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(JudgementCutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> CUT_LOG = SynchedEntityData.defineId(JudgementCutEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final CompoundTag log = new CompoundTag();
    private int logged;
    /** Client copy of the cut log: ids of every enemy sliced by cut k. */
    private final List<int[]> clientCuts = new ArrayList<>();

    public JudgementCutEntity(EntityType<? extends JudgementCutEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static JudgementCutEntity create(Level level, LivingEntity caster) {
        JudgementCutEntity e = new JudgementCutEntity(VergilRegistry.CUT, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().normalize().scale(JudgementCutSpell.RANGE));
        BlockHitResult bhit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(level, caster, eye, stop, new AABB(eye, stop).inflate(1.5),
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en != caster);
        Vec3 at = stop;
        if (ehit != null) {
            at = ehit.getEntity().position();
        } else if (bhit.getType() == HitResult.Type.MISS) {
            BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -48, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
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
        this.entityData.define(CUT_LOG, new CompoundTag());
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

    /** Client: number of cuts already made and the ids of the enemies each one hit. */
    public List<int[]> cuts() {
        return clientCuts;
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
            level().playSound(null, x, y + 1, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.6f);
            level().playSound(null, x, y + 1, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 3.0f, 1.9f);
            level().playSound(null, x, y + 1, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 2.0f);
        }
        if (tickCount >= RING && tickCount < FINAL && tickCount % 4 == 0) {
            for (LivingEntity t : inZone(owner)) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 9, false, false, false));
            }
        }
        int k = (tickCount - CUT0) / STEP;
        if (tickCount >= CUT0 && (tickCount - CUT0) % STEP == 0 && k < CUTS) {
            List<LivingEntity> in = inZone(owner);
            int[] ids = new int[in.size()];
            for (int i = 0; i < in.size(); i++) {
                ids[i] = in.get(i).getId();
                hit(in.get(i), owner, JudgementCutSpell.CUT_FLAT, JudgementCutSpell.CUT_PERCENT);
            }
            log.putIntArray("c" + k, ids);
            logged = k + 1;
            log.putInt("n", logged);
            this.entityData.set(CUT_LOG, log.copy());
            float pitch = 1.5f + 0.08f * k;
            level().playSound(null, x, y + 3, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, pitch);
            level().playSound(null, x, y + 3, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.5f, pitch);
        }
        if (tickCount == FINAL) {
            level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.7f);
            level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 1.2f);
            level().playSound(null, x, y + 4, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.5f);
            level().playSound(null, x, y + 4, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.5f);
            level().playSound(null, x, y + 4, z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0f, 2.0f);
            for (LivingEntity t : inZone(owner)) {
                hit(t, owner, JudgementCutSpell.FINAL_FLAT, JudgementCutSpell.FINAL_PERCENT);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true, true));
                t.setDeltaMovement(t.getDeltaMovement().add(0, 0.35, 0));
                t.hurtMarked = true;
            }
            if (owner != null) {
                owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false, true));
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(CUT_LOG);
        int n = tag.getInt("n");
        while (clientCuts.size() < n) {
            clientCuts.add(tag.getIntArray("c" + clientCuts.size()));
        }
        double x = getX();
        double y = getY();
        double z = getZ();
        if (tickCount >= CUT0 && (tickCount - CUT0) % STEP == 0 && (tickCount - CUT0) / STEP < CUTS) {
            for (int i = 0; i < 24; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = random.nextDouble() * R;
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, x + Math.cos(a) * r, y + 0.2 + random.nextDouble() * 12, z + Math.sin(a) * r, 0, 0.05, 0);
            }
        }
        if (tickCount == FINAL) {
            for (int i = 0; i < 50; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.4 + random.nextDouble() * 1.1;
                level().addParticle(ParticleTypes.END_ROD, x, y + 6, z, Math.cos(a) * s, (random.nextDouble() - 0.4) * 0.8, Math.sin(a) * s);
            }
        }
    }
}
