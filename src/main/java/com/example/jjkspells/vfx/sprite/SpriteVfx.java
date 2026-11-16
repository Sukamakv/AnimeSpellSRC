package com.example.jjkspells.vfx.sprite;

import net.minecraft.client.renderer.RenderType;

/** One live effect. Pooled and reused by {@link SpriteVfxManager}; never keep a reference after it dies. Client only. */
public final class SpriteVfx {
    SpriteEffectDef def;
    RenderType type;
    double x, y, z, px, py, pz;
    float vx, vy, vz;
    /** AXIS: direction. NORMAL: plane normal. Always unit length (or zero = unset). */
    float dx, dy, dz;
    float length = 4f;
    float scale = 1f;
    float roll, prevRoll;
    float r = 1f, g = 1f, b = 1f, a = 1f;
    int age, life;
    int light = 0xF000F0;
    int attachId = -1;
    float attachYOff;
    float distSq;

    void reset(SpriteEffectDef d, RenderType t, double x, double y, double z) {
        this.def = d;
        this.type = t;
        this.x = this.px = x;
        this.y = this.py = y;
        this.z = this.pz = z;
        vx = vy = vz = 0f;
        dx = 0f;
        dy = 1f;
        dz = 0f;
        length = 4f;
        scale = 1f;
        roll = prevRoll = 0f;
        r = d.tintR;
        g = d.tintG;
        b = d.tintB;
        a = 1f;
        age = 0;
        life = d.lifetime;
        light = 0xF000F0;
        attachId = -1;
        attachYOff = 0f;
    }

    // ---- fluent setters, used right after SpriteVfxManager.spawn ----

    public SpriteVfx velocity(double vx, double vy, double vz) {
        this.vx = (float) vx;
        this.vy = (float) vy;
        this.vz = (float) vz;
        return this;
    }

    public SpriteVfx scale(float s) {
        this.scale = s;
        return this;
    }

    public SpriteVfx life(int ticks) {
        this.life = Math.max(1, ticks);
        return this;
    }

    public SpriteVfx roll(float degrees) {
        this.roll = this.prevRoll = degrees;
        return this;
    }

    public SpriteVfx tint(float r, float g, float b) {
        this.r = this.def.tintR * r;
        this.g = this.def.tintG * g;
        this.b = this.def.tintB * b;
        return this;
    }

    public SpriteVfx alpha(float a) {
        this.a = a;
        return this;
    }

    /** Direction for AXIS effects, plane normal for NORMAL effects. Need not be normalised. */
    public SpriteVfx dir(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l > 1e-6) {
            this.dx = (float) (x / l);
            this.dy = (float) (y / l);
            this.dz = (float) (z / l);
        }
        return this;
    }

    public SpriteVfx length(float blocks) {
        this.length = blocks;
        return this;
    }

    public SpriteVfx attach(int entityId, float yOffset) {
        this.attachId = entityId;
        this.attachYOff = yOffset;
        return this;
    }
}
