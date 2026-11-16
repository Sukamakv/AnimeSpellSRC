package com.example.jjkspells.vfx.sprite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * One sprite-sheet effect, loaded from assets/jjk_spells/vfx_sprites/NAME.json (see tools_vfx/gen_sprite_sheets.py).
 * The texture is a grid of equally sized frames, read left to right, top to bottom. Immutable and shared by every instance.
 * Common (server safe): it only touches json and resource locations.
 */
public final class SpriteEffectDef {
    public enum Blend { ADDITIVE, ALPHA }

    /**
     * BILLBOARD   - always faces the camera, spins by 'spin' degrees per tick.
     * BILLBOARD_Y - turns around the vertical axis only (flames, columns).
     * FLAT        - lies on the ground (XZ plane).
     * AXIS        - stretched along a direction (beams, lightning). 'axis_uv' says which texture axis runs along it.
     * NORMAL      - lies on a plane given by a normal vector (slashes, circles on walls).
     */
    public enum Orientation { BILLBOARD, BILLBOARD_Y, FLAT, AXIS, NORMAL }

    /** ONCE fits all frames into the lifetime. LOOP runs at 'fps'. HOLD runs once at 'fps' and keeps the last frame. */
    public enum Playback { ONCE, LOOP, HOLD }

    public final ResourceLocation id;
    public final ResourceLocation texture;
    public final int cols, rows, frames;
    public final Blend blend;
    public final Orientation orientation;
    public final Playback playback;
    public final boolean axisAlongU;
    public final float fps;
    public final int lifetime;
    /** Edge length of the quad in blocks at scale 1. */
    public final float size;
    public final float scaleStart, scaleEnd;
    public final int fadeIn, fadeOut;
    public final boolean fullbright, crossfade, ignoreDepth;
    /** Degrees per tick: roll for BILLBOARD / NORMAL, yaw for FLAT. */
    public final float spin;
    /** AXIS only: blocks per repeated segment (0 = one stretched quad). Needs a horizontally tileable sheet. */
    public final float tileLength;
    public final float gravity, drag;
    public final float tintR, tintG, tintB, alpha;

    private SpriteEffectDef(ResourceLocation id, JsonObject o) {
        this.id = id;
        this.texture = new ResourceLocation(GsonHelper.getAsString(o, "texture"));
        this.cols = Math.max(1, GsonHelper.getAsInt(o, "cols", 1));
        this.rows = Math.max(1, GsonHelper.getAsInt(o, "rows", 1));
        this.frames = Math.max(1, Math.min(cols * rows, GsonHelper.getAsInt(o, "frames", cols * rows)));
        this.blend = "alpha".equalsIgnoreCase(GsonHelper.getAsString(o, "blend", "additive")) ? Blend.ALPHA : Blend.ADDITIVE;
        this.orientation = parseOrientation(GsonHelper.getAsString(o, "orientation", "billboard"));
        this.playback = parsePlayback(GsonHelper.getAsString(o, "playback", "once"));
        this.axisAlongU = !"v".equalsIgnoreCase(GsonHelper.getAsString(o, "axis_uv", "u"));
        this.fps = Math.max(1f, GsonHelper.getAsFloat(o, "fps", 20f));
        this.lifetime = Math.max(1, GsonHelper.getAsInt(o, "lifetime", 20));
        this.size = GsonHelper.getAsFloat(o, "size", 2f);
        this.scaleStart = GsonHelper.getAsFloat(o, "scale_start", 1f);
        this.scaleEnd = GsonHelper.getAsFloat(o, "scale_end", 1f);
        this.fadeIn = Math.max(0, GsonHelper.getAsInt(o, "fade_in", 0));
        this.fadeOut = Math.max(0, GsonHelper.getAsInt(o, "fade_out", 0));
        this.fullbright = GsonHelper.getAsBoolean(o, "fullbright", blend == Blend.ADDITIVE);
        this.crossfade = GsonHelper.getAsBoolean(o, "crossfade", true);
        this.ignoreDepth = GsonHelper.getAsBoolean(o, "ignore_depth", false);
        this.spin = GsonHelper.getAsFloat(o, "spin", 0f);
        this.tileLength = Math.max(0f, GsonHelper.getAsFloat(o, "tile_length", 0f));
        this.gravity = GsonHelper.getAsFloat(o, "gravity", 0f);
        this.drag = GsonHelper.getAsFloat(o, "drag", 1f);
        float r = 1f, g = 1f, b = 1f;
        if (o.has("tint") && o.get("tint").isJsonArray()) {
            JsonArray a = o.getAsJsonArray("tint");
            if (a.size() >= 3) {
                r = a.get(0).getAsFloat();
                g = a.get(1).getAsFloat();
                b = a.get(2).getAsFloat();
            }
        }
        this.tintR = r;
        this.tintG = g;
        this.tintB = b;
        this.alpha = GsonHelper.getAsFloat(o, "alpha", 1f);
    }

    private static Orientation parseOrientation(String s) {
        return switch (s.toLowerCase()) {
            case "billboard_y" -> Orientation.BILLBOARD_Y;
            case "flat" -> Orientation.FLAT;
            case "axis" -> Orientation.AXIS;
            case "normal" -> Orientation.NORMAL;
            default -> Orientation.BILLBOARD;
        };
    }

    private static Playback parsePlayback(String s) {
        return switch (s.toLowerCase()) {
            case "loop" -> Playback.LOOP;
            case "hold" -> Playback.HOLD;
            default -> Playback.ONCE;
        };
    }

    public static SpriteEffectDef fromJson(ResourceLocation id, JsonObject o) {
        return new SpriteEffectDef(id, o);
    }
}
