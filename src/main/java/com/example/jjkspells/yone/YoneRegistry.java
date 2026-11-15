package com.example.jjkspells.yone;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yone.entity.FateSealedEntity;
import com.example.jjkspells.yone.entity.GhostWalkEntity;
import com.example.jjkspells.yone.entity.HornedVolleyEntity;
import com.example.jjkspells.yone.entity.SpiritCleaveEntity;
import com.example.jjkspells.yone.entity.DestinyCleavedEntity;
import com.example.jjkspells.yone.entity.TwinCrescentsEntity;
import com.example.jjkspells.yone.entity.TwinMoonEclipseEntity;
import com.example.jjkspells.yone.spell.FateSealedSpell;
import com.example.jjkspells.yone.spell.GhostWalkSpell;
import com.example.jjkspells.yone.spell.HornedVolleySpell;
import com.example.jjkspells.yone.spell.SpiritCleaveSpell;
import com.example.jjkspells.yone.spell.DestinyCleavedSpell;
import com.example.jjkspells.yone.spell.TwinCrescentsSpell;
import com.example.jjkspells.yone.spell.TwinMoonEclipseSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Self-contained registration for the Yone (League of Legends) spell pack. No existing file has to be edited. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YoneRegistry {
    public static final String CRESCENTS_ID = "yone_twin_crescents_zone";
    public static EntityType<TwinCrescentsEntity> CRESCENTS;
    public static final String CLEAVE_ID = "yone_spirit_cleave_zone";
    public static EntityType<SpiritCleaveEntity> CLEAVE;
    public static final String GHOST_ID = "yone_ghost_walk_zone";
    public static EntityType<GhostWalkEntity> GHOST;
    public static final String FATE_ID = "yone_fate_sealed_zone";
    public static EntityType<FateSealedEntity> FATE;
    public static final String VOLLEY_ID = "yone_horned_volley_zone";
    public static EntityType<HornedVolleyEntity> VOLLEY;
    public static final String ECLIPSE_ID = "yone_twin_moon_eclipse_zone";
    public static EntityType<TwinMoonEclipseEntity> ECLIPSE;
    public static final String DESTINY_ID = "yone_destiny_cleaved_zone";
    public static EntityType<DestinyCleavedEntity> DESTINY;

    private YoneRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            CRESCENTS = EntityType.Builder.<TwinCrescentsEntity>of(TwinCrescentsEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(CRESCENTS_ID).toString());
            helper.register(JJKSpells.id(CRESCENTS_ID), CRESCENTS);
            CLEAVE = EntityType.Builder.<SpiritCleaveEntity>of(SpiritCleaveEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(CLEAVE_ID).toString());
            helper.register(JJKSpells.id(CLEAVE_ID), CLEAVE);
            GHOST = EntityType.Builder.<GhostWalkEntity>of(GhostWalkEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(GHOST_ID).toString());
            helper.register(JJKSpells.id(GHOST_ID), GHOST);
            FATE = EntityType.Builder.<FateSealedEntity>of(FateSealedEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(FATE_ID).toString());
            helper.register(JJKSpells.id(FATE_ID), FATE);
            VOLLEY = EntityType.Builder.<HornedVolleyEntity>of(HornedVolleyEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(VOLLEY_ID).toString());
            helper.register(JJKSpells.id(VOLLEY_ID), VOLLEY);
            ECLIPSE = EntityType.Builder.<TwinMoonEclipseEntity>of(TwinMoonEclipseEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(ECLIPSE_ID).toString());
            helper.register(JJKSpells.id(ECLIPSE_ID), ECLIPSE);
            DESTINY = EntityType.Builder.<DestinyCleavedEntity>of(DestinyCleavedEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(DESTINY_ID).toString());
            helper.register(JJKSpells.id(DESTINY_ID), DESTINY);
        });
        event.register(SpellRegistry.SPELL_REGISTRY_KEY, helper -> {
            helper.register(JJKSpells.id(TwinCrescentsSpell.NAME), new TwinCrescentsSpell());
            helper.register(JJKSpells.id(SpiritCleaveSpell.NAME), new SpiritCleaveSpell());
            helper.register(JJKSpells.id(GhostWalkSpell.NAME), new GhostWalkSpell());
            helper.register(JJKSpells.id(FateSealedSpell.NAME), new FateSealedSpell());
            helper.register(JJKSpells.id(HornedVolleySpell.NAME), new HornedVolleySpell());
            helper.register(JJKSpells.id(TwinMoonEclipseSpell.NAME), new TwinMoonEclipseSpell());
            helper.register(JJKSpells.id(DestinyCleavedSpell.NAME), new DestinyCleavedSpell());
        });
    }
}
