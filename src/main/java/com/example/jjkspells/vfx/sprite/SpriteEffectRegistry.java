package com.example.jjkspells.vfx.sprite;

import com.example.jjkspells.JJKSpells;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Client side table of every sprite effect, rebuilt on resource reload (F3+T). Effects live in
 * assets/NAMESPACE/vfx_sprites/NAME.json, so a resource pack can restyle or add effects without code.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpriteEffectRegistry {
    private static final Logger LOG = LoggerFactory.getLogger("jjk_spells/sprite_vfx");
    private static volatile Map<ResourceLocation, SpriteEffectDef> DEFS = Map.of();

    private SpriteEffectRegistry() {}

    @Nullable
    public static SpriteEffectDef get(ResourceLocation id) {
        return DEFS.get(id);
    }

    public static int size() {
        return DEFS.size();
    }

    public static Iterable<ResourceLocation> ids() {
        return DEFS.keySet();
    }

    @SubscribeEvent
    public static void onReload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new GsonBuilder().create(), "vfx_sprites");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, SpriteEffectDef> out = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> en : files.entrySet()) {
                try {
                    out.put(en.getKey(), SpriteEffectDef.fromJson(en.getKey(), en.getValue().getAsJsonObject()));
                } catch (Exception ex) {
                    LOG.error("Bad sprite effect {}: {}", en.getKey(), ex.toString());
                }
            }
            DEFS = Map.copyOf(out);
            SpriteVfxManager.clear();
            LOG.info("Loaded {} sprite effects", out.size());
        }
    }
}
