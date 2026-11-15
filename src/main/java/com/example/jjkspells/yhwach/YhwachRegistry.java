package com.example.jjkspells.yhwach;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yhwach.entity.AlmightyEntity;
import com.example.jjkspells.yhwach.entity.AuswaehlenEntity;
import com.example.jjkspells.yhwach.entity.PfeilEntity;
import com.example.jjkspells.yhwach.entity.QualKreisEntity;
import com.example.jjkspells.yhwach.entity.SanktZwingerEntity;
import com.example.jjkspells.yhwach.entity.VollstreckungEntity;
import com.example.jjkspells.yhwach.spell.AlmightySpell;
import com.example.jjkspells.yhwach.spell.AuswaehlenSpell;
import com.example.jjkspells.yhwach.spell.HeiligerPfeilSpell;
import com.example.jjkspells.yhwach.spell.KirchenliedSpell;
import com.example.jjkspells.yhwach.spell.QualKreisSpell;
import com.example.jjkspells.yhwach.spell.VollstreckungSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Self-contained registration for the Yhwach spell pack.
 * Hooks the Forge {@link RegisterEvent} directly, so no existing file (JJKSpells, ModSpells, ModEntities...) has to be edited.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YhwachRegistry {
    public static final String PFEIL_ID = "yhwach_pfeil";

    /** Assigned during the entity-type registration phase, long before any renderer or spell touches it. */
    public static EntityType<PfeilEntity> PFEIL;

    public static final String QUAL_ID = "yhwach_qual_kreis_storm";
    public static EntityType<QualKreisEntity> QUAL;

    public static final String SANKT_ID = "yhwach_sankt_zwinger";
    public static EntityType<SanktZwingerEntity> SANKT;

    public static final String VOLL_ID = "yhwach_vollstreckung";
    public static EntityType<VollstreckungEntity> VOLL;

    public static final String AUSW_ID = "yhwach_auswaehlen";
    public static EntityType<AuswaehlenEntity> AUSW;

    public static final String ALMIGHTY_ID = "yhwach_the_almighty_zone";
    public static EntityType<AlmightyEntity> ALMIGHTY;

    private YhwachRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            PFEIL = EntityType.Builder.<PfeilEntity>of(PfeilEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(PFEIL_ID).toString());
            helper.register(JJKSpells.id(PFEIL_ID), PFEIL);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            QUAL = EntityType.Builder.<QualKreisEntity>of(QualKreisEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(QUAL_ID).toString());
            helper.register(JJKSpells.id(QUAL_ID), QUAL);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            SANKT = EntityType.Builder.<SanktZwingerEntity>of(SanktZwingerEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(SANKT_ID).toString());
            helper.register(JJKSpells.id(SANKT_ID), SANKT);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            VOLL = EntityType.Builder.<VollstreckungEntity>of(VollstreckungEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(VOLL_ID).toString());
            helper.register(JJKSpells.id(VOLL_ID), VOLL);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            AUSW = EntityType.Builder.<AuswaehlenEntity>of(AuswaehlenEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(AUSW_ID).toString());
            helper.register(JJKSpells.id(AUSW_ID), AUSW);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            ALMIGHTY = EntityType.Builder.<AlmightyEntity>of(AlmightyEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(ALMIGHTY_ID).toString());
            helper.register(JJKSpells.id(ALMIGHTY_ID), ALMIGHTY);
        });
        event.register(SpellRegistry.SPELL_REGISTRY_KEY, helper -> {
            helper.register(JJKSpells.id(HeiligerPfeilSpell.NAME), new HeiligerPfeilSpell());
            helper.register(JJKSpells.id(QualKreisSpell.NAME), new QualKreisSpell());
            helper.register(JJKSpells.id(KirchenliedSpell.NAME), new KirchenliedSpell());
            helper.register(JJKSpells.id(VollstreckungSpell.NAME), new VollstreckungSpell());
            helper.register(JJKSpells.id(AuswaehlenSpell.NAME), new AuswaehlenSpell());
            helper.register(JJKSpells.id(AlmightySpell.NAME), new AlmightySpell());
        });
    }
}
