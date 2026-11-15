package com.example.jjkspells;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Memory-leak guard for the whole project. The spell packages keep short-lived state in static collections (holds, slows, damage
 * reduction, running sequences...). Two things could make them grow or pin a world in memory:
 *  1. entries keyed by an entity UUID that nobody removes when the entity dies or unloads - swept every 2 minutes: an entry whose
 *     entity is in no loaded level in two sweeps in a row is dropped;
 *  2. sessions and state that survive leaving a world (singleplayer: back to the title screen, then into another world) and keep
 *     the old ServerLevel alive - every one of them is cleared when the server stops.
 * The collections are found by reflection from the list below (generated from the source), so a missing class or field is only logged.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class LeakGuard {
    private static final Logger LOG = LoggerFactory.getLogger("jjk_spells");
    private static final String ROOT = "com.example.jjkspells.";
    private static final int SWEEP_TICKS = 2400;

    /** "class#FIELD#kind": U = a collection keyed by entity UUID, C = a collection that is only cleared when the server stops. */
    private static final String[] FIELDS = {
            "capri.CapriServer#SESSIONS#U",
            "acheron.util.Util#LOCKED#U",
            "acheron.util.Util#UNTOUCHABLE#U",
            "acheron.util.Util#FALL_IMMUNE#U",
            "acheron.util.Util#HOLD#U",
            "acheron.util.Util#REDUCE#U",
            "acheron.util.Util#SLOWED#U",
            "acheron.util.Util#HASTED#U",
            "acheron.util.Util#HEAL_CUT#U",
            "acheron.util.Util#MELEE_BONUS#U",
            "acheron.util.Util#KB_IMMUNE#U",
            "acheron.util.Util#HIDDEN#U",
            "acheron.util.Util#SPELLPOW#U",
            "acheron.util.Util#OUTDMG#U",
            "acheron.util.Util#ATK_SLOW#U",
            "acheron.util.Util#BARRIER#U",
            "acheron.util.Util#CC_IMMUNE#U",
            "acheron.util.Util#VULN#U",
            "acheron.util.Util#DOT#U",
            "acheron.util.Util#NOTP#U",
            "acheron.util.Util#FEAR#U",
            "acheron.util.Util#PROB#U",
            "acheron.util.Util#ASC#U",
            "acheron.util.Util#CATCH#U",
            "acheron.util.Util#INCOMING#U",
            "acheron.util.Util#SDT#U",
            "acheron.util.Nihility#MARKED#U",
            "afterlife.util.Blossom#OWNER#U",
            "afterlife.util.Util#LOCKED#U",
            "afterlife.util.Util#UNTOUCHABLE#U",
            "afterlife.util.Util#FALL_IMMUNE#U",
            "afterlife.util.Util#BUDGET#U",
            "diablo.entity.NegationEntity#ACTIVE#U",
            "diablo.util.Util#LOCKED#U",
            "diablo.util.Util#UNTOUCHABLE#U",
            "diablo.util.Util#FALL_IMMUNE#U",
            "diablo.util.Util#HOLD#U",
            "diablo.util.Util#REDUCE#U",
            "diablo.util.Util#SLOWED#U",
            "diablo.util.Util#HASTED#U",
            "diablo.util.Util#HEAL_CUT#U",
            "diablo.util.Util#MELEE_BONUS#U",
            "diablo.util.Util#KB_IMMUNE#U",
            "diablo.util.Util#HIDDEN#U",
            "diablo.util.Util#SPELLPOW#U",
            "diablo.util.Util#OUTDMG#U",
            "diablo.util.Util#ATK_SLOW#U",
            "diablo.util.Util#BARRIER#U",
            "diablo.util.Util#CC_IMMUNE#U",
            "diablo.util.Util#VULN#U",
            "diablo.util.Util#DOT#U",
            "diablo.util.Util#FEAR#U",
            "dragon.entity.AscensionFxEntity#ASCENDING#U",
            "effect.AatroxEvents#HEALED#U",
            "effect.CripplingStrikeEvents#KILL_WATCH#U",
            "effect.CripplingStrikeEvents#PENDING#C",
            "entity.ColossusBond#BONDS#U",
            "prime.BoundlessSlashManager#ACTIVE#C",
            "prime.WorldDisintegrationManager#ACTIVE#C",
            "prime.UniverseSeveringManager#ACTIVE#C",
            "frieren.entity.AncientReleaseEntity#ACTIVE#C",
            "frieren.entity.HeightOfMagicEntity#ACTIVE#C",
            "garou.GarouEvents#ACTIVE#C",
            "garou.GarouEvents#ACTIVE_GK#C",
            "garou.GarouEvents#ACTIVE_CP#C",
            "garou.entity.CosmicRadiationHooks#ACTIVE#C",
            "yhwach.VollstreckungEvents#ENTRIES#C",
            "yasuo.entity.DuelEvents#DUELS#U",
            "dante.entity.DevilTriggerEntity#ACTIVE#U",
            "dante.entity.RoyalGuardEntity#ACTIVE#U",
            "dante.entity.StylishRankEntity#ACTIVE#U",
            "yhwach.entity.AlmightyEntity#ACTIVE#C",
            "yhwach.entity.SanktZwingerEntity#ACTIVE#C",
            "prime.PrimeStatus#LOCK#U",
            "prime.PrimeStatus#SHRUNK#U",
            "prime.VoidBlueManager#ACTIVE#C",
            "prime.HeavenlyRedManager#ACTIVE#C",
            "prime.DivinePurpleManager#ACTIVE#C",
            "prime.HeavenlyOnslaughtManager#ACTIVE#C",
            "gil.EnkiduManager#ACTIVE#C",
            "gil.EnumaManager#ACTIVE#C",
            "gil.GateManager#ACTIVE#C",
            "gil.GilState#GUARDS#U",
            "gil.GilState#FALL_SAFE#U",
            "gil.GilState#SUPREMACY#U",
            "gil.GilState#DIVINE_BOUND#U",
            "gil.MerodachManager#ACTIVE#C",
            "gil.VimanaManager#ACTIVE#C",
            "gil.VimanaManager#GROUND#U",
            "malenia.util.Util#LOCKED#U",
            "malenia.util.Util#UNTOUCHABLE#U",
            "malenia.util.Util#FALL_IMMUNE#U",
            "malenia.util.Util#HOLD#U",
            "malenia.util.Util#REDUCE#U",
            "malenia.util.Util#SLOWED#U",
            "malenia.util.Util#HASTED#U",
            "malenia.util.Util#HEAL_CUT#U",
            "malenia.util.Util#MELEE_BONUS#U",
            "malenia.util.Util#KB_IMMUNE#U",
            "malenia.util.Util#HIDDEN#U",
            "malenia.util.Util#LS_BONUS#U",
            "malenia.util.Util#PROJ_REDUCE#U",
            "malenia.util.Util#ROT#U",
            "malenia.util.Util#WOUNDS#U",
            "malenia.util.Util#CC_IMMUNE#U",
            "malphite.entity.GraniteEntity#ACTIVE#U",
            "malphite.util.Util#LOCKED#U",
            "malphite.util.Util#UNTOUCHABLE#U",
            "malphite.util.Util#FALL_IMMUNE#U",
            "malphite.util.Util#HOLD#U",
            "malphite.util.Util#STUN#U",
            "malphite.util.Util#REDUCE#U",
            "malphite.util.Util#SLOWED#U",
            "malphite.util.Util#HASTED#U",
            "malphite.util.Util#HEAL_CUT#U",
            "malphite.util.Util#MELEE_BONUS#U",
            "malphite.util.Util#KB_IMMUNE#U",
            "malphite.util.Util#HIDDEN#U",
            "malphite.util.Util#GRANITE#U",
            "malphite.util.Util#ATK_SLOW#U",
            "malphite.util.Util#ARMOR_DOWN#U",
            "malphite.util.Util#EARTH_VULN#U",
            "malphite.util.Util#CC_IMMUNE#U",
            "overlord.entity.GoatBase#GUARDED#U",
            "overlord.util.Magic#LOCKED#U",
            "pantheon.spell.CometSpearSpell#START#U",
            "pantheon.spell.CometSpearSpell#THROWN#U",
            "pantheon.util.Util#LOCKED#U",
            "pantheon.util.Util#UNTOUCHABLE#U",
            "pantheon.util.Util#FALL_IMMUNE#U",
            "pantheon.util.Util#HOLD#U",
            "pantheon.util.Util#STUN#U",
            "pantheon.util.Util#REDUCE#U",
            "pantheon.util.Util#SLOWED#U",
            "pantheon.util.Util#HASTED#U",
            "pantheon.util.Util#HEAL_CUT#U",
            "pantheon.util.Util#MELEE_BONUS#U",
            "pantheon.util.Util#KB_IMMUNE#U",
            "pantheon.util.Util#HIDDEN#U",
            "pantheon.util.Util#BURN#U",
            "pantheon.util.Util#AEGIS#U",
            "rimuru.entity.DefenseEntity#ACTIVE#U",
            "rimuru.util.Util#LOCKED#U",
            "rimuru.util.Util#UNTOUCHABLE#U",
            "rimuru.util.Util#FALL_IMMUNE#U",
            "rimuru.util.Util#HOLD#U",
            "rimuru.util.Util#REDUCE#U",
            "rimuru.util.Util#SLOWED#U",
            "rimuru.util.Util#HASTED#U",
            "rimuru.util.Util#HEAL_CUT#U",
            "rimuru.util.Util#MELEE_BONUS#U",
            "rimuru.util.Util#KB_IMMUNE#U",
            "rimuru.util.Util#HIDDEN#U",
            "rimuru.util.Util#SPELLPOW#U",
            "rimuru.util.Util#OUTDMG#U",
            "rimuru.util.Util#ATK_SLOW#U",
            "rimuru.util.Util#BARRIER#U",
            "rimuru.util.Util#CC_IMMUNE#U",
            "shadow.util.Util#LOCKED#U",
            "shadow.util.Util#UNTOUCHABLE#U",
            "shadow.util.Util#FALL_IMMUNE#U",
            "shadow.util.Util#HOLD#U",
            "shadow.util.Util#REDUCE#U",
            "shadow.util.Util#SLOWED#U",
            "shadow.util.Util#HASTED#U",
            "shadow.util.Util#HEAL_CUT#U",
            "shadow.util.Util#MELEE_BONUS#U",
            "shadow.util.Util#KB_IMMUNE#U",
            "shadow.util.Util#HIDDEN#U",
            "shadow.util.Util#SPELLPOW#U",
            "shadow.util.Util#OUTDMG#U",
            "shadow.util.Util#ATK_SLOW#U",
            "shadow.util.Util#BARRIER#U",
            "shadow.util.Util#CC_IMMUNE#U",
            "shadow.util.Util#VULN#U",
            "shadow.util.Util#DOT#U",
            "shadow.util.Util#NOTP#U",
            "shadow.util.Util#FEAR#U",
            "shadow.util.Util#PROB#U",
            "shadow.util.Util#ASC#U",
            "shadow.util.Util#CATCH#U",
            "shadow.util.Util#INCOMING#U",
            "spells.ApprehendManager#ACTIVE#C",
            "spells.AzureBarrage#TASKS#C",
            "spells.BladeManager#ACTIVE#C",
            "spells.CrimsonRayManager#ACTIVE#C",
            "spells.CrimsonWrathManager#ACTIVE#C",
            "spells.DariusMarkManager#ACTIVE#C",
            "spells.DarkinBladeManager#ACTIVE#C",
            "spells.Debuffs#ACTIVE#U",
            "spells.DecimateManager#ACTIVE#C",
            "spells.DioHold#HELD#U",
            "spells.DioManager#ACTIVE#C",
            "spells.DominationManager#LOCKS#U",
            "spells.DominationManager#ACTIVE#C",
            "spells.FugaManager#ACTIVE#C",
            "spells.GarenManager#TASKS#C",
            "spells.GarenManager#SPINS#U",
            "spells.GroundSmashManager#ACTIVE#C",
            "spells.GuillotineManager#LOCKS#U",
            "spells.GuillotineManager#WINDUPS#C",
            "spells.GuillotineManager#STRIKES#C",
            "spells.HarrowedPathManager#ZONES#C",
            "spells.HeartbreakerManager#LOCKS#U",
            "spells.HeartbreakerManager#WINDUPS#C",
            "spells.HeartbreakerManager#STRIKES#C",
            "spells.HollowPurpleManager#ACTIVE#C",
            "spells.InfernalChainsManager#ACTIVE#C",
            "spells.JotaroManager#ACTIVE#C",
            "spells.JotaroManager#GUARDS#U",
            "spells.LaevatainFX#BURNING#U",
            "spells.LaevatainManager#ACTIVE#C",
            "spells.LaevatainManager#INVULNERABLE#U",
            "spells.MoonRainbowManager#ACTIVE#C",
            "spells.MoonWhirlManager#ACTIVE#C",
            "spells.NineRopesManager#ACTIVE#C",
            "spells.OblivionsDescentManager#SPOTS#U",
            "spells.OblivionsDescentManager#WINDUPS#C",
            "spells.OblivionsDescentManager#PLUNGES#C",
            "spells.OblivionsDescentManager#EMBERS#C",
            "spells.ReversalRedBlast#ACTIVE#C",
            "spells.ShrineManager#ACTIVE#C",
            "spells.SimpleDomainManager#ACTIVE#C",
            "spells.SpectralMawManager#CHARGES#C",
            "spells.SpectralMawManager#DASHES#C",
            "spells.SpectralMawManager#WRAPS#C",
            "spells.SpectralMawManager#BOLTS#C",
            "spells.SunBreathingManager#INVULN#U",
            "spells.SunBreathingManager#ACTIVE#C",
            "spells.TengaiShinseiManager#ACTIVE#C",
            "spells.TimeStopManager#ACTIVE#C",
            "spells.UmbralDashManager#ACTIVE#C",
            "spells.UnlimitedVoidManager#ACTIVE#C",
            "spells.WorldCuttingSlashManager#ACTIVE#C",
            "tempest.entity.LawEntity#ACTIVE#U",
            "tempest.util.Util#LOCKED#U",
            "tempest.util.Util#UNTOUCHABLE#U",
            "tempest.util.Util#FALL_IMMUNE#U",
            "tempest.util.Util#HOLD#U",
            "tempest.util.Util#REDUCE#U",
            "tempest.util.Util#SLOWED#U",
            "tempest.util.Util#HASTED#U",
            "tempest.util.Util#HEAL_CUT#U",
            "tempest.util.Util#MELEE_BONUS#U",
            "tempest.util.Util#KB_IMMUNE#U",
            "tempest.util.Util#HIDDEN#U",
            "tempest.util.Util#SPELLPOW#U",
            "tempest.util.Util#OUTDMG#U",
            "tempest.util.Util#ATK_SLOW#U",
            "tempest.util.Util#BARRIER#U",
            "tempest.util.Util#CC_IMMUNE#U",
            "tempest.util.Util#VULN#U",
            "tempest.util.Util#DOT#U",
            "tempest.util.Util#NOTP#U",
            "tempest.util.Util#FEAR#U",
            "veldora.util.Util#LOCKED#U",
            "veldora.util.Util#UNTOUCHABLE#U",
            "veldora.util.Util#FALL_IMMUNE#U",
            "veldora.util.Util#HOLD#U",
            "veldora.util.Util#REDUCE#U",
            "veldora.util.Util#SLOWED#U",
            "veldora.util.Util#HASTED#U",
            "veldora.util.Util#HEAL_CUT#U",
            "veldora.util.Util#MELEE_BONUS#U",
            "veldora.util.Util#KB_IMMUNE#U",
            "veldora.util.Util#HIDDEN#U",
            "veldora.util.Util#SPELLPOW#U",
            "veldora.util.Util#OUTDMG#U",
            "veldora.util.Util#ATK_SLOW#U",
            "veldora.util.Util#BARRIER#U",
            "veldora.util.Util#CC_IMMUNE#U",
            "veldora.util.Util#VULN#U",
            "veldora.util.Util#DOT#U",
            "veldora.util.Util#NOTP#U",
            "veldora.util.Util#FEAR#U",
            "veldora.util.Util#PROB#U",
            "veldora.util.Util#ASC#U",
            "velgrynd.util.Util#LOCKED#U",
            "velgrynd.util.Util#UNTOUCHABLE#U",
            "velgrynd.util.Util#FALL_IMMUNE#U",
            "velgrynd.util.Util#HOLD#U",
            "velgrynd.util.Util#REDUCE#U",
            "velgrynd.util.Util#SLOWED#U",
            "velgrynd.util.Util#HASTED#U",
            "velgrynd.util.Util#HEAL_CUT#U",
            "velgrynd.util.Util#MELEE_BONUS#U",
            "velgrynd.util.Util#KB_IMMUNE#U",
            "velgrynd.util.Util#HIDDEN#U",
            "velgrynd.util.Util#SPELLPOW#U",
            "velgrynd.util.Util#OUTDMG#U",
            "velgrynd.util.Util#ATK_SLOW#U",
            "velgrynd.util.Util#BARRIER#U",
            "velgrynd.util.Util#CC_IMMUNE#U",
            "velgrynd.util.Util#VULN#U",
            "velgrynd.util.Util#DOT#U",
            "velgrynd.util.Util#NOTP#U",
            "velgrynd.util.Util#FEAR#U",
            "velgrynd.util.Util#PROB#U",
            "velgrynd.util.Util#ASC#U",
            "zegion.entity.ObsidianEntity#ACTIVE#U",
            "zegion.util.Util#LOCKED#U",
            "zegion.util.Util#UNTOUCHABLE#U",
            "zegion.util.Util#HIDDEN#U",
            "zegion.util.Util#SLOW_IMMUNE#U",
            "zegion.util.Util#FALL_IMMUNE#U",
            "zegion.util.Util#HOLD#U",
            "zegion.util.Util#STUN#U",
            "zegion.util.Util#KB_IMMUNE#U",
            "zegion.util.Util#CC_IMMUNE#U",
            "zegion.util.Util#FRACTURE#U",
            "zegion.util.Util#REDUCE#U",
            "zegion.util.Util#SLOWED#U",
            "zegion.util.Util#HASTED#U",
            "zegion.util.Util#HEAL_CUT#U",
            "zegion.util.Util#MELEE_BONUS#U",
            "zegion.util.Util#MELEE_FLAT#U",
            "zegion.util.Util#OUT_BONUS#U",
            "zegion.util.Util#OUT_DOWN#U",
            "zegion.util.Util#VULN#U",
            "zegion.util.Util#ATK_SLOW#U",
            "zegion.util.Util#PROJ_REDUCE#U",
            "zegion.util.Util#CARAPACE#U",
            "zegion.util.Util#FORM#U",
            "zegion.util.Util#KILLS#U",
    };

    private static final List<Object[]> RESOLVED = new ArrayList<>();   // {Object collection, Boolean uuidKeyed, String label}
    private static final Map<Object, Set<UUID>> SUSPECTS = new IdentityHashMap<>();
    private static boolean resolved;
    private static int ticks;

    private LeakGuard() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        for (String row : FIELDS) {
            String[] p = row.split("#");
            try {
                Class<?> c = Class.forName(ROOT + p[0], false, LeakGuard.class.getClassLoader());
                Field f = c.getDeclaredField(p[1]);
                f.setAccessible(true);
                Object o = f.get(null);
                if (o instanceof Collection || o instanceof Map) {
                    RESOLVED.add(new Object[]{o, "U".equals(p[2]), p[0] + "." + p[1]});
                }
            } catch (Throwable t) {
                LOG.debug("LeakGuard: skipped {} ({})", row, t.toString());
            }
        }
    }

    private static boolean alive(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(id) != null) {
                return true;
            }
        }
        return server.getPlayerList().getPlayer(id) != null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks < SWEEP_TICKS) {
            return;
        }
        ticks = 0;
        resolve();
        MinecraftServer server = e.getServer();
        int dropped = 0;
        for (Object[] r : RESOLVED) {
            if (!(Boolean) r[1]) {
                continue;
            }
            try {
                Object coll = r[0];
                Set<UUID> old = SUSPECTS.getOrDefault(coll, Collections.emptySet());
                Set<UUID> now = new HashSet<>();
                Iterable<?> keys = coll instanceof Map<?, ?> m ? new ArrayList<>(m.keySet()) : new ArrayList<>((Collection<?>) coll);
                for (Object k : keys) {
                    if (!(k instanceof UUID id) || alive(server, id)) {
                        continue;
                    }
                    if (old.contains(id)) {
                        if (coll instanceof Map<?, ?> m) {
                            m.remove(id);
                        } else {
                            ((Collection<?>) coll).remove(id);
                        }
                        dropped++;
                    } else {
                        now.add(id);
                    }
                }
                SUSPECTS.put(coll, now);
            } catch (Throwable t) {
                LOG.debug("LeakGuard: sweep of {} failed: {}", r[2], t.toString());
            }
        }
        if (dropped > 0) {
            LOG.debug("LeakGuard: dropped {} stale entries", dropped);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        resolve();
        for (Object[] r : RESOLVED) {
            try {
                if (r[0] instanceof Map<?, ?> m) {
                    m.clear();
                } else if (r[0] instanceof Collection<?> c) {
                    c.clear();
                }
            } catch (Throwable t) {
                LOG.debug("LeakGuard: could not clear {}: {}", r[2], t.toString());
            }
        }
        SUSPECTS.clear();
        ticks = 0;
    }
}
