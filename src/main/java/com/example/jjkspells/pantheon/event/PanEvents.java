package com.example.jjkspells.pantheon.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.pantheon.util.Util;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

/** The rules the five spells rely on: shield, damage reduction, stun, burning, speed changes, hidden casters. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class PanEvents {
    private PanEvents() {
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
        // a stunned creature cannot hurt anyone
        Entity attacker = s.getEntity();
        if (attacker != null && !Util.STUN.isEmpty() && Util.isStunned(attacker)) {
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

        // Aegis: the shield takes frontal hits (it cannot stop things that bypass everything, like the void)
        Util.Aegis ag = Util.AEGIS.get(v.getUUID());
        if (ag != null && !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Vec3 from = s.getSourcePosition();
            if (from == null && s.getDirectEntity() != null) {
                from = s.getDirectEntity().position();
            }
            if (from != null) {
                Vec3 look = v.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0, look.z);
                Vec3 to = new Vec3(from.x - v.getX(), 0, from.z - v.getZ());
                if (flat.lengthSqr() > 1.0E-6 && to.lengthSqr() > 1.0E-6 && flat.normalize().dot(to.normalize()) >= ag.cosHalf) {
                    float stopped = (float) (amount * ag.block);
                    ag.prevented += stopped;
                    amount -= stopped;
                    if (v.level() instanceof ServerLevel sl) {
                        Vec3 p = v.position().add(look.x * 1.1, v.getBbHeight() * 0.6, look.z * 1.1);
                        sl.sendParticles(com.example.jjkspells.pantheon.registry.ModParticles.METAL.get(), p.x, p.y, p.z, 5, 0.3, 0.3, 0.3, 0.1);
                        sl.sendParticles(com.example.jjkspells.pantheon.registry.ModParticles.SPARK.get(), p.x, p.y, p.z, 4, 0.25, 0.25, 0.25, 0.2);
                        if (v.tickCount % 3 == 0) {
                            Util.sound(v.level(), v.getX(), v.getY() + 1, v.getZ(), com.example.jjkspells.pantheon.registry.ModSounds.CLANG.get(), 0.6f, 1.0f + v.getRandom().nextFloat() * 0.3f);
                        }
                    }
                }
            }
        }
        double red = Util.reduction(v);
        if (red > 0 && !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= (float) (1.0 - red);
        }
        // melee damage bonus of the attacker (Mortal Will)
        Entity atk = s.getEntity();
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

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide && !Util.STUN.isEmpty() && Util.isStunned(v)) {
            Vec3 m = v.getDeltaMovement();
            v.setDeltaMovement(m.x, 0, m.z);
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
        if (!Util.BURN.isEmpty()) {
            Util.Burn b = Util.BURN.get(id);
            if (b != null) {
                if (now >= b.until || !v.isAlive()) {
                    Util.BURN.remove(id);
                } else if (now >= b.next) {
                    b.next = now + 20;
                    LivingEntity caster = v.level() instanceof ServerLevel sl && sl.getEntity(b.caster) instanceof LivingEntity c ? c : null;
                    if (caster != null) {
                        Util.hurt(caster, v, b.dps, 0);
                        if (v.level() instanceof ServerLevel sl) {
                            sl.sendParticles(com.example.jjkspells.pantheon.registry.ModParticles.FLAME.get(), v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 6, 0.3, 0.4, 0.3, 0.03);
                        }
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
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) {
            Util.release(v);
            Util.BURN.remove(v.getUUID());
            Util.STUN.remove(v.getUUID());
            Util.clearSpeed(v);
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
        Util.AEGIS.remove(id);
        Util.clearReduce(p);
        if (!p.level().isClientSide) {
            p.setNoGravity(false);
            p.setInvisible(false);
            Util.clearSpeed(p);
            Util.release(p);
            Util.STUN.remove(id);
            AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(com.example.jjkspells.pantheon.entity.PanEntityBase.KB_ID);
            }
            AttributeInstance sp = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (sp != null) {
                sp.removeModifier(com.example.jjkspells.pantheon.entity.PanEntityBase.AEGIS_SPEED_ID);
            }
        }
    }
}
