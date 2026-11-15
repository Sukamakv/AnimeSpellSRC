package com.example.jjkspells.garou.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.GarouEvents;
import com.example.jjkspells.garou.entity.GammaRayBurstEntity;
import com.example.jjkspells.garou.util.GarouAnim;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Garou (Cosmic Fear Mode): GAMMA RAY BURST.
 * The caster stops, a dark cosmic sphere forms between his hands, compresses, and two beams erupt from it and merge
 * into one catastrophic beam. The work is done by {@link GammaRayBurstEntity}; this class only holds the numbers.
 */
@SuppressWarnings("removal")
@AutoSpellConfig
public class GarouGammaRayBurstSpell extends AbstractSpell {
    // ---- tuning (everything the description specifies lives here) ----
    public static final int MANA_COST = 400;
    public static final int COOLDOWN_SECONDS = 45;
    /** Initial beam impact, once per target: flat + percent of the target's max health. */
    public static final float INITIAL_FLAT = 500f;
    public static final float INITIAL_PERCENT = 0.20f;
    /** Continuous beam damage, every {@link #INTERVAL_TICKS}: flat + percent of max health. */
    public static final float TICK_FLAT = 100f;
    public static final float TICK_PERCENT = 0.04f;
    public static final int INTERVAL_TICKS = 10;      // 0.5 s
    public static final int DURATION_TICKS = 60;      // 3 s of beam
    public static final float RANGE = 80f;            // blocks
    public static final float WIDTH = 9f;             // blocks (diameter)
    /** Hard cap on continuous hits per target per cast (DURATION / INTERVAL): a target can never be hit endlessly. */
    public static final int MAX_TICK_HITS = DURATION_TICKS / INTERVAL_TICKS;

    private final ResourceLocation id = new ResourceLocation(JJKSpells.MODID, "garou_gamma_ray_burst");
    private final DefaultConfig config;

    public GarouGammaRayBurstSpell() {
        this.config = new DefaultConfig()
                .setMinRarity(SpellRarity.LEGENDARY)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(1)
                .setCooldownSeconds(COOLDOWN_SECONDS)
                .build();
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
        this.baseManaCost = MANA_COST;
    }

    @Override public ResourceLocation getSpellResource() { return id; }
    @Override public DefaultConfig getDefaultConfig() { return config; }
    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }
    @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public AnimationHolder getCastStartAnimation() {
        AnimationHolder h = GarouAnim.find("ANIMATION_LONG_CAST", "CHARGE_RAISED_HAND", "ANIMATION_INSTANT_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    /** No second burst while one is still running (the cooldown already covers it; this also guards other casters of the item). */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (GarouEvents.isCasting(entity)) return false;
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(GammaRayBurstEntity.create(level, entity, this));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("garou_gamma_ray_burst failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Garou recreates a Gamma Ray Burst: a dark cosmic sphere compresses and two beams merge into one"),
                Component.literal("Impact " + (int) INITIAL_FLAT + " + " + (int) (INITIAL_PERCENT * 100) + "% max health, then "
                        + (int) TICK_FLAT + " + " + (int) (TICK_PERCENT * 100) + "% every 0.5 s for up to " + DURATION_TICKS / 20 + " s"),
                Component.literal("Beam " + (int) WIDTH + " blocks wide, " + (int) RANGE + " blocks long. You cannot move while it fires"));
    }
}
