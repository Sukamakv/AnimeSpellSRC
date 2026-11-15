package com.example.jjkspells.entity;

import com.example.jjkspells.AzureConfig;
import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.spells.FX;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Azure Colossus: a 25-block-tall guardian made of blue chakra fire, bonded to the caster who summoned it.
 * While it lives, the caster takes 85% less damage from every source (see {@link ColossusBond}).
 *
 * It follows and defends its caster and fights with three attacks:
 * <ol>
 *   <li><b>Sword slash</b> - the default melee swing.</li>
 *   <li><b>Orb barrage</b> - volleys of {@link AzureOrbProjectile}s thrown from its hands.</li>
 *   <li><b>Ground smash</b> - it hammers the sword into the ground, sending out a huge shockwave.</li>
 * </ol>
 * It is a spectral construct: it has no collision with blocks and simply strides over the terrain.
 */
public class AzureColossus extends Mob {
    // ---- tuning ---------------------------------------------------------------------------------------
    public static final float HEIGHT = 25f;
    /** Blocks per tick while walking. */
    public static final float WALK_SPEED = 0.55f;

    public static final float SLASH_REACH = 20f;
    public static final float SMASH_MAX_RADIUS = 44f;

    // ---- animation / behaviour states (synced to the client for the model) ----------------------------
    public static final int STATE_IDLE = 0, STATE_WALK = 1, STATE_SLASH = 2, STATE_BARRAGE = 3, STATE_SMASH = 4;

    private static final int SLASH_HIT_TICK = 12, SLASH_END_TICK = 26;
    private static final int[] BARRAGE_TICKS = {22, 30, 38};
    private static final int BARRAGE_END_TICK = 60;
    private static final int SMASH_HIT_TICK = 34, SMASH_END_TICK = 66;

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(AzureColossus.class, EntityDataSerializers.INT);

    private UUID ownerId;
    private float power = 1f;
    private int lifeTicks = 20 * 60;

    private int stateTick;
    private int slashCooldown, barrageCooldown, smashCooldown;

    // ground smash shockwave (server side)
    private boolean shockwaveActive;
    private Vec3 shockwaveCenter = Vec3.ZERO;
    private float shockwaveRadius;
    private final Set<Integer> shockwaveHit = new HashSet<>();

    /** Client side: entity age at which the current state began (the model animates from this). */
    private int clientStateStart;

    public AzureColossus(EntityType<? extends AzureColossus> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.FOLLOW_RANGE, 80.0);
    }

    // ---- summoning ----------------------------------------------------------------------------------

    /**
     * Summons a colossus bonded to {@code owner}. Any colossus the owner already has is dismissed first.
     *
     * @param spellLevel 1-5: scales health, damage and duration through {@link AzureConfig}
     * @param lifeTicks how long it stays
     */
    public static AzureColossus summon(ServerLevel level, LivingEntity owner, int spellLevel) {
        AzureColossus old = ColossusBond.get(owner);
        if (old != null) {
            old.dissolve();
        }
        AzureColossus c = new AzureColossus(ModEntities.AZURE_COLOSSUS.get(), level);
        c.ownerId = owner.getUUID();
        c.power = (float) AzureConfig.damageMult(spellLevel);
        c.lifeTicks = AzureConfig.durationTicks(spellLevel);
        c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(AzureConfig.BASE_HEALTH.get() * AzureConfig.healthMult(spellLevel));
        c.setHealth(c.getMaxHealth());

        Vec3 look = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z);
        look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 spot = owner.position().subtract(look.scale(10.0));
        c.moveTo(spot.x, owner.getY(), spot.z, owner.getYRot(), 0f);
        c.yBodyRot = owner.getYRot();
        c.yHeadRot = owner.getYRot();
        level.addFreshEntity(c);
        ColossusBond.bind(owner, c);

        // summoning effects: a ring of blue fire and a pillar of flame
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 4.0f, 0.6f);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0f, 0.5f);
        FX.emit(level, ModParticles.AZURE_RING_FLAT.get(), spot.x, spot.y + 0.2, spot.z, 0, 22, 0, 0, 1.0);
        FX.emit(level, ModParticles.AZURE_GLOW.get(), spot.x, spot.y + 2, spot.z, 0, 10, 0, 0, 1.0);
        for (int i = 0; i < 60; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = level.random.nextDouble() * 6;
            FX.emit(level, ModParticles.AZURE_AURA.get(), spot.x + Math.cos(a) * r, spot.y, spot.z + Math.sin(a) * r, 0,
                    0, 0.25 + level.random.nextDouble() * 0.5, 0, 1.0);
        }
        return c;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(STATE, STATE_IDLE);
    }

    // ---- accessors ----------------------------------------------------------------------------------

    public int getState() {
        return this.entityData.get(STATE);
    }

    private void setState(int state) {
        this.entityData.set(STATE, state);
        this.stateTick = 0;
    }

    /** Client: ticks (with partial) since the current animation state started. */
    public float getStateTime(float ageInTicks) {
        return Math.max(0f, ageInTicks - clientStateStart);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key)) {
            this.clientStateStart = this.tickCount;
        }
    }

    public Entity getOwnerEntity() {
        if (ownerId != null && this.level() instanceof ServerLevel server) {
            return server.getEntity(ownerId);
        }
        return null;
    }

    public boolean isOwnedBy(Entity e) {
        return e != null && ownerId != null && ownerId.equals(e.getUUID());
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    // ---- not a normal mob ---------------------------------------------------------------------------

    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.AMETHYST_BLOCK_BREAK; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && (isOwnedBy(attacker) || attacker == this)) {
            return false; // the caster cannot hurt their own colossus
        }
        return super.hurt(source, amount);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            ColossusBond.unbind(this);
        }
        super.remove(reason);
    }

    @Override
    protected void tickDeath() {
        // dissolve straight away instead of falling over
        if (!this.level().isClientSide && !this.isRemoved()) {
            dissolve();
        }
    }

    /** Ends the colossus with a burst of blue fire. */
    public void dissolve() {
        if (this.level() instanceof ServerLevel server && !this.isRemoved()) {
            Vec3 p = this.position();
            server.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_DEATH, SoundSource.PLAYERS, 3.0f, 0.7f);
            FX.emit(server, ModParticles.AZURE_RING_FLAT.get(), p.x, p.y + 0.2, p.z, 0, 24, 0, 0, 1.0);
            for (int i = 0; i < 70; i++) {
                double a = server.random.nextDouble() * Math.PI * 2;
                double r = server.random.nextDouble() * 4;
                FX.emit(server, ModParticles.AZURE_AURA.get(), p.x + Math.cos(a) * r, p.y + server.random.nextDouble() * HEIGHT, p.z + Math.sin(a) * r, 0,
                        0, 0.1 + server.random.nextDouble() * 0.3, 0, 1.0);
            }
        }
        this.discard();
    }

    // ---- main loop ----------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientAura();
        }
    }

    /** Client: licks of blue fire flowing up the colossus' body. */
    private void clientAura() {
        if (this.isRemoved()) return;
        double base = this.getY();
        for (int i = 0; i < 3; i++) {
            double h = this.random.nextDouble() * HEIGHT;
            double w = 2.0 + (h > 12 ? 1.5 : 0.5);
            double a = this.random.nextDouble() * Math.PI * 2;
            this.level().addParticle(ModParticles.AZURE_AURA.get(),
                    this.getX() + Math.cos(a) * w, base + h, this.getZ() + Math.sin(a) * w,
                    0, 0.05 + this.random.nextDouble() * 0.12, 0);
        }
        // fire pouring off the sword arm / hands while attacking
        if (getState() == STATE_SMASH || getState() == STATE_SLASH) {
            Vec3 f = forward();
            Vec3 r = right();
            for (int i = 0; i < 4; i++) {
                double t = this.random.nextDouble();
                Vec3 pos = this.position().add(f.scale(6 + t * 16)).add(r.scale(5.0)).add(0, 12 + (1 - t) * 2, 0);
                this.level().addParticle(ModParticles.AZURE_FLAME.get(), pos.x, pos.y, pos.z, 0, 0.05, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel server)) return;

        Entity ownerEntity = getOwnerEntity();
        if (!(ownerEntity instanceof LivingEntity owner) || !owner.isAlive() || owner.level() != this.level()) {
            dissolve();
            return;
        }
        if (--lifeTicks <= 0) {
            dissolve();
            return;
        }

        if (slashCooldown > 0) slashCooldown--;
        if (barrageCooldown > 0) barrageCooldown--;
        if (smashCooldown > 0) smashCooldown--;

        if (this.tickCount % 8 == 0) {
            refreshTarget(owner);
        }
        if (this.tickCount % 6 == 0) {
            // faint blue flames on the bonded caster, so the bond is visible
            Vec3 o = owner.position();
            FX.emit(server, ModParticles.AZURE_FLAME.get(), o.x, o.y + owner.getBbHeight() * 0.5, o.z, 0,
                    (this.random.nextDouble() - 0.5) * 0.06, 0.06, (this.random.nextDouble() - 0.5) * 0.06, 1.0);
        }

        tickShockwave(server, owner);

        // keep the sword/hands facing and the legs grounded while the brain runs
        LivingEntity target = this.getTarget();
        switch (getState()) {
            case STATE_SLASH -> runSlash(server, owner, target);
            case STATE_BARRAGE -> runBarrage(server, owner, target);
            case STATE_SMASH -> runSmash(server, owner, target);
            default -> runFreeMovement(server, owner, target);
        }
    }

    @Override
    public void travel(Vec3 input) {
        // movement is set directly in the brain; just let the inherited code apply the velocity and drive the walk cycle
        super.travel(Vec3.ZERO);
    }

    // ---- movement -----------------------------------------------------------------------------------

    private Vec3 forward() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** The colossus' own right-hand side (the sword arm). */
    private Vec3 right() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
    }

    private void faceToward(Vec3 point, float maxTurn) {
        double dx = point.x - this.getX();
        double dz = point.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-3) return;
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(this.getYRot(), want, maxTurn);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** Cheap surface height, only used to place shockwave particles. */
    private double groundY(double x, double z, double reference) {
        int h = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return Math.abs(h - reference) > 14 ? reference : h;
    }

    private boolean solidAt(BlockPos pos) {
        if (!this.level().hasChunkAt(pos)) return false;
        BlockState st = this.level().getBlockState(pos);
        if (st.is(BlockTags.LEAVES)) return false; // never stand on tree canopies
        return st.blocksMotion() || !st.getFluidState().isEmpty();
    }

    /** Top of the floor in the column (x, z), found by walking up out of solid or down through air from near {@code refY}. */
    private double floorAt(double x, double z, double refY) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int y = Mth.floor(refY) + 3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, y, bz);
        if (solidAt(pos)) {
            int limit = y + 14;
            while (y < limit && solidAt(pos.set(bx, y, bz))) y++;
        } else {
            int limit = y - 40;
            while (y > limit && !solidAt(pos.set(bx, y - 1, bz))) y--;
        }
        return y;
    }

    /**
     * Sets the velocity for this tick: horizontal part from {@code horizontal}; the vertical part glides the feet to
     * the highest floor under the colossus' footprint, so it never sinks into slopes, stairs or steps.
     */
    private void applyMotion(Vec3 horizontal, double referenceY) {
        Vec3 next = this.position().add(horizontal);
        double ref = this.getY();
        double wantY = floorAt(next.x, next.z, ref);
        double r = 3.5;
        wantY = Math.max(wantY, floorAt(next.x + r, next.z, ref));
        wantY = Math.max(wantY, floorAt(next.x - r, next.z, ref));
        wantY = Math.max(wantY, floorAt(next.x, next.z + r, ref));
        wantY = Math.max(wantY, floorAt(next.x, next.z - r, ref));
        double diff = wantY - this.getY();
        // rise fast (never clip into terrain), settle down gently
        double vy = diff > 0 ? Math.min(diff, 3.0) : Math.max(diff * 0.3, -1.0);
        this.setDeltaMovement(horizontal.x, vy, horizontal.z);
    }

    private void runFreeMovement(ServerLevel server, LivingEntity owner, LivingEntity target) {
        double speed = WALK_SPEED;
        Vec3 goal;
        double stop;
        double refY;
        if (target != null) {
            goal = target.position();
            stop = 12.0;
            refY = target.getY();
        } else {
            Vec3 look = new Vec3(-Mth.sin(owner.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(owner.getYRot() * Mth.DEG_TO_RAD));
            Vec3 side = new Vec3(-look.z, 0, look.x);
            goal = owner.position().subtract(look.scale(9.0)).add(side.scale(5.0));
            stop = 3.0;
            refY = owner.getY();
        }

        // too far from the caster: step through the fire to their side
        if (this.distanceToSqr(owner) > 70.0 * 70.0) {
            Vec3 o = owner.position();
            this.moveTo(o.x - 8, o.y, o.z - 8, owner.getYRot(), 0f);
            FX.emit(server, ModParticles.AZURE_RING_FLAT.get(), o.x - 8, o.y + 0.2, o.z - 8, 0, 18, 0, 0, 1.0);
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        double dx = goal.x - this.getX();
        double dz = goal.z - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        Vec3 step = Vec3.ZERO;
        boolean walking = dist > stop;
        if (walking) {
            double s = Math.min(speed * (dist > 30 ? 1.5 : 1.0), dist - stop + 0.1);
            step = new Vec3(dx / dist * s, 0, dz / dist * s);
            faceToward(goal, 6f);
        } else if (target != null) {
            faceToward(target.position(), 8f);
        } else {
            float yaw = Mth.approachDegrees(this.getYRot(), owner.getYRot(), 4f);
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
        }
        applyMotion(step, refY);

        int wanted = walking ? STATE_WALK : STATE_IDLE;
        if (getState() != wanted && (getState() == STATE_IDLE || getState() == STATE_WALK)) {
            setState(wanted);
        }
        if (walking && this.tickCount % 12 == 0) {
            this.playSound(SoundEvents.IRON_GOLEM_STEP, 4.0f, 0.4f);
        }

        if (target != null) {
            chooseAttack(server, owner, target);
        }
    }

    // ---- target selection ---------------------------------------------------------------------------

    private boolean valid(LivingEntity owner, LivingEntity e) {
        if (e == null || e == owner || e == this || !e.isAlive() || e instanceof AzureColossus) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (e.isAlliedTo(owner) || DamageSources.isFriendlyFireBetween(owner, e)) return false;
        if (e instanceof Mob m && m.getTarget() == null && !(e instanceof Enemy) && m.getLastHurtByMob() != owner) {
            // peaceful bystanders are only fought if the owner picked the fight
            return false;
        }
        return this.distanceToSqr(e) < 90.0 * 90.0;
    }

    private void refreshTarget(LivingEntity owner) {
        LivingEntity current = this.getTarget();
        if (current != null && !valid(owner, current)) {
            this.setTarget(null);
            current = null;
        }
        LivingEntity best = null;
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 && valid(owner, attacker)) {
            best = attacker;
        } else {
            LivingEntity victim = owner.getLastHurtMob();
            if (victim != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200 && valid(owner, victim)) {
                best = victim;
            }
        }
        if (best == null && current == null) {
            double bestDist = Double.MAX_VALUE;
            AABB box = owner.getBoundingBox().inflate(48, 24, 48);
            for (Mob cand : this.level().getEntitiesOfClass(Mob.class, box, c -> c instanceof Enemy && valid(owner, c))) {
                double d = cand.distanceToSqr(owner);
                if (d < bestDist) {
                    bestDist = d;
                    best = cand;
                }
            }
        }
        if (best != null) {
            this.setTarget(best);
        }
    }

    private int enemiesNear(LivingEntity owner, double radius) {
        AABB box = this.getBoundingBox().inflate(radius, 10, radius);
        return this.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof Enemy && valid(owner, e) && e.distanceToSqr(this) < radius * radius).size();
    }

    // ---- attack selection ---------------------------------------------------------------------------

    private void chooseAttack(ServerLevel server, LivingEntity owner, LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double reach = SLASH_REACH * 0.85 + target.getBbWidth() * 0.5;

        if (smashCooldown <= 0 && dist < 30 && (enemiesNear(owner, 28) >= 3 || (dist < 18 && this.random.nextInt(4) == 0))) {
            setState(STATE_SMASH);
            this.playSound(SoundEvents.WARDEN_ROAR, 5.0f, 0.5f);
        } else if (slashCooldown <= 0 && dist <= reach) {
            setState(STATE_SLASH);
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 5.0f, 0.4f);
        } else if (barrageCooldown <= 0 && dist >= 14 && dist < 75) {
            setState(STATE_BARRAGE);
            this.playSound(SoundEvents.BLAZE_AMBIENT, 5.0f, 0.4f);
        }
    }

    private void holdPosition(double referenceY) {
        applyMotion(Vec3.ZERO, referenceY);
    }

    private void endAttack() {
        setState(STATE_IDLE);
    }

    // ---- attack 1: sword slash ------------------------------------------------------------------------

    private void runSlash(ServerLevel server, LivingEntity owner, LivingEntity target) {
        holdPosition(this.getY());
        stateTick++;
        if (target != null && stateTick < SLASH_HIT_TICK) {
            faceToward(target.position(), 14f);
        }
        if (stateTick == SLASH_HIT_TICK) {
            doSlash(server, owner);
        }
        if (stateTick >= SLASH_END_TICK) {
            slashCooldown = 24;
            endAttack();
        }
    }

    private void doSlash(ServerLevel server, LivingEntity owner) {
        Vec3 base = this.position();
        Vec3 f = forward();
        Vec3 attackerSource = base;
        float dmg = (float) (AzureConfig.SLASH_DAMAGE.get() * power);

        AABB box = new AABB(base.x - SLASH_REACH, base.y - 4, base.z - SLASH_REACH, base.x + SLASH_REACH, base.y + 16, base.z + SLASH_REACH);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> canHurt(owner, e))) {
            Vec3 d = e.position().subtract(base);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            double len = flat.length();
            if (len > SLASH_REACH + e.getBbWidth() * 0.5) continue;
            if (len > 2.5 && flat.normalize().dot(f) < 0.2) continue; // roughly a 160 degree sweep in front
            hurtWithSpell(e, dmg, owner);
            Vec3 push = (len < 1.0E-3 ? f : flat.normalize()).scale(1.6 * (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.45, push.z));
            e.hurtMarked = true;
        }

        // blue fire trailing the blade through its sweep
        server.playSound(null, base.x, base.y, base.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.5f);
        Vec3 side = right();
        for (int arc = -6; arc <= 6; arc++) {
            double ang = arc / 6.0 * 1.2; // +-69 degrees
            double cos = Math.cos(ang), sin = Math.sin(ang);
            Vec3 dir = f.scale(cos).add(side.scale(sin));
            for (double r = 6; r <= SLASH_REACH; r += 3.5) {
                Vec3 p = base.add(dir.scale(r)).add(0, 2.5 + (r / SLASH_REACH) * 3, 0);
                FX.emit(server, ModParticles.AZURE_FLAME.get(), p.x, p.y, p.z, 0, dir.x * 0.3, 0.05, dir.z * 0.3, 1.0);
            }
        }
        Vec3 tip = base.add(f.scale(SLASH_REACH * 0.8)).add(0, 1.5, 0);
        FX.emit(server, ModParticles.AZURE_RING_FLAT.get(), tip.x, tip.y, tip.z, 0, 10, 0, 0, 1.0);
        FX.emit(server, ModParticles.AZURE_BLAST.get(), tip.x, tip.y + 2, tip.z, 0, 0, 0.05, 0, 1.0);
    }

    // ---- attack 2: orb barrage ------------------------------------------------------------------------

    private void runBarrage(ServerLevel server, LivingEntity owner, LivingEntity target) {
        holdPosition(this.getY());
        stateTick++;
        if (target != null && stateTick < BARRAGE_TICKS[BARRAGE_TICKS.length - 1]) {
            faceToward(target.position(), 12f);
        }
        for (int i = 0; i < BARRAGE_TICKS.length; i++) {
            if (stateTick == BARRAGE_TICKS[i]) {
                fireVolley(server, owner, target, i);
            }
        }
        if (stateTick >= BARRAGE_END_TICK) {
            barrageCooldown = 200;
            endAttack();
        }
    }

    private void fireVolley(ServerLevel server, LivingEntity owner, LivingEntity target, int index) {
        Vec3 f = forward();
        Vec3 r = right();
        boolean rightHand = index % 2 == 0;
        Vec3 hand = this.position().add(f.scale(7.0)).add(r.scale(rightHand ? 5.0 : -5.0)).add(0, 13.5, 0);

        Vec3 aimPoint = target != null && target.isAlive()
                ? target.getBoundingBox().getCenter()
                : hand.add(f.scale(40));
        Vec3 aim = aimPoint.subtract(hand);
        if (aim.lengthSqr() < 1.0E-3) aim = f;
        aim = aim.normalize();

        server.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 4.0f, 0.5f);
        FX.emit(server, ModParticles.AZURE_GLOW.get(), hand.x, hand.y, hand.z, 0, 5, 0, 0, 1.0);
        FX.emit(server, ModParticles.AZURE_RING.get(), hand.x, hand.y, hand.z, 0, 7, 0, 0, 1.0);

        float[] fan = {-0.2f, 0f, 0.2f}; // about +-11 degrees
        for (float angle : fan) {
            AzureOrbProjectile orb = new AzureOrbProjectile(server, this);
            orb.setPos(hand.x, hand.y, hand.z);
            orb.launch(aim.yRot(angle), 1.15f).setRadius(7.5f).setMaxLife(110);
            if (angle == 0f && target != null) {
                orb.setHoming(aimPoint, 0.05f);
            }
            server.addFreshEntity(orb);
        }
    }

    // ---- attack 3: ground smash + shockwave ------------------------------------------------------------

    private void runSmash(ServerLevel server, LivingEntity owner, LivingEntity target) {
        holdPosition(this.getY());
        stateTick++;
        if (target != null && stateTick < SMASH_HIT_TICK - 6) {
            faceToward(target.position(), 8f);
        }
        if (stateTick == SMASH_HIT_TICK) {
            Vec3 f = forward();
            shockwaveCenter = this.position().add(f.scale(11.0));
            shockwaveRadius = 0f;
            shockwaveActive = true;
            shockwaveHit.clear();

            Vec3 c = shockwaveCenter;
            server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 8.0f, 0.4f);
            server.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 8.0f, 0.3f);
            FX.emit(server, ModParticles.AZURE_GLOW.get(), c.x, c.y + 2, c.z, 0, 16, 0, 0, 1.0);
            FX.emit(server, ModParticles.AZURE_RING.get(), c.x, c.y + 2, c.z, 0, 24, 0, 0, 1.0);
            for (int i = 0; i < 40; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = this.random.nextDouble() * 6;
                FX.emit(server, ModParticles.AZURE_BLAST.get(), c.x + Math.cos(a) * r, c.y + 1, c.z + Math.sin(a) * r, 0,
                        Math.cos(a) * 0.15, 0.15 + this.random.nextDouble() * 0.2, Math.sin(a) * 0.15, 1.0);
            }
        }
        if (stateTick >= SMASH_END_TICK) {
            smashCooldown = 240;
            endAttack();
        }
    }

    /** Expands the smash shockwave ring and hurts everything it passes over. Runs every tick, independent of state. */
    private void tickShockwave(ServerLevel server, LivingEntity owner) {
        if (!shockwaveActive) return;
        float prev = shockwaveRadius;
        shockwaveRadius += 2.3f;
        Vec3 c = shockwaveCenter;

        if (shockwaveRadius >= SMASH_MAX_RADIUS) {
            shockwaveActive = false;
        }

        // ground ring of blue fire
        FX.emit(server, ModParticles.AZURE_RING_FLAT.get(), c.x, c.y + 0.3, c.z, 0, shockwaveRadius * 1.02, 0, 0, 1.0);
        int points = Math.max(12, (int) (shockwaveRadius * 1.6));
        for (int i = 0; i < points; i++) {
            double a = (i + this.random.nextDouble()) / points * Math.PI * 2;
            double px = c.x + Math.cos(a) * shockwaveRadius;
            double pz = c.z + Math.sin(a) * shockwaveRadius;
            double py = groundY(px, pz, c.y);
            FX.emit(server, ModParticles.AZURE_FLAME.get(), px, py + 0.5, pz, 0, Math.cos(a) * 0.1, 0.35 + this.random.nextDouble() * 0.25, Math.sin(a) * 0.1, 1.0);
            if (i % 3 == 0) {
                FX.emit(server, ModParticles.AZURE_BLAST.get(), px, py + 1, pz, 0, 0, 0.12, 0, 1.0);
            }
        }

        float dmg = (float) (AzureConfig.SMASH_DAMAGE.get() * power);
        double outer = shockwaveRadius + 1.0;
        AABB box = new AABB(c.x - outer, c.y - 12, c.z - outer, c.x + outer, c.y + 22, c.z + outer);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> canHurt(owner, e))) {
            if (shockwaveHit.contains(e.getId())) continue;
            double ex = e.getX() - c.x, ez = e.getZ() - c.z;
            double d = Math.sqrt(ex * ex + ez * ez) - e.getBbWidth() * 0.5;
            if (d > shockwaveRadius + 1.0 || d < prev - 3.0) continue;
            shockwaveHit.add(e.getId());
            hurtWithSpell(e, dmg, owner);
            Vec3 dir = new Vec3(ex, 0, ez);
            dir = dir.lengthSqr() < 1.0E-3 ? forward() : dir.normalize();
            double res = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            e.setDeltaMovement(dir.x * 2.2 * res, 0.9 * res, dir.z * 2.2 * res);
            e.hurtMarked = true;
        }
    }

    // ---- damage helpers -------------------------------------------------------------------------------

    private boolean canHurt(LivingEntity owner, LivingEntity e) {
        if (e == owner || e == this || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof AzureColossus) return false;
        if (e instanceof Player p && p.isCreative()) return false;
        return !(e.isAlliedTo(owner) || DamageSources.isFriendlyFireBetween(owner, e));
    }

    private void hurtWithSpell(LivingEntity victim, float amount, LivingEntity owner) {
        victim.invulnerableTime = 0;
        DamageSources.applyDamage(victim, amount, ModSpells.AZURE_COLOSSUS.get().getDamageSource(this, owner));
    }

    // ---- persistence (the colossus is noSave, but keep the data tidy in case it is ever saved) -------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("Power", power);
        tag.putInt("Life", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        if (tag.contains("Power")) power = tag.getFloat("Power");
        if (tag.contains("Life")) lifeTicks = tag.getInt("Life");
    }
}
