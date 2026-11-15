package com.example.jjkspells.zegion.entity;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.registry.ModEffects;
import com.example.jjkspells.zegion.registry.ModEntities;
import com.example.jjkspells.zegion.registry.ModParticles;
import com.example.jjkspells.zegion.registry.ModSounds;
import com.example.jjkspells.zegion.registry.ModSpells;
import com.example.jjkspells.zegion.util.Util;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Illusion King Mephisto - The Extinction of Reality. Sits on the caster's feet; one clock (age = tickCount - 1), 20 s:
 * <pre>
 *  0..DOME        the sovereign of illusions (mist, wings, rings)       Phase 1
 *  DOME..GIANTS   the dome grows to 75 blocks                            Phase 2
 *  GIANTS..FIRST  twelve giant Kaisers appear                            Phase 3
 *  FIRST          First Judgment (marks every enemy)                     Phase 4
 *  EXEC           Dimensional Execution                                  Phase 5
 *  COLLAPSE       Reality Collapse                                       Phase 6
 *  RISE..FINAL    the dimension shatters, Zegion rises                   Phase 7
 *  FINAL          Final Annihilation                                     Phase 8
 *  END            the unshaken Kaiser                                    Phase 9
 * </pre>
 */
public class MephistoEntity extends ZegEntityBase {
    public static final int DOME = 40, GIANTS = 80, LAUNCH = 120, FIRST = 140, EXEC_START = 160, EXEC = 190, SPHERE = 210, COLLAPSE = 260, RISE = 270, CLOSE = 330, FINAL = 350, LAND = 372, END = 400;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(MephistoEntity.class, EntityDataSerializers.INT);

    private final Set<UUID> marked = new HashSet<>();
    private double groundY;
    private boolean floating;

    public MephistoEntity(EntityType<? extends MephistoEntity> type, Level level) {
        super(type, level);
    }

    public static MephistoEntity create(Level level, LivingEntity caster) {
        MephistoEntity e = new MephistoEntity(ModEntities.MEPHISTO.get(), level);
        e.setOwner(caster);
        e.setPos(caster.getX(), caster.getY(), caster.getZ());
        e.groundY = caster.getY();
        e.entityData.set(DATA_SEED, level.random.nextInt(100000));
        return e;
    }

    public int getSeed() {
        return entityData.get(DATA_SEED);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SEED, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.zegion.client.ClientFx.tickMephisto(this));
            return;
        }
        var c = ZegConfig.MEPHISTO;
        LivingEntity owner = owner();
        int age = tickCount - 1;
        if (owner == null || owner.level() != level()) {
            finish(false);
            discard();
            return;
        }
        Vec3 at = position();
        double R = c.domainRadius.get();
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.KILLS.put(owner.getUUID(), 0);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.HUM.get(), 3.0f, 0.5f);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.MIST.get(), 3.0f, 0.7f);
        }
        // the caster stays put; the only thing that moves him is the rise of phase 7
        if (!floating) {
            owner.setDeltaMovement(0, Math.min(0, owner.getDeltaMovement().y), 0);
            owner.hurtMarked = true;
        }
        Util.kbImmune(owner, 4);
        Util.ccImmune(owner, 4);
        Util.fallImmune(owner, 6);
        if (age >= (END - (int) (c.protectionSeconds.get() * 20))) {
            Util.reduce(owner, c.protectionPercent.get() / 100.0, 6);
        }
        ambience(owner, at, R, age);
        if (age >= DOME && age < LAND) {
            domainEffects(owner, at, R, age);
        }
        if (age == DOME) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 3.0f, 0.6f);
            Util.blast(level(), at.x, at.y + 1, at.z, 6, 60);
        }
        if (age == GIANTS) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.CHIME.get(), 3.0f, 0.4f);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.TOLL.get(), 3.0f, 0.5f);
        }
        if (age == LAUNCH) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.WHOOSH.get(), 3.0f, 0.4f);
        }
        if (age == FIRST) {
            firstJudgment(owner, at);
        }
        if (age >= EXEC_START && age < EXEC + 10 && age % 6 == 0) {
            blades(owner, at, R, age);
        }
        if (age == EXEC) {
            execution(owner, at);
        }
        if (age == SPHERE) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.VOID.get(), 3.0f, 0.5f);
        }
        if (age >= SPHERE && age < COLLAPSE && age % 3 == 0) {
            double k = (age - SPHERE) / (double) (COLLAPSE - SPHERE);
            for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
                Util.pull(e, at.add(0, 6, 0), 0.04 + 0.12 * k);
            }
        }
        if (age == COLLAPSE) {
            reality(owner, at);
        }
        if (age == RISE) {
            floating = true;
            Util.sound(level(), at.x, at.y, at.z, ModSounds.WING.get(), 3.0f, 0.6f);
        }
        if (floating && age < LAND) {
            Util.hover(owner, groundY + 6.0);
        }
        if (age == LAND) {
            floating = false;
            Util.unhover(owner);
        }
        if (age == CLOSE) {
            Util.swing(owner);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.CHARGE.get(), 3.0f, 0.5f);
        }
        if (age == FINAL) {
            finalAnnihilation(owner, at);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    private void ambience(LivingEntity owner, Vec3 at, double R, int age) {
        if (age % 3 != 0) {
            return;
        }
        double k = Math.min(1.0, age / (double) DOME);
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * R * Math.min(1.0, (age - 10) / (double) (GIANTS - 10) + 0.1);
            Util.fxDir(level(), ModParticles.MIST.get(), at.x + Math.cos(a) * r, at.y + 0.3, at.z + Math.sin(a) * r, 0, 0.03, 0);
        }
        if (age % 6 == 0) {
            Vec3 p = owner.position();
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 1.4, p.z, 3, 0.8, 1.0, 0.8, 0.05);
            double a = random.nextDouble() * Math.PI * 2, r = R * (0.2 + random.nextDouble() * 0.7);
            Util.tear(level(), at.x + Math.cos(a) * r, at.y + 2 + random.nextDouble() * 20, at.z + Math.sin(a) * r, 1.2 * k);
        }
    }

    /** Dimensional Imprisonment (everyone inside the dome) and the mark. */
    private void domainEffects(LivingEntity owner, Vec3 at, double R, int age) {
        var c = ZegConfig.MEPHISTO;
        if (age % 10 != 0) {
            return;
        }
        for (LivingEntity e : Util.enemies(level(), at, R, owner)) {
            double slow = Util.isBoss(e) ? c.bossSlowPercent.get() : c.slowPercent.get();
            Util.slowPct(e, slow, 16);
            Util.put(Util.OUT_DOWN, e, c.outDownPercent.get() / 100.0, 16);
            Util.atkSlow(e, c.atkDownPercent.get(), 16);
            e.addEffect(new MobEffectInstance(ModEffects.IMPRISON.get(), 24, 0, false, false, true));
        }
    }

    private void firstJudgment(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.MEPHISTO;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.PUNCH.get(), 3.0f, 0.6f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 3.0f, 0.7f);
        Util.blast(level(), at.x, at.y + 1, at.z, c.firstRadius.get() * 0.4, 130);
        Util.ring(level(), ModParticles.SHARD.get(), at.add(0, 0.5, 0), 48, 1.4, 0.2);
        double ig = c.ignoreArmorPercent.get() / 100.0;
        int ticks = (int) Math.round(c.markSeconds.get() * 20);
        for (LivingEntity e : Util.enemies(level(), at, c.firstRadius.get(), owner)) {
            Util.hurt(owner, e, c.firstFlat.get(), c.firstPercent.get(), 1.0, ig);
            if (e.isAlive()) {
                marked.add(e.getUUID());
                Util.put(Util.VULN, e, c.markVulnPercent.get() / 100.0, ticks);
                Util.put(Util.HEAL_CUT, e, c.markHealPercent.get() / 100.0, ticks);
                e.addEffect(new MobEffectInstance(ModEffects.EXTINCTION.get(), ticks, 0, false, false, true));
                Util.knock(e, at, 5.0, 0.3);
            }
        }
    }

    private void blades(LivingEntity owner, Vec3 at, double R, int age) {
        double a = random.nextDouble() * Math.PI * 2, r = R * (0.2 + random.nextDouble() * 0.7);
        Vec3 top = at.add(Math.cos(a) * r, 40, Math.sin(a) * r);
        Vec3 bot = new Vec3(top.x, at.y, top.z);
        Util.arc(level(), ModParticles.SPARK.get(), top, bot, 1.5);
        Util.fx(level(), ModParticles.FLASH.get(), bot.x, bot.y + 1, bot.z, 1, 0, 0, 0, 0);
        Util.sound(level(), bot.x, bot.y, bot.z, ModSounds.SLASH.get(), 2.0f, 0.5f + random.nextFloat() * 0.3f);
    }

    private void execution(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.MEPHISTO;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 3.0f, 0.55f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 3.0f, 0.5f);
        Util.blast(level(), at.x, at.y + 2, at.z, c.execRadius.get() * 0.4, 140);
        Util.ring(level(), ModParticles.MIRROR.get(), at.add(0, 0.6, 0), 48, 1.3, 0.25);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.4, 0), 40, 1.5, 0.05);
        double ig = c.ignoreArmorPercent.get() / 100.0;
        for (LivingEntity e : Util.enemies(level(), at, c.execRadius.get(), owner)) {
            Util.hurt(owner, e, c.execFlat.get(), c.execPercent.get(), 1.0, ig);
        }
    }

    private void reality(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.MEPHISTO;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 3.2f, 0.45f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.VOID.get(), 3.2f, 0.7f);
        Util.blast(level(), at.x, at.y + 6, at.z, c.collapseRadius.get() * 0.4, 150);
        Util.ring(level(), ModParticles.VOID.get(), at.add(0, 2, 0), 40, 1.2, 0.1);
        double ig = c.ignoreArmorPercent.get() / 100.0;
        for (LivingEntity e : Util.enemies(level(), at, c.collapseRadius.get(), owner)) {
            Util.hurt(owner, e, c.collapseFlat.get(), c.collapsePercent.get(), 1.0, ig);
            if (e.isAlive()) {
                Util.launchBlocks(e, 3.0);
            }
        }
    }

    private void finalAnnihilation(LivingEntity owner, Vec3 at) {
        var c = ZegConfig.MEPHISTO;
        Util.sound(level(), at.x, at.y, at.z, ModSounds.BOOM.get(), 4.0f, 0.4f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.SHATTER.get(), 4.0f, 0.4f);
        Util.sound(level(), at.x, at.y, at.z, ModSounds.TOLL.get(), 4.0f, 0.4f);
        Util.blast(level(), at.x, at.y + 3, at.z, c.finalRadius.get() * 0.35, 200);
        Util.ring(level(), ModParticles.MIST.get(), at.add(0, 0.4, 0), 64, 2.2, 0.05);
        Util.ring(level(), ModParticles.SHARD.get(), at.add(0, 1.0, 0), 48, 1.6, 0.2);
        Util.ring(level(), ModParticles.MIRROR.get(), at.add(0, 2.0, 0), 40, 1.3, 0.3);
        double ig = c.ignoreArmorPercent.get() / 100.0;
        int dead = 0;
        for (LivingEntity e : Util.enemies(level(), at, c.finalRadius.get(), owner)) {
            double mult = Util.isBoss(e) ? 1.0 + c.bossBonusPercent.get() / 100.0 : 1.0;
            boolean died = Util.hurt(owner, e, c.finalFlat.get(), c.finalPercent.get(), mult, ig);
            if (died || !e.isAlive()) {
                dead++;
            } else {
                Util.knock(e, at, 12.0, 0.4);
            }
        }
        int kills = Math.max(dead, Util.KILLS.getOrDefault(owner.getUUID(), 0));
        double after = c.afterSeconds.get();
        if (kills >= c.resetKills.get()) {
            Util.refundFraction(owner, ModSpells.LABYRINTH.get(), 1.0);
            Util.refundFraction(owner, ModSpells.FORM.get(), 0.5);
            owner.heal((float) (owner.getMaxHealth() * c.healPercent.get() / 100.0));
            after += c.extraSeconds.get();
            Util.fx(level(), ModParticles.STAR.get(), owner.getX(), owner.getY() + 1.2, owner.getZ(), 30, 1.0, 1.2, 1.0, 0.2);
        }
        int ticks = (int) Math.round(after * 20);
        Util.put(Util.OUT_BONUS, owner, c.afterDamagePercent.get() / 100.0, ticks);
        Util.put(Util.MELEE_BONUS, owner, c.afterDamagePercent.get() / 100.0, ticks);
        Util.reduce(owner, c.afterReductionPercent.get() / 100.0, ticks);
        Util.hastePct(owner, c.afterSpeedPercent.get(), ticks);
        Util.kbImmune(owner, ticks);
        owner.addEffect(new MobEffectInstance(ModEffects.SOVEREIGN.get(), ticks, 0, false, false, true));
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
            Util.KILLS.remove(ownerId);
            if (level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity o) {
                o.setNoGravity(false);
                Util.fallImmune(o, 100);
            }
        }
    }
}
