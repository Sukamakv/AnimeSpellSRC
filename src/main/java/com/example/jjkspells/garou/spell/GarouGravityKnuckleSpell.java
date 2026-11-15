package com.example.jjkspells.garou.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.GarouEvents;
import com.example.jjkspells.garou.entity.GravityKnuckleEntity;
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
 * Garou (Cosmic Fear Mode): GRAVITY KNUCKLE.
 * Raise a fist wrapped in a gravity sphere, dash at the target, punch, and leave a gravitational well that drags every
 * enemy within 30 blocks to the impact point for 1.5 s before it collapses. All the work is in {@link GravityKnuckleEntity}.
 */
@SuppressWarnings("removal")
@AutoSpellConfig
public class GarouGravityKnuckleSpell extends AbstractSpell {
    // ---- tuning ----
    public static final int MANA_COST = 160;
    public static final int COOLDOWN_SECONDS = 15;
    /** Direct punch: flat + percent of the target's max health. */
    public static final float PUNCH_FLAT = 350f;
    public static final float PUNCH_PERCENT = 0.15f;
    /** Collapse: flat + percent of max health, once per enemy. */
    public static final float COLLAPSE_FLAT = 200f;
    public static final float COLLAPSE_PERCENT = 0.10f;
    public static final float PULL_RANGE = 30f;          // blocks, enemies pulled toward the impact
    public static final float COLLAPSE_RADIUS = 12f;     // blocks, damage + knockback of the collapse
    public static final float TARGET_RANGE = 20f;        // blocks, how far the dash can reach for a target
    public static final int WELL_TICKS = 30;             // 1.5 s

    private final ResourceLocation id = new ResourceLocation(JJKSpells.MODID, "garou_gravity_knuckle");
    private final DefaultConfig config;

    public GarouGravityKnuckleSpell() {
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

    /** One Garou channel at a time. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (GarouEvents.isCasting(entity)) return false;
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                level.addFreshEntity(GravityKnuckleEntity.create(level, entity));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("garou_gravity_knuckle failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Dash and punch: " + (int) PUNCH_FLAT + " + " + (int) (PUNCH_PERCENT * 100) + "% max health"),
                Component.literal("Gravity well pulls enemies within " + (int) PULL_RANGE + " blocks and holds them for " + WELL_TICKS / 20f + " s"),
                Component.literal("Collapse: " + (int) COLLAPSE_FLAT + " + " + (int) (COLLAPSE_PERCENT * 100) + "% max health and strong knockback"));
    }
}
