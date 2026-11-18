package com.example.jjkspells.yhwach.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yhwach.VollstreckungEvents;
import com.example.jjkspells.yhwach.YhwachRegistry;
import com.example.jjkspells.yhwach.client.AuswaehlenCamera;
import com.example.jjkspells.yhwach.spell.AuswaehlenSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Controller of "Auswählen". Timeline (ticks since cast):
 * <pre>
 *   0 .. SKY_IN                  golden insignia forms in the sky (client sky dim + camera low-angle reveal)
 *   MARK0 ..                     luminous marks appear over each selected enemy (staggered)
 *   PILLAR0 .. +DESCEND          pillars of light descend; on landing: divine-light hit (350 + 20%)
 *   FLOW0 ..                     energy streams from every target up its pillar and into the halo behind the caster
 *   HALO0 .. COLLAPSE0           halo grows to full size
 *   COLLAPSE0 .. COLLAPSE_END    halo collapses into the caster
 *   RECLAIM                      final reclamation hit (200 + 15%)
 *   .. TOTAL                     sky returns to normal
 * </pre>
 * The client only renders; nothing about weather or the world is changed.
 */
public class AuswaehlenEntity extends Entity {
    public static final int SKY_IN = 40;
    public static final int MARK0 = 34;
    public static final int MARK_STAGGER = 3;
    public static final int PILLAR0 = 62;
    public static final int PILLAR_STAGGER = 3;
    public static final int DESCEND = 18;
    public static final int FLOW0 = 132;
    public static final int HALO0 = 150;
    public static final int COLLAPSE0 = 208;
    public static final int COLLAPSE_END = 234;
    public static final int RECLAIM = 228;
    public static final int PILLAR_END = 196;
    public static final int SKY_OUT0 = 226;
    public static final int SKY_OUT1 = 258;
    public static final int TOTAL = 264;
    public static final int CAMERA_END = 262;

    public static final UUID DAMAGE_MOD = UUID.fromString("5b8c7e12-3d0a-4a53-9c2e-6a0d11f0c003");
    public static final UUID RESIST_MOD = UUID.fromString("5b8c7e12-3d0a-4a53-9c2e-6a0d11f0c004");

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(AuswaehlenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> TARGETS = SynchedEntityData.defineId(AuswaehlenEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final List<UUID> targetUuids = new ArrayList<>();
    private final List<Vec3> targetStart = new ArrayList<>();
    private final List<Boolean> struck = new ArrayList<>();

    // client side
    private int[] clientIds = new int[0];
    private Vec3[] clientStart = new Vec3[0];
    private boolean parsed = false;
    private boolean cameraStarted = false;
    /** Horizontal direction the caster faced when the halo opened; the halo stands behind it. */
    public Vec3 haloDir = null;
    public Vec3 castDir = null;

    public AuswaehlenEntity(EntityType<? extends AuswaehlenEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static AuswaehlenEntity create(Level level, LivingEntity caster) {
        AuswaehlenEntity e = new AuswaehlenEntity(YhwachRegistry.AUSW, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        double r = AuswaehlenSpell.RANGE;
        Vec3 cp = caster.position();
        AABB box = new AABB(cp.x - r, cp.y - r, cp.z - r, cp.x + r, cp.y + r, cp.z + r);
        List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
                x -> x.isAlive() && !x.isSpectator() && x != caster && !(x instanceof ArmorStand) && !x.isAlliedTo(caster)
                        && x.distanceToSqr(caster) <= r * r));
        found.sort(Comparator.comparingDouble(x -> x.distanceToSqr(caster)));
        if (found.size() > AuswaehlenSpell.MAX_TARGETS) {
            found = found.subList(0, AuswaehlenSpell.MAX_TARGETS);
        }
        ListTag ids = new ListTag();
        ListTag px = new ListTag();
        ListTag py = new ListTag();
        ListTag pz = new ListTag();
        for (LivingEntity t : found) {
            e.targetUuids.add(t.getUUID());
            e.targetStart.add(t.position());
            e.struck.add(false);
            CompoundTag c = new CompoundTag();
            c.putInt("i", t.getId());
            ids.add(c);
            px.add(DoubleTag.valueOf(t.getX()));
            py.add(DoubleTag.valueOf(t.getY()));
            pz.add(DoubleTag.valueOf(t.getZ()));
        }
        CompoundTag root = new CompoundTag();
        root.put("ids", ids);
        root.put("x", px);
        root.put("y", py);
        root.put("z", pz);
        e.entityData.set(TARGETS, root);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(TARGETS, new CompoundTag());
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

    public int getOwnerId() {
        return this.entityData.get(OWNER_ID);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level().isClientSide) {
            AuswaehlenCamera.stop(this);
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

    // ------------------------------------------------------------------ client accessors

    private void parse() {
        if (parsed) {
            return;
        }
        CompoundTag root = this.entityData.get(TARGETS);
        if (!root.contains("ids", Tag.TAG_LIST)) {
            return;
        }
        ListTag ids = root.getList("ids", Tag.TAG_COMPOUND);
        ListTag px = root.getList("x", Tag.TAG_DOUBLE);
        ListTag py = root.getList("y", Tag.TAG_DOUBLE);
        ListTag pz = root.getList("z", Tag.TAG_DOUBLE);
        clientIds = new int[ids.size()];
        clientStart = new Vec3[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            clientIds[i] = ids.getCompound(i).getInt("i");
            clientStart[i] = new Vec3(px.getDouble(i), py.getDouble(i), pz.getDouble(i));
        }
        parsed = true;
    }

    public int targetCount() {
        parse();
        return clientIds.length;
    }

    /** Live position of target i (falls back to the cast-time position when the entity is gone or out of tracking range). */
    public Vec3 targetPos(int i, float pt) {
        parse();
        if (i < 0 || i >= clientIds.length) {
            return position();
        }
        Entity t = this.level().getEntity(clientIds[i]);
        if (t != null && t.isAlive()) {
            return t.getPosition(pt);
        }
        return clientStart[i];
    }

    public float targetHeight(int i) {
        parse();
        if (i < 0 || i >= clientIds.length) {
            return 1.8f;
        }
        Entity t = this.level().getEntity(clientIds[i]);
        return t != null ? t.getBbHeight() : 1.8f;
    }

    public Vec3 ownerPos(float pt) {
        Entity o = this.level().getEntity(getOwnerId());
        return o != null ? o.getPosition(pt) : position();
    }

    // ------------------------------------------------------------------ server

    private LivingEntity owner() {
        if (ownerUuid == null || !(this.level() instanceof ServerLevel sl)) {
            return null;
        }
        Entity e = sl.getEntity(ownerUuid);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private void serverTick() {
        if (tickCount > TOTAL) {
            discard();
            return;
        }
        LivingEntity owner = owner();
        if (owner != null) {
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        Vec3 c = position();
        if (tickCount == 1) {
            if (owner != null) {
                AttributeInstance inst = owner.getAttribute(ModAttributes.MAX_HP_DAMAGE.get());
                if (inst != null) {
                    inst.removeModifier(DAMAGE_MOD);
                    inst.addTransientModifier(new AttributeModifier(DAMAGE_MOD, "auswaehlen_damage", AuswaehlenSpell.CASTER_MAX_HP_DAMAGE_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
                    VollstreckungEvents.track(owner, DAMAGE_MOD, ModAttributes.MAX_HP_DAMAGE.get(), level().getGameTime() + AuswaehlenSpell.BUFF_TICKS);
                }
            }
            level().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 8.0f, 0.5f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 8.0f, 0.7f);
        }
        if (tickCount == HALO0) {
            level().playSound(null, c.x, c.y + 10, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 8.0f, 0.6f);
        }
        if (tickCount == COLLAPSE0) {
            level().playSound(null, c.x, c.y + 10, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 8.0f, 0.5f);
        }
        for (int i = 0; i < targetUuids.size(); i++) {
            int land = PILLAR0 + i * PILLAR_STAGGER + DESCEND;
            if (tickCount == PILLAR0 + i * PILLAR_STAGGER) {
                Vec3 s = targetStart.get(i);
                level().playSound(null, s.x, s.y, s.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 6.0f, 0.6f);
            }
            if (tickCount == land) {
                hit(owner, i, AuswaehlenSpell.DIVINE_FLAT, AuswaehlenSpell.DIVINE_PERCENT, true);
            }
            if (tickCount == RECLAIM) {
                hit(owner, i, AuswaehlenSpell.RECLAIM_FLAT, AuswaehlenSpell.RECLAIM_PERCENT, false);
            }
        }
        if (tickCount == RECLAIM && owner != null) {
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0f, 0.6f);
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 6.0f, 1.6f);
        }
    }

    private LivingEntity targetAt(int i) {
        if (!(this.level() instanceof ServerLevel sl)) {
            return null;
        }
        Entity e = sl.getEntity(targetUuids.get(i));
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private void hit(LivingEntity owner, int i, double flat, double percent, boolean first) {
        LivingEntity t = targetAt(i);
        if (t == null) {
            return;
        }
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0;
        if (flat > 0) io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, (float) flat, dsrc);
        if (t.isAlive() && percent > 0) com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent / 100.0), owner, dsrc);   // respects Max Health Damage / Resistance (the caster's buff and the target's resist loss are attribute modifiers)
        com.example.jjkspells.spells.SpellDamage.irradiate(t, 120);
        t.setDeltaMovement(keep);
        Vec3 p = t.position();
        if (first) {
            AttributeInstance inst = t.getAttribute(ModAttributes.MAX_HP_RESIST.get());
            if (inst != null) {
                inst.removeModifier(RESIST_MOD);
                inst.addTransientModifier(new AttributeModifier(RESIST_MOD, "auswaehlen_resist", -AuswaehlenSpell.TARGET_RESIST_LOSS, AttributeModifier.Operation.ADDITION));
                VollstreckungEvents.track(t, RESIST_MOD, ModAttributes.MAX_HP_RESIST.get(), level().getGameTime() + AuswaehlenSpell.BUFF_TICKS);
            }
            level().playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 1.3f);
            level().playSound(null, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.5f);
            level().playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 4.0f, 0.6f);
        } else {
            level().playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 4.0f, 1.9f);
            level().playSound(null, p.x, p.y, p.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.8f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        if (!cameraStarted) {
            cameraStarted = true;
            Entity o = this.level().getEntity(getOwnerId());
            if (o != null) {
                Vec3 look = o.getLookAngle();
                Vec3 h = new Vec3(look.x, 0, look.z);
                castDir = h.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : h.normalize();
            } else {
                castDir = new Vec3(0, 0, 1);
            }
            AuswaehlenCamera.start(this);
        }
        if (tickCount == HALO0) {
            Entity o = this.level().getEntity(getOwnerId());
            if (o != null) {
                Vec3 look = o.getLookAngle();
                Vec3 h = new Vec3(look.x, 0, look.z);
                haloDir = h.lengthSqr() < 1.0E-4 ? castDir : h.normalize();
            }
        }
        if (haloDir == null && tickCount > HALO0) {
            haloDir = castDir;
        }
        // reishi fragments around the caster while the halo gathers
        if (tickCount > HALO0 && tickCount < COLLAPSE_END && tickCount % 2 == 0) {
            Vec3 c = ownerPos(1.0f);
            for (int k = 0; k < 2; k++) {
                level().addParticle(ParticleTypes.END_ROD, c.x + (random.nextDouble() - 0.5) * 3, c.y + random.nextDouble() * 3, c.z + (random.nextDouble() - 0.5) * 3, 0, 0.1, 0);
            }
        }
        if (tickCount == RECLAIM) {
            Vec3 c = ownerPos(1.0f);
            for (int i = 0; i < 40; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double s = 0.2 + random.nextDouble() * 0.8;
                level().addParticle(ParticleTypes.END_ROD, c.x, c.y + 1.2, c.z, Math.cos(a) * s, (random.nextDouble() - 0.3) * 0.6, Math.sin(a) * s);
            }
        }
    }
}
