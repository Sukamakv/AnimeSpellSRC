package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The Darkin Blade. Aatrox pulls his greatsword back over his shoulder while crimson energy gathers along it, then
 * brings it down in a brutal overhead strike. A red fissure tears across the ground in front of him 6 blocks long and
 * 5 wide. Everything in it takes 70 damage plus 5% of its maximum health and is knocked back and staggered; whatever
 * is near the centre takes 200 damage plus 7% and is launched into the air instead.
 *
 * Instant cast, then a fixed swing (8 ticks of wind-up, the slam, a short recovery) so nothing can interrupt it.
 * Under World Ender the strike is 20% bigger. He steps slightly forward into the slam, and the direction snaps toward
 * an enemy that is nearly in line, which helps land the centre.
 */
@AutoSpellConfig
public class DarkinBladeSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "darkin_blade");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(8)
            .build();

    /** Length and width of the strike zone, in blocks. */
    public static final float RANGE = 6f;
    public static final float WIDTH = 5f;
    /** Anything struck: flat damage plus a fraction of the target's max health. */
    public static final float EDGE_DAMAGE = 70f;
    public static final float EDGE_PERCENT = 0.05f;
    /** The central impact: more of both. */
    public static final float CENTER_DAMAGE = 200f;
    public static final float CENTER_PERCENT = 0.07f;
    /** The centre is a circle this big (radius), 60% of the way down the zone. */
    public static final float CENTER_RADIUS = 1.7f;
    /** Ticks of wind-up before the slam, and ticks of recovery after it. */
    public static final int WINDUP_TICKS = 8;
    public static final int RECOVERY_TICKS = 5;

    private static final AnimationHolder SLAM_ANIMATION = SpellAnimLookup.find("OVERHEAD_MELEE_SWING_ANIMATION", "ONE_HANDED_VERTICAL_UPSWING_ANIMATION");

    public DarkinBladeSpell() {
        this.manaCostPerLevel = 6;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.castTime = 0;
        this.baseManaCost = 50;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SLAM_ANIMATION != null ? SLAM_ANIMATION : super.getCastStartAnimation();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float s = DariusFX.levelScale(spellLevel);
        return List.of(
                Component.literal("Struck: " + String.format("%.0f", EDGE_DAMAGE * s) + " + " + (int) (EDGE_PERCENT * 100) + "% max health, knocked back"),
                Component.literal("Centre: " + String.format("%.0f", CENTER_DAMAGE * s) + " + " + (int) (CENTER_PERCENT * 100) + "% max health, launched upward"),
                Component.literal("Zone: " + (int) RANGE + " blocks long, " + (int) WIDTH + " wide")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            DarkinBladeManager.start(server, entity, this, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
