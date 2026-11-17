package com.example.jjkspells.yasuo.entity;

import com.example.jjkspells.ModAttributes;
import com.example.jjkspells.yasuo.YasuoRegistry;
import com.example.jjkspells.yasuo.spell.ThousandWindsSpell;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller of "Thousand Winds". The entity sits at the cast point.
 * <pre>
 *   1 .. CHARGE                       Yasuo gathers the wind, doppelgangers ring the field
 *   CHARGE .. CHARGE + BARRAGE        every VOLLEY_INTERVAL ticks a giant blade strikes (mostly where an enemy stands); logged as {x, y, z (* 10), tick}
 *   FIN = CHARGE + BARRAGE + 8        every blade converges: the whole arena is hit and thrown
 *   FIN .. END                        visuals only
 * </pre>
 */
public class ThousandWindsEntity extends Entity {
    public static final int CHARGE = ThousandWindsSpell.CHARGE_TICKS;
    public static final int FIN = CHARGE + ThousandWindsSpell.BARRAGE_TICKS + 8;
    public static final int FADE = 28;
    public static final int END = FIN + FADE;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ThousandWindsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> LOG = SynchedEntityData.defineId(ThousandWindsEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private UUID ownerUuid;
    private final List<Integer> strikeLog = new ArrayList<>();
    private List<int[]> clientStrikes = new ArrayList<>();

    public ThousandWindsEntity(EntityType<? extends ThousandWindsEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ThousandWindsEntity create(Level level, LivingEntity caster) {
        ThousandWindsEntity e = new ThousandWindsEntity(YasuoRegistry.WINDS, level);
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

    /** Client: blade strikes so far, each {x, y, z (* 10), tick}. */
    public List<int[]> strikes() {
        return clientStrikes;
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

    private void serverTick() {
        if (tickCount > END) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        Entity oe = sl.getEntity(ownerUuid);
        LivingEntity owner = oe instanceof LivingEntity le ? le : null;
        Vec3 c = position();
        double R = ThousandWindsSpell.RADIUS;
        if (tickCount == 1) {
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 5.0f, 0.5f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 4.0f, 0.8f);
            level().playSound(null, c.x, c.y + 1, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 4.0f, 1.2f);
            com.example.jjkspells.vfx.sprite.SpriteFx.vortex(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 3f, CHARGE);
        }
        if (tickCount < FIN && owner != null) {
            owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
            owner.invulnerableTime = Math.max(owner.invulnerableTime, 5);
            owner.fallDistance = 0;
        }
        if (tickCount == CHARGE) {
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 1.4f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2f);
        }
        AABB box = new AABB(c.x - R, c.y - 12, c.z - R, c.x + R, c.y + 24, c.z + R);
        if (tickCount >= CHARGE && tickCount < CHARGE + ThousandWindsSpell.BARRAGE_TICKS && (tickCount - CHARGE) % ThousandWindsSpell.VOLLEY_INTERVAL == 0) {
            strike(sl, owner, c, R, box);
        }
        if (tickCount == FIN) {
            for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
                double dx = t.getX() - c.x;
                double dz = t.getZ() - c.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > R || !isEnemy(t, owner)) {
                    continue;
                }
                hit(t, owner, ThousandWindsSpell.FINAL_FLAT, ThousandWindsSpell.FINAL_PERCENT);
                double d = Math.max(dist, 1.0E-3);
                t.setDeltaMovement(dx / d * 0.9, 1.0, dz / d * 0.9);
                t.hurtMarked = true;
                t.fallDistance = 0;
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 1.4f);
            }
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 5.0f, 1.6f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 1.0f);
            level().playSound(null, c.x, c.y + 2, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 4.0f, 0.8f);
            com.example.jjkspells.vfx.sprite.SpriteFx.ring(sl, c, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 7.3f);
            com.example.jjkspells.vfx.sprite.SpriteFx.burst(sl, c.add(0, 1, 0), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 5f);
        }
    }

    private void strike(ServerLevel sl, LivingEntity owner, Vec3 c, double R, AABB box) {
        List<LivingEntity> enemies = new ArrayList<>();
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = t.getX() - c.x;
            double dz = t.getZ() - c.z;
            if (dx * dx + dz * dz <= R * R && isEnemy(t, owner)) {
                enemies.add(t);
            }
        }
        double x;
        double z;
        double y;
        if (!enemies.isEmpty() && random.nextDouble() < 0.65) {
            LivingEntity t = enemies.get(random.nextInt(enemies.size()));
            x = t.getX() + (random.nextDouble() - 0.5) * 1.5;
            z = t.getZ() + (random.nextDouble() - 0.5) * 1.5;
            y = t.getY();
        } else {
            double ang = random.nextDouble() * Math.PI * 2;
            double rad = R * Math.sqrt(random.nextDouble());
            x = c.x + Math.cos(ang) * rad;
            z = c.z + Math.sin(ang) * rad;
            y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            if (Math.abs(y - c.y) > 6) {
                y = c.y;
            }
        }
        double r2 = ThousandWindsSpell.STRIKE_RADIUS * ThousandWindsSpell.STRIKE_RADIUS;
        for (LivingEntity t : enemies) {
            double dx = t.getX() - x;
            double dz = t.getZ() - z;
            if (dx * dx + dz * dz <= r2 && t.getY() > y - 3 && t.getY() < y + 6) {
                hit(t, owner, ThousandWindsSpell.STRIKE_FLAT, ThousandWindsSpell.STRIKE_PERCENT);
                double d = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-3);
                t.setDeltaMovement(dx / d * 0.25, 0.35, dz / d * 0.25);
                t.hurtMarked = true;
            }
        }
        level().playSound(null, x, y + 1, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 2.5f, 1.2f);
        level().playSound(null, x, y + 1, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 1.6f);
        if (random.nextInt(3) == 0) {
            level().playSound(null, x, y + 4, z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.5f, 1.8f);
        }
        com.example.jjkspells.vfx.sprite.SpriteFx.slash(sl, new Vec3(x, y + 1.5, z), new Vec3(1, 0, 0), 0f, com.example.jjkspells.vfx.sprite.SpriteFx.Palette.WHITE, 3f);
        com.example.jjkspells.vfx.sprite.SpriteFx.impact(sl, new Vec3(x, y + 0.3, z), com.example.jjkspells.vfx.sprite.SpriteFx.Palette.AZURE, 2f);
        strikeLog.add((int) Math.round(x * 10));
        strikeLog.add((int) Math.round(y * 10));
        strikeLog.add((int) Math.round(z * 10));
        strikeLog.add(tickCount);
        while (strikeLog.size() > 160) {
            strikeLog.subList(0, 4).clear();
        }
        int[] arr = new int[strikeLog.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = strikeLog.get(i);
        }
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("s", arr);
        this.entityData.set(LOG, tag);
    }

    // ------------------------------------------------------------------ client

    private void clientTick() {
        int[] s = this.entityData.get(LOG).getIntArray("s");
        if (s.length >= 4 && (clientStrikes.isEmpty() || clientStrikes.get(clientStrikes.size() - 1)[3] != s[s.length - 1] || clientStrikes.size() != s.length / 4)) {
            clientStrikes = new ArrayList<>();
            for (int i = 0; i + 3 < s.length; i += 4) {
                clientStrikes.add(new int[]{s[i], s[i + 1], s[i + 2], s[i + 3]});
            }
            int[] l = clientStrikes.get(clientStrikes.size() - 1);
            for (int i = 0; i < 16; i++) {
                level().addParticle(ParticleTypes.CLOUD, l[0] / 10.0 + (random.nextDouble() - 0.5) * 3, l[1] / 10.0 + 0.3, l[2] / 10.0 + (random.nextDouble() - 0.5) * 3,
                        (random.nextDouble() - 0.5) * 0.5, 0.15, (random.nextDouble() - 0.5) * 0.5);
            }
        }
    }
}
