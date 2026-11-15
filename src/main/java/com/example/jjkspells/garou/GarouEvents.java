package com.example.jjkspells.garou;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.entity.CosmicPortalEntity;
import com.example.jjkspells.garou.entity.GammaRayBurstEntity;
import com.example.jjkspells.garou.entity.GravityKnuckleEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Forge-bus hooks of Garou. The ACTIVE lists hold every live controller entity of each spell (server AND client copies;
 * check {@code level().isClientSide} when you iterate them).
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class GarouEvents {
    private GarouEvents() {}

    public static final List<GammaRayBurstEntity> ACTIVE = new CopyOnWriteArrayList<>();
    public static final List<GravityKnuckleEntity> ACTIVE_GK = new CopyOnWriteArrayList<>();
    public static final List<CosmicPortalEntity> ACTIVE_CP = new CopyOnWriteArrayList<>();

    /** True on the server while this entity is channelling one of Garou's spells (rooted / dashing). */
    public static boolean isCasting(LivingEntity caster) {
        for (GammaRayBurstEntity g : ACTIVE) {
            if (!g.level().isClientSide && g.isAlive() && g.getCasterId() == caster.getId() && g.holdsCaster()) return true;
        }
        for (GravityKnuckleEntity g : ACTIVE_GK) {
            if (!g.level().isClientSide && g.isAlive() && g.getCasterId() == caster.getId() && g.holdsCaster()) return true;
        }
        for (CosmicPortalEntity g : ACTIVE_CP) {
            if (!g.level().isClientSide && g.isAlive() && g.getCasterId() == caster.getId() && g.holdsCaster()) return true;
        }
        return false;
    }

    /** The caster plants his feet: nothing pushes him around while a spell is channelling. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.level().isClientSide && isCasting(e)) event.setCanceled(true);
    }
}
