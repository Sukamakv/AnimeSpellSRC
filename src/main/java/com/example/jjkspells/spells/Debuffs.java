package com.example.jjkspells.spells;

import com.example.jjkspells.BalanceConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.effect.OverloadEvents;
import com.example.jjkspells.effect.StunEffect;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The wound / stun riders configured in jjk_spells-balance.toml. Every spell calls {@link #cast} when it is cast; for
 * the next few seconds anything that caster (or its summons) damages is wounded (cannot heal) and/or stunned, once per cast.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class Debuffs {
    private static final class Ctx {
        long until;
        double antiHeal, stun;
        final Set<UUID> done = ConcurrentHashMap.newKeySet();
    }

    private static final Map<UUID, Ctx> ACTIVE = new ConcurrentHashMap<>();

    private Debuffs() {}

    public static void cast(AbstractSpell spell, @Nullable LivingEntity caster) {
        if (caster == null || caster.level().isClientSide) return;
        BalanceConfig.Rule r = BalanceConfig.ENABLED.get() ? BalanceConfig.rule(spell.getSpellResource()) : null;
        if (r == null || r.window().get() <= 0 || r.antiHeal().get() <= 0) {
            ACTIVE.remove(caster.getUUID());
            return;
        }
        Ctx c = new Ctx();
        c.until = caster.level().getGameTime() + (long) (r.window().get() * 20);
        c.antiHeal = r.antiHeal().get();
        c.stun = 0; // the balance stun rider is retired: a stale jjk_spells-balance.toml can no longer stun anything
        ACTIVE.put(caster.getUUID(), c);
    }

    /** Called whenever {@code attacker} damages {@code target} (spell hits, percent hits, summons included). */
    public static void hit(@Nullable LivingEntity attacker, @Nullable LivingEntity target) {
        if (attacker == null || target == null || target == attacker || target.level().isClientSide) return;
        if (attacker instanceof IMagicSummon s && s.getSummoner() instanceof LivingEntity owner) attacker = owner;
        Ctx c = ACTIVE.get(attacker.getUUID());
        if (c == null || target == attacker) return;
        if (target.level().getGameTime() > c.until) {
            ACTIVE.remove(attacker.getUUID());
            return;
        }
        if (attacker.isAlliedTo(target) || !c.done.add(target.getUUID())) return;

        double scale = attacker instanceof Player ? BalanceConfig.PLAYER_SCALE.get() : 1.0;
        if (c.antiHeal > 0) {
            int ticks = (int) (c.antiHeal * 20 * scale);
            if (ticks > 0) {
                OverloadEvents.blockHealing(target, ticks);
                SpellDamage.irradiate(target, ticks);
            }
        }
        if (c.stun > 0) {
            double s = c.stun * scale;
            if (target.getType().is(Tags.EntityTypes.BOSSES)) s *= BalanceConfig.BOSS_STUN_SCALE.get();
            if (target instanceof Player) s = Math.min(s, BalanceConfig.PLAYER_STUN_CAP.get());
            int ticks = (int) (s * 20);
            if (ticks > 0) StunEffect.apply(target, ticks);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity a = event.getSource().getEntity();
        if (a instanceof LivingEntity la) hit(la, event.getEntity());
    }
}
