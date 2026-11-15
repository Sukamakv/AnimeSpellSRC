package com.example.jjkspells.spells;

import com.example.jjkspells.GarenConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AutoSpellConfig;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.entity.JusticeSwordEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Calls a spectral sword down on the enemy you are looking at; the weaker the target, the harder it lands. */
@AutoSpellConfig
public class DemacianJusticeSpell extends GarenSpellBase {
    public DemacianJusticeSpell() {
        super("demacian_justice", CastType.INSTANT, SpellRarity.LEGENDARY);
    }

    @Override protected int configMana() { return GarenConfig.DJ_MANA.get(); }
    @Override protected double configCooldownSeconds() { return GarenConfig.DJ_COOLDOWN.get(); }


    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("Range: " + GarenConfig.DJ_RANGE.get().intValue() + " blocks"),
                Component.literal("Damage: " + GarenConfig.DJ_BASE_DAMAGE.get().intValue() + " base, up to " + GarenConfig.DJ_DAMAGE_0.get().intValue() + " on a nearly dead target"),
                Component.literal("Ignores damage caps")
        );
    }

    /** The living entity closest to the crosshair within range (cone of about 20 degrees), or null. */
    public static LivingEntity findTarget(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        LivingEntity best = null;
        double bestScore = 0.0;
        AABB box = caster.getBoundingBox().inflate(range);
        for (Entity e : caster.level().getEntities(caster, box)) {
            if (!GarenManager.validTarget(caster, e)) continue;
            LivingEntity cand = (LivingEntity) e;
            Vec3 to = cand.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.01) continue;
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot < 0.94) continue;
            if (!caster.hasLineOfSight(cand)) continue;
            double score = dot - dist / range * 0.03;
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        com.example.jjkspells.spells.Debuffs.cast(this, entity);
        if (level instanceof ServerLevel server) {
            LivingEntity target = findTarget(entity, GarenConfig.DJ_RANGE.get());
            if (target != null) {
                JusticeSwordEntity.summon(server, entity, target);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
