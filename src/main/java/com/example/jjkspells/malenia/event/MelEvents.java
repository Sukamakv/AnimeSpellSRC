package com.example.jjkspells.malenia.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malenia.util.Util;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.server.level.ServerLevel;
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

/** The rules the five spells rely on: damage reduction, projectile reduction, Scarlet Rot, speed changes, immunities. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class MelEvents {
    private MelEvents() {
    }

    // ------------------------------------------------------------------ damage in

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) {
            return;
        }
        boolean bypass = e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && (Util.UNTOUCHABLE.contains(v.getUUID()) || Util.HIDDEN.contains(v.getUUID()))) {
            e.setCanceled(true);
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
        // Aerial Grace: projectiles hurt less
        if (!Util.PROJ_REDUCE.isEmpty() && !bypass && s.getDirectEntity() instanceof Projectile) {
            double[] pr = Util.PROJ_REDUCE.get(v.getUUID());
            if (pr != null && pr[1] >= v.level().getGameTime()) {
                amount *= (float) (1.0 - pr[0]);
            }
        }
        // melee damage bonus of the attacker (Goddess of Rot)
        Entity atk = s.getEntity();
        if (atk instanceof LivingEntity la && !Util.MELEE_BONUS.isEmpty() && (s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.MOB_ATTACK))) {
            double[] mb = Util.MELEE_BONUS.get(la.getUUID());
            if (mb != null && mb[1] >= la.level().getGameTime()) {
                amount *= (float) (1.0 + mb[0]);
                // the Goddess of Rot steals life with her melee hits too
                double[] lb = Util.LS_BONUS.get(la.getUUID());
                if (lb != null && lb[1] >= la.level().getGameTime()) {
                    la.heal((float) (amount * 0.05 * (1.0 + lb[0])));
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
        // Scarlet Rot: damage once per second until it runs out
        if (!Util.ROT.isEmpty()) {
            Util.Rot r = Util.ROT.get(id);
            if (r != null) {
                if (now >= r.until || !v.isAlive()) {
                    Util.ROT.remove(id);
                } else if (now >= r.nextAt) {
                    r.nextAt = now + 20;
                    if (v.level() instanceof ServerLevel sl) {
                        Entity c = sl.getEntity(r.caster);
                        LivingEntity caster = c instanceof LivingEntity lc ? lc : null;
                        if (caster != null && caster.isAlive()) {
                            Util.rotTick(caster, v, r.dps);
                        } else {
                            v.hurt(v.damageSources().magic(), (float) r.dps);
                        }
                        Util.fx(sl, com.example.jjkspells.malenia.registry.ModParticles.ROT.get(), v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 3, 0.3, 0.5, 0.3, 0.03);
                    }
                }
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
            double[] lb = Util.LS_BONUS.get(id);
            if (lb != null && lb[1] < now) {
                Util.LS_BONUS.remove(id);
            }
            double[] pr = Util.PROJ_REDUCE.get(id);
            if (pr != null && pr[1] < now) {
                Util.PROJ_REDUCE.remove(id);
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
            Util.ROT.remove(v.getUUID());
            Util.WOUNDS.remove(v.getUUID());
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
        Util.PROJ_REDUCE.remove(id);
        Util.ROT.remove(id);
        Util.WOUNDS.remove(id);
        Util.clearReduce(p);
        if (!p.level().isClientSide) {
            p.setNoGravity(false);
            p.setInvisible(false);
            Util.clearSpeed(p);
            Util.release(p);
            AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(com.example.jjkspells.malenia.entity.MelEntityBase.KB_ID);
            }
            AttributeInstance ar = p.getAttribute(Attributes.ARMOR);
            if (ar != null) {
                ar.removeModifier(Util.ARMOR_ID);
            }
        }
    }
}
