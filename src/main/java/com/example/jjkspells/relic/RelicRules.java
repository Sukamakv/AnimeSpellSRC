package com.example.jjkspells.relic;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.learner.SkillGroups;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Spells of a character can only be cast while that character's Relic is worn. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class RelicRules {
    private static Map<String, SkillGroups.Group> bySpell;

    private RelicRules() {}

    private static Map<String, SkillGroups.Group> map() {
        if (bySpell == null) {
            Map<String, SkillGroups.Group> m = new HashMap<>();
            for (SkillGroups.Group g : SkillGroups.ALL) {
                if (g.id().equals("wards") || g.id().equals("relics")) continue;
                SkillGroups.Group owner = g;
                if (SkillGroups.isPrime(g)) {   // PRIME spells need the same Relic as the character they belong to
                    String base = g.id().substring(SkillGroups.PRIME_PREFIX.length());
                    for (SkillGroups.Group o : SkillGroups.ALL) if (o.id().equals(base)) owner = o;
                }
                for (String s : g.spells()) if (!SkillGroups.isItem(s)) m.putIfAbsent(s, owner);
            }
            bySpell = m;
        }
        return bySpell;
    }

    /** The character section whose Relic a spell needs (spell id with or without the mod prefix), or null if it needs none. */
    public static SkillGroups.Group groupOf(String spellId) {
        String id = spellId;
        if (id.startsWith(JJKSpells.MODID + ":")) id = id.substring(JJKSpells.MODID.length() + 1);
        else if (id.contains(":")) return null;
        return map().get(id);
    }

    // Curios is reached by reflection so the mod compiles without it on the classpath.
    private static java.lang.reflect.Method getInventory, getEquipped;
    private static boolean lookedUp = false, available = false;

    private static void lookUp() {
        lookedUp = true;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            getInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            getEquipped = handler.getMethod("getEquippedCurios");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    /** Group ids of the Relics {@code e} wears. */
    public static Set<String> worn(LivingEntity e) {
        Set<String> out = new HashSet<>();
        if (!lookedUp) lookUp();
        if (!available) return out;
        try {
            Object opt = getInventory.invoke(null, e);
            if (opt instanceof LazyOptional<?> lazy) {
                Object handler = lazy.resolve().orElse(null);
                if (handler != null && getEquipped.invoke(handler) instanceof IItemHandler eq) {
                    for (int i = 0; i < eq.getSlots(); i++) {
                        if (eq.getStackInSlot(i).getItem() instanceof RelicItem r) out.add(r.group());
                    }
                }
            }
        } catch (Throwable ignored) {
            // curios missing or changed: treat as no relics
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPreCast(SpellPreCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !RelicConfig.enabled()) return;
        String id = String.valueOf(e.getSpellId());
        if (id.startsWith(JJKSpells.MODID + ":")) id = id.substring(JJKSpells.MODID.length() + 1);
        else if (id.contains(":")) return;
        SkillGroups.Group g = map().get(id);
        if (g == null) return;
        if (RelicConfig.creativeBypass() && p.isCreative()) return;
        if (worn(p).contains(g.id())) return;
        e.setCanceled(true);
        p.displayClientMessage(Component.literal("You need the " + g.title() + " Relic in a Relic slot to cast this"), true);
    }
}
