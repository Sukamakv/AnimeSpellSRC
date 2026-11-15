package com.example.jjkspells.entity;

import com.example.jjkspells.GaleConfig;
import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.spells.GaleBarrageSpell;
import io.redspace.ironsspellbooks.api.entity.NoKnockbackProjectile;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Controller of one Gale Barrage. It sticks to the caster, performs the strikes on the server and draws the
 * effects on the client. Strike k happens on the tick where floor(age*hits/life) increases, which spreads
 * exactly 'hits' strikes evenly over the lifetime.
 */
public class GaleBarrageEntity extends Projectile implements NoKnockbackProjectile {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GaleBarrageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(GaleBarrageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HITS = SynchedEntityData.defineId(GaleBarrageEntity.class, EntityDataSerializers.INT);

    private int spellLevel = 1;

    public GaleBarrageEntity(EntityType<? extends GaleBarrageEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.noCulling = true;
    }

    public GaleBarrageEntity(Level level, LivingEntity owner) {
        this(ModEntities.BARRAGE.get(), level);
        this.setOwner(owner);
        this.entityData.set(DATA_OWNER, owner.getId());
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        this.setOldPosAndRot();
    }

    public void setup(int level) {
        this.spellLevel = level;
        this.entityData.set(DATA_LIFE, Math.max(1, (int) Math.round(GaleConfig.SPELL.durationSeconds.get() * 20.0)));
        this.entityData.set(DATA_HITS, Math.max(1, GaleConfig.SPELL.hits.get()));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER, -1);
        this.entityData.define(DATA_LIFE, 80);
        this.entityData.define(DATA_HITS, 27);
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    public int getHits() {
        return this.entityData.get(DATA_HITS);
    }

    public Entity caster() {
        return this.level().isClientSide ? this.level().getEntity(this.entityData.get(DATA_OWNER)) : getOwner();
    }

    /** Number of strikes that happen exactly on the given age (tick). */
    public static int strikesAt(int age, int life, int hits) {
        if (age < 1) {
            return 0;
        }
        return (int) ((long) age * hits / life) - (int) ((long) (age - 1) * hits / life);
    }

    @Override
    public void tick() {
        super.tick();
        Entity caster = caster();
        boolean client = this.level().isClientSide;
        if (caster == null || !caster.isAlive()) {
            if (!client && this.tickCount > 2) {
                this.discard();
            }
            return;
        }
        this.setOldPosAndRot();
        this.setPos(caster.getX(), caster.getY(), caster.getZ());

        int life = getLife();
        int hits = getHits();
        if (client) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.jjkspells.client.BarrageFx.tick(this, caster));
            if (this.tickCount > life + 12) {
                this.discard();
            }
            return;
        }

        if (this.tickCount > life) {
            if (this.tickCount == life + 1) {
                this.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.BARRAGE_END.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
            }
            if (this.tickCount > life + 12) {
                this.discard();
            }
            return;
        }
        int n = strikesAt(this.tickCount, life, hits);
        for (int i = 0; i < n; i++) {
            strike((LivingEntity) caster);
        }
    }

    private void strike(LivingEntity caster) {
        if (GaleConfig.SPELL.swingArm.get()) {
            caster.swing(InteractionHand.MAIN_HAND, true);
        }
        float damage = GaleBarrageSpell.weaponDamage(caster) * GaleBarrageSpell.fraction(spellLevel);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        double reach = GaleConfig.SPELL.reach.get();
        double wBase = GaleConfig.SPELL.widthBase.get();
        double wGrow = GaleConfig.SPELL.widthPerBlock.get();
        double kb = GaleConfig.SPELL.knockback.get();
        AABB box = new AABB(eye, eye.add(look.scale(reach))).inflate(wBase + wGrow * reach + 0.5);
        boolean any = false;
        for (Entity e : this.level().getEntities(this, box, x -> x instanceof LivingEntity && x != caster && x.isAlive() && !x.isSpectator())) {
            if (e instanceof Player && !GaleConfig.SPELL.hitPlayers.get()) {
                continue;
            }
            if (DamageSources.isFriendlyFireBetween(caster, e)) {
                continue;
            }
            Vec3 r = e.getBoundingBox().getCenter().subtract(eye);
            double d = r.dot(look);
            if (d < -0.5 || d > reach + e.getBbWidth() * 0.5) {
                continue;
            }
            double lateral2 = r.lengthSqr() - d * d;
            double half = wBase + wGrow * Math.max(0, d) + e.getBbWidth() * 0.5;
            if (lateral2 > half * half) {
                continue;
            }
            LivingEntity living = (LivingEntity) e;
            living.invulnerableTime = 0;
            DamageSources.applyDamage(living, damage, ModSpells.GALE_BARRAGE.get().getDamageSource(this, caster));
            if (kb > 0 && living.isAlive()) {
                living.setDeltaMovement(living.getDeltaMovement().add(look.x * kb, 0.02, look.z * kb));
                living.hurtMarked = true;
            }
            any = true;
        }
        this.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.SWORD_SWING.get(), SoundSource.PLAYERS, 1.2f, 0.85f + this.random.nextFloat() * 0.35f);
        if (any) {
            Vec3 p = eye.add(look.scale(Math.min(reach, 3.0)));
            this.level().playSound(null, p.x, p.y, p.z, ModSounds.SLASH_HIT.get(), SoundSource.PLAYERS, 1.0f, 0.9f + this.random.nextFloat() * 0.3f);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128.0 * 128.0;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }
}
