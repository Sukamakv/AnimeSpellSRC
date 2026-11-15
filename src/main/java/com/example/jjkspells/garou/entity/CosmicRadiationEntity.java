package com.example.jjkspells.garou.entity;

import com.example.jjkspells.garou.GarouRegistry;
import com.example.jjkspells.garou.spell.GarouCosmicRadiationSpell;
import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller of Cosmic Radiation. Non-living, follows the caster, lives AURA_TICKS + LINGER_TICKS at most.
 *
 * Server: every 4 ticks scans for targets inside the aura (refreshing their radiation to LINGER_TICKS), every 40 ticks
 * deals the damage pulse to everybody irradiated (inside or lingering), keeps the -15% speed modifier on them and
 * mirrors the target set + a pulse counter to the client through synched data (no custom packets).
 * Client: all visuals, in com.example.jjkspells.garou.client.CosmicRadiationFx / CosmicRadiationRenderer.
 */
public class CosmicRadiationEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(CosmicRadiationEntity.class, EntityDataSerializers.INT);
    /** Incremented by the server at every damage pulse; the client starts a wave when it changes. */
    private static final EntityDataAccessor<Integer> PULSES =
            SynchedEntityData.defineId(CosmicRadiationEntity.class, EntityDataSerializers.INT);
    /** {"in": int[] ids inside the aura, "out": int[] ids still irradiated after leaving it}. */
    private static final EntityDataAccessor<CompoundTag> TARGETS =
            SynchedEntityData.defineId(CosmicRadiationEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** Fixed id of the movement speed modifier. */
    public static final UUID SLOW_ID = UUID.fromString("5b8c2f64-7d11-4e0a-9a3e-6c1f0d9a47e2");

    private UUID ownerUuid;
    /** Server: irradiated entity -> tickCount at which its radiation expires. */
    private final Map<UUID, Integer> irradiated = new HashMap<>();
    private int[] lastIn = new int[0];
    private int[] lastOut = new int[0];

    // ---- client-side cache, filled from the synched data (read by the renderer / fx class) ----
    public int[] clientIn = new int[0];
    public int[] clientOut = new int[0];
    public int clientPulses;
    /** Client visual state (com.example.jjkspells.garou.client.CosmicRadiationFx); kept as Object so this class stays server-safe. */
    public Object fx;

    public CosmicRadiationEntity(EntityType<? extends CosmicRadiationEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static CosmicRadiationEntity create(Level level, LivingEntity owner) {
        CosmicRadiationEntity e = new CosmicRadiationEntity(GarouRegistry.COSMIC_RADIATION_AURA, level);
        e.ownerUuid = owner.getUUID();
        e.entityData.set(OWNER_ID, owner.getId());
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(PULSES, 0);
        this.entityData.define(TARGETS, new CompoundTag());
    }

    public int getOwnerId() {
        return this.entityData.get(OWNER_ID);
    }

    public int getPulseCounter() {
        return this.entityData.get(PULSES);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == TARGETS) {
            CompoundTag t = this.entityData.get(TARGETS);
            clientIn = t.getIntArray("in");
            clientOut = t.getIntArray("out");
        }
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) CosmicRadiationHooks.ACTIVE.add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        if (!level().isClientSide) {
            CosmicRadiationHooks.ACTIVE.remove(this);
            releaseAll();
        }
        super.onRemovedFromWorld();
    }

    public boolean isOwnedBy(Entity e) {
        return e != null && ownerUuid != null && ownerUuid.equals(e.getUUID());
    }

    public boolean isIrradiated(UUID id) {
        return irradiated.containsKey(id);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            com.example.jjkspells.garou.client.CosmicRadiationFx.tick(this);
        } else {
            serverTick((ServerLevel) level());
        }
    }

    private void serverTick(ServerLevel sl) {
        Entity o = ownerUuid == null ? null : sl.getEntity(ownerUuid);
        if (!(o instanceof LivingEntity owner) || !owner.isAlive()
                || tickCount >= GarouCosmicRadiationSpell.AURA_TICKS + GarouCosmicRadiationSpell.LINGER_TICKS) {
            discard();
            return;
        }
        this.entityData.set(OWNER_ID, owner.getId());
        this.setPos(owner.getX(), owner.getY(), owner.getZ());

        boolean auraOn = tickCount < GarouCosmicRadiationSpell.AURA_TICKS;
        if (!auraOn && irradiated.isEmpty()) {
            discard();
            return;
        }
        if (tickCount == 1) {
            sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 0.8f, 0.55f);
        }

        // 1. who stands inside the aura right now
        if (auraOn && tickCount % 4 == 0) scan(sl, owner);

        // 2. expire / drop invalid targets
        boolean changed = false;
        for (Iterator<Map.Entry<UUID, Integer>> it = irradiated.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> en = it.next();
            Entity t = sl.getEntity(en.getKey());
            if (!(t instanceof LivingEntity le) || !le.isAlive() || tickCount >= en.getValue()) {
                it.remove();
                if (t instanceof LivingEntity le2) releaseTarget(le2);
                changed = true;
            }
        }

        // 3. the damage pulse (a visual wave starts on the client at the same moment)
        int first = GarouCosmicRadiationSpell.FIRST_PULSE_TICK;
        if (tickCount >= first && (tickCount - first) % GarouCosmicRadiationSpell.PULSE_TICKS == 0) {
            if (auraOn) {
                this.entityData.set(PULSES, this.entityData.get(PULSES) + 1);
                sl.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 1.2f, 0.5f);
            }
            pulse(sl, owner);
        }

        // 4. mirror the target set to the client
        if (changed || tickCount % 4 == 0) syncTargets();
    }

    private void scan(ServerLevel sl, LivingEntity owner) {
        double r = GarouCosmicRadiationSpell.RADIUS;
        Vec3 c = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r).inflate(2.0);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, e -> isValidTarget(owner, e))) {
            if (distanceToBox(c, t.getBoundingBox()) <= r) {
                if (irradiated.put(t.getUUID(), tickCount + GarouCosmicRadiationSpell.LINGER_TICKS) == null) {
                    applySlow(t);
                }
            }
        }
    }

    private void pulse(ServerLevel sl, LivingEntity owner) {
        DamageSource src = GarouRegistry.COSMIC_RADIATION.getDamageSource(owner, owner);
        List<UUID> ids = new ArrayList<>(irradiated.keySet());
        for (UUID id : ids) {
            Entity t = sl.getEntity(id);
            if (!(t instanceof LivingEntity le) || !le.isAlive()) continue;
            try {
                hit(owner, le, src);
            } catch (Throwable ex) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("garou cosmic radiation pulse failed", ex);
            }
        }
    }

    /** 35 flat (a normal spell hit, knockback cancelled) + 2% max health (SpellDamage.percent). */
    private static void hit(LivingEntity owner, LivingEntity t, DamageSource src) {
        Vec3 keep = t.getDeltaMovement();
        t.invulnerableTime = 0; // radiation is not swallowed by hurt-frames
        DamageSources.applyDamage(t, GarouCosmicRadiationSpell.FLAT_DAMAGE, src);
        t.setDeltaMovement(keep);
        if (t.isAlive()) {
            SpellDamage.percent(t, t.getMaxHealth() * GarouCosmicRadiationSpell.MAX_HP_FRACTION, owner, src);
        }
    }

    // ------------------------------------------------------------------------------------------ targets

    /** Enemy rules: never the caster, never allies (teams, owned pets, the caster's summons, party/PvP rules). */
    public static boolean isValidTarget(LivingEntity owner, Entity e) {
        if (!(e instanceof LivingEntity le) || e == owner || !le.isAlive() || le.isSpectator()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof Player p) {
            if (p.isCreative() || p.getAbilities().invulnerable) return false;
            if (owner instanceof Player op && !op.canHarmPlayer(p)) return false;
        }
        if (e instanceof OwnableEntity oe && owner.getUUID().equals(oe.getOwnerUUID())) return false;
        if (e instanceof IMagicSummon s && s.getSummoner() == owner) return false;
        if (owner.isAlliedTo(e)) return false;
        return !DamageSources.isFriendlyFireBetween(owner, e);
    }

    private static double distanceToBox(Vec3 p, AABB b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void applySlow(LivingEntity t) {
        AttributeInstance a = t.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null && a.getModifier(SLOW_ID) == null) {
            a.addTransientModifier(new AttributeModifier(SLOW_ID, "garou_cosmic_radiation",
                    -GarouCosmicRadiationSpell.SPEED_REDUCTION, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Removes the speed modifier unless another Cosmic Radiation still holds this target. */
    private void releaseTarget(LivingEntity t) {
        for (CosmicRadiationEntity other : CosmicRadiationHooks.ACTIVE) {
            if (other != this && other.isIrradiated(t.getUUID())) return;
        }
        AttributeInstance a = t.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.removeModifier(SLOW_ID);
    }

    private void releaseAll() {
        if (!(level() instanceof ServerLevel sl)) return;
        List<UUID> ids = new ArrayList<>(irradiated.keySet());
        irradiated.clear();
        for (UUID id : ids) {
            Entity t = sl.getEntity(id);
            if (t instanceof LivingEntity le) releaseTarget(le);
        }
    }

    private void syncTargets() {
        if (!(level() instanceof ServerLevel sl)) return;
        List<Integer> in = new ArrayList<>(), out = new ArrayList<>();
        for (Map.Entry<UUID, Integer> en : irradiated.entrySet()) {
            Entity t = sl.getEntity(en.getKey());
            if (t == null) continue;
            // refreshed within the last ~10 ticks = still inside the aura
            if (en.getValue() - tickCount > GarouCosmicRadiationSpell.LINGER_TICKS - 10) in.add(t.getId());
            else out.add(t.getId());
        }
        int[] a = in.stream().mapToInt(Integer::intValue).toArray();
        int[] b = out.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(a);
        Arrays.sort(b);
        if (!Arrays.equals(a, lastIn) || !Arrays.equals(b, lastOut)) {
            lastIn = a;
            lastOut = b;
            CompoundTag t = new CompoundTag();
            t.putIntArray("in", a);
            t.putIntArray("out", b);
            this.entityData.set(TARGETS, t);
        }
    }

    // ------------------------------------------------------------------------------------------ boilerplate

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 96.0 * 96.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(10.0);
    }
}
