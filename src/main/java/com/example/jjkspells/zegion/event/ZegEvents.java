package com.example.jjkspells.zegion.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.ZegEntityBase;
import com.example.jjkspells.zegion.registry.ModParticles;
import com.example.jjkspells.zegion.registry.ModSounds;
import com.example.jjkspells.zegion.util.Util;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** The rules the eight spells rely on: carapace, damage reduction, fracture, healing cuts, melee bonuses, holds and speed changes. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class ZegEvents {
    private ZegEvents() {
    }

    private static boolean melee(DamageSource s) {
        return s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.MOB_ATTACK);
    }

    // ------------------------------------------------------------------ damage in

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) {
            return;
        }
        DamageSource s = e.getSource();
        boolean bypass = s.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && (Util.UNTOUCHABLE.contains(v.getUUID()) || Util.HIDDEN.contains(v.getUUID()))) {
            e.setCanceled(true);
            return;
        }
        Entity attacker = s.getEntity();
        if (attacker != null && !Util.STUN.isEmpty() && Util.isStunned(attacker)) {
            e.setCanceled(true);
            return;
        }
        if (attacker instanceof LivingEntity la && !(la instanceof net.minecraft.world.entity.player.Player) && !Util.ATK_SLOW.isEmpty()) {
            double[] a = Util.ATK_SLOW.get(la.getUUID());
            if (a != null && a[1] >= la.level().getGameTime() && la.getRandom().nextDouble() < a[0]) {
                e.setCanceled(true);
                return;
            }
        }
        // Invincible Carapace: the first attacks are completely negated
        Util.Carapace c = Util.CARAPACE.get(v.getUUID());
        if (c != null && !bypass && c.charges > 0) {
            c.charges--;
            c.prevented += e.getAmount();
            carapaceFlash(v, s, e.getAmount());
            e.setCanceled(true);
        }
    }

    private static void carapaceFlash(LivingEntity v, DamageSource s, float amount) {
        if (v.level() instanceof ServerLevel sl) {
            Vec3 p = v.position().add(0, v.getBbHeight() * 0.6, 0);
            Entity from = s.getDirectEntity() != null ? s.getDirectEntity() : s.getEntity();
            if (from != null) {
                Vec3 d = from.position().subtract(v.position());
                d = new Vec3(d.x, 0, d.z);
                if (d.lengthSqr() > 1e-4) {
                    p = p.add(d.normalize().scale(0.9));
                }
            }
            sl.sendParticles(ModParticles.FLASH.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ModParticles.SHARD.get(), p.x, p.y, p.z, 8, 0.3, 0.3, 0.3, 0.25);
            sl.sendParticles(ModParticles.CRACK.get(), p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
            sl.sendParticles(ModParticles.SPARK.get(), p.x, p.y, p.z, 6, 0.3, 0.3, 0.3, 0.3);
            Util.sound(v.level(), v.getX(), v.getY() + 1, v.getZ(), ModSounds.ARMOR.get(), 0.9f, 0.7f + v.getRandom().nextFloat() * 0.4f);
        }
        // kinetic reflection: a retaliatory energy spear for every blocked projectile
        Util.Carapace c = Util.CARAPACE.get(v.getUUID());
        Entity shooter = s.getEntity();
        if (c != null && !Util.reflecting && s.getDirectEntity() instanceof Projectile && shooter instanceof LivingEntity la && la != v && la.isAlive()
                && c.reflections < ZegConfig.OBSIDIAN.maxReflections.get()) {
            c.reflections++;
            Util.reflecting = true;
            try {
                Util.hurt(v, la, ZegConfig.OBSIDIAN.reflectDamage.get(), 0);
            } finally {
                Util.reflecting = false;
            }
            if (v.level() instanceof ServerLevel) {
                Util.arc(v.level(), ModParticles.STAR.get(), v.position().add(0, v.getBbHeight() * 0.6, 0), la.position().add(0, la.getBbHeight() * 0.5, 0), 0.3);
                Util.arc(v.level(), ModParticles.SPARK.get(), v.position().add(0, v.getBbHeight() * 0.6, 0), la.position().add(0, la.getBbHeight() * 0.5, 0), 0.6);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) {
            return;
        }
        DamageSource s = e.getSource();
        float amount = e.getAmount();
        boolean bypass = s.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        Entity atk = s.getEntity();

        // carapace: after the negation charges the armor still stops most of every hit
        Util.Carapace c = Util.CARAPACE.get(v.getUUID());
        if (c != null && !bypass) {
            float stopped = (float) (amount * c.block);
            c.prevented += stopped;
            amount -= stopped;
            if (v.level() instanceof ServerLevel sl && v.tickCount % 2 == 0) {
                Vec3 p = v.position().add(0, v.getBbHeight() * 0.6, 0);
                sl.sendParticles(ModParticles.SHARD.get(), p.x, p.y, p.z, 5, 0.35, 0.4, 0.35, 0.18);
                sl.sendParticles(ModParticles.SPARK.get(), p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.2);
                Util.sound(v.level(), v.getX(), v.getY() + 1, v.getZ(), ModSounds.ARMOR.get(), 0.5f, 0.9f + v.getRandom().nextFloat() * 0.3f);
            }
            if (s.getDirectEntity() instanceof Projectile && atk instanceof LivingEntity la && la != v && !Util.reflecting && c.reflections < ZegConfig.OBSIDIAN.maxReflections.get()) {
                c.reflections++;
                Util.reflecting = true;
                try {
                    Util.hurt(v, la, ZegConfig.OBSIDIAN.reflectDamage.get(), 0);
                } finally {
                    Util.reflecting = false;
                }
                Util.arc(v.level(), ModParticles.STAR.get(), v.position().add(0, v.getBbHeight() * 0.6, 0), la.position().add(0, la.getBbHeight() * 0.5, 0), 0.3);
            }
        }
        if (!bypass) {
            double red = Util.reduction(v);
            if (red > 0) {
                amount *= (float) (1.0 - red);
            }
            if (s.getDirectEntity() instanceof Projectile && !Util.PROJ_REDUCE.isEmpty()) {
                double pr = Util.frac(Util.PROJ_REDUCE, v);
                if (pr > 0) {
                    amount *= (float) (1.0 - pr);
                }
            }
            // Kaiser Fracture: physical damage (anything armor works against) hurts more
            if (!Util.FRACTURE.isEmpty() && !s.is(DamageTypeTags.BYPASSES_ARMOR)) {
                double fr = Util.frac(Util.FRACTURE, v);
                if (fr > 0) {
                    amount *= (float) (1.0 + fr);
                }
            }
        }
        if (atk instanceof LivingEntity la) {
            // reduced outgoing damage (domains)
            if (!Util.OUT_DOWN.isEmpty()) {
                double od = Util.frac(Util.OUT_DOWN, la);
                if (od > 0) {
                    amount *= (float) (1.0 - od);
                }
            }
            // melee bonuses (Final Form, Sovereign of the Void) and the third-strike explosion
            if (melee(s)) {
                if (!Util.MELEE_FLAT.isEmpty()) {
                    amount += (float) Util.frac(Util.MELEE_FLAT, la);
                }
                if (!Util.MELEE_BONUS.isEmpty()) {
                    amount *= (float) (1.0 + Util.frac(Util.MELEE_BONUS, la));
                }
                var form = Util.FORM.get(la.getUUID());
                if (form != null) {
                    form.onMelee(v);
                }
            }
        }
        e.setAmount(amount);
        if (amount <= 0f) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || Util.HEAL_CUT.isEmpty()) {
            return;
        }
        double h = Util.frac(Util.HEAL_CUT, v);
        if (h > 0) {
            e.setAmount((float) (e.getAmount() * (1.0 - h)));
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
    public static void onTarget(LivingChangeTargetEvent e) {
        LivingEntity t = e.getNewTarget();
        if (t != null && Util.HIDDEN.contains(t.getUUID())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || Util.KB_IMMUNE.isEmpty()) {
            return;
        }
        Long u = Util.KB_IMMUNE.get(v.getUUID());
        if (u != null) {
            if (u >= v.level().getGameTime()) {
                e.setCanceled(true);
            } else {
                Util.KB_IMMUNE.remove(v.getUUID());
            }
        }
    }

    // ------------------------------------------------------------------ per-entity upkeep

    private static void expire(java.util.Map<UUID, double[]> map, UUID id, long now) {
        double[] a = map.get(id);
        if (a != null && a[1] < now) {
            map.remove(id);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) {
            return;
        }
        long now = v.level().getGameTime();
        UUID id = v.getUUID();

        if (!Util.HOLD.isEmpty()) {
            Long until = Util.HOLD.get(id);
            if (until != null) {
                if (now >= until || !v.isAlive()) {
                    Util.release(v);
                } else if (!Util.isBoss(v)) {
                    Vec3 m = v.getDeltaMovement();
                    v.setDeltaMovement(0, m.y, 0);
                }
            }
        }
        if (!Util.SLOW_IMMUNE.isEmpty()) {
            Long u = Util.SLOW_IMMUNE.get(id);
            if (u != null) {
                if (u < now) {
                    Util.SLOW_IMMUNE.remove(id);
                } else if (v.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
            }
        }
        if (!Util.STUN.isEmpty()) {
            Long u = Util.STUN.get(id);
            if (u != null && u < now) {
                Util.STUN.remove(id);
            }
        }
        if (!Util.SLOWED.isEmpty()) {
            double[] s = Util.SLOWED.get(id);
            if (s != null && s[1] < now) {
                Util.SLOWED.remove(id);
                AttributeInstance inst = v.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst != null) {
                    inst.removeModifier(Util.SLOW_ID);
                }
            }
        }
        if (!Util.HASTED.isEmpty()) {
            double[] s = Util.HASTED.get(id);
            if (s != null && s[1] < now) {
                Util.HASTED.remove(id);
                AttributeInstance inst = v.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst != null) {
                    inst.removeModifier(Util.HASTE_ID);
                }
            }
        }
        if (!Util.ATK_SLOW.isEmpty()) {
            double[] a = Util.ATK_SLOW.get(id);
            if (a != null && a[1] < now) {
                Util.ATK_SLOW.remove(id);
                AttributeInstance inst = v.getAttribute(Attributes.ATTACK_SPEED);
                if (inst != null) {
                    inst.removeModifier(Util.ATK_ID);
                }
            }
        }
        if (!Util.FRACTURE.isEmpty()) {
            double[] a = Util.FRACTURE.get(id);
            if (a != null && a[1] < now) {
                Util.clearFracture(v);
            }
        }
        if (v.tickCount % 100 == 0) {
            Long u = Util.FALL_IMMUNE.get(id);
            if (u != null && u < now) {
                Util.FALL_IMMUNE.remove(id);
            }
            expire(Util.REDUCE, id, now);
            expire(Util.HEAL_CUT, id, now);
            expire(Util.MELEE_BONUS, id, now);
            expire(Util.MELEE_FLAT, id, now);
            expire(Util.OUT_BONUS, id, now);
            expire(Util.OUT_DOWN, id, now);
            expire(Util.VULN, id, now);
            expire(Util.PROJ_REDUCE, id, now);
            Long ci = Util.CC_IMMUNE.get(id);
            if (ci != null && ci < now) {
                Util.CC_IMMUNE.remove(id);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) {
            return;
        }
        Entity k = e.getSource().getEntity();
        if (k instanceof LivingEntity && !Util.KILLS.isEmpty() && k != v) {
            Util.KILLS.computeIfPresent(k.getUUID(), (id, n) -> n + 1);
        }
        Util.release(v);
        Util.STUN.remove(v.getUUID());
        Util.clearSpeed(v);
        Util.ATK_SLOW.remove(v.getUUID());
        Util.clearFracture(v);
        Util.CARAPACE.remove(v.getUUID());
    }

    /** No casting during the long sequences. */
    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!e.getEntity().level().isClientSide && Util.LOCKED.contains(e.getEntity().getUUID())) {
            e.setCanceled(true);
        }
    }

    /** Never log back in floating, locked, hidden or untouchable after a disconnect in the middle of a sequence. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        var p = e.getEntity();
        UUID id = p.getUUID();
        Util.LOCKED.remove(id);
        Util.UNTOUCHABLE.remove(id);
        Util.HIDDEN.remove(id);
        Util.CARAPACE.remove(id);
        Util.FORM.remove(id);
        Util.KILLS.remove(id);
        Util.CC_IMMUNE.remove(id);
        Util.KB_IMMUNE.remove(id);
        Util.SLOW_IMMUNE.remove(id);
        Util.REDUCE.remove(id);
        Util.PROJ_REDUCE.remove(id);
        Util.MELEE_BONUS.remove(id);
        Util.MELEE_FLAT.remove(id);
        Util.OUT_BONUS.remove(id);
        Util.OUT_DOWN.remove(id);
        Util.VULN.remove(id);
        if (!p.level().isClientSide) {
            p.setNoGravity(false);
            Util.clearSpeed(p);
            Util.release(p);
            Util.STUN.remove(id);
            Util.clearFracture(p);
            AttributeInstance at = p.getAttribute(Attributes.ATTACK_SPEED);
            if (at != null) {
                at.removeModifier(Util.ATK_ID);
            }
            Util.ATK_SLOW.remove(id);
            AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(ZegEntityBase.KB_ID);
            }
        }
    }
}
