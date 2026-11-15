package com.example.jjkspells;

import java.util.HashMap;
import java.util.Map;

/** Byte-identical environment textures are stored once (keeps the jar small); this points the removed names at the kept file. */
public final class TexAlias {
    private static final Map<String, String> M = new HashMap<>();

    static {
        M.put("textures/environment/dragon_beam.png", "textures/environment/beam.png");
        M.put("textures/environment/dragon_glow.png", "textures/environment/al_glow.png");
        M.put("textures/environment/dragon_ring_wave.png", "textures/environment/al_ring_wave.png");
        M.put("textures/environment/dragon_rune_ring.png", "textures/environment/al_rune_ring.png");
        M.put("textures/environment/dragon_wave_band.png", "textures/environment/al_flame_band.png");
        M.put("textures/environment/glow.png", "textures/environment/al_glow.png");
        M.put("textures/environment/ring_wave.png", "textures/environment/al_ring_wave.png");
        M.put("textures/environment/rune_ring.png", "textures/environment/al_rune_ring.png");
        M.put("textures/environment/vel_barrier.png", "textures/environment/tem_barrier.png");
        M.put("textures/environment/vel_beam.png", "textures/environment/tem_beam.png");
        M.put("textures/environment/vel_black.png", "textures/environment/tem_black.png");
        M.put("textures/environment/vel_blob.png", "textures/environment/tem_blob.png");
        M.put("textures/environment/vel_circle.png", "textures/environment/tem_circle.png");
        M.put("textures/environment/vel_corona.png", "textures/environment/tem_corona.png");
        M.put("textures/environment/vel_crack.png", "textures/environment/tem_crack.png");
        M.put("textures/environment/vel_crescent.png", "textures/environment/tem_crescent.png");
        M.put("textures/environment/vel_droplets.png", "textures/environment/tem_droplets.png");
        M.put("textures/environment/vel_fragment.png", "textures/environment/tem_fragment.png");
        M.put("textures/environment/vel_glow.png", "textures/environment/tem_glow.png");
        M.put("textures/environment/vel_glow_blue.png", "textures/environment/tem_glow_blue.png");
        M.put("textures/environment/vel_glow_violet.png", "textures/environment/tem_glow_violet.png");
        M.put("textures/environment/vel_lens.png", "textures/environment/tem_lens.png");
        M.put("textures/environment/vel_lightning.png", "textures/environment/tem_lightning.png");
        M.put("textures/environment/vel_line.png", "textures/environment/tem_line.png");
        M.put("textures/environment/vel_mist.png", "textures/environment/tem_mist.png");
        M.put("textures/environment/vel_rift.png", "textures/environment/tem_rift.png");
        M.put("textures/environment/vel_ring_wave.png", "textures/environment/tem_ring_wave.png");
        M.put("textures/environment/vel_rings.png", "textures/environment/tem_rings.png");
        M.put("textures/environment/vel_rune_ring.png", "textures/environment/tem_rune_ring.png");
        M.put("textures/environment/vel_slash.png", "textures/environment/tem_slash.png");
        M.put("textures/environment/vel_slime_wave.png", "textures/environment/tem_slime_wave.png");
        M.put("textures/environment/vel_soulwisp.png", "textures/environment/tem_soulwisp.png");
        M.put("textures/environment/vel_spear.png", "textures/environment/tem_spear.png");
        M.put("textures/environment/vel_sphere.png", "textures/environment/tem_sphere.png");
        M.put("textures/environment/vel_star.png", "textures/environment/tem_star.png");
        M.put("textures/environment/vel_target.png", "textures/environment/tem_target.png");
        M.put("textures/environment/vel_tendril.png", "textures/environment/tem_tendril.png");
        M.put("textures/environment/vel_vortex.png", "textures/environment/tem_vortex.png");
        M.put("textures/environment/vel_wave.png", "textures/environment/tem_wave.png");
        M.put("textures/environment/vgr_barrier.png", "textures/environment/tem_barrier.png");
        M.put("textures/environment/vgr_beam.png", "textures/environment/tem_beam.png");
        M.put("textures/environment/vgr_black.png", "textures/environment/tem_black.png");
        M.put("textures/environment/vgr_blob.png", "textures/environment/tem_blob.png");
        M.put("textures/environment/vgr_circle.png", "textures/environment/tem_circle.png");
        M.put("textures/environment/vgr_corona.png", "textures/environment/tem_corona.png");
        M.put("textures/environment/vgr_crack.png", "textures/environment/tem_crack.png");
        M.put("textures/environment/vgr_crescent.png", "textures/environment/tem_crescent.png");
        M.put("textures/environment/vgr_dragon.png", "textures/environment/vel_dragon.png");
        M.put("textures/environment/vgr_droplets.png", "textures/environment/tem_droplets.png");
        M.put("textures/environment/vgr_fragment.png", "textures/environment/tem_fragment.png");
        M.put("textures/environment/vgr_glow.png", "textures/environment/tem_glow.png");
        M.put("textures/environment/vgr_glow_blue.png", "textures/environment/tem_glow_blue.png");
        M.put("textures/environment/vgr_glow_violet.png", "textures/environment/tem_glow_violet.png");
        M.put("textures/environment/vgr_lens.png", "textures/environment/tem_lens.png");
        M.put("textures/environment/vgr_lightning.png", "textures/environment/tem_lightning.png");
        M.put("textures/environment/vgr_line.png", "textures/environment/tem_line.png");
        M.put("textures/environment/vgr_mist.png", "textures/environment/tem_mist.png");
        M.put("textures/environment/vgr_rift.png", "textures/environment/tem_rift.png");
        M.put("textures/environment/vgr_ring_wave.png", "textures/environment/tem_ring_wave.png");
        M.put("textures/environment/vgr_rings.png", "textures/environment/tem_rings.png");
        M.put("textures/environment/vgr_rune_ring.png", "textures/environment/tem_rune_ring.png");
        M.put("textures/environment/vgr_slash.png", "textures/environment/tem_slash.png");
        M.put("textures/environment/vgr_slime_wave.png", "textures/environment/tem_slime_wave.png");
        M.put("textures/environment/vgr_soulwisp.png", "textures/environment/tem_soulwisp.png");
        M.put("textures/environment/vgr_spear.png", "textures/environment/tem_spear.png");
        M.put("textures/environment/vgr_sphere.png", "textures/environment/tem_sphere.png");
        M.put("textures/environment/vgr_star.png", "textures/environment/tem_star.png");
        M.put("textures/environment/vgr_target.png", "textures/environment/tem_target.png");
        M.put("textures/environment/vgr_tendril.png", "textures/environment/tem_tendril.png");
        M.put("textures/environment/vgr_vortex.png", "textures/environment/tem_vortex.png");
        M.put("textures/environment/vgr_wave.png", "textures/environment/tem_wave.png");
        M.put("textures/environment/vgr_wings.png", "textures/environment/vel_wings.png");
        M.put("textures/environment/wave_band.png", "textures/environment/al_flame_band.png");
    }

    private TexAlias() {
    }

    public static String of(String path) {
        return M.getOrDefault(path, path);
    }
}
