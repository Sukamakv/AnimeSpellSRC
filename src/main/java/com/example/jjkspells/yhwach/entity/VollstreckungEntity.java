package com.example.jjkspells.yhwach.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yhwach.VollstreckungEvents;
import com.example.jjkspells.yhwach.YhwachRegistry;
import com.example.jjkspells.yhwach.spell.VollstreckungSpell;
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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * Controller of "Vollstreckung". Timeline (ticks since cast):
 * <pre>
 *   0 .. APPEAR            the sword materialises 6 blocks above the target
 *   .. STRIKE              it tracks the target (entity position follows the target)
 *   STRIKE .. IMPACT       it falls
 *   IMPACT                 effect applied
 *   .. TOTAL               sword stuck in the ground, then shatters
 * </pre>
 */
public class VollstreckungEntity extends Entity {
    public static final int APPEAR = 16;
    public static final int STRIKE = VollstreckungSpell.FOLLOW_TICKS;
    public static final int DROP = 6;
    public static final int IMPACT = STRIKE + DROP;
    public static final int TOTAL = IMPACT + 34;

    public static final UUID HP_MOD = UUID.fromString("5b8c7e12-3d0a-4a53-9c2e-6a0d11f0c001");
    public static final UUID RESIST_MOD = UUID.fromString("5b8c7e12-3d0a-4a53-9c2e-6a0d11f0c002");

    private static final EntityDataAccessor<Float> TARGET_H = SynchedEntityData.defineId(VollstreckungEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LOCKED = SynchedEntityData.defineId(VollstreckungEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerUuid;
    private UUID targetUuid;

    public VollstreckungEntity(EntityType<? extends VollstreckungEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static VollstreckungEntity create(Level level, LivingEntity caster) {
        VollstreckungEntity e = new VollstreckungEntity(YhwachRegistry.VOLL, level);
        e.ownerUuid = caster.getUUID();
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().normalize().scale(VollstreckungSpell.RANGE));
        BlockHitResult bhit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = bhit.getType() == HitResult.Type.MISS ? end : bhit.getLocation();
        EntityHitResult ehit = ProjectileUtil.getEntityHitResult(level, caster, eye, stop, new AABB(eye, stop).inflate(1.5),
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en != caster);
        Vec3 at = stop;
        float h = 1.8f;
        if (ehit != null) {
            Entity t = ehit.getEntity();
            e.targetUuid = t.getUUID();
            at = t.position();
            h = t.getBbHeight();
            e.entityData.set(LOCKED, true);
        } else if (bhit.getType() == HitResult.Type.MISS) {
            BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -48, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (down.getType() != HitResult.Type.MISS) {
                at = down.getLocation();
            }
        }
        e.entityData.set(TARGET_H, h);
        e.setPos(at.x, at.y, at.z);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_H, 1.8f);
        this.entityData.define(LOCKED, false);
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

    public float targetHeight() {
        return this.entityData.get(TARGET_H);
    }

    public boolean hasTarget() {
        return this.entityData.get(LOCKED);
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

    private LivingEntity target() {
        if (targetUuid == null || !(this.level() instanceof ServerLevel sl)) {
            return null;
        }
        Entity e = sl.getEntity(targetUuid);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private void serverTick() {
        if (tickCount > TOTAL) {
            discard();
            return;
        }
        LivingEntity t = target();
        if (tickCount < IMPACT && t != null) {
            this.setPos(t.getX(), t.getY(), t.getZ());
            this.entityData.set(TARGET_H, t.getBbHeight());
        }
        double x = getX();
        double y = getY();
        double z = getZ();
        if (tickCount == 1) {
            level().playSound(null, x, y + 6, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 3.0f, 1.8f);
            level().playSound(null, x, y + 6, z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0f, 1.6f);
        }
        if (tickCount == STRIKE - 14) {
            level().playSound(null, x, y + 6, z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 3.0f, 0.6f);
        }
        if (tickCount == STRIKE) {
            level().playSound(null, x, y + 8, z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 4.0f, 0.5f);
        }
        if (tickCount == IMPACT) {
            level().playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.7f);
            level().playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.2f);
            level().playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.6f);
            if (t != null) {
                apply(t);
            }
        }
    }

    private static boolean isBoss(LivingEntity t) {
        return t.getType().is(Tags.EntityTypes.BOSSES);
    }

    private void apply(LivingEntity t) {
        Entity ownerEnt = this.level() instanceof ServerLevel sl && ownerUuid != null ? sl.getEntity(ownerUuid) : null;
        LivingEntity owner = ownerEnt instanceof LivingEntity le ? le : null;
        if (t == owner) {
            return;
        }
        float mult = ModAttributes.multiplier(owner);
        float resist = ModAttributes.resist(t);
        double through = Math.max(0.0, 1.0 - resist) * mult;
        com.example.jjkspells.spells.SpellDamage.irradiate(t, 120);
        long end = level().getGameTime() + VollstreckungSpell.DEBUFF_TICKS;

        if (isBoss(t)) {
            float dmg = (float) (t.getMaxHealth() * VollstreckungSpell.BOSS_DAMAGE);
            t.invulnerableTime = 0;
            com.example.jjkspells.spells.SpellDamage.percent(t, Math.max(0f, dmg), owner, level().damageSources().indirectMagic(this, owner));   // respects Max Health Damage / Resistance
            AttributeInstance inst = t.getAttribute(ModAttributes.MAX_HP_RESIST.get());
            if (inst != null) {
                inst.removeModifier(RESIST_MOD);
                inst.addTransientModifier(new AttributeModifier(RESIST_MOD, "vollstreckung_resist", -VollstreckungSpell.BOSS_RESIST_LOSS, AttributeModifier.Operation.ADDITION));
                VollstreckungEvents.track(t, RESIST_MOD, ModAttributes.MAX_HP_RESIST.get(), end);
            }
        } else {
            double loss = Mth.clamp(VollstreckungSpell.MAX_HEALTH_LOSS * through, 0.0, 0.95);
            AttributeInstance inst = t.getAttribute(Attributes.MAX_HEALTH);
            if (inst != null && loss > 0.0) {
                inst.removeModifier(HP_MOD);
                inst.addTransientModifier(new AttributeModifier(HP_MOD, "vollstreckung_hp", -loss, AttributeModifier.Operation.MULTIPLY_TOTAL));
                if (t.getHealth() > t.getMaxHealth()) {
                    t.setHealth(t.getMaxHealth());
                }
                VollstreckungEvents.track(t, HP_MOD, Attributes.MAX_HEALTH, end);
            }
            t.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        double x = getX();
        double y = getY();
        double z = getZ();
        double h = targetHeight();
        if (tickCount < IMPACT && tickCount > 4 && tickCount % 2 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.5 + random.nextDouble() * 2.0;
            level().addParticle(ParticleTypes.END_ROD, x + Math.cos(a) * r, y + h + 6 + random.nextDouble() * 16, z + Math.sin(a) * r, 0, -0.05, 0);
        }
        if (tickCount == IMPACT) {
            for (int i = 0; i < 40; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.2 + random.nextDouble() * 0.9;
                level().addParticle(ParticleTypes.END_ROD, x, y + 0.3, z, Math.cos(a) * s, 0.2 + random.nextDouble() * 0.6, Math.sin(a) * s);
            }
            for (int i = 0; i < 20; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.3 + random.nextDouble() * 0.8;
                level().addParticle(ParticleTypes.SNOWFLAKE, x, y + 0.3, z, Math.cos(a) * s, 0.1 + random.nextDouble() * 0.5, Math.sin(a) * s);
            }
        }
    }
}
