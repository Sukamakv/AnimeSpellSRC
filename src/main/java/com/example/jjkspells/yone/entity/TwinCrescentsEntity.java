package com.example.jjkspells.yone.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yone.YoneRegistry;
import com.example.jjkspells.yone.spell.TwinCrescentsSpell;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controller of "Twin Crescents". Strokes land at T0, T0 + STEP and T0 + 2 * STEP; each stroke launches one crescent (the third launches two, crossed).
 * A crescent flies SPEED blocks per tick for LIFE ticks and cuts every enemy it passes through once. Crescents are logged as
 * {x, y, z (* 10), tick, dirX * 1000, dirZ * 1000, rollDegrees, colour (0 blue, 1 red), big}, hits as {x, y, z (* 10), tick, colour}.
 */
public class TwinCrescentsEntity extends Entity {
    public static final int T0 = TwinCrescentsSpell.T0;
    public static final int STEP = TwinCrescentsSpell.STEP;
    public static final int LIFE = TwinCrescentsSpell.LIFE;
    public static final int END = T0 + 2 * STEP + LIFE + 8;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(TwinCrescentsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(TwinCrescentsEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final class Cres {
        Vec3 origin;
        Vec3 dir;
        int start;
        int colour;
        boolean big;
        final Set<Integer> hit = new HashSet<>();
    }

    private UUID ownerUuid;
    private final List<Cres> cres = new ArrayList<>();
    private final List<Integer> cresLog = new ArrayList<>();
    private final List<Integer> hitLog = new ArrayList<>();
    private List<int[]> clientCres = new ArrayList<>();
    private List<int[]> clientHits = new ArrayList<>();

    public TwinCrescentsEntity(EntityType<? extends TwinCrescentsEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static TwinCrescentsEntity create(Level level, LivingEntity caster) {
        TwinCrescentsEntity e = new TwinCrescentsEntity(YoneRegistry.CRESCENTS, level);
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

    /** Client: crescents launched so far. */
    public List<int[]> crescents() {
        return clientCres;
    }

    /** Client: hits so far. */
    public List<int[]> hits() {
        return clientHits;
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

    private void hit(LivingEntity t, LivingEntity owner, double flat, double percent) {
        t.invulnerableTime = 0;
        net.minecraft.world.damagesource.DamageSource dsrc = level().damageSources().indirectMagic(this, owner);
        float flatDmg = (float) (flat);
        if (flatDmg > 0) {
            io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(t, flatDmg, dsrc);
        }
        if (t.isAlive()) {
            com.example.jjkspells.spells.SpellDamage.percent(t, (float) (t.getMaxHealth() * percent), owner, dsrc);
        }
    }

    private void launch(LivingEntity owner, Vec3 dir, int colour, boolean big, int roll) {
        Cres c = new Cres();
        c.origin = owner.position().add(0, 1.2, 0).add(dir.scale(0.8));
        c.dir = dir;
        c.start = tickCount;
        c.colour = colour;
        c.big = big;
        cres.add(c);
        cresLog.add((int) Math.round(c.origin.x * 10));
        cresLog.add((int) Math.round(c.origin.y * 10));
        cresLog.add((int) Math.round(c.origin.z * 10));
        cresLog.add(tickCount);
        cresLog.add((int) Math.round(dir.x * 1000));
        cresLog.add((int) Math.round(dir.z * 1000));
        cresLog.add(roll);
        cresLog.add(colour);
        cresLog.add(big ? 1 : 0);
    }

    private void sync() {
        CompoundTag tag = new CompoundTag();
        int[] a = new int[cresLog.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = cresLog.get(i);
        }
        int[] h = new int[hitLog.size()];
        for (int i = 0; i < h.length; i++) {
            h[i] = hitLog.get(i);
        }
        tag.putIntArray("c", a);
        tag.putIntArray("h", h);
        this.entityData.set(LOG, tag);
    }

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        if (tickCount == 1 && owner != null) {
            level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.5f, 1.7f);
            com.example.jjkspells.vfx.sprite.SpriteFx.glowOn(sl, owner, 1.0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 1.6f, 14);
        }
        if (owner != null) {
            int k = (tickCount - T0) / STEP;
            if (tickCount >= T0 && (tickCount - T0) % STEP == 0 && k <= 2) {
                Vec3 look = owner.getLookAngle();
                Vec3 dir = new Vec3(look.x, 0, look.z);
                dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, owner.getYRot()) : dir.normalize();
                if (k == 0) {
                    launch(owner, dir, 0, false, 52);
                } else if (k == 1) {
                    launch(owner, dir, 1, false, -52);
                } else {
                    launch(owner, dir, 0, true, 62);
                    launch(owner, dir, 1, true, -62);
                }
                com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, owner.position().add(0, 1.2, 0).add(dir.scale(1.6)), dir, k == 1 ? -52f : 52f,
                        k == 0 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE : k == 1 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE,
                        k == 2 ? 5f : 3.5f);
                if (k == 2) {
                    com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, owner.position().add(0, 1.2, 0).add(dir.scale(1.6)), dir, -62f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, 5f);
                    com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, owner.position().add(0, 1.2, 0).add(dir.scale(2.0)), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 1.5f);
                }
                owner.setDeltaMovement(dir.x * 0.55, Math.min(0.0, owner.getDeltaMovement().y), dir.z * 0.55);
                owner.hurtMarked = true;
                owner.fallDistance = 0;
                Vec3 o = owner.position();
                level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 3.0f, k == 1 ? 1.2f : 1.6f);
                level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 1.8f - 0.2f * k);
                if (k == 2) {
                    level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.9f);
                    level().playSound(null, o.x, o.y + 1, o.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.5f, 1.6f);
                }
                sync();
            }
        }
        boolean changed = false;
        for (Cres c : cres) {
            int age = tickCount - c.start;
            if (age < 1 || age > LIFE) {
                continue;
            }
            Vec3 pos = c.origin.add(c.dir.scale(1.0 + TwinCrescentsSpell.SPEED * age));
            double hw = c.big ? TwinCrescentsSpell.HALF_WIDTH_BIG : TwinCrescentsSpell.HALF_WIDTH;
            double reach = hw + 3.0;
            AABB box = new AABB(pos.x - reach, pos.y - reach, pos.z - reach, pos.x + reach, pos.y + reach, pos.z + reach);
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (!isEnemy(t, owner) || c.hit.contains(t.getId())) {
                    continue;
                }
                Vec3 rel = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(pos);
                double along = rel.dot(c.dir);
                double side = rel.subtract(c.dir.scale(along)).length();
                if (Math.abs(along) > 2.4 + TwinCrescentsSpell.SPEED || side > hw + t.getBbWidth() * 0.5) {
                    continue;
                }
                c.hit.add(t.getId());
                if (c.big) {
                    hit(t, owner, TwinCrescentsSpell.CROSS_FLAT, TwinCrescentsSpell.CROSS_PERCENT);
                } else {
                    hit(t, owner, TwinCrescentsSpell.STROKE_FLAT, TwinCrescentsSpell.STROKE_PERCENT);
                }
                t.setDeltaMovement(c.dir.x * 0.45, 0.28, c.dir.z * 0.45);
                t.hurtMarked = true;
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 2.0f, c.colour == 0 ? 1.4f : 1.0f);
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 1.5f);
                com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), c.colour == 0 ? com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE : com.example.jjkspells.vfx.sprite.SpriteFx.Palette.CRIMSON, c.big ? 2.2f : 1.5f);
                hitLog.add((int) Math.round(t.getX() * 10));
                hitLog.add((int) Math.round((t.getY() + t.getBbHeight() * 0.5) * 10));
                hitLog.add((int) Math.round(t.getZ() * 10));
                hitLog.add(tickCount);
                hitLog.add(c.colour);
                while (hitLog.size() > 160) {
                    hitLog.subList(0, 5).clear();
                }
                changed = true;
            }
        }
        if (changed) {
            sync();
        }
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        CompoundTag tag = this.entityData.get(LOG);
        int[] c = tag.getIntArray("c");
        if (c.length / 9 != clientCres.size()) {
            clientCres = new ArrayList<>();
            for (int i = 0; i + 8 < c.length; i += 9) {
                clientCres.add(new int[]{c[i], c[i + 1], c[i + 2], c[i + 3], c[i + 4], c[i + 5], c[i + 6], c[i + 7], c[i + 8]});
            }
        }
        int[] h = tag.getIntArray("h");
        if (h.length / 5 != clientHits.size() || (h.length >= 5 && !clientHits.isEmpty() && clientHits.get(clientHits.size() - 1)[3] != h[h.length - 2])) {
            clientHits = new ArrayList<>();
            for (int i = 0; i + 4 < h.length; i += 5) {
                clientHits.add(new int[]{h[i], h[i + 1], h[i + 2], h[i + 3], h[i + 4]});
            }
            if (!clientHits.isEmpty()) {
                int[] l = clientHits.get(clientHits.size() - 1);
                for (int i = 0; i < 10; i++) {
                    level().addParticle(ParticleTypes.CLOUD, l[0] / 10.0, l[1] / 10.0, l[2] / 10.0, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4);
                }
            }
        }
    }
}
