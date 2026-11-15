package com.example.jjkspells.effect;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSpells;
import com.example.jjkspells.spells.CripplingStrikeSpell;
import com.example.jjkspells.spells.SpellDamage;
import com.example.jjkspells.spells.DariusFX;
import com.example.jjkspells.spells.DariusMarkManager;
import com.example.jjkspells.spells.FX;
import com.example.jjkspells.spells.MartialFX;
import com.example.jjkspells.spells.MoonFX;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the Crippling Strike buff does to the next melee hit:
 *   - a hit that gets through: bonus damage, Slowness III, a red mark, a stagger backward, red sparks
 *   - a hit that is blocked by a shield: the shield takes heavy durability damage and is disabled
 *   - a kill of an ordinary (non-boss) enemy with it: half of the spell's remaining cooldown is refunded
 * The buff is used up by the first melee hit either way.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class CripplingStrikeEvents {

    /** A kill counts for the refund if the victim dies within this many ticks of the strike. */
    private static final int KILL_WINDOW = 10;

    private record Watch(UUID attacker, long tick) {}
    private record Stagger(ServerLevel level, UUID target, Vec3 dir) {}

    private static final Map<UUID, Watch> KILL_WATCH = new HashMap<>();
    private static final List<Stagger> PENDING = new ArrayList<>();

    private static boolean isMelee(DamageSource src, LivingEntity attacker) {
        return (src.is(DamageTypes.PLAYER_ATTACK) || src.is(DamageTypes.MOB_ATTACK)) && src.getDirectEntity() == attacker;
    }

    /** Same test vanilla uses: the shield only stops hits from in front of it. */
    private static boolean blockedByShield(LivingEntity target, DamageSource src) {
        if (!target.isBlocking()) return false;
        Vec3 from = src.getSourcePosition();
        if (from == null) return true;
        Vec3 view = target.getViewVector(1.0f);
        Vec3 toTarget = from.vectorTo(target.position()).normalize();
        toTarget = new Vec3(toTarget.x, 0, toTarget.z);
        return toTarget.dot(view) < 0.0;
    }

    /** Fires before blocking is worked out, so this is where a shield gets wrecked. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide) return;
        if (!attacker.hasEffect(ModEffects.CRIPPLING_STRIKE.get()) || !isMelee(event.getSource(), attacker)) return;
        LivingEntity target = event.getEntity();
        if (target == attacker || target.isAlliedTo(attacker) || !blockedByShield(target, event.getSource())) return;

        attacker.removeEffect(ModEffects.CRIPPLING_STRIKE.get());
        ItemStack shield = target.getUseItem();
        Item shieldItem = shield.getItem();
        shield.hurtAndBreak(CripplingStrikeSpell.SHIELD_DURABILITY_DAMAGE, target, t -> t.broadcastBreakEvent(t.getUsedItemHand()));
        if (target instanceof Player player) {
            player.getCooldowns().addCooldown(shieldItem, CripplingStrikeSpell.SHIELD_DISABLE_TICKS);
            player.stopUsingItem();
        }
        target.level().broadcastEntityEvent(target, (byte) 30);   // the shield-break animation
        if (attacker.level() instanceof ServerLevel level) {
            Vec3 p = target.position().add(0, target.getBbHeight() * 0.6, 0);
            DariusFX.metalBurst(level, p, 1.3f);
            MartialFX.sound(level, p, SoundEvents.SHIELD_BREAK, 1.0f, 0.8f);
        }
    }

    /** The empowered hit itself. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !(attacker.level() instanceof ServerLevel level)) return;
        MobEffectInstance buff = attacker.getEffect(ModEffects.CRIPPLING_STRIKE.get());
        if (buff == null || !isMelee(event.getSource(), attacker)) return;
        LivingEntity target = event.getEntity();
        if (target == attacker || target.isAlliedTo(attacker)) return;

        int spellLevel = buff.getAmplifier() + 1;
        attacker.removeEffect(ModEffects.CRIPPLING_STRIKE.get());

        float bonus = MoonFX.weaponDamage(attacker) * CripplingStrikeSpell.DAMAGE_MULT * DariusFX.levelScale(spellLevel);
        event.setAmount(event.getAmount() + bonus);
        SpellDamage.wound(target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CripplingStrikeSpell.SLOW_TICKS, CripplingStrikeSpell.SLOW_AMPLIFIER));
        DariusMarkManager.mark(level, target, 16, DariusMarkManager.MARK);

        // the stagger is applied a tick later, after vanilla has finished its own knockback
        Vec3 dir = target.position().subtract(attacker.position());
        PENDING.add(new Stagger(level, target.getUUID(), new Vec3(dir.x, 0, dir.z)));
        KILL_WATCH.put(target.getUUID(), new Watch(attacker.getUUID(), level.getGameTime()));

        // the short brutal downward chop, red sparks, heavy sound
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 c = target.position().add(0, 0.1, 0);
        DariusFX.ribbon(level, c, flat, up, 2.4f, 2.4f, 0f, 2.3f, 0.15f, 0.85f, 9, com.example.jjkspells.particle.MoonRibbonOptions.BRIGHT_RED);
        DariusFX.ribbon(level, c, flat, up, 2.5f, 2.5f, 0.3f, 2.2f, 0.2f, 0.35f, 8, com.example.jjkspells.particle.MoonRibbonOptions.RED);
        Vec3 hit = target.getBoundingBox().getCenter();
        DariusFX.redBurst(level, hit, 1.4f);
        FX.one(level, ModParticles.DR_SHOCK.get(), hit, new Vec3(2.2, 0, 0));
        MartialFX.sound(level, hit, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        MartialFX.sound(level, hit, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.7f);
    }

    /** A kill with the strike refunds part of the cooldown, unless the victim was a boss. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Watch w = KILL_WATCH.remove(dead.getUUID());
        if (w == null || !(dead.level() instanceof ServerLevel level)) return;
        if (level.getGameTime() - w.tick() > KILL_WINDOW || DariusFX.isBoss(dead)) return;
        Entity a = level.getEntity(w.attacker());
        if (a instanceof LivingEntity attacker) {
            DariusFX.refundCooldown(attacker, ModSpells.CRIPPLING_STRIKE.get(), CripplingStrikeSpell.KILL_COOLDOWN_REFUND);
            DariusFX.redBurst(level, attacker.position().add(0, 1.0, 0), 1.0f);
        }
    }

    /** Applies the queued staggers; forgets old kill watches. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PENDING.isEmpty()) {
            for (Stagger s : PENDING) {
                Entity e = s.level().getEntity(s.target());
                if (e instanceof LivingEntity le && le.isAlive()) {
                    MartialFX.shove(le, s.dir().x, s.dir().z, 0.55, 0.12);
                }
            }
            PENDING.clear();
        }
        if (!KILL_WATCH.isEmpty() && event.getServer() != null && event.getServer().getTickCount() % 40 == 0) {
            long now = event.getServer().overworld().getGameTime();
            Iterator<Map.Entry<UUID, Watch>> it = KILL_WATCH.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().tick() > 100) it.remove();
            }
        }
    }

    /** While the strike is armed, the axe head smoulders with gathering red energy. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.tickCount % 2 != 0 || !(e.level() instanceof ServerLevel level)) return;
        if (!e.hasEffect(ModEffects.CRIPPLING_STRIKE.get())) return;
        Vec3 hand = DariusFX.handPos(e);
        DariusFX.gather(level, hand, 0.9, 2);
        FX.one(level, ModParticles.DR_GLOW.get(), hand.add(0, 0.2, 0), new Vec3(0.4, 0, 0));
    }
}
