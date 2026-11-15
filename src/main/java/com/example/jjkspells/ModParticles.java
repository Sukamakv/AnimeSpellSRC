package com.example.jjkspells;

import com.example.jjkspells.particle.MoonRibbonType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    // ---- Malevolent Shrine ----
    /** Big, slow, layered black ink smoke used for the domain's dark zone. */
    public static final RegistryObject<SimpleParticleType> INK_SMOKE = simple("ink_smoke");
    /** Ordinary upright billowing smoke (use this for every smoke that is not the Shrine floor). */
    public static final RegistryObject<SimpleParticleType> BILLOW_SMOKE = simple("billow_smoke");
    /** A thin glowing line stretched between two points (one slash). */
    public static final RegistryObject<SimpleParticleType> SLASH_LINE = simple("slash_line");

    // ---- Fuga ----
    public static final RegistryObject<SimpleParticleType> FUGA_FLAME = simple("fuga_flame");        // tongue of fire
    public static final RegistryObject<SimpleParticleType> FUGA_BLAST = simple("fuga_blast");        // huge billowing fireball
    public static final RegistryObject<SimpleParticleType> FUGA_EMBER = simple("fuga_ember");        // tiny falling spark
    public static final RegistryObject<SimpleParticleType> FUGA_GLOW = simple("fuga_glow");          // white flash
    public static final RegistryObject<SimpleParticleType> FUGA_RING_FLAT = simple("fuga_ring_flat"); // shock ring on the ground
    public static final RegistryObject<SimpleParticleType> FUGA_RING = simple("fuga_ring");          // shock ring facing the camera
    public static final RegistryObject<SimpleParticleType> FUGA_SMOKE = simple("fuga_smoke");        // soot smoke
    public static final RegistryObject<SimpleParticleType> FUGA_LINE = simple("fuga_line");          // thin glowing line
    public static final RegistryObject<SimpleParticleType> FUGA_BEAM = simple("fuga_beam");          // thick glowing line

    // ---- Hollow Purple ----
    public static final RegistryObject<SimpleParticleType> HP_CORE = simple("hp_core");
    public static final RegistryObject<SimpleParticleType> HP_GLOW = simple("hp_glow");
    public static final RegistryObject<SimpleParticleType> HP_GLOW_BLUE = simple("hp_glow_blue");
    public static final RegistryObject<SimpleParticleType> HP_GLOW_RED = simple("hp_glow_red");
    public static final RegistryObject<SimpleParticleType> HP_WISP = simple("hp_wisp");
    public static final RegistryObject<SimpleParticleType> HP_WISP_BLUE = simple("hp_wisp_blue");
    public static final RegistryObject<SimpleParticleType> HP_WISP_RED = simple("hp_wisp_red");
    public static final RegistryObject<SimpleParticleType> HP_RING = simple("hp_ring");
    public static final RegistryObject<SimpleParticleType> HP_RING_FLAT = simple("hp_ring_flat");
    public static final RegistryObject<SimpleParticleType> HP_VOID = simple("hp_void");
    public static final RegistryObject<SimpleParticleType> HP_LINE = simple("hp_line");

    // ---- Unlimited Void ----
    public static final RegistryObject<SimpleParticleType> UV_STAR = simple("uv_star");
    public static final RegistryObject<SimpleParticleType> UV_STAR_IN = simple("uv_star_in");
    public static final RegistryObject<SimpleParticleType> UV_FLOOR = simple("uv_floor");
    public static final RegistryObject<SimpleParticleType> UV_GLOW = simple("uv_glow");
    public static final RegistryObject<SimpleParticleType> UV_CORE = simple("uv_core");
    public static final RegistryObject<SimpleParticleType> UV_RING = simple("uv_ring");
    public static final RegistryObject<SimpleParticleType> UV_RING_FLAT = simple("uv_ring_flat");
    public static final RegistryObject<SimpleParticleType> UV_LINE = simple("uv_line");
    public static final RegistryObject<SimpleParticleType> SD_STAR = simple("sd_star");
    public static final RegistryObject<SimpleParticleType> SD_RING_FLAT = simple("sd_ring_flat");

    // ---- World Cutting Slash ----
    public static final RegistryObject<SimpleParticleType> WC_INK = simple("wc_ink");
    public static final RegistryObject<SimpleParticleType> WC_GLOW = simple("wc_glow");
    public static final RegistryObject<SimpleParticleType> WC_RING = simple("wc_ring");
    public static final RegistryObject<SimpleParticleType> WC_RING_FLAT = simple("wc_ring_flat");

    // ---- Lapse: Blue / Reversal: Red ----
    /** Inward-spiralling particle. Parameters (xd,yd,zd) = vector from the particle to the vortex centre. */
    public static final RegistryObject<SimpleParticleType> BLUE_VORTEX = PARTICLES.register("blue_vortex", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> BLUE_SPARK = PARTICLES.register("blue_spark", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> RED_SPARK = PARTICLES.register("red_spark", () -> new SimpleParticleType(false));
    /** Expanding shockwave ring. xd = final radius, yd = 0 flat / 1 facing the camera. */
    public static final RegistryObject<SimpleParticleType> RED_RING = PARTICLES.register("red_ring", () -> new SimpleParticleType(false));
    /** Whole impact effect (rings + flash + sparks). xd = explosion radius. */
    public static final RegistryObject<SimpleParticleType> RED_IMPACT = PARTICLES.register("red_impact", () -> new SimpleParticleType(false));

    // ---- The Ruler of Death's Crimson Wrath ----
    /** A huge menacing eye that opens in the sky. xd = width, yd = lifetime in ticks. */
    public static final RegistryObject<SimpleParticleType> CW_EYE = simple("cw_eye");
    /** Cross-shaped flash. xd = radius. */
    public static final RegistryObject<SimpleParticleType> CW_GLINT = simple("cw_glint");
    /** The falling red laser: a line from the position along (xd, yd, zd). */
    public static final RegistryObject<SimpleParticleType> CW_LASER = simple("cw_laser");
    /** Outer (runes) and inner (star + eye) layers of the ground circle. xd = radius, yd = lifetime, zd = spin per tick. */
    public static final RegistryObject<SimpleParticleType> CW_CIRCLE_OUTER = simple("cw_circle_outer");
    public static final RegistryObject<SimpleParticleType> CW_CIRCLE_INNER = simple("cw_circle_inner");
    /** Invisible: tells nearby clients to turn the sky and fog crimson. yd = lifetime in ticks. */
    public static final RegistryObject<SimpleParticleType> CW_SKY = simple("cw_sky");

    // ---- Azure Colossus / Azure Orb Barrage (blue chakra fire) ----
    public static final RegistryObject<SimpleParticleType> AZURE_FLAME = simple("azure_flame");
    public static final RegistryObject<SimpleParticleType> AZURE_BLAST = simple("azure_blast");
    public static final RegistryObject<SimpleParticleType> AZURE_AURA = simple("azure_aura");
    public static final RegistryObject<SimpleParticleType> AZURE_EMBER = simple("azure_ember");
    /** xd = radius */
    public static final RegistryObject<SimpleParticleType> AZURE_GLOW = simple("azure_glow");
    /** xd = final radius */
    public static final RegistryObject<SimpleParticleType> AZURE_RING_FLAT = simple("azure_ring_flat");
    public static final RegistryObject<SimpleParticleType> AZURE_RING = simple("azure_ring");

    // ---- Darius (crimson) ----
    /** Crimson ring of force lying on the ground. xd = final radius. */
    public static final RegistryObject<SimpleParticleType> DR_RING_FLAT = simple("dr_ring_flat");
    /** Crimson ring of force facing the camera. xd = final radius. */
    public static final RegistryObject<SimpleParticleType> DR_SHOCK = simple("dr_shock");
    /** Red flash. xd = radius. */
    public static final RegistryObject<SimpleParticleType> DR_GLOW = simple("dr_glow");
    /** Red starburst at a hit. xd = radius. */
    public static final RegistryObject<SimpleParticleType> DR_HIT = simple("dr_hit");
    /** Hot red spark that arcs and falls. */
    public static final RegistryObject<SimpleParticleType> DR_SPARK = simple("dr_spark");
    /** Red energy mote that drifts straight (used for energy being pulled into the axe). */
    public static final RegistryObject<SimpleParticleType> DR_GATHER = simple("dr_gather");
    /** Steel-coloured spark for metallic impacts. */
    public static final RegistryObject<SimpleParticleType> DR_METAL = simple("dr_metal");
    /** The crimson mark over a target's head. */
    public static final RegistryObject<SimpleParticleType> DR_MARK = simple("dr_mark");
    /** Reddish dust puff. */
    public static final RegistryObject<SimpleParticleType> DR_DUST = simple("dr_dust");
    /** Thin red line from the position along (xd, yd, zd). */
    public static final RegistryObject<SimpleParticleType> DR_LINE = simple("dr_line");
    /** Heavier red-iron line, used for Apprehend's chain. */
    public static final RegistryObject<SimpleParticleType> DR_CHAIN = simple("dr_chain");

    // ---- Jotaro / Star Platinum (SP_*): purple, deep blue, cyan, white ----
    public static final RegistryObject<SimpleParticleType> SP_GLOW = simple("sp_glow");
    public static final RegistryObject<SimpleParticleType> SP_HIT = simple("sp_hit");
    public static final RegistryObject<SimpleParticleType> SP_SPARK = simple("sp_spark");
    public static final RegistryObject<SimpleParticleType> SP_RING = simple("sp_ring");
    public static final RegistryObject<SimpleParticleType> SP_RING_FLAT = simple("sp_ring_flat");
    public static final RegistryObject<SimpleParticleType> SP_FIST = simple("sp_fist");
    public static final RegistryObject<SimpleParticleType> SP_STAR = simple("sp_star");
    public static final RegistryObject<SimpleParticleType> SP_LINE = simple("sp_line");
    public static final RegistryObject<SimpleParticleType> SP_BEAM = simple("sp_beam");

    // ---- Aatrox (crimson, black shadow, blood) ----
    public static final RegistryObject<SimpleParticleType> AT_BLOOD = simple("at_blood");
    public static final RegistryObject<SimpleParticleType> AT_EMBER = simple("at_ember");
    public static final RegistryObject<SimpleParticleType> AT_SMOKE = simple("at_smoke");
    /** xd = radius */
    public static final RegistryObject<SimpleParticleType> AT_GHOST = simple("at_ghost");
    /** xd = radius; lives 4 ticks, re-spawn to keep it up */
    public static final RegistryObject<SimpleParticleType> AT_CIRCLE = simple("at_circle");

    // ---- Garen spells: steel (GR_*) and gold (GOLD_*) ----
    public static final RegistryObject<SimpleParticleType> GR_LINE = simple("gr_line");
    public static final RegistryObject<SimpleParticleType> GR_MOTE = simple("gr_mote");
    /** xd = radius */
    public static final RegistryObject<SimpleParticleType> GR_GLOW = simple("gr_glow");
    public static final RegistryObject<SimpleParticleType> GR_SPARK = simple("gr_spark");
    public static final RegistryObject<SimpleParticleType> GR_RING = simple("gr_ring");
    public static final RegistryObject<SimpleParticleType> GR_RING_FLAT = simple("gr_ring_flat");
    public static final RegistryObject<SimpleParticleType> GG_GLOW = simple("gg_glow");
    public static final RegistryObject<SimpleParticleType> GG_RING = simple("gg_ring");
    public static final RegistryObject<SimpleParticleType> GG_RING_FLAT = simple("gg_ring_flat");
    public static final RegistryObject<SimpleParticleType> GOLD_MOTE = simple("gold_mote");
    public static final RegistryObject<SimpleParticleType> GG_SHARD = simple("gg_shard");
    /** xd = radius: the shield emblem that flashes in front of the caster. */
    public static final RegistryObject<SimpleParticleType> GOLD_CREST = simple("gold_crest");
    public static final RegistryObject<SimpleParticleType> GOLD_BEAM = simple("gold_beam");

    private static RegistryObject<SimpleParticleType> simple(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }

    // ---- Za Warudo: Time Stop ----
    public static final RegistryObject<SimpleParticleType> TS_FACE = simple("ts_face");
    public static final RegistryObject<SimpleParticleType> TS_RUNES = simple("ts_runes");
    public static final RegistryObject<SimpleParticleType> TS_GEARS = simple("ts_gears");
    public static final RegistryObject<SimpleParticleType> TS_RAYS = simple("ts_rays");
    public static final RegistryObject<SimpleParticleType> TS_HAND_MIN = simple("ts_hand_min");
    public static final RegistryObject<SimpleParticleType> TS_HAND_HR = simple("ts_hand_hr");
    public static final RegistryObject<SimpleParticleType> TS_GLOW = simple("ts_glow");
    public static final RegistryObject<SimpleParticleType> TS_MOTE = simple("ts_mote");
    public static final RegistryObject<SimpleParticleType> TS_STAR = simple("ts_star");
    public static final RegistryObject<SimpleParticleType> TS_GEAR = simple("ts_gear");
    public static final RegistryObject<SimpleParticleType> TS_RING = simple("ts_ring");
    public static final RegistryObject<SimpleParticleType> TS_RING_FLAT = simple("ts_ring_flat");
    public static final RegistryObject<SimpleParticleType> TS_NUMERAL = simple("ts_numeral");

    // ---- Storm Lord's Wrath ----
    /** Short bright white-blue spark. xd,yd,zd = velocity. */
    public static final RegistryObject<SimpleParticleType> STORM_SPARK = simple("storm_spark");
    /** Big dark swirling cloud puff. xd,yd,zd = velocity. */
    public static final RegistryObject<SimpleParticleType> STORM_CLOUD = simple("storm_cloud");

    // ---- Gale Barrage ----
    /** Bright white-blue glint that flies with the given velocity. */
    public static final RegistryObject<SimpleParticleType> SLASH_SPARK = simple("slash_spark");
    /** Soft white wind puff that is blown along the given velocity. */
    public static final RegistryObject<SimpleParticleType> WIND_WISP = simple("wind_wisp");

    // ---- Martial arts (Barrage of Punches, Ground Smash, Hard Strike) ----
    /** Comic-style impact starburst. xd = radius. */
    public static final RegistryObject<SimpleParticleType> MA_IMPACT = simple("ma_impact");
    /** Short white-orange flash. xd = radius. */
    public static final RegistryObject<SimpleParticleType> MA_GLOW = simple("ma_glow");
    /** A clenched fist that flashes in and fades. xd = radius. */
    public static final RegistryObject<SimpleParticleType> MA_FIST = simple("ma_fist");
    /** Expanding shock ring facing the camera. xd = final radius. */
    public static final RegistryObject<SimpleParticleType> MA_SHOCK_RING = simple("ma_shock_ring");
    /** Expanding shock ring lying on the ground. xd = final radius. */
    public static final RegistryObject<SimpleParticleType> MA_SHOCK_RING_FLAT = simple("ma_shock_ring_flat");
    /** Cracks in the ground that fade out slowly. xd = radius. */
    public static final RegistryObject<SimpleParticleType> MA_CRACKS = simple("ma_cracks");
    /** Soft dust puff. */
    public static final RegistryObject<SimpleParticleType> MA_DUST = simple("ma_dust");
    /** Small yellow star (orbits the head of a stunned target). */
    public static final RegistryObject<SimpleParticleType> MA_STAR = simple("ma_star");
    /** Tiny hot spark that arcs and falls. */
    public static final RegistryObject<SimpleParticleType> MA_SPARK = simple("ma_spark");
    /** Thin speed streak from the position along (xd, yd, zd). */
    public static final RegistryObject<SimpleParticleType> MA_LINE = simple("ma_line");
    /** Thick speed streak from the position along (xd, yd, zd). */
    public static final RegistryObject<SimpleParticleType> MA_LINE_HEAVY = simple("ma_line_heavy");

    // ---- Moon Breathing (Crescent Slash, Whirl, Rainbow) ----
    /** A curved purple slash or a blue crescent, drawn as a tapering ribbon. Its shape travels in the particle options. */
    public static final RegistryObject<MoonRibbonType> MOON_RIBBON = PARTICLES.register("moon_ribbon", MoonRibbonType::new);
    /** Spiky star lying on the ground where a slash erupts. xd = radius. */
    public static final RegistryObject<SimpleParticleType> MOON_BURST = simple("moon_burst");
    /** Violet-white flash. xd = radius. */
    public static final RegistryObject<SimpleParticleType> MOON_FLASH = simple("moon_flash");
    /** Violet starburst where a slash cuts something. xd = radius. */
    public static final RegistryObject<SimpleParticleType> MOON_HIT = simple("moon_hit");
    /** Tiny violet-blue spark. */
    public static final RegistryObject<SimpleParticleType> MOON_SPARK = simple("moon_spark");

    // ---- Crimson Ray and Death's Wraiths ----
    /** The Crimson Ray: a 1.75x wide version of the Crimson Wrath laser; resent every tick. */
    public static final RegistryObject<SimpleParticleType> CR_BEAM = simple("cr_beam");
    /** Hot white-red core inside the Crimson Ray (additive). */
    public static final RegistryObject<SimpleParticleType> CR_CORE = simple("cr_core");
    /** Cold pale glow used around the wraiths. */
    public static final RegistryObject<SimpleParticleType> GHOST_GLOW = simple("ghost_glow");

    // ---- Gilgamesh ----
    public static final RegistryObject<SimpleParticleType> GATE_PORTAL = simple("gate_portal");
    public static final RegistryObject<SimpleParticleType> GATE_PORTAL_SMALL = simple("gate_portal_small");
    public static final RegistryObject<SimpleParticleType> GOLD_RING_FLAT = simple("gold_ring_flat");
    public static final RegistryObject<SimpleParticleType> GOLD_RING = simple("gold_ring");
    public static final RegistryObject<SimpleParticleType> GOLD_GLINT = simple("gold_glint");
    public static final RegistryObject<SimpleParticleType> GOLD_SPARK = simple("gold_spark");
    public static final RegistryObject<SimpleParticleType> GOLD_GLOW = simple("gold_glow");
    public static final RegistryObject<SimpleParticleType> GOLD_SHARD = simple("gold_shard");
    public static final RegistryObject<SimpleParticleType> GOLD_RUNE = simple("gold_rune");
    public static final RegistryObject<SimpleParticleType> GOLD_CHAIN = simple("gold_chain");
    public static final RegistryObject<SimpleParticleType> GOLD_LINE = simple("gold_line");
    public static final RegistryObject<SimpleParticleType> EA_CRACK = simple("ea_crack");
    public static final RegistryObject<SimpleParticleType> EA_GLOW = simple("ea_glow");

    // ---- Viego (spectral green flames and the Black Mist) ----
    /** Spectral green ring along the ground. xd = final radius. */
    public static final RegistryObject<SimpleParticleType> VG_RING_FLAT = simple("vg_ring_flat");
    /** Spectral green ring facing the camera. xd = final radius. */
    public static final RegistryObject<SimpleParticleType> VG_SHOCK = simple("vg_shock");
    /** Green flash. xd = radius. */
    public static final RegistryObject<SimpleParticleType> VG_GLOW = simple("vg_glow");
    /** Green starburst at a hit. xd = radius. */
    public static final RegistryObject<SimpleParticleType> VG_HIT = simple("vg_hit");
    /** Green spark that arcs and falls. */
    public static final RegistryObject<SimpleParticleType> VG_SPARK = simple("vg_spark");
    /** Green mote that drifts straight (energy being pulled in). */
    public static final RegistryObject<SimpleParticleType> VG_GATHER = simple("vg_gather");
    /** Spectral green flame that rises and flickers out. */
    public static final RegistryObject<SimpleParticleType> VG_FLAME = simple("vg_flame");
    /** Black Mist puff (dark teal, translucent). */
    public static final RegistryObject<SimpleParticleType> VG_MIST = simple("vg_mist");
    /** Ghostly runic circle lying on the ground. xd = radius. Short-lived; re-spawn it to keep it up. */
    public static final RegistryObject<SimpleParticleType> VG_CIRCLE = simple("vg_circle");
    /** Translucent green soul silhouette. xd = radius. */
    public static final RegistryObject<SimpleParticleType> VG_SOUL = simple("vg_soul");
    /** Shard of spectral energy. */
    public static final RegistryObject<SimpleParticleType> VG_SHARD = simple("vg_shard");
    /** Thin green line from the position along (xd, yd, zd). */
    public static final RegistryObject<SimpleParticleType> VG_LINE = simple("vg_line");
    /** Dark-green tendril line from the position along (xd, yd, zd). */
    public static final RegistryObject<SimpleParticleType> VG_TENDRIL = simple("vg_tendril");

    // ---- Sun Breathing ----
    public static final RegistryObject<SimpleParticleType> SUN_SPARK = simple("sun_spark");
    public static final RegistryObject<SimpleParticleType> SUN_FLARE = simple("sun_flare");
    public static final RegistryObject<SimpleParticleType> SUN_RING = simple("sun_ring");
    public static final RegistryObject<SimpleParticleType> SUN_RING_FLAT = simple("sun_ring_flat");
    public static final RegistryObject<SimpleParticleType> SUN_EMBER = simple("sun_ember");
    public static final RegistryObject<SimpleParticleType> SUN_CIRCLE = simple("sun_circle");
    public static final RegistryObject<SimpleParticleType> SUN_FIGURE = simple("sun_figure");
    public static final RegistryObject<SimpleParticleType> SUN_BEAM = simple("sun_beam");
    public static final RegistryObject<SimpleParticleType> SUN_LINE = simple("sun_line");
    public static final RegistryObject<SimpleParticleType> SUN_BEAM_RED = simple("sun_beam_red");
    public static final RegistryObject<SimpleParticleType> SUN_GLOW = simple("sun_glow");
}
