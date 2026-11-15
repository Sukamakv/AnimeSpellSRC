package com.example.jjkspells.acheron.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.acheron.util.Util;
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

/** The rules the eight Acheron spells rely on: damage reduction, the Absolute Defense barrier, outgoing damage and attack speed penalties, speed changes, immunities. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class AcheronEvents {
    private AcheronEvents() {
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
        // Vergil: attacks on a caster who is in an evasion state are caught and handed to the spell
        if (!bypass && !Util.CATCH.isEmpty() && Util.CATCH.containsKey(v.getUUID()) && s.getEntity() instanceof LivingEntity att && att != v && Util.isEnemy(v, att)) {
            Util.INCOMING.computeIfAbsent(v.getUUID(), k -> new java.util.concurrent.ConcurrentLinkedQueue<>()).add(att.getUUID());
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
        // Probability Manipulation: a share of ordinary attacks against the caster simply miss
        if (!Util.PROB.isEmpty() && !bypass && !Util.reflecting) {
            double[] pr = Util.PROB.get(v.getUUID());
            if (pr != null && pr[0] >= v.level().getGameTime() && v.getRandom().nextDouble() < pr[1]) {
                e.setCanceled(true);
                Util.fx(v.level(), com.example.jjkspells.acheron.registry.ModParticles.SPARK.get(), v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 8, 0.6, 0.6, 0.6, 0.05);
                Util.sound(v.level(), v.getX(), v.getY(), v.getZ(), com.example.jjkspells.acheron.registry.ModSounds.CRIMSON_KNOT_APPLY.get(), 0.8f, 0.7f);
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
        // extra damage taken (wounds, marks, domains)
        if (!Util.VULN.isEmpty() && !bypass) {
            double[] vu = Util.VULN.get(v.getUUID());
            if (vu != null && vu[1] >= v.level().getGameTime()) {
                amount *= (float) (1.0 + vu[0]);
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
        // Probability Manipulation: a share of the caster's attacks deals extra damage
        if (atk instanceof LivingEntity pa && !Util.PROB.isEmpty() && !Util.reflecting) {
            double[] pr = Util.PROB.get(pa.getUUID());
            if (pr != null && pr[0] >= pa.level().getGameTime() && pa.getRandom().nextDouble() < pr[2]) {
                amount *= (float) (1.0 + pr[3]);
                Util.fx(v.level(), com.example.jjkspells.acheron.registry.ModParticles.SPARK.get(), v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(), 6, 0.4, 0.5, 0.4, 0.1);
            }
        }
        // Storm Dragon's Ascension: melee bonus, every Nth hit a lightning shockwave
        if (atk instanceof LivingEntity aa && !Util.ASC.isEmpty()) {
            double[] as = Util.ASC.get(aa.getUUID());
            if (as != null && as[0] >= aa.level().getGameTime() && as[1] >= 0 && (s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.MOB_ATTACK)) && v != aa) {
                amount += (float) as[1];
                as[2] += 1;
                if (as[3] > 0 && ((long) as[2]) % (long) as[3] == 0) {
                    Util.shockwave(aa, v, as[4], as[5], as[6]);
                }
            }
        }
        if (!Util.ASC.isEmpty() && !bypass) {
            double[] vs = Util.ASC.get(v.getUUID());
            if (vs != null && vs[0] >= v.level().getGameTime() && s.getDirectEntity() instanceof Projectile) {
                amount *= (float) (1.0 - vs[7]);
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
            return;
        }
        if (t != null && !Util.FEAR.isEmpty()) {
            Util.Fear f = Util.FEAR.get(e.getEntity().getUUID());
            if (f != null && f.caster.equals(t.getUUID()) && f.until >= e.getEntity().level().getGameTime()) {
                e.setCanceled(true);
            }
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
        if (!Util.DOT.isEmpty()) {
            Util.Dot d = Util.DOT.get(id);
            if (d != null) {
                if (now >= d.until || !v.isAlive()) {
                    Util.DOT.remove(id);
                } else if (now >= d.nextAt) {
                    d.nextAt = now + 20;
                    if (v.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        Entity c = sl.getEntity(d.caster);
                        if (c instanceof LivingEntity caster && caster.isAlive()) {
                            Util.hurt(caster, v, d.dps, 0);
                        } else {
                            v.hurt(v.damageSources().magic(), (float) d.dps);
                        }
                        Util.fx(sl, com.example.jjkspells.acheron.registry.ModParticles.SPARK.get(), v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 4, 0.3, 0.5, 0.3, 0.03);
                    }
                }
            }
        }
        if (!Util.FEAR.isEmpty()) {
            Util.Fear fr = Util.FEAR.get(id);
            if (fr != null && fr.until < now) {
                Util.FEAR.remove(id);
        Util.NOTP.remove(id);
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
            double[] vu = Util.VULN.get(id);
            if (vu != null && vu[1] < now) {
                Util.VULN.remove(id);
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
            UUID mk = com.example.jjkspells.acheron.util.Nihility.MARKED.remove(v.getUUID());
            if (mk != null && v.level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(mk) instanceof com.example.jjkspells.acheron.entity.RainleafEntity rl) {
                rl.transfer(v);
            }
            clearState(v);
            if (e.getSource().getEntity() instanceof LivingEntity k && !Util.ASC.isEmpty()) {
                double[] as = Util.ASC.get(k.getUUID());
                if (as != null && as[0] >= k.level().getGameTime()) {
                    as[8] += 1;
                }
            }
        }
    }

    private static void clearState(LivingEntity v) {
        UUID id = v.getUUID();
        Util.SPELLPOW.remove(id);
        Util.OUTDMG.remove(id);
        Util.PROB.remove(id);
        Util.CATCH.remove(id);
        Util.INCOMING.remove(id);
        Util.ASC.remove(id);
        Util.VULN.remove(id);
        Util.DOT.remove(id);
        Util.FEAR.remove(id);
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
        Util.SDT.remove(id);
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
                kb.removeModifier(com.example.jjkspells.acheron.entity.AchEntityBase.KB_ID);
            }
            AttributeInstance ar = p.getAttribute(Attributes.ARMOR);
            if (ar != null) {
                ar.removeModifier(Util.ARMOR_ID);
            }
        }
    }

    // ------------------------------------------------------------------ Spatial Imprisonment

    @SubscribeEvent
    public static void onEnderTeleport(net.minecraftforge.event.entity.EntityTeleportEvent.EnderEntity e) {
        if (!Util.NOTP.isEmpty() && e.getEntity() instanceof LivingEntity le && Util.cannotTeleport(le)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChorusTeleport(net.minecraftforge.event.entity.EntityTeleportEvent.ChorusFruit e) {
        if (!Util.NOTP.isEmpty() && Util.cannotTeleport(e.getEntityLiving())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPearlTeleport(net.minecraftforge.event.entity.EntityTeleportEvent.EnderPearl e) {
        if (!Util.NOTP.isEmpty() && Util.cannotTeleport(e.getPlayer())) {
            e.setCanceled(true);
        }
    }
}
