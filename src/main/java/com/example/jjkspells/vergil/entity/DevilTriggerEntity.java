package com.example.jjkspells.vergil.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.vergil.VergilRegistry;
import com.example.jjkspells.vergil.spell.DevilTriggerSpell;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of "Devil Trigger". It follows the caster. Timeline (ticks since cast):
 * <pre>
 *   0 .. RISE                the demon forms behind the caster, buffs are applied
 *   SLASH0 + k * STEP        phantom slash k: up to SLASH_TARGETS random enemies are cut
 *   DURATION                 the demon bursts: shockwave, buffs removed
 *   .. END                   fading glass
 * </pre>
 */
public class DevilTriggerEntity extends Entity {
    public static final int RISE = 24;
    public static final int SLASH0 = 24;
    public static final int STEP = 10;
    public static final int DURATION = DevilTriggerSpell.DURATION_TICKS;
    public static final int END = DURATION + 40;

    public static final UUID DMG_MOD = UUID.fromString("a7c1d2e3-4b5a-4c6d-8e9f-0a1b2c3d4e01");
    public static final UUID RESIST_MOD = UUID.fromString("a7c1d2e3-4b5a-4c6d-8e9f-0a1b2c3d4e02");

    /** Server only: one active Devil Trigger per caster. */
    private static final Map<UUID, DevilTriggerEntity> ACTIVE = new HashMap<>();

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(DevilTriggerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(DevilTriggerEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private boolean buffed;
    private final CompoundTag log = new CompoundTag();
    private int logged;
    private final List<int[]> clientSlashes = new ArrayList<>();

    public DevilTriggerEntity(EntityType<? extends DevilTriggerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static DevilTriggerEntity create(Level level, LivingEntity caster) {
        DevilTriggerEntity old = ACTIVE.remove(caster.getUUID());
        if (old != null) {
            old.discard();
        }
        DevilTriggerEntity e = new DevilTriggerEntity(VergilRegistry.TRIGGER, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        ACTIVE.put(caster.getUUID(), e);
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

    /** Client: ids of the enemies each phantom slash hit (index = slash). */
    public List<int[]> slashes() {
        return clientSlashes;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            if (ownerUuid != null && ACTIVE.get(ownerUuid) == this) {
                ACTIVE.remove(ownerUuid);
            }
            clearBuffs();
        }
        super.remove(reason);
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

    private void applyBuffs(LivingEntity owner) {
        buffed = true;
        AttributeInstance dmg = owner.getAttribute(ModAttributes.MAX_HP_DAMAGE.get());
        if (dmg != null) {
            dmg.removeModifier(DMG_MOD);
            dmg.addTransientModifier(new AttributeModifier(DMG_MOD, "vergil_dt_damage", DevilTriggerSpell.DAMAGE_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        AttributeInstance res = owner.getAttribute(ModAttributes.MAX_HP_RESIST.get());
        if (res != null) {
            res.removeModifier(RESIST_MOD);
            res.addTransientModifier(new AttributeModifier(RESIST_MOD, "vergil_dt_resist", DevilTriggerSpell.RESIST_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    private void clearBuffs() {
        if (!buffed || !(this.level() instanceof ServerLevel sl)) {
            return;
        }
        buffed = false;
        LivingEntity owner = owner(sl);
        if (owner == null) {
            return;
        }
        AttributeInstance dmg = owner.getAttribute(ModAttributes.MAX_HP_DAMAGE.get());
        if (dmg != null) {
            dmg.removeModifier(DMG_MOD);
        }
        AttributeInstance res = owner.getAttribute(ModAttributes.MAX_HP_RESIST.get());
        if (res != null) {
            res.removeModifier(RESIST_MOD);
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

    private List<LivingEntity> enemies(LivingEntity owner, double radius) {
        Vec3 c = owner.position();
        AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (t.distanceToSqr(owner) <= radius * radius && isEnemy(t, owner)) {
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
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        double x = owner.getX();
        double y = owner.getY();
        double z = owner.getZ();

        if (tickCount == 1) {
            applyBuffs(owner);
            int d = DURATION + 20;
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, d, 1, false, false, true));
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, d, 0, false, false, true));
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, d, 0, false, false, true));
            level().playSound(null, x, y + 2, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0f, 1.6f);
            level().playSound(null, x, y + 2, z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.5f, 1.2f);
            level().playSound(null, x, y + 2, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.6f);
        }
        if (tickCount == RISE) {
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, new Vec3(x, y + 1.5, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 4f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2f);
            level().playSound(null, x, y + 4, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.6f);
            level().playSound(null, x, y + 4, z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0f, 2.0f);
        }
        int k = (tickCount - SLASH0) / STEP;
        if (tickCount >= SLASH0 && tickCount < DURATION && (tickCount - SLASH0) % STEP == 0) {
            List<LivingEntity> pool = enemies(owner, DevilTriggerSpell.RADIUS);
            Collections.shuffle(pool, new java.util.Random(sl.getGameTime() * 31 + k));
            int n = Math.min(DevilTriggerSpell.SLASH_TARGETS, pool.size());
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = pool.get(i).getId();
                hit(pool.get(i), owner, DevilTriggerSpell.SLASH_FLAT, DevilTriggerSpell.SLASH_PERCENT);
                com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, pool.get(i).position().add(0, pool.get(i).getBbHeight() * 0.5, 0), new Vec3(0, 0, 1), 37f * (k + i), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, pool.get(i).position().add(0, pool.get(i).getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.5f);
            }
            log.putIntArray("c" + k, ids);
            log.putInt("n", k + 1);
            logged = k + 1;
            this.entityData.set(LOG, log.copy());
            if (n > 0) {
                level().playSound(null, x, y + 2, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, 1.5f + 0.05f * (k % 6));
                level().playSound(null, x, y + 2, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 1.2f + 0.05f * (k % 6));
            }
        }
        if (tickCount == DURATION) {
            clearBuffs();
            level().playSound(null, x, y + 3, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.6f);
            level().playSound(null, x, y + 3, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 1.1f);
            level().playSound(null, x, y + 3, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.2f);
            level().playSound(null, x, y + 3, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, new Vec3(x, y + 2, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, new Vec3(x, y, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.VIOLET, (float) (DevilTriggerSpell.RADIUS * 2.0 / 6.0));
            for (LivingEntity t : enemies(owner, DevilTriggerSpell.RADIUS)) {
                hit(t, owner, DevilTriggerSpell.FINAL_FLAT, DevilTriggerSpell.FINAL_PERCENT);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2.5f);
                Vec3 out = new Vec3(t.getX() - x, 0, t.getZ() - z);
                out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(0.7);
                t.setDeltaMovement(t.getDeltaMovement().add(out.x, 0.5, out.z));
                t.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int n = tag.getInt("n");
        while (clientSlashes.size() < n) {
            clientSlashes.add(tag.getIntArray("c" + clientSlashes.size()));
        }
        Entity own = owner();
        double x = own != null ? own.getX() : getX();
        double y = own != null ? own.getY() : getY();
        double z = own != null ? own.getZ() : getZ();
        if (tickCount < DURATION && tickCount % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 1.0 + random.nextDouble() * 6.0;
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r, 0, 0.08 + random.nextDouble() * 0.1, 0);
            }
        }
        if (tickCount == 1 || tickCount == DURATION) {
            for (int i = 0; i < 60; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.4 + random.nextDouble() * 1.2;
                level().addParticle(ParticleTypes.END_ROD, x, y + 1, z, Math.cos(a) * s, 0.1 + random.nextDouble() * 0.5, Math.sin(a) * s);
            }
        }
    }
}
