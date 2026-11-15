package com.example.jjkspells;

import com.example.jjkspells.spells.DecimateSpell;
import com.example.jjkspells.spells.CripplingStrikeSpell;
import com.example.jjkspells.spells.ApprehendSpell;
import com.example.jjkspells.spells.NoxianGuillotineSpell;
import com.example.jjkspells.spells.DecisiveStrikeSpell;
import com.example.jjkspells.spells.JudgmentSpell;
import com.example.jjkspells.spells.CourageSpell;
import com.example.jjkspells.spells.DemacianJusticeSpell;
import com.example.jjkspells.spells.CrimsonRaySpell;
import com.example.jjkspells.spells.SunDanceSpell;
import com.example.jjkspells.spells.SunRadianceSpell;
import com.example.jjkspells.spells.SunSettingSpell;
import com.example.jjkspells.spells.SunEternalSpell;
import com.example.jjkspells.spells.DeathWraithsSpell;
import com.example.jjkspells.spells.CrimsonWrathSpell;
import com.example.jjkspells.spells.FugaSpell;
import com.example.jjkspells.spells.GaleBarrageSpell;
import com.example.jjkspells.spells.HollowPurpleSpell;
import com.example.jjkspells.spells.LapseBlueSpell;
import com.example.jjkspells.spells.ReversalRedSpell;
import com.example.jjkspells.spells.SimpleDomainSpell;
import com.example.jjkspells.spells.StormLordsWrathSpell;
import com.example.jjkspells.spells.TimeStopSpell;
import com.example.jjkspells.spells.UnlimitedVoidSpell;
import com.example.jjkspells.spells.WorldCuttingSlashSpell;
import com.example.jjkspells.spells.MalevolentShrineSpell;
import com.example.jjkspells.spells.NineRopesSpell;
import com.example.jjkspells.spells.GroundSmashSpell;
import com.example.jjkspells.spells.HardStrikeSpell;
import com.example.jjkspells.spells.MoonCrescentSlashSpell;
import com.example.jjkspells.spells.MoonRainbowSpell;
import com.example.jjkspells.spells.MoonWhirlSpell;
import com.example.jjkspells.spells.PunchBarrageSpell;
import com.example.jjkspells.spells.TengaiShinseiSpell;
import com.example.jjkspells.spells.BladeOfTheRuinedKingSpell;
import com.example.jjkspells.spells.SpectralMawSpell;
import com.example.jjkspells.spells.HarrowedPathSpell;
import com.example.jjkspells.spells.SovereignsDominationSpell;
import com.example.jjkspells.spells.HeartbreakerSpell;
import com.example.jjkspells.spells.DioMudaSpell;
import com.example.jjkspells.spells.DarkinBladeSpell;
import com.example.jjkspells.spells.InfernalChainsSpell;
import com.example.jjkspells.spells.UmbralDashSpell;
import com.example.jjkspells.spells.WorldEnderSpell;
import com.example.jjkspells.spells.OblivionsDescentSpell;
import com.example.jjkspells.spells.JotaroOraSpell;
import com.example.jjkspells.spells.JotaroStarFingerSpell;
import com.example.jjkspells.spells.JotaroGuardSpell;
import com.example.jjkspells.spells.JotaroOnslaughtSpell;
import com.example.jjkspells.spells.DioBeamSpell;
import com.example.jjkspells.spells.DioImpactSpell;
import com.example.jjkspells.spells.DioRollerSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import com.example.jjkspells.spells.AzureColossusSpell;
import com.example.jjkspells.spells.AzureOrbBarrageSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModSpells {
    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, JJKSpells.MODID);

    public static final RegistryObject<AbstractSpell> MALEVOLENT_SHRINE =
            registerSpell(new MalevolentShrineSpell());

    public static final RegistryObject<AbstractSpell> FUGA =
            registerSpell(new FugaSpell());

    public static final RegistryObject<AbstractSpell> HOLLOW_PURPLE =
            registerSpell(new HollowPurpleSpell());

    public static final RegistryObject<AbstractSpell> LAPSE_BLUE =
            SPELLS.register("lapse_blue", LapseBlueSpell::new);

    public static final RegistryObject<AbstractSpell> REVERSAL_RED =
            SPELLS.register("reversal_red", ReversalRedSpell::new);

    public static final RegistryObject<AbstractSpell> UNLIMITED_VOID =
            SPELLS.register("unlimited_void", UnlimitedVoidSpell::new);

    // ---- PRIME Gojo ----
    public static final RegistryObject<AbstractSpell> VOID_BLUE =
            SPELLS.register("void_blue", com.example.jjkspells.prime.PrimeSpells.VoidBlue::new);
    public static final RegistryObject<AbstractSpell> HEAVENLY_RED =
            SPELLS.register("heavenly_red", com.example.jjkspells.prime.PrimeSpells.HeavenlyRed::new);
    public static final RegistryObject<AbstractSpell> DIVINE_PURPLE =
            SPELLS.register("divine_purple", com.example.jjkspells.prime.PrimeSpells.DivinePurple::new);
    public static final RegistryObject<AbstractSpell> HEAVENLY_ONSLAUGHT =
            SPELLS.register("heavenly_onslaught", com.example.jjkspells.prime.PrimeSpells.HeavenlyOnslaught::new);
    public static final RegistryObject<AbstractSpell> BOUNDLESS_SLASH =
            SPELLS.register("boundless_slash", com.example.jjkspells.prime.PrimeSpells.BoundlessSlash::new);
    public static final RegistryObject<AbstractSpell> WORLD_DISINTEGRATION =
            SPELLS.register("world_disintegration", com.example.jjkspells.prime.PrimeSpells.WorldDisintegration::new);
    public static final RegistryObject<AbstractSpell> UNIVERSE_SEVERING_SLASH =
            SPELLS.register("universe_severing_slash", com.example.jjkspells.prime.PrimeSpells.UniverseSeveringSlash::new);

    public static final RegistryObject<AbstractSpell> SIMPLE_DOMAIN =
            SPELLS.register("simple_domain", SimpleDomainSpell::new);

    public static final RegistryObject<AbstractSpell> NINE_ROPES =
            SPELLS.register("hollow_purple_nine_ropes", NineRopesSpell::new);

    public static final RegistryObject<AbstractSpell> WORLD_CUTTING_SLASH =
            SPELLS.register("world_cutting_slash", WorldCuttingSlashSpell::new);

    public static final RegistryObject<AbstractSpell> CRIMSON_WRATH =
            SPELLS.register("crimson_wrath", CrimsonWrathSpell::new);

    public static final RegistryObject<AbstractSpell> CRIMSON_RAY =
            SPELLS.register("crimson_ray", CrimsonRaySpell::new);

    public static final RegistryObject<AbstractSpell> DEATH_WRAITHS =
            SPELLS.register("death_wraiths", DeathWraithsSpell::new);

    public static final RegistryObject<AbstractSpell> GATE_OF_BABYLON =
            SPELLS.register("gate_of_babylon", com.example.jjkspells.gil.GilSpells.Gate::new);

    public static final RegistryObject<AbstractSpell> ENKIDU_CHAINS_OF_HEAVEN =
            SPELLS.register("enkidu_chains_of_heaven", com.example.jjkspells.gil.GilSpells.Enkidu::new);

    public static final RegistryObject<AbstractSpell> VIMANA =
            SPELLS.register("vimana", com.example.jjkspells.gil.GilSpells.Vimana::new);

    public static final RegistryObject<AbstractSpell> MERODACH =
            SPELLS.register("merodach", com.example.jjkspells.gil.GilSpells.Merodach::new);

    public static final RegistryObject<AbstractSpell> ENUMA_ELISH =
            SPELLS.register("enuma_elish", com.example.jjkspells.gil.GilSpells.Ea::new);

    public static final RegistryObject<AbstractSpell> STORM_LORDS_WRATH =
            SPELLS.register("storm_lords_wrath", StormLordsWrathSpell::new);

    public static final RegistryObject<AbstractSpell> GALE_BARRAGE =
            SPELLS.register("gale_barrage", GaleBarrageSpell::new);

    public static final RegistryObject<AbstractSpell> TIME_STOP =
            SPELLS.register("time_stop", TimeStopSpell::new);

    public static final RegistryObject<AbstractSpell> AZURE_COLOSSUS =
            SPELLS.register("azure_colossus", AzureColossusSpell::new);

    public static final RegistryObject<AbstractSpell> AZURE_ORB_BARRAGE =
            SPELLS.register("azure_orb_barrage", AzureOrbBarrageSpell::new);

    // ---- Martial arts ----
    public static final RegistryObject<AbstractSpell> PUNCH_BARRAGE =
            SPELLS.register("punch_barrage", PunchBarrageSpell::new);

    public static final RegistryObject<AbstractSpell> GROUND_SMASH =
            SPELLS.register("ground_smash", GroundSmashSpell::new);

    public static final RegistryObject<AbstractSpell> HARD_STRIKE =
            SPELLS.register("hard_strike", HardStrikeSpell::new);

    // ---- Moon Breathing ----
    public static final RegistryObject<AbstractSpell> MOON_CRESCENT_SLASH =
            SPELLS.register("moon_crescent_slash", MoonCrescentSlashSpell::new);

    public static final RegistryObject<AbstractSpell> MOON_WHIRL =
            SPELLS.register("moon_whirl", MoonWhirlSpell::new);

    public static final RegistryObject<AbstractSpell> MOON_RAINBOW =
            SPELLS.register("moon_rainbow", MoonRainbowSpell::new);

    public static final RegistryObject<AbstractSpell> TENGAI_SHINSEI =
            SPELLS.register("tengai_shinsei", TengaiShinseiSpell::new);

    public static final RegistryObject<AbstractSpell> SUN_DANCE =
            SPELLS.register("sun_dance", SunDanceSpell::new);

    public static final RegistryObject<AbstractSpell> SUN_RADIANCE =
            SPELLS.register("sun_radiance", SunRadianceSpell::new);

    public static final RegistryObject<AbstractSpell> SUN_SETTING =
            SPELLS.register("sun_setting", SunSettingSpell::new);

    public static final RegistryObject<AbstractSpell> SUN_ETERNAL =
            SPELLS.register("sun_eternal", SunEternalSpell::new);

    public static final RegistryObject<AbstractSpell> DECIMATE =
            SPELLS.register("decimate", DecimateSpell::new);

    public static final RegistryObject<AbstractSpell> CRIPPLING_STRIKE =
            SPELLS.register("crippling_strike", CripplingStrikeSpell::new);

    public static final RegistryObject<AbstractSpell> APPREHEND =
            SPELLS.register("apprehend", ApprehendSpell::new);

    public static final RegistryObject<AbstractSpell> NOXIAN_GUILLOTINE =
            SPELLS.register("noxian_guillotine", NoxianGuillotineSpell::new);

    public static final RegistryObject<AbstractSpell> DECISIVE_STRIKE =
            SPELLS.register("decisive_strike", DecisiveStrikeSpell::new);

    public static final RegistryObject<AbstractSpell> JUDGMENT =
            SPELLS.register("judgment", JudgmentSpell::new);

    public static final RegistryObject<AbstractSpell> COURAGE =
            SPELLS.register("courage", CourageSpell::new);

    public static final RegistryObject<AbstractSpell> DEMACIAN_JUSTICE =
            SPELLS.register("demacian_justice", DemacianJusticeSpell::new);

    // ---- Viego ----
    public static final RegistryObject<AbstractSpell> BLADE_OF_THE_RUINED_KING =
            SPELLS.register("blade_of_the_ruined_king", BladeOfTheRuinedKingSpell::new);

    public static final RegistryObject<AbstractSpell> SPECTRAL_MAW =
            SPELLS.register("spectral_maw", SpectralMawSpell::new);

    public static final RegistryObject<AbstractSpell> HARROWED_PATH =
            SPELLS.register("harrowed_path", HarrowedPathSpell::new);

    public static final RegistryObject<AbstractSpell> SOVEREIGNS_DOMINATION =
            SPELLS.register("sovereigns_domination", SovereignsDominationSpell::new);

    public static final RegistryObject<AbstractSpell> HEARTBREAKER =
            SPELLS.register("heartbreaker", HeartbreakerSpell::new);

    // ---- DIO ----
    public static final RegistryObject<AbstractSpell> DIO_MUDA =
            SPELLS.register("dio_muda", DioMudaSpell::new);

    public static final RegistryObject<AbstractSpell> DIO_BEAM =
            SPELLS.register("dio_beam", DioBeamSpell::new);

    public static final RegistryObject<AbstractSpell> DIO_IMPACT =
            SPELLS.register("dio_impact", DioImpactSpell::new);

    public static final RegistryObject<AbstractSpell> DIO_ROLLER =
            SPELLS.register("dio_roller", DioRollerSpell::new);

    // ---- Laevatain ----
    public static final RegistryObject<AbstractSpell> SMOULDERING_FIRE =
            SPELLS.register("smouldering_fire", com.example.jjkspells.spells.SmoulderingFireSpell::new);

    public static final RegistryObject<AbstractSpell> SEETHE =
            SPELLS.register("seethe", com.example.jjkspells.spells.SeetheSpell::new);

    public static final RegistryObject<AbstractSpell> SVIGA_LAEVI =
            SPELLS.register("sviga_laevi", com.example.jjkspells.spells.SvigaLaeviSpell::new);

    public static final RegistryObject<AbstractSpell> TWILIGHT_WORLD_IN_FLAMES =
            SPELLS.register("twilight_world_in_flames", com.example.jjkspells.spells.TwilightWorldInFlamesSpell::new);

    // ---- Aatrox ----
    public static final RegistryObject<AbstractSpell> DARKIN_BLADE =
            SPELLS.register("darkin_blade", DarkinBladeSpell::new);

    public static final RegistryObject<AbstractSpell> INFERNAL_CHAINS =
            SPELLS.register("infernal_chains", InfernalChainsSpell::new);

    public static final RegistryObject<AbstractSpell> UMBRAL_DASH =
            SPELLS.register("umbral_dash", UmbralDashSpell::new);

    public static final RegistryObject<AbstractSpell> WORLD_ENDER =
            SPELLS.register("world_ender", WorldEnderSpell::new);

    public static final RegistryObject<AbstractSpell> OBLIVIONS_DESCENT =
            SPELLS.register("oblivions_descent", OblivionsDescentSpell::new);

    // ---- Jotaro ----
    public static final RegistryObject<AbstractSpell> JOTARO_ORA =
            SPELLS.register("jotaro_ora", JotaroOraSpell::new);

    public static final RegistryObject<AbstractSpell> JOTARO_STAR_FINGER =
            SPELLS.register("jotaro_star_finger", JotaroStarFingerSpell::new);

    public static final RegistryObject<AbstractSpell> JOTARO_GUARD =
            SPELLS.register("jotaro_guard", JotaroGuardSpell::new);

    public static final RegistryObject<AbstractSpell> JOTARO_ONSLAUGHT =
            SPELLS.register("jotaro_onslaught", JotaroOnslaughtSpell::new);

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }
}
