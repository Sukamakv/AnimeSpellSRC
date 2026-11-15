package com.example.jjkspells.frieren;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.AncientReleaseEntity;
import com.example.jjkspells.frieren.entity.HeightOfMagicEntity;
import com.example.jjkspells.frieren.spell.HeightOfMagicSpell;
import com.example.jjkspells.frieren.spell.AncientReleaseSpell;
import com.example.jjkspells.frieren.entity.DefensiveMagicEntity;
import com.example.jjkspells.frieren.entity.FlammeMagicEntity;
import com.example.jjkspells.frieren.entity.VollzanbelEntity;
import com.example.jjkspells.frieren.entity.ZoltraakEntity;
import com.example.jjkspells.frieren.spell.DefensiveMagicSpell;
import com.example.jjkspells.frieren.spell.FrierenFlammesUltimateMagicSpell;
import com.example.jjkspells.frieren.spell.FrierenVollzanbelSpell;
import com.example.jjkspells.frieren.spell.ZoltraakSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Frieren (Sousou no Frieren): self-registering. Nothing in the existing mod has to be edited - this class is a MOD-bus
 * subscriber that adds its entity types and spells through RegisterEvent.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FrierenRegistry {
    /** The Zoltraak controller (formation + beam). Non-living, never saved. */
    public static EntityType<ZoltraakEntity> ZOLTRAAK_ENTITY;
    private static EntityType<ZoltraakEntity> mk_ZOLTRAAK_ENTITY() {
        return EntityType.Builder
            .<ZoltraakEntity>of(ZoltraakEntity::new, MobCategory.MISC)
            .sized(0.2f, 0.2f)
            .clientTrackingRange(16)
            .updateInterval(20)
            .fireImmune()
            .noSave()
            .noSummon()
            .build("frieren_zoltraak");
    }

    /** The barrier controller: follows the caster, so it is tracked every tick. */
    public static EntityType<DefensiveMagicEntity> DEFENSIVE_ENTITY;
    private static EntityType<DefensiveMagicEntity> mk_DEFENSIVE_ENTITY() {
        return EntityType.Builder
            .<DefensiveMagicEntity>of(DefensiveMagicEntity::new, MobCategory.MISC)
            .sized(0.2f, 0.2f)
            .clientTrackingRange(16)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .noSummon()
            .build("frieren_defensive_magic");
    }

    /** The Ancient Release controller (charge, beam, explosion). */
    public static EntityType<AncientReleaseEntity> ANCIENT_ENTITY;
    private static EntityType<AncientReleaseEntity> mk_ANCIENT_ENTITY() {
        return EntityType.Builder
            .<AncientReleaseEntity>of(AncientReleaseEntity::new, MobCategory.MISC)
            .sized(0.2f, 0.2f)
            .clientTrackingRange(24)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .noSummon()
            .build("frieren_ancient_release");
    }

    public static EntityType<HeightOfMagicEntity> HEIGHT_ENTITY;
    private static EntityType<HeightOfMagicEntity> mk_HEIGHT_ENTITY() {
        return EntityType.Builder
            .<HeightOfMagicEntity>of(HeightOfMagicEntity::new, MobCategory.MISC)
            .sized(0.2f, 0.2f)
            .clientTrackingRange(24)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .noSummon()
            .build("frieren_height_of_magic");
    }

    public static final String ENTITY_FLAMME = "frieren_flamme_magic";

    /** Controller of Flamme's Ultimate Magic (placed at the target point, runs the whole sequence). */
    public static EntityType<FlammeMagicEntity> FLAMME_MAGIC;
    private static EntityType<FlammeMagicEntity> mk_FLAMME_MAGIC() {
        return
            EntityType.Builder.<FlammeMagicEntity>of(FlammeMagicEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(16).updateInterval(20)
                    .setShouldReceiveVelocityUpdates(false)
                    .build(ENTITY_FLAMME);
    }

    public static final String ENTITY_VOLLZANBEL = "frieren_vollzanbel";

    /** Controller of Vollzanbel (follows the caster, then holds the explosion point). */
    public static EntityType<VollzanbelEntity> VOLLZANBEL_ENTITY;
    private static EntityType<VollzanbelEntity> mk_VOLLZANBEL_ENTITY() {
        return
            EntityType.Builder.<VollzanbelEntity>of(VollzanbelEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f).noSave().noSummon().fireImmune()
                    .clientTrackingRange(16).updateInterval(2)
                    .setShouldReceiveVelocityUpdates(false)
                    .build(ENTITY_VOLLZANBEL);
    }

    /** Set when the spell registry event fires (the same instance Iron's holds). */
    public static ZoltraakSpell ZOLTRAAK_SPELL;
    public static DefensiveMagicSpell DEFENSIVE_SPELL;
    public static FrierenFlammesUltimateMagicSpell FLAMMES_ULTIMATE_MAGIC;
    public static FrierenVollzanbelSpell VOLLZANBEL;
    public static AncientReleaseSpell ANCIENT_SPELL;
    public static HeightOfMagicSpell HEIGHT_SPELL;

    private FrierenRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            ZOLTRAAK_ENTITY = mk_ZOLTRAAK_ENTITY();
            helper.register(JJKSpells.id("frieren_zoltraak"), ZOLTRAAK_ENTITY);
            DEFENSIVE_ENTITY = mk_DEFENSIVE_ENTITY();
            helper.register(JJKSpells.id("frieren_defensive_magic"), DEFENSIVE_ENTITY);
            FLAMME_MAGIC = mk_FLAMME_MAGIC();
            helper.register(JJKSpells.id(ENTITY_FLAMME), FLAMME_MAGIC);
            VOLLZANBEL_ENTITY = mk_VOLLZANBEL_ENTITY();
            helper.register(JJKSpells.id(ENTITY_VOLLZANBEL), VOLLZANBEL_ENTITY);
            ANCIENT_ENTITY = mk_ANCIENT_ENTITY();
            helper.register(JJKSpells.id("frieren_ancient_release"), ANCIENT_ENTITY);
            HEIGHT_ENTITY = mk_HEIGHT_ENTITY();
            helper.register(JJKSpells.id("frieren_height_of_magic"), HEIGHT_ENTITY);
        });
        event.register(SpellRegistry.SPELL_REGISTRY_KEY, helper -> {
            ZOLTRAAK_SPELL = new ZoltraakSpell();
            helper.register(ZOLTRAAK_SPELL.getSpellResource(), ZOLTRAAK_SPELL);
            DEFENSIVE_SPELL = new DefensiveMagicSpell();
            helper.register(DEFENSIVE_SPELL.getSpellResource(), DEFENSIVE_SPELL);
            FLAMMES_ULTIMATE_MAGIC = new FrierenFlammesUltimateMagicSpell();
            helper.register(JJKSpells.id(FrierenFlammesUltimateMagicSpell.NAME), FLAMMES_ULTIMATE_MAGIC);
            VOLLZANBEL = new FrierenVollzanbelSpell();
            helper.register(JJKSpells.id(FrierenVollzanbelSpell.NAME), VOLLZANBEL);
            ANCIENT_SPELL = new AncientReleaseSpell();
            helper.register(ANCIENT_SPELL.getSpellResource(), ANCIENT_SPELL);
            HEIGHT_SPELL = new HeightOfMagicSpell();
            helper.register(HEIGHT_SPELL.getSpellResource(), HEIGHT_SPELL);
        });
    }
}
