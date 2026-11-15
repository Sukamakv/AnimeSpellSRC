package com.example.jjkspells.rimuru.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.rimuru.util.Util;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** The rules the eight spells rely on: damage reduction, the Absolute Defense barrier, outgoing damage and attack speed penalties, speed changes, immunities. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class RimEvents {
    private RimEvents() {
    }

    // ------------------------------------------------------------------ damage in

    @SubscribeEvent
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
        // Electrical paralysis: a share of the attacks of a slowed mob never lands
        if (!Util.ATK_SLOW.isEmpty() && s.getEntity() instanceof net.minecraft.world.entity.Mob m && !(s.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
            double[] a = Util.ATK_SLOW.get(m.getUUID());
            if (a != null && a[1] >= m.level().getGameTime() && m.getRandom().nextDouble() < a[0]) {
                e.setCanceled(true);
                return;
            }
        }
        // Absolute Defense
        if (!Util.BARRIER.isEmpty() && !bypass && !Util.reflecting) {
            Util.Barrier b = Util.BARRIER.get(v.getUUID());
            if (b != null && b.until >= v.level().getGameTime()) {
                Entity direct = s.getDirectEntity();
                if (direct instanceof Projectile proj) {
                    b.prevented += e.getAmount();
                    e.setCanceled(true);
                    Entity shooter = s.getEntity();
                    proj.discard();
                    Util.fx(v.level(), com.example.jjkspells.rimuru.registry.ModParticles.RUNE.get(), v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 6, 0.6, 0.6, 0.6, 0.05);
                    Util.sound(v.level(), v.getX(), v.getY(), v.getZ(), com.example.jjkspells.rimuru.registry.ModSounds.PING.get(), 0.8f, 1.2f);
                    if (shooter instanceof LivingEntity att && att != v && att.isAlive() && b.reflects < com.example.jjkspells.rimuru.config.RimConfig.DEFENSE.reflectMax.get()) {
                        b.reflects++;
                        com.example.jjkspells.rimuru.entity.DefenseEntity.reflect(v, att);
                    }
                    return;
                }
                if (b.charges > 0) {
                    b.charges--;
                    b.prevented += e.getAmount();
                    e.setCanceled(true);
                    Util.fx(v.level(), com.example.jjkspells.rimuru.registry.ModParticles.RUNE.get(), v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 8, 0.6, 0.6, 0.6, 0.05);
                    Util.sound(v.level(), v.getX(), v.getY(), v.getZ(), com.example.jjkspells.rimuru.registry.ModSounds.PING.get(), 0.8f, 0.9f);
                }
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
        double red = Util.reduction(v);
        if (red > 0 && !bypass) {
            amount *= (float) (1.0 - red);
        }
        // Absolute Defense: base reduction, explosions reduced even more; what is held back is stored
        if (!Util.BARRIER.isEmpty() && !bypass) {
            Util.Barrier b = Util.BARRIER.get(v.getUUID());
            if (b != null && b.until >= v.level().getGameTime()) {
                double r = com.example.jjkspells.rimuru.config.RimConfig.DEFENSE.reductionPercent.get() / 100.0;
                if (s.is(DamageTypeTags.IS_EXPLOSION)) {
                    r = Math.min(0.99, r + (1.0 - r) * 0.5);
                }
                double before = amount;
                amount *= (float) (1.0 - r);
                b.prevented += before - amount;
            }
        }
        // the attacker deals less damage (void instability, gluttonous suppression)
        Entity atk = s.getEntity();
        if (atk instanceof LivingEntity la && !Util.OUTDMG.isEmpty()) {
            double[] od = Util.OUTDMG.get(la.getUUID());
            if (od != null && od[1] >= la.level().getGameTime()) {
                amount *= (float) (1.0 - od[0]);
            }
        }
        // melee damage bonus of the attacker
        if (atk instanceof LivingEntity la && !Util.MELEE_BONUS.isEmpty() && (s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.MOB_ATTACK))) {
            double[] mb = Util.MELEE_BONUS.get(la.getUUID());
            if (mb != null && mb[1] >= la.level().getGameTime()) {
                amount *= (float) (1.0 + mb[0]);
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
        double[] h = Util.HEAL_CUT.get(v.getUUID());
        if (h != null && h[1] >= v.level().getGameTime()) {
            e.setAmount((float) (e.getAmount() * (1.0 - h[0])));
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

    /** Casters hidden in the sky cannot be chosen as a target. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        LivingEntity t = e.getNewTarget();
        if (t != null && Util.HIDDEN.contains(t.getUUID())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKnockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent e) {
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
        if (!Util.BARRIER.isEmpty()) {
            Util.Barrier b = Util.BARRIER.get(id);
            if (b != null && b.until < now - 40) {
                Util.BARRIER.remove(id);
            }
        }
        if (v.tickCount % 100 == 0) {
            Long u = Util.FALL_IMMUNE.get(id);
            if (u != null && u < now) {
                Util.FALL_IMMUNE.remove(id);
            }
            double[] rd = Util.REDUCE.get(id);
            if (rd != null && rd[1] < now) {
                Util.REDUCE.remove(id);
            }
            double[] h = Util.HEAL_CUT.get(id);
            if (h != null && h[1] < now) {
                Util.HEAL_CUT.remove(id);
            }
            double[] m = Util.MELEE_BONUS.get(id);
            if (m != null && m[1] < now) {
                Util.MELEE_BONUS.remove(id);
            }
            double[] sp = Util.SPELLPOW.get(id);
            if (sp != null && sp[1] < now) {
                Util.SPELLPOW.remove(id);
            }
            double[] od = Util.OUTDMG.get(id);
            if (od != null && od[1] < now) {
                Util.OUTDMG.remove(id);
            }
            Long ci = Util.CC_IMMUNE.get(id);
            if (ci != null && ci < now) {
                Util.CC_IMMUNE.remove(id);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) {
            Util.release(v);
            Util.clearSpeed(v);
            clearState(v);
        }
    }

    private static void clearState(LivingEntity v) {
        UUID id = v.getUUID();
        Util.SPELLPOW.remove(id);
        Util.OUTDMG.remove(id);
        Util.BARRIER.remove(id);
        if (Util.ATK_SLOW.remove(id) != null) {
            AttributeInstance inst = v.getAttribute(Attributes.ATTACK_SPEED);
            if (inst != null) {
                inst.removeModifier(Util.ATK_ID);
            }
        }
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
        Util.CC_IMMUNE.remove(id);
        Util.KB_IMMUNE.remove(id);
        Util.clearReduce(p);
        if (!p.level().isClientSide) {
            clearState(p);
        }
        if (!p.level().isClientSide) {
            p.setNoGravity(false);
            p.setInvisible(false);
            Util.clearSpeed(p);
            Util.release(p);
            AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(com.example.jjkspells.rimuru.entity.RimEntityBase.KB_ID);
            }
            AttributeInstance ar = p.getAttribute(Attributes.ARMOR);
            if (ar != null) {
                ar.removeModifier(Util.ARMOR_ID);
            }
        }
    }
}
