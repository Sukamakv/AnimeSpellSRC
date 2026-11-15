package com.example.jjkspells.rimuru.entity;

import com.example.jjkspells.rimuru.config.RimConfig;
import com.example.jjkspells.rimuru.registry.ModEffects;
import com.example.jjkspells.rimuru.registry.ModEntities;
import com.example.jjkspells.rimuru.registry.ModParticles;
import com.example.jjkspells.rimuru.registry.ModSounds;
import com.example.jjkspells.rimuru.registry.ModSpells;
import com.example.jjkspells.rimuru.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Void God Azathoth - End of Creation. One clock (age = tickCount - 1), the entity stands at the marked point:
 * <pre>
 *  0..SPACE        awakening
 *  SPACE..SUN      the imaginary space (6-block sphere) around the caster
 *  SUN..BEAM       the black sun forms above the marked circle
 *  BEAM            reality rupture: beam sun -> point, gravity at the impact (the central void starts)
 *  DESCEND..FALL   the sun descends, FALL is the collapse explosion
 *  SOUL..WAVE      the soul pull, WAVE is the soul wave
 *  COMPRESS..FINAL everything contracts, FINAL is the end of creation
 *  FINAL..END      the chaos creator fades; the blessing starts at FINAL
 * </pre>
 * Visual events (fx list): 1 = beam {} (marker), 2 = wave {radius}.
 */
public class AzathothEntity extends RimEntityBase {
    public static final int SPACE = 30, SUN = 60, BEAM = 100, BEAM_HIT = 110, DESCEND = 130, FALL = 170, SOUL = 170, WAVE = 190,
            COMPRESS = 210, FINAL = 248, PROTECT = 140, END = 280;

    private Vec3 point;
    private final Set<UUID> felled = new HashSet<>();
    private boolean supremacy;

    public AzathothEntity(EntityType<? extends AzathothEntity> type, Level level) {
        super(type, level);
    }

    public static AzathothEntity create(Level level, LivingEntity caster, Vec3 point) {
        AzathothEntity e = new AzathothEntity(ModEntities.AZATHOTH.get(), level);
        e.setOwner(caster);
        e.setPos(point.x, point.y, point.z);
        e.point = point;
        return e;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.example.jjkspells.rimuru.client.ClientFx.tickAzathoth(this));
            return;
        }
        var c = RimConfig.AZATHOTH;
        int age = tickCount - 1;
        LivingEntity owner = owner();
        if (age < END && (owner == null || owner.level() != level())) {
            finish(false);
            discard();
            return;
        }
        double armor = c.armorIgnorePercent.get() / 100.0, reduce = c.reduceIgnorePercent.get() / 100.0;
        if (age == 0) {
            Util.LOCKED.add(owner.getUUID());
            Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.VOID.get(), 1.8f, 0.6f);
        }
        if (age < END) {
            freeze(owner);
            if (age % 40 == 0 && age >= 20) {
                Util.sound(level(), owner.getX(), owner.getY(), owner.getZ(), ModSounds.HUM.get(), 1.4f, 0.6f);
            }
        }
        // void protection for the second half of the casting
        if (age >= PROTECT && age < END) {
            Util.reduce(owner, c.protectReductionPercent.get() / 100.0, 6);
            Util.kbImmune(owner, 6);
            Util.ccImmune(owner, 6);
        }
        // imaginary space: the central void
        if (age >= BEAM_HIT && age < COMPRESS) {
            for (LivingEntity e : Util.enemies(level(), point, c.centralRadius.get(), owner)) {
                Util.pull(e, point, c.pullStrength.get() * 0.35, 1.5);
                if (age % 10 == 0) {
                    Util.slowPct(e, c.spaceSlowPercent.get(), 30);
                    Util.outDamage(e, c.spaceOutPercent.get(), 30);
                }
            }
        }
        if (age == BEAM) {
            pushFx(1, 0);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.LASER.get(), 2.0f, 0.5f);
        }
        if (age == BEAM_HIT) {
            for (LivingEntity e : Util.enemies(level(), point, c.beamRadius.get(), owner)) {
                hit(owner, e, c.beamFlat.get(), c.beamPercent.get(), armor, reduce, 1.0);
            }
            Util.voidBurst(level(), point.x, point.y + 0.5, point.z, 3.0, 60);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 2.0f, 0.6f);
        }
        if (age >= SOUL && age < COMPRESS) {
            for (LivingEntity e : Util.enemies(level(), point, c.voidRadius.get(), owner)) {
                Util.pull(e, point, c.pullStrength.get() * (age < WAVE ? 0.7 : 0.4), 2.0);
            }
        }
        if (age == FALL) {
            double r = c.radius.get();
            for (LivingEntity e : Util.enemies(level(), point, r, owner)) {
                double central = e.position().distanceTo(point) <= c.centralRadius.get() ? 1.0 + c.centralBonusPercent.get() / 100.0 : 1.0;
                hit(owner, e, c.collapseFlat.get(), c.collapsePercent.get(), armor, reduce, central);
                Util.knock(e, point, 4.0, 0.35);
            }
            pushFx(2, r);
            Util.voidBurst(level(), point.x, point.y + 0.5, point.z, r * 0.4, 120);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.VOID.get(), 2.0f, 0.5f);
        }
        if (age == WAVE) {
            double r = c.voidRadius.get();
            for (LivingEntity e : Util.enemies(level(), point, r, owner)) {
                hit(owner, e, c.soulFlat.get(), c.soulPercent.get(), armor, reduce, 1.0);
                soul(owner, e, c);
            }
            pushFx(2, r);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.COLLAPSE.get(), 2.0f, 0.6f);
        }
        if (age == FINAL) {
            double r = c.finalRadius.get();
            for (LivingEntity e : Util.enemies(level(), point, r, owner)) {
                hit(owner, e, c.finalFlat.get(), c.finalPercent.get(), armor, reduce, 1.0);
                soul(owner, e, c);
            }
            pushFx(2, r);
            Util.voidBurst(level(), point.x, point.y + 1.0, point.z, r * 0.4, 200);
            Util.sound(level(), point.x, point.y, point.z, ModSounds.BOOM.get(), 2.5f, 0.5f);
            chaosBlessing(owner, c);
        }
        if (age >= END) {
            finish(true);
            discard();
        }
    }

    /** Damage with Void God Authority: the armor and magic reduction are partly ignored, bosses take more. */
    private void hit(LivingEntity owner, LivingEntity e, double flat, double pct, double armor, double reduce, double mult) {
        double m = mult * (Util.isBoss(e) ? 1.0 + RimConfig.AZATHOTH.bossBonusPercent.get() / 100.0 : 1.0);
        if (Util.hurt(owner, e, flat, pct, m, 1.0, armor, reduce)) {
            felled.add(e.getUUID());
        }
    }

    /** Soul Consumption: a weak enemy is devoured, a boss takes soul damage. */
    private void soul(LivingEntity owner, LivingEntity e, RimConfig.Azathoth c) {
        if (e.isAlive() && Util.consume(owner, e, c.executeThresholdPercent.get(), c.bossSoul.get())) {
            felled.add(e.getUUID());
        }
        if (!e.isAlive()) {
            felled.add(e.getUUID());
        }
    }

    /** Chaos Creator's Blessing, and Absolute Supremacy when enough enemies fell. */
    private void chaosBlessing(LivingEntity owner, RimConfig.Azathoth c) {
        if (!owner.isAlive()) {
            return;
        }
        supremacy = felled.size() >= c.supremacyKills.get();
        int ticks = (int) Math.round(c.blessSeconds.get() * 20) + (supremacy ? (int) Math.round(c.supremacyExtraSeconds.get() * 20) : 0);
        Util.reduce(owner, c.blessReductionPercent.get() / 100.0, ticks);
        Util.spellBonus(owner, c.blessSpellPercent.get(), ticks);
        Util.hastePct(owner, c.blessSpeedPercent.get(), ticks);
        Util.kbImmune(owner, ticks);
        owner.addEffect(new MobEffectInstance(ModEffects.CHAOS.get(), ticks, 0, false, false, true));
        if (supremacy) {
            Util.refundFraction(owner, ModSpells.COLLAPSE.get(), 1.0);
            Util.heal(owner, owner.getMaxHealth() * c.supremacyHealPercent.get() / 100.0);
            Util.fx(level(), ModParticles.SOUL.get(), owner.getX(), owner.getY() + 1.0, owner.getZ(), 30, 0.6, 0.9, 0.6, 0.1);
        }
    }

    @Override
    protected void finish(boolean normal) {
        if (finished) {
            return;
        }
        finished = true;
        releaseCommon(normal);
    }
}
