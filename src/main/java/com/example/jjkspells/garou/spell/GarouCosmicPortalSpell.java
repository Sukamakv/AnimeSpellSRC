package com.example.jjkspells.garou.spell;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.GarouEvents;
import com.example.jjkspells.garou.entity.CosmicPortalEntity;
import com.example.jjkspells.garou.util.GarouAnim;
import com.example.jjkspells.garou.util.PortalPlanner;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Garou (Cosmic Fear Mode): COSMIC PORTAL (Blast's ability).
 * One spell, three behaviours chosen by where the caster looks:
 *  - an enemy within 35 blocks on the look ray: emerge behind it with a punch (180 + 8% max HP, shockwave, knock forward);
 *  - a block / point within 30 blocks: teleport there (no damage);
 *  - open air: 30 blocks forward (no damage).
 * If the destination is blocked a nearby free spot is used, otherwise the cast is refused (no mana spent).
 */
@SuppressWarnings("removal")
@AutoSpellConfig
public class GarouCosmicPortalSpell extends AbstractSpell {
    // ---- tuning ----
    public static final int MANA_COST = 120;
    public static final int COOLDOWN_SECONDS = 12;
    public static final float PUNCH_FLAT = 180f;
    public static final float PUNCH_PERCENT = 0.08f;
    public static final float FORWARD_RANGE = 30f;   // blocks, plain / "visible position" teleport
    public static final float ENEMY_RANGE = 35f;     // blocks, behind-the-enemy teleport

    private final ResourceLocation id = new ResourceLocation(JJKSpells.MODID, "garou_cosmic_portal");
    private final DefaultConfig config;

    public GarouCosmicPortalSpell() {
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
        AnimationHolder h = GarouAnim.find("ANIMATION_INSTANT_CAST", "ANIMATION_LONG_CAST");
        return h != null ? h : super.getCastStartAnimation();
    }

    /** Refuses the cast (before mana is spent) when no safe destination exists. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (GarouEvents.isCasting(entity)) return false;
        if (!level.isClientSide && PortalPlanner.plan(level, entity, ENEMY_RANGE, FORWARD_RANGE) == null) {
            if (entity instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("No safe place to open the portal"), true);
            return false;
        }
        return super.checkPreCastConditions(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (!level.isClientSide) {
            try {
                PortalPlanner.Plan plan = PortalPlanner.plan(level, entity, ENEMY_RANGE, FORWARD_RANGE);
                if (plan != null) level.addFreshEntity(CosmicPortalEntity.create(level, entity, plan));
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("jjk_spells").error("garou_cosmic_portal failed", t);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Look at an enemy (" + (int) ENEMY_RANGE + " blocks): emerge behind it with a punch, "
                        + (int) PUNCH_FLAT + " + " + (int) (PUNCH_PERCENT * 100) + "% max health"),
                Component.literal("Look at a spot or open air: teleport up to " + (int) FORWARD_RANGE + " blocks, no damage"),
                Component.literal("Invulnerable while inside the portal"));
    }
}
