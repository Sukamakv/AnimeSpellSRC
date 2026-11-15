package com.example.jjkspells.capri;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.learner.SkillGroups;
import com.example.jjkspells.relic.RelicConfig;
import com.example.jjkspells.relic.RelicRules;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything Capri does that matters for gameplay, decided here and nowhere else: whether she is worn, which spells she may cast,
 * when she casts them (through Iron's normal pipeline), which stats are lowered. The client only asks and displays.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class CapriServer {
    /** Per-player runtime state. Swept by LeakGuard when the player is gone. */
    public static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    public static final class Session {
        boolean linked;
        int lockId = -1;
        int pendingTimer = -1;
        String pendingId = "";
        int castCooldown;
        final Map<String, Long> lastCast = new HashMap<>();
        int syncTimer;
        boolean dirty = true;
        long lastSay;
        final Set<String> reportedBlocked = new HashSet<>();
        boolean onsOn, prevAuto;
        int onsMode;
        long onsEnd;
    }

    private CapriServer() {
    }

    private static Session session(ServerPlayer p) {
        return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
    }

    // ------------------------------------------------------------------ packets

    public static void handle(ServerPlayer p, CapriNet.Action m) {
        Session s = session(p);
        if (!CapriConfig.serverEnabled() || !CapriCurios.hasCapri(p)) {
            if (m.op == CapriNet.OP_SYNC) {
                sync(p, s);
            }
            return;
        }
        switch (m.op) {
            case CapriNet.OP_ONS -> {
                if (m.a == 2 ? !s.onsOn : m.a == 1) {
                    startOns(p, s);
                } else {
                    stopOns(p, s, false);
                }
            }
            case CapriNet.OP_ONS_MODE -> {
                s.onsMode = m.a < 0 ? 1 - s.onsMode : (m.a == 0 ? 0 : 1);
                if (s.onsOn) {
                    sendOns(p, s);
                    CapriNet.toPlayer(p, new CapriNet.Say(s.onsMode == 0 ? "ons_psycho" : "ons_strategic", ""));
                }
            }
            case CapriNet.OP_AUTOCAST -> {
                if (s.onsOn) {
                    CapriData.setAutoCast(p, true);
                } else if (CapriConfig.ALLOW_AUTOCAST.get()) {
                    CapriData.setAutoCast(p, m.a != 0);
                    s.pendingTimer = -1;
                }
            }
            case CapriNet.OP_SET_SPELLS -> {
                List<String> ok = new ArrayList<>();
                Map<String, CapriNet.Pool> pool = poolMap(p);
                for (String id : m.list) {
                    CapriNet.Pool e = pool.get(id);
                    if (e != null && e.status() != 3 && !ok.contains(id) && ok.size() < CapriConfig.maxSpells()) {
                        ok.add(id);
                    }
                }
                CapriData.setSpells(p, ok);
                s.reportedBlocked.clear();
            }
            case CapriNet.OP_SET_BREAK -> CapriData.setBreakDist(p, m.a);
            case CapriNet.OP_SET_INTERVAL -> CapriData.setInterval(p, m.s, Math.max(0, Math.min(20 * 120, m.a)));
            case CapriNet.OP_SET_ATTR -> {
                if (CapriConfig.ALLOW_ATTRIBUTES.get()) {
                    Attribute at = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(m.s));
                    if (at != null && p.getAttribute(at) != null && governable(m.s)) {
                        // only ever lower: never above 100 % of the real value, never below the configured minimum
                        int pct = Math.max(CapriConfig.minAttr(), Math.min(100, m.a));
                        CapriData.setAttr(p, m.s, pct);
                        governor(p);
                    }
                }
            }
            case CapriNet.OP_RESET_ATTRS -> {
                removeModifiers(p);
                CapriData.clearAttrs(p);
            }
            case CapriNet.OP_LOCK -> {
                if (m.a < 0) {
                    s.lockId = -1;
                } else {
                    Entity e = p.level().getEntity(m.a);
                    s.lockId = e instanceof LivingEntity le && le.isAlive() && le != p && p.distanceTo(le) < lockLimit(p, s) ? le.getId() : -1;
                }
            }
            default -> {
            }
        }
        sync(p, s);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) {
            return;
        }
        Session s = session(p);
        boolean linked = CapriConfig.serverEnabled() && CapriCurios.hasCapri(p);
        if (linked != s.linked) {
            s.linked = linked;
            s.dirty = true;
            if (linked) {
                CapriNet.toPlayer(p, new CapriNet.Say("boot", ""));
                governor(p);
            } else {
                removeModifiers(p);
                s.lockId = -1;
                s.pendingTimer = -1;
                if (s.onsOn) {
                    stopOns(p, s, false);
                }
                CapriNet.toPlayer(p, new CapriNet.Say("unlinked", ""));
            }
        }
        if (!linked) {
            if (s.dirty) {
                sync(p, s);
            }
            return;
        }
        if (p.tickCount % 20 == 0) {
            governor(p);
        }
        if (s.onsOn) {
            if (!p.isAlive()) {
                stopOns(p, s, false);
            } else if (p.level().getGameTime() >= s.onsEnd) {
                stopOns(p, s, true);
            } else if (!CapriData.autoCast(p)) {
                CapriData.setAutoCast(p, true);
                s.dirty = true;
            }
        }
        if (CapriData.autoCast(p) && (CapriConfig.ALLOW_AUTOCAST.get() || s.onsOn)) {
            autoCast(p, s);
        }
        if (s.lockId >= 0 && p.tickCount % 10 == 0) {
            Entity t = p.level().getEntity(s.lockId);
            if (t instanceof LivingEntity le && le.isAlive() && p.distanceTo(le) < lockLimit(p, s)) {
                int aggro = le instanceof Mob m ? (m.getTarget() != null ? m.getTarget().getId() : -1) : -2;
                CapriNet.toPlayer(p, new CapriNet.Target(s.lockId, aggro));
            } else {
                s.lockId = -1;
                CapriNet.toPlayer(p, new CapriNet.Target(-1, -2));
            }
        }
        if (s.dirty || ++s.syncTimer >= 20) {
            sync(p, s);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SESSIONS.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        Session s = SESSIONS.get(e.getEntity().getUUID());
        if (s != null) {
            s.lockId = -1;
            s.pendingTimer = -1;
            s.dirty = true;
            s.onsOn = false;
            if (e.getEntity() instanceof ServerPlayer sp) {
                CapriNet.toPlayer(sp, new CapriNet.Ons(false, s.onsMode, 0));
            }
        }
    }

    // ------------------------------------------------------------------ onslaught

    public static final int ONSLAUGHT_TICKS = 20 * 120, CYBER_TICKS = 20 * 20;

    private static int lockLimit(ServerPlayer p, Session s) {
        return s.onsOn ? Math.max(80, CapriData.breakDist(p)) : CapriData.breakDist(p);
    }

    private static void sendOns(ServerPlayer p, Session s) {
        CapriNet.toPlayer(p, new CapriNet.Ons(s.onsOn, s.onsMode, s.onsOn ? (int) Math.max(0, s.onsEnd - p.level().getGameTime()) : 0));
    }

    private static void startOns(ServerPlayer p, Session s) {
        if (s.onsOn || !p.isAlive() || p.hasEffect(CapriEffects.CYBER.get())) {
            return;
        }
        s.onsOn = true;
        s.onsEnd = p.level().getGameTime() + ONSLAUGHT_TICKS;
        s.prevAuto = CapriData.autoCast(p);
        CapriData.setAutoCast(p, true);
        s.dirty = true;
        sendOns(p, s);
        CapriNet.toPlayer(p, new CapriNet.Say("ons_on", ""));
    }

    /** expired = the two minutes ran out: the user pays for it with Cyberpsychosis. A manual stop is free. */
    private static void stopOns(ServerPlayer p, Session s, boolean expired) {
        if (!s.onsOn) {
            return;
        }
        s.onsOn = false;
        CapriData.setAutoCast(p, s.prevAuto);
        s.dirty = true;
        sendOns(p, s);
        if (expired) {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(CapriEffects.CYBER.get(), CYBER_TICKS, 0, false, false, true));
            CapriNet.toPlayer(p, new CapriNet.Say("ons_end", ""));
        } else {
            CapriNet.toPlayer(p, new CapriNet.Say("ons_off", ""));
        }
    }

    // ------------------------------------------------------------------ spells

    private static List<IronsBridge.Found> carried(ServerPlayer p) {
        List<IronsBridge.Found> out = new ArrayList<>();
        out.addAll(IronsBridge.spellsOf(p.getMainHandItem()));
        out.addAll(IronsBridge.spellsOf(p.getOffhandItem()));
        for (ItemStack st : CapriCurios.equipped(p)) {
            out.addAll(IronsBridge.spellsOf(st));
        }
        return out;
    }

    private static String relicNote(ServerPlayer p, AbstractSpell spell) {
        if (!RelicConfig.enabled() || (RelicConfig.creativeBypass() && p.isCreative())) {
            return "";
        }
        SkillGroups.Group g = RelicRules.groupOf(String.valueOf(spell.getSpellResource()));
        if (g == null || RelicRules.worn(p).contains(g.id())) {
            return "";
        }
        return g.title();
    }

    private static int status(ServerPlayer p, MagicData md, AbstractSpell spell, int level, String relicMissing) {
        if (!relicMissing.isEmpty()) {
            return 3;
        }
        var ci = md.getPlayerCooldowns().getSpellCooldowns().get(spell.getSpellId());
        if (ci != null && ci.getCooldownRemaining() > 0) {
            return 1;
        }
        if (md.getMana() < spell.getManaCost(level)) {
            return 2;
        }
        return md.isCasting() ? 4 : 0;
    }

    /** Every spell the player could cast right now (carried spellbooks, swords, curios), with its availability. */
    public static List<CapriNet.Pool> pool(ServerPlayer p) {
        MagicData md = MagicData.getPlayerMagicData(p);
        Map<String, CapriNet.Pool> out = new LinkedHashMap<>();
        for (IronsBridge.Found f : carried(p)) {
            String id = String.valueOf(f.spell().getSpellResource());
            if (out.containsKey(id)) {
                continue;
            }
            String note = relicNote(p, f.spell());
            var ci = md.getPlayerCooldowns().getSpellCooldowns().get(f.spell().getSpellId());
            int cd = ci == null ? 0 : ci.getCooldownRemaining();
            out.put(id, new CapriNet.Pool(id, f.level(), f.spell().getManaCost(f.level()), cd, status(p, md, f.spell(), f.level(), note), note));
        }
        return new ArrayList<>(out.values());
    }

    private static Map<String, CapriNet.Pool> poolMap(ServerPlayer p) {
        Map<String, CapriNet.Pool> m = new LinkedHashMap<>();
        for (CapriNet.Pool e : pool(p)) {
            m.put(e.id(), e);
        }
        return m;
    }

    private static LivingEntity pickTarget(ServerPlayer p, Session s) {
        double range = CapriConfig.AUTOCAST_RANGE.get();
        if (s.lockId >= 0 && p.level().getEntity(s.lockId) instanceof LivingEntity le && le.isAlive() && p.distanceTo(le) <= range + 8 && p.hasLineOfSight(le)) {
            return le;
        }
        LivingEntity best = null;
        double bestDot = Math.cos(Math.toRadians(40));
        Vec3 look = p.getLookAngle();
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), m -> m instanceof Enemy && m.isAlive() && !p.isAlliedTo(m))) {
            if (!p.hasLineOfSight(m)) {
                continue;
            }
            Vec3 d = m.getEyePosition().subtract(p.getEyePosition());
            if (d.length() > range) {
                continue;
            }
            double dot = d.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = m;
            }
        }
        return best;
    }

    private static void face(ServerPlayer p, LivingEntity t) {
        Vec3 d = t.getBoundingBox().getCenter().subtract(p.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    private static void say(ServerPlayer p, Session s, String event, String arg, int minGapTicks) {
        long now = p.level().getGameTime();
        if (now - s.lastSay >= minGapTicks) {
            s.lastSay = now;
            CapriNet.toPlayer(p, new CapriNet.Say(event, arg));
        }
    }

    private static String shortName(String id) {
        return id;
    }

    private static void autoCast(ServerPlayer p, Session s) {
        MagicData md = MagicData.getPlayerMagicData(p);
        if (md.isCasting()) {
            return;
        }
        if (s.castCooldown > 0) {
            s.castCooldown -= 5;
            return;
        }
        List<String> chosen = CapriData.spells(p);
        if (chosen.isEmpty()) {
            return;
        }
        Map<String, CapriNet.Pool> pool = poolMap(p);
        // authorization is revalidated every time: a spell whose relic is gone is dropped at once
        List<String> valid = new ArrayList<>();
        for (String id : chosen) {
            CapriNet.Pool e = pool.get(id);
            if (e == null || e.status() == 3) {
                if (s.reportedBlocked.add(id)) {
                    say(p, s, "blocked_relic", shortName(id), 40);
                }
                continue;
            }
            valid.add(id);
        }
        if (valid.size() != chosen.size()) {
            CapriData.setSpells(p, valid);
            s.dirty = true;
        }
        if (valid.isEmpty()) {
            return;
        }
        LivingEntity target = pickTarget(p, s);
        if (target == null) {
            s.pendingTimer = -1;
            return;
        }
        if (s.pendingTimer >= 0) {
            s.pendingTimer -= 5;
            if (s.pendingTimer > 0) {
                return;
            }
            String id = s.pendingId;
            s.pendingTimer = -1;
            CapriNet.Pool e = pool.get(id);
            if (e == null || e.status() != 0) {
                return;
            }
            for (IronsBridge.Found f : carried(p)) {
                if (String.valueOf(f.spell().getSpellResource()).equals(id)) {
                    face(p, target);
                    boolean curio = !f.source().equals(p.getMainHandItem()) && !f.source().equals(p.getOffhandItem());
                    if (IronsBridge.cast(p, f.spell(), f.level(), f.source(), curio)) {
                        s.lastCast.put(id, p.level().getGameTime());
                        s.castCooldown = CapriConfig.CAST_INTERVAL.get();
                    }
                    break;
                }
            }
            return;
        }
        // strict priority: the first spell in the player's list that is ready and past its own interval wins
        Map<String, Integer> iv = CapriData.intervals(p);
        long now = p.level().getGameTime();
        for (String id : valid) {
            CapriNet.Pool e = pool.get(id);
            if (e == null || e.status() != 0) {
                continue;
            }
            Long last = s.lastCast.get(id);
            if (last != null && now - last < iv.getOrDefault(id, 0)) {
                continue;
            }
            s.pendingId = id;
            s.pendingTimer = Math.max(5, CapriConfig.CAST_WARNING_TICKS.get());
            say(p, s, "autocast", id, 30);
            CapriNet.toPlayer(p, new CapriNet.Say("slot_pulse", String.valueOf(chosen.indexOf(id))));
            return;
        }
    }

    private static void sync(ServerPlayer p, Session s) {
        s.dirty = false;
        s.syncTimer = 0;
        boolean linked = s.linked;
        CapriNet.toPlayer(p, new CapriNet.Sync(linked, linked && CapriData.autoCast(p), linked ? CapriData.spells(p) : List.of(),
                linked ? CapriData.attrs(p) : Map.of(), linked ? CapriData.intervals(p) : Map.of(), linked ? pool(p) : List.of()));
    }

    // ------------------------------------------------------------------ stat governor: it only ever lowers

    public static boolean governable(String id) {
        return !id.equals("minecraft:generic.follow_range") && !id.equals("minecraft:zombie.spawn_reinforcements") && !id.equals("minecraft:generic.flying_speed");
    }

    private static UUID modId(String attr) {
        return UUID.nameUUIDFromBytes(("jjk_capri:" + attr).getBytes(StandardCharsets.UTF_8));
    }

    /** Applies the player's chosen percentages as negative multipliers that scale the whole effective value (gear and potions included). */
    public static void governor(ServerPlayer p) {
        Map<String, Integer> attrs = CapriData.attrs(p);
        for (Map.Entry<String, Integer> en : attrs.entrySet()) {
            Attribute at = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(en.getKey()));
            AttributeInstance inst = at == null ? null : p.getAttribute(at);
            if (inst == null) {
                continue;
            }
            UUID id = modId(en.getKey());
            int pct = Math.max(CapriConfig.minAttr(), Math.min(100, en.getValue()));
            double amount = pct / 100.0 - 1.0;
            AttributeModifier cur = inst.getModifier(id);
            if (pct >= 100) {
                if (cur != null) {
                    inst.removeModifier(id);
                }
                continue;
            }
            if (en.getKey().equals("minecraft:generic.max_health") && cur != null) {
                // never let the reduction push the maximum below one heart: 2 hp is the floor
                double real = inst.getValue() / (1.0 + cur.getAmount());
                if (real * (1.0 + amount) < 2.0 && real > 2.0) {
                    amount = 2.0 / real - 1.0;
                }
            }
            if (cur == null || Math.abs(cur.getAmount() - amount) > 1.0e-6) {
                inst.removeModifier(id);
                inst.addTransientModifier(new AttributeModifier(id, "Capri governor", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        // modifiers of stats the player has since restored are already gone; clean any orphan
        for (Attribute at : ForgeRegistries.ATTRIBUTES) {
            AttributeInstance inst = p.getAttribute(at);
            ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(at);
            if (inst != null && key != null && !attrs.containsKey(key.toString()) && inst.getModifier(modId(key.toString())) != null) {
                inst.removeModifier(modId(key.toString()));
            }
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    public static void removeModifiers(ServerPlayer p) {
        for (Attribute at : ForgeRegistries.ATTRIBUTES) {
            AttributeInstance inst = p.getAttribute(at);
            ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(at);
            if (inst != null && key != null && inst.getModifier(modId(key.toString())) != null) {
                inst.removeModifier(modId(key.toString()));
            }
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }
}
