package com.example.jjkspells.yhwach.entity;

import com.example.jjkspells.yhwach.YhwachRegistry;
import com.example.jjkspells.yhwach.client.SanktCamera;
import com.example.jjkspells.yhwach.spell.KirchenliedSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controller of "Kirchenlied: Sankt Zwinger". Timeline (ticks since cast):
 * <pre>
 *   0 .. EXPAND                  ground insignia expands (camera close-up on the seal)
 *   PILLAR_START ..              eight pillars rise one after another (camera pulls back)
 *   ARCH_START .. BUILD          arches, ribs, rings and ornaments form
 *   ACTIVE_START .. ACTIVE_END   enemies inside the walls are struck by the nearest pillar
 *   .. TOTAL                     everything dissolves
 * </pre>
 * Server keeps a small synced event list (ripples on the barrier, strikes); the client turns it into visuals.
 */
public class SanktZwingerEntity extends Entity {
    public static final int EXPAND = 30;
    public static final int PILLAR_START = 26;
    public static final int PILLAR_STAGGER = 3;
    public static final int PILLAR_RISE = 26;
    public static final int ARCH_START = 66;
    public static final int BUILD = 104;
    public static final int ACTIVE_START = 76;
    public static final int ACTIVE_END = BUILD + KirchenliedSpell.ACTIVE_SECONDS * 20;
    public static final int FADE = 24;
    public static final int TOTAL = ACTIVE_END + FADE;
    public static final int PILLARS = 8;

    public static final double SEAL_R = KirchenliedSpell.SEAL_RADIUS;
    public static final double PILLAR_R = SEAL_R * 0.65;
    public static final double WALL_R = PILLAR_R * Math.cos(Math.PI / PILLARS);
    public static final double WALL_H = 13.0;
    public static final double PILLAR_H = KirchenliedSpell.PILLAR_HEIGHT;
    public static final int RIPPLE_LIFE = 34;
    public static final int STRIKE_LIFE = 26;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SanktZwingerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> EVENTS = SynchedEntityData.defineId(SanktZwingerEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** Server side: live sanctuaries, so the hurt event handler can find them. */
    public static final List<SanktZwingerEntity> ACTIVE = new CopyOnWriteArrayList<>();

    /** Client side record of an event: {x, y, z, bornTick, pillar, id}. */
    public static final class Fx {
        public final int id;
        public final boolean strike;
        public final int pillar;
        public final Vec3 pos;
        public final int born;

        Fx(int id, boolean strike, int pillar, Vec3 pos, int born) {
            this.id = id;
            this.strike = strike;
            this.pillar = pillar;
            this.pos = pos;
            this.born = born;
        }
    }

    private UUID ownerUuid;
    private int nextEventId = 1;
    private int lastRippleTick = -99;
    private final List<CompoundTag> events = new ArrayList<>();
    private final Map<UUID, Integer> lastTrigger = new HashMap<>();
    private final Map<Integer, Integer> projectileSeen = new HashMap<>();

    public final List<Fx> clientFx = new ArrayList<>();
    private int clientLastId = 0;
    private boolean cameraStarted = false;

    public SanktZwingerEntity(EntityType<? extends SanktZwingerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SanktZwingerEntity create(Level level, LivingEntity caster) {
        SanktZwingerEntity e = new SanktZwingerEntity(YhwachRegistry.SANKT, level);
        e.ownerUuid = caster.getUUID();
        e.entityData.set(OWNER_ID, caster.getId());
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        return e;
    }

    /** Ground position of the pillar's base. */
    public static Vec3 pillarBase(Vec3 c, int k) {
        double a = k * Math.PI * 2.0 / PILLARS;
        return c.add(Math.cos(a) * PILLAR_R, 0, Math.sin(a) * PILLAR_R);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(EVENTS, new CompoundTag());
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
        ACTIVE.remove(this);
        if (this.level().isClientSide) {
            SanktCamera.stop(this);
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

    /** Add a synced event; {@code strike=false} is a ripple on the barrier. */
    private void addEvent(boolean strike, int pillar, Vec3 pos) {
        CompoundTag c = new CompoundTag();
        c.putInt("id", nextEventId++);
        c.putBoolean("s", strike);
        c.putInt("p", pillar);
        c.putDouble("x", pos.x);
        c.putDouble("y", pos.y);
        c.putDouble("z", pos.z);
        c.putInt("b", tickCount);
        events.add(c);
        syncEvents();
    }

    private void syncEvents() {
        events.removeIf(c -> tickCount - c.getInt("b") > 30);
        while (events.size() > 16) {
            events.remove(0);
        }
        ListTag l = new ListTag();
        l.addAll(events);
        CompoundTag root = new CompoundTag();
        root.put("l", l);
        this.entityData.set(EVENTS, root);
    }

    /** Called by the hurt handler / projectile scan: a light ripple where an attack meets the walls. */
    public void ripple(Vec3 worldPoint) {
        if (this.level().isClientSide || tickCount < ACTIVE_START || tickCount > ACTIVE_END || tickCount - lastRippleTick < 3) {
            return;
        }
        Vec3 c = position();
        double dx = worldPoint.x - c.x;
        double dz = worldPoint.z - c.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            return;
        }
        double y = Math.max(c.y + 1.0, Math.min(worldPoint.y, c.y + WALL_H - 1.0));
        Vec3 p = new Vec3(c.x + dx / len * WALL_R, y, c.z + dz / len * WALL_R);
        lastRippleTick = tickCount;
        addEvent(false, 0, p);
        level().playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.2f + level().random.nextFloat() * 0.5f);
        if (level() instanceof net.minecraft.server.level.ServerLevel rsl) {
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(rsl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.2f);   // attack meets the wall
            com.example.jjkspells.vfx.sprite.SpriteFx.sparks(rsl, p, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.0f);
        }
    }

    public boolean isInside(Vec3 p) {
        Vec3 c = position();
        double dx = p.x - c.x;
        double dz = p.z - c.z;
        return dx * dx + dz * dz <= WALL_R * WALL_R && p.y > c.y - 4 && p.y < c.y + PILLAR_H + 4;
    }

    private LivingEntity owner() {
        if (ownerUuid == null || !(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
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
        if (tickCount == 1) {
            ACTIVE.add(this);
            Vec3 c = position();
            level().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0f, 0.6f);
            level().playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 3.0f, 1.4f);
            if (level() instanceof net.minecraft.server.level.ServerLevel zsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(zsl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) (SEAL_R / 3.0));
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(zsl, c.add(0, 1.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3.0f);
            }
        }
        if (tickCount >= PILLAR_START && (tickCount - PILLAR_START) % PILLAR_STAGGER == 0 && (tickCount - PILLAR_START) / PILLAR_STAGGER < PILLARS) {
            Vec3 b = pillarBase(position(), (tickCount - PILLAR_START) / PILLAR_STAGGER);
            level().playSound(null, b.x, b.y, b.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 4.0f, 0.5f);
            level().playSound(null, b.x, b.y, b.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 3.0f, 0.5f);
            if (level() instanceof net.minecraft.server.level.ServerLevel zsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(zsl, b, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 0.8f);
            }
        }
        if (tickCount == ARCH_START) {
            Vec3 c = position();
            level().playSound(null, c.x, c.y + 10, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 4.0f, 1.2f);
            if (level() instanceof net.minecraft.server.level.ServerLevel zsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(zsl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, (float) (WALL_R / 3.0));   // arches form
                com.example.jjkspells.vfx.sprite.SpriteFx.burst(zsl, c.add(0, 10.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 4.0f);
            }
        }
        if (tickCount == ACTIVE_END) {
            Vec3 c = position();
            level().playSound(null, c.x, c.y + 10, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 4.0f, 0.8f);
            if (level() instanceof net.minecraft.server.level.ServerLevel zsl) {
                com.example.jjkspells.vfx.sprite.SpriteFx.ring(zsl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, (float) (WALL_R / 3.0));   // sanctuary fades
                com.example.jjkspells.vfx.sprite.SpriteFx.sparks(zsl, c.add(0, 2.0, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 3.0f);
            }
        }
        if (tickCount % 10 == 0) {
            syncEvents();
        }
        if (tickCount < ACTIVE_START || tickCount >= ACTIVE_END) {
            return;
        }
        LivingEntity owner = owner();
        Vec3 c = position();
        double r = WALL_R;
        AABB box = new AABB(c.x - r - 8, c.y - 4, c.z - r - 8, c.x + r + 8, c.y + PILLAR_H + 4, c.z + r + 8);

        // enemies inside the walls -> counterattack from the nearest pillar
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && !x.isSpectator())) {
            if (t == owner || t.getUUID().equals(ownerUuid) || (owner != null && t.isAlliedTo(owner)) || t instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                continue;
            }
            Vec3 tp = t.position();
            if (!isInside(tp)) {
                continue;
            }
            Integer last = lastTrigger.get(t.getUUID());
            if (last != null && tickCount - last < KirchenliedSpell.TRIGGER_COOLDOWN_TICKS) {
                continue;
            }
            lastTrigger.put(t.getUUID(), tickCount);
            strike(t, owner, tp);
        }

        // projectiles skimming the walls light them up
        for (Projectile pr : level().getEntitiesOfClass(Projectile.class, box)) {
            if (pr.getOwner() != null && pr.getOwner().getUUID().equals(ownerUuid)) {
                continue;
            }
            Vec3 pp = pr.position();
            double d = Math.sqrt((pp.x - c.x) * (pp.x - c.x) + (pp.z - c.z) * (pp.z - c.z));
            if (Math.abs(d - WALL_R) < 2.5) {
                Integer seen = projectileSeen.get(pr.getId());
                if (seen == null || tickCount - seen > 12) {
                    projectileSeen.put(pr.getId(), tickCount);
                    ripple(pp);
                }
            }
        }
        if (tickCount % 100 == 0) {
            lastTrigger.values().removeIf(v -> tickCount - v > 200);
            projectileSeen.values().removeIf(v -> tickCount - v > 200);
        }
    }

    private void strike(LivingEntity target, LivingEntity owner, Vec3 tp) {
        Vec3 c = position();
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int k = 0; k < PILLARS; k++) {
            double d = pillarBase(c, k).distanceToSqr(tp.x, c.y, tp.z);
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        float pctAmt = (float) (target.getMaxHealth() * KirchenliedSpell.PERCENT_MAX_HEALTH / 100.0);
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        Vec3 keep = target.getDeltaMovement();
        target.invulnerableTime = 0;
        if (KirchenliedSpell.FLAT_DAMAGE > 0) io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(target, (float) KirchenliedSpell.FLAT_DAMAGE, dsrc);
        if (target.isAlive()) com.example.jjkspells.spells.SpellDamage.percent(target, pctAmt, owner, dsrc);   // respects Max Health Damage / Resistance
        com.example.jjkspells.spells.SpellDamage.irradiate(target, 120);
        target.setDeltaMovement(keep.x, Math.max(keep.y, 0.0), keep.z);
        addEvent(true, best, tp.add(0, target.getBbHeight() * 0.5, 0));
        Vec3 b = pillarBase(c, best).add(0, PILLAR_H, 0);
        level().playSound(null, b.x, b.y, b.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.7f);
        level().playSound(null, tp.x, tp.y, tp.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 1.6f);
        level().playSound(null, tp.x, tp.y, tp.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 3.0f, 1.9f);
        if (level() instanceof net.minecraft.server.level.ServerLevel ksl) {
            Vec3 hitPos = tp.add(0, target.getBbHeight() * 0.5, 0);
            com.example.jjkspells.vfx.sprite.SpriteFx.lightning(ksl, b, hitPos, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 1.6f);   // pillar strikes
            com.example.jjkspells.vfx.sprite.SpriteFx.impact(ksl, hitPos, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 2.5f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(ksl, tp, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.GOLD, 0.8f);
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        if (!cameraStarted) {
            cameraStarted = true;
            SanktCamera.start(this);
        }
        CompoundTag root = this.entityData.get(EVENTS);
        if (root.contains("l", Tag.TAG_LIST)) {
            ListTag l = root.getList("l", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                int id = c.getInt("id");
                if (id > clientLastId) {
                    clientLastId = id;
                    Fx fx = new Fx(id, c.getBoolean("s"), c.getInt("p"), new Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")), tickCount);
                    clientFx.add(fx);
                    if (fx.strike) {
                        for (int j = 0; j < 14; j++) {
                            level().addParticle(ParticleTypes.END_ROD, fx.pos.x + (random.nextDouble() - 0.5), fx.pos.y + (random.nextDouble() - 0.5), fx.pos.z + (random.nextDouble() - 0.5),
                                    (random.nextDouble() - 0.5) * 0.5, 0.2 + random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.5);
                        }
                    }
                }
            }
        }
        for (Iterator<Fx> it = clientFx.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            if (tickCount - f.born > (f.strike ? STRIKE_LIFE : RIPPLE_LIFE) + 2) {
                it.remove();
            }
        }
        // rising motes while the pillars build and during the whole active phase
        if (tickCount > 4 && tickCount < ACTIVE_END && tickCount % 2 == 0) {
            Vec3 c = position();
            int k = random.nextInt(PILLARS);
            Vec3 b = pillarBase(c, k);
            double h = Math.min(1.0, Math.max(0.0, (tickCount - PILLAR_START - k * PILLAR_STAGGER) / (double) PILLAR_RISE));
            if (h > 0) {
                level().addParticle(ParticleTypes.END_ROD, b.x + (random.nextDouble() - 0.5) * 2, c.y + 0.2, b.z + (random.nextDouble() - 0.5) * 2, 0, 0.15 + random.nextDouble() * 0.25, 0);
            }
        }
    }
}
