package com.example.jjkspells.dante.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.dante.DanteRegistry;
import com.example.jjkspells.dante.spell.SinJudgmentSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Controller of "Sin Devil Trigger: Judgment of Sparda". The entity sits at the cast point A (the centre of the zone).
 * <pre>
 *   0 .. RISE            Dante rises to HOVER blocks, heptagram and sword ring spread
 *   LOCK0 + 3k           strike k locks on a random enemy (or a random point) in RADIUS; it lands 8 ticks later
 *   DIVE0 .. FIN         Dante dives to the centre
 *   FIN                  Judgment: giant blast in FINAL_RADIUS
 *   .. END               fade
 * </pre>
 */
public class SinJudgmentEntity extends Entity {
    public static final int RISE = 30;
    public static final int STRIKES = 36;
    public static final int LOCK0 = 36;
    public static final int STEP = 3;
    public static final int WARN = 8;
    public static final int LAST_IMPACT = LOCK0 + (STRIKES - 1) * STEP + WARN;
    public static final int DIVE0 = LAST_IMPACT + 10;
    public static final int FIN = DIVE0 + 10;
    public static final int END = FIN + 70;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SinJudgmentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(SinJudgmentEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private boolean floating;
    private final List<Integer> strikeLog = new ArrayList<>();
    private List<int[]> clientStrikes = new ArrayList<>();
    private int clientStrikeSize = 0;

    public SinJudgmentEntity(EntityType<? extends SinJudgmentEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SinJudgmentEntity create(Level level, LivingEntity caster) {
        SinJudgmentEntity e = new SinJudgmentEntity(DanteRegistry.JUDGMENT, level);
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

    /** Client: the strikes as {x * 10, z * 10, impact tick}. */
    public List<int[]> strikes() {
        return clientStrikes;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && floating && this.level() instanceof ServerLevel sl) {
            floating = false;
            Entity e = ownerUuid == null ? null : sl.getEntity(ownerUuid);
            if (e != null) {
                e.setNoGravity(false);
            }
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

    private List<LivingEntity> near(Vec3 c, double radius, LivingEntity owner) {
        AABB box = new AABB(c.x - radius, c.y - 10, c.z - radius, c.x + radius, c.y + 20, c.z + radius);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - c.x;
            double dz = t.getZ() - c.z;
            if (dx * dx + dz * dz <= radius * radius && isEnemy(t, owner)) {
                out.add(t);
            }
        }
        return out;
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

    private void placeOwner(LivingEntity owner, double x, double y, double z) {
        if (owner instanceof ServerPlayer sp) {
            sp.connection.teleport(x, y, z, sp.getYRot(), sp.getXRot());
        } else {
            owner.moveTo(x, y, z, owner.getYRot(), owner.getXRot());
        }
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        Vec3 a = position();

        if (tickCount == 1) {
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, FIN + 20, 3, false, false, true));
            owner.setNoGravity(true);
            floating = true;
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 5.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 5.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 0.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, a, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (SinJudgmentSpell.RADIUS / 3.5), LOCK0);   // cast: judgment heptagram
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, a, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.glowOn(sl, owner, 1.0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 3.0f, FIN);
        }
        if (floating && tickCount < DIVE0) {
            double target = a.y + SinJudgmentSpell.HOVER;
            double vy = Mth.clamp((target - owner.getY()) * 0.22, -0.6, 0.9);
            owner.setDeltaMovement(0, vy, 0);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
        }
        if (tickCount % 20 == 10 && tickCount < DIVE0) {
            level().playSound(null, a.x, a.y + 2, a.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 3.0f, 0.5f);
            level().playSound(null, a.x, a.y + 2, a.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 3.0f, 0.7f);
        }

        // lock-on
        if (tickCount >= LOCK0 && (tickCount - LOCK0) % STEP == 0 && (tickCount - LOCK0) / STEP < STRIKES) {
            Random r = new Random(getId() * 131L + tickCount * 17L);
            List<LivingEntity> pool = near(a, SinJudgmentSpell.RADIUS, owner);
            double px;
            double pz;
            if (!pool.isEmpty()) {
                LivingEntity t = pool.get(r.nextInt(pool.size()));
                px = t.getX();
                pz = t.getZ();
            } else {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * (SinJudgmentSpell.RADIUS - 2);
                px = a.x + Math.cos(ang) * rad;
                pz = a.z + Math.sin(ang) * rad;
            }
            strikeLog.add((int) Math.round(px * 10));
            strikeLog.add((int) Math.round(pz * 10));
            strikeLog.add(tickCount + WARN);
            int[] arr = new int[strikeLog.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = strikeLog.get(i);
            }
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("s", arr);
            this.entityData.set(LOG, tag);
            level().playSound(null, px, a.y + 1, pz, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 2.0f, 1.8f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, new Vec3(px, a.y, pz), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (SinJudgmentSpell.STRIKE_RADIUS / 3.5), WARN);   // telegraph: strike lock-on
        }
        // impacts
        for (int i = 0; i + 2 < strikeLog.size(); i += 3) {
            if (strikeLog.get(i + 2) != tickCount) {
                continue;
            }
            Vec3 p = new Vec3(strikeLog.get(i) / 10.0, a.y, strikeLog.get(i + 1) / 10.0);
            for (LivingEntity t : near(p, SinJudgmentSpell.STRIKE_RADIUS, owner)) {
                hit(t, owner, SinJudgmentSpell.STRIKE_FLAT, SinJudgmentSpell.STRIKE_PERCENT);
                t.setDeltaMovement(t.getDeltaMovement().add(0, 0.6, 0));
                t.hurtMarked = true;
                t.setSecondsOnFire(3);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f);
            }
            level().playSound(null, p.x, p.y + 1, p.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.5f, 0.6f);
            level().playSound(null, p.x, p.y + 1, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 1.1f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, p.add(0, 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2.5f);   // giant Rebellion lands
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (SinJudgmentSpell.STRIKE_RADIUS / 3.0));
        }
        // dive and judgment
        if (tickCount == DIVE0) {
            owner.setDeltaMovement(0, -2.4, 0);
            owner.hurtMarked = true;
            level().playSound(null, a.x, a.y + 6, a.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 5.0f, 0.5f);
            level().playSound(null, a.x, a.y + 6, a.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 4.0f, 0.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.circle(sl, a, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) (SinJudgmentSpell.FINAL_RADIUS / 3.5), FIN - DIVE0);   // telegraph: judgment zone
            com.example.jjkspells.vfx.sprite.SpriteFx.beam(sl, owner.position(), a, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 3.0f, FIN - DIVE0);   // the dive
        }
        if (tickCount > DIVE0 && tickCount < FIN) {
            owner.setDeltaMovement(0, -2.4, 0);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
        }
        if (tickCount == FIN) {
            owner.setNoGravity(false);
            floating = false;
            owner.setDeltaMovement(Vec3.ZERO);
            placeOwner(owner, a.x, a.y, a.z);
            owner.hurtMarked = true;
            for (LivingEntity t : near(a, SinJudgmentSpell.FINAL_RADIUS, owner)) {
                hit(t, owner, SinJudgmentSpell.FINAL_FLAT, SinJudgmentSpell.FINAL_PERCENT);
                Vec3 out = new Vec3(t.getX() - a.x, 0, t.getZ() - a.z);
                out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize().scale(1.3);
                t.setDeltaMovement(out.x, 1.0, out.z);
                t.hurtMarked = true;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true, true));
                t.setSecondsOnFire(6);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 2.5f);
            }
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 8.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 8.0f, 0.6f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 5.0f, 0.5f);
            level().playSound(null, a.x, a.y + 1, a.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 5.0f, 0.6f);
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, a.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 5.0f);   // Judgment
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, a.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 6.0f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, a, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, (float) (SinJudgmentSpell.FINAL_RADIUS / 3.0));
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, a, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, (float) (SinJudgmentSpell.FINAL_RADIUS / 5.0));
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int[] s = tag.getIntArray("s");
        if (s.length / 3 != clientStrikeSize) {
            clientStrikes = new ArrayList<>();
            for (int i = 0; i + 2 < s.length; i += 3) {
                clientStrikes.add(new int[]{s[i], s[i + 1], s[i + 2]});
            }
            clientStrikeSize = s.length / 3;
        }
        Vec3 a = position();
        for (int[] st : clientStrikes) {
            if (st[2] == tickCount) {
                for (int i = 0; i < 30; i++) {
                    double ang = random.nextDouble() * Math.PI * 2;
                    double sp = 0.3 + random.nextDouble() * 1.0;
                    level().addParticle(ParticleTypes.FLAME, st[0] / 10.0, a.y + 0.3, st[1] / 10.0, Math.cos(ang) * sp, 0.1 + random.nextDouble() * 0.5, Math.sin(ang) * sp);
                }
            }
        }
        if (tickCount == FIN) {
            for (int i = 0; i < 200; i++) {
                double ang = random.nextDouble() * Math.PI * 2;
                double sp = 0.6 + random.nextDouble() * 3.0;
                level().addParticle(ParticleTypes.FLAME, a.x, a.y + 0.5, a.z, Math.cos(ang) * sp, 0.1 + random.nextDouble() * 1.0, Math.sin(ang) * sp);
            }
        }
    }
}
