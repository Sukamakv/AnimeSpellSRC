package com.example.jjkspells.afterlife.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModEffects;
import com.example.jjkspells.afterlife.registry.ModParticles;
import com.example.jjkspells.afterlife.registry.ModSounds;
import com.example.jjkspells.afterlife.util.Blossom;
import com.example.jjkspells.afterlife.util.Util;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class AfterlifeEvents {
    private AfterlifeEvents() {
    }

    private static boolean guided(LivingEntity e) {
        return e != null && e.hasEffect(ModEffects.GUIDE.get());
    }

    /** Guide to Afterlife: every melee hit adds fire damage + max-HP damage and plants Blood Blossom. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        var src = e.getSource();
        if (e.getEntity().level().isClientSide || src instanceof SpellDamageSource) {
            return;
        }
        if (src.getEntity() instanceof LivingEntity attacker && src.getDirectEntity() == attacker && guided(attacker)
                && Util.isEnemy(attacker, e.getEntity())) {
            LivingEntity target = e.getEntity();
            var c = AfterlifeConfig.GUIDE;
            double m = AfterlifeConfig.G.damageMultiplier.get();
            e.setAmount(e.getAmount() + (float) (c.bonusFlat.get() * m * AfterlifeConfig.G.flatScale.get()));
            // the max-health part ignores damage caps
            com.example.jjkspells.spells.SpellDamage.percent(target, Util.budget(target, (float) (c.bonusPercent.get() / 100.0 * target.getMaxHealth() * m * AfterlifeConfig.G.percentScale.get())), attacker, Util.src(attacker));
            Blossom.apply(attacker, target, Blossom.ticksOf(AfterlifeConfig.BLOSSOM_SECONDS_GUIDE()));
            Vec3 p = target.getBoundingBox().getCenter();
            var lvl = target.level();
            Util.fx(lvl, ModParticles.SLASH.get(), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            Util.fx(lvl, ModParticles.FLASH.get(), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            Util.fx(lvl, ModParticles.BUTTERFLY_ORANGE.get(), p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0.1);
            Util.fx(lvl, ModParticles.EMBER.get(), p.x, p.y, p.z, 8, 0.3, 0.3, 0.3, 0.12);
            Util.sound(lvl, p.x, p.y, p.z, ModSounds.STRIKE.get(), 0.7f, 1.1f);
        }
    }

    /** Ordinary fire does nothing to the caster while transformed; the ceremony makes the caster untouchable. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) {
            return;
        }
        if (Util.UNTOUCHABLE.contains(v.getUUID()) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            return;
        }
        if (guided(v) && e.getSource().is(DamageTypeTags.IS_FIRE)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        LivingEntity v = e.getEntity();
        Long until = Util.FALL_IMMUNE.get(v.getUUID());
        if (Util.UNTOUCHABLE.contains(v.getUUID()) || (until != null && until >= v.level().getGameTime())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) {
            return;
        }
        if (v.tickCount % 100 == 0) {
            Long u = Util.FALL_IMMUNE.get(v.getUUID());
            if (u != null && u < v.level().getGameTime()) {
                Util.FALL_IMMUNE.remove(v.getUUID());
            }
        }
        if (v.tickCount % 5 == 0 && guided(v)) {
            v.clearFire();
        }
    }

    /** No casting during the ultimate. */
    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!e.getEntity().level().isClientSide && Util.LOCKED.contains(e.getEntity().getUUID())) {
            e.setCanceled(true);
        }
    }

    /** Never log back in floating or locked after a disconnect in the middle of the ceremony. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        var p = e.getEntity();
        Util.LOCKED.remove(p.getUUID());
        Util.UNTOUCHABLE.remove(p.getUUID());
        if (!p.level().isClientSide) {
            p.setNoGravity(false);
        }
    }
}
