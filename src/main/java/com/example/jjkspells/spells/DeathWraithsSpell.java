package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.entity.DeathWraithEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * The Ruler of Death's Wraiths. A host of hooded wraiths rises around the caster, then each one flies straight at the
 * creature the caster is looking at (blocks do not stop them). Every wraith removes 1.5% of the target's max health.
 */
@AutoSpellConfig
public class DeathWraithsSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(JJKSpells.MODID, "death_wraiths");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(45)
            .build();

    public static final double RANGE = 64.0;

    public DeathWraithsSpell() {
        this.manaCostPerLevel = 0;
        this.baseManaCost = 220;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 0;
        this.castTime = 25;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.SOUL_ESCAPE); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.of(SoundEvents.WARDEN_AMBIENT); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Wraiths: " + JJKConfig.DEATH_WRAITH_COUNT.get()),
                Component.literal("Damage: " + String.format("%.1f", JJKConfig.DEATH_WRAITH_PERCENT.get() * 100) + "% max health per wraith"),
                Component.literal("Target: whoever you are looking at (" + (int) RANGE + " blocks)")
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            summon(server, entity);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void summon(ServerLevel level, LivingEntity caster) {
        LivingEntity target = findTarget(level, caster);
        if (target == null) {
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("The wraiths find no one to hunt").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            }
            return;
        }
        int n = JJKConfig.DEATH_WRAITH_COUNT.get();
        var rng = level.random;
        for (int i = 0; i < n; i++) {
            float phase = (float) (Math.PI * 2 * i / n);
            float radius = 2.6f + rng.nextFloat() * 2.2f;
            float height = 1.2f + rng.nextFloat() * 2.4f;
            int launch = 24 + i * 2;
            level.addFreshEntity(new DeathWraithEntity(level, caster, target, launch, phase, radius, height));
        }
        Vec3 p = caster.position();
        FX.one(level, ModParticles.WC_RING_FLAT.get(), p.add(0, 0.08, 0), new Vec3(6, 0, 0));
        FX.one(level, ModParticles.GHOST_GLOW.get(), p.add(0, 1, 0), new Vec3(6, 0, 0));
        level.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.PLAYERS, 3f, 0.5f);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 3f, 0.5f);
    }

    /** The living thing nearest to the caster's line of sight (within about 14 degrees), up to 64 blocks away. */
    private static LivingEntity findTarget(ServerLevel level, LivingEntity caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        AABB box = caster.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(10);
        LivingEntity best = null;
        double bestScore = 0.97;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == caster || e.isAlliedTo(caster) || e.isSpectator()) continue;
            if (e instanceof Player p && p.getAbilities().invulnerable) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > RANGE || d < 0.5) continue;
            double dot = to.scale(1.0 / d).dot(look);
            // very close creatures are allowed a wider angle
            double need = d < 4 ? 0.8 : 0.97;
            if (dot < need) continue;
            double score = dot - d * 0.0005;
            if (best == null || score > bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }
}
