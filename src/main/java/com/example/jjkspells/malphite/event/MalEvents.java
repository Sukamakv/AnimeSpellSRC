package com.example.jjkspells.malphite.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malphite.config.MalConfig;
import com.example.jjkspells.malphite.registry.ModParticles;
import com.example.jjkspells.malphite.registry.ModSounds;
import com.example.jjkspells.malphite.util.Util;
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

/** The rules the five spells rely on: granite shield, damage reduction, staggers, armor and attack-speed debuffs, speed changes. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class MalEvents {
    private MalEvents() {
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
            return;
        }
        // Crippling Tremor: players are slowed through the attack speed attribute, mobs simply miss a share of their attacks
        if (attacker instanceof LivingEntity la && !(la instanceof net.minecraft.world.entity.player.Player) && !Util.ATK_SLOW.isEmpty()) {
            double[] a = Util.ATK_SLOW.get(la.getUUID());
            if (a != null && a[1] >= la.level().getGameTime() && la.getRandom().nextDouble() < a[0]) {
                e.setCanceled(true);
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

        // Granite Shield: stops a share of every hit, stores it and throws part of it back at the attacker
        Util.Granite gs = Util.GRANITE.get(v.getUUID());
        if (gs != null && !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float stopped = (float) (amount * gs.block);
            gs.prevented += stopped;
            amount -= stopped;
            if (v.level() instanceof ServerLevel sl) {
                Vec3 p = v.position().add(0, v.getBbHeight() * 0.6, 0);
                sl.sendParticles(ModParticles.ROCK.get(), p.x, p.y, p.z, 4, 0.35, 0.4, 0.35, 0.12);
                sl.sendParticles(ModParticles.GRAVEL.get(), p.x, p.y, p.z, 5, 0.35, 0.4, 0.35, 0.18);
                sl.sendParticles(ModParticles.SPARK.get(), p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.2);
                if (v.tickCount % 3 == 0) {
                    Util.sound(v.level(), v.getX(), v.getY() + 1, v.getZ(), ModSounds.CRACK.get(), 0.6f, 0.8f + v.getRandom().nextFloat() * 0.3f);
                }
            }
            Entity atk0 = s.getEntity();
            if (!Util.reflecting && atk0 instanceof LivingEntity la && la != v && la.isAlive() && stopped > 0) {
                var gc = MalConfig.GRANITE;
                double refl = Math.min(gc.reflectCap.get(), stopped * gc.reflectPercent.get() / 100.0);
                if (refl > 0.01) {
                    Util.reflecting = true;
                    try {
                        Util.hurt(v, la, refl, 0);
                    } finally {
                        Util.reflecting = false;
                    }
                    if (v.level() instanceof ServerLevel sl) {
                        Vec3 q = la.position().add(0, la.getBbHeight() * 0.5, 0);
                        sl.sendParticles(ModParticles.SHARD.get(), q.x, q.y, q.z, 8, 0.3, 0.3, 0.3, 0.25);
                        sl.sendParticles(ModParticles.ROCK.get(), q.x, q.y, q.z, 3, 0.3, 0.3, 0.3, 0.2);
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
        if (!Util.ARMOR_DOWN.isEmpty()) {
            double[] a = Util.ARMOR_DOWN.get(id);
            if (a != null && a[1] < now) {
                Util.ARMOR_DOWN.remove(id);
                AttributeInstance inst = v.getAttribute(Attributes.ARMOR);
                if (inst != null) {
                    inst.removeModifier(Util.ARMOR_ID);
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
            double[] ev = Util.EARTH_VULN.get(id);
            if (ev != null && ev[1] < now) {
                Util.EARTH_VULN.remove(id);
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
            Util.STUN.remove(v.getUUID());
            Util.clearSpeed(v);
            Util.ATK_SLOW.remove(v.getUUID());
            Util.ARMOR_DOWN.remove(v.getUUID());
            Util.GRANITE.remove(v.getUUID());
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
        Util.GRANITE.remove(id);
        Util.CC_IMMUNE.remove(id);
        Util.KB_IMMUNE.remove(id);
        Util.EARTH_VULN.remove(id);
        Util.clearReduce(p);
        if (!p.level().isClientSide) {
            p.setNoGravity(false);
            p.setInvisible(false);
            Util.clearSpeed(p);
            Util.release(p);
            Util.STUN.remove(id);
            AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(com.example.jjkspells.malphite.entity.MalEntityBase.KB_ID);
            }
            AttributeInstance ar = p.getAttribute(Attributes.ARMOR);
            if (ar != null) {
                ar.removeModifier(com.example.jjkspells.malphite.entity.MalEntityBase.GRANITE_ARMOR_ID);
                ar.removeModifier(Util.ARMOR_ID);
            }
            AttributeInstance at = p.getAttribute(Attributes.ATTACK_SPEED);
            if (at != null) {
                at.removeModifier(Util.ATK_ID);
            }
            Util.ATK_SLOW.remove(id);
            Util.ARMOR_DOWN.remove(id);
        }
    }
}
