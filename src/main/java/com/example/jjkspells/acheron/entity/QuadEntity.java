package com.example.jjkspells.acheron.entity;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.registry.*;
import com.example.jjkspells.acheron.util.Nihility;
import com.example.jjkspells.acheron.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.example.jjkspells.vfx.sprite.SpriteFx;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Quadrivalent Ascendance: four dashes through a square around the aimed point, then the caster rises over the centre and the last
 * strike executes the weak and cuts the strong.
 * fx 1 = start {cx,cy,cz, yaw}, 2 = dash {i, fromx,fromy,fromz, tox,toy,toz}, 3 = execution {x,y,z}, 4 = final {x,y,z}.
 */
public class QuadEntity extends SeqEntity {
    private static final double R = 9.0;
    private final Vec3[] corner = new Vec3[4];
    private Vec3 last;
    private int executed;

    public QuadEntity(EntityType<? extends QuadEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 100;
    }

    public static QuadEntity create(Level level, LivingEntity caster) {
        var c = AcheronConfig.QUAD;
        Vec3 p = aimCenter(level, caster, c.range.get(), c.radius.get());
        QuadEntity e = new QuadEntity(ModEntities.QUAD.get(), level);
        e.dreamAmt = c.dreams.get();
        e.begin(caster, p, c.radius.get(), 0, Util.yawTo(caster.position(), p));
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(e.yaw() + 45 + 90 * i);
            double x = p.x - Math.sin(a) * R, z = p.z + Math.cos(a) * R;
            e.corner[i] = new Vec3(x, p.y, z);
        }
        return e;
    }

    @Override
    public int total() {
        return tk(3.8);
    }

    /**
     * A standable spot in the column (x, z) near height y: the caster's box is free there and a solid block is right below.
     * Searches up and down (nearest first) and also a few blocks around, so a corner inside a hill is lifted onto it.
     */
    private Vec3 stand(LivingEntity o, double x, double y, double z) {
        double[][] around = {{0, 0}, {1.5, 0}, {-1.5, 0}, {0, 1.5}, {0, -1.5}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        int base = (int) Math.floor(y);
        for (double[] off : around) {
            double cx = x + off[0], cz = z + off[1];
            for (int d = 0; d <= 16; d++) {
                for (int sgn = (d == 0 ? 1 : -1); sgn <= 1; sgn += 2) {
                    int yy = base + d * sgn;
                    if (yy < level().getMinBuildHeight() + 1 || yy > level().getMaxBuildHeight() - 3) {
                        continue;
                    }
                    net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(cx, yy - 0.5, cz);
                    if (level().getBlockState(below).getCollisionShape(level(), below).isEmpty() || !level().getFluidState(below).isEmpty()) {
                        continue;
                    }
                    net.minecraft.world.phys.AABB box = o.getBoundingBox().move(cx - o.getX(), yy - o.getY(), cz - o.getZ());
                    if (level().noCollision(o, box)) {
                        return new Vec3(cx, yy, cz);
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = AcheronConfig.QUAD;
        Vec3 p = position();
        if (age < tk(3.4)) {
            Util.LOCKED.add(ownerId);
            Util.UNTOUCHABLE.add(ownerId);
            if (age < tk(2.5)) {
                freeze(o);
            }
        } else {
            Util.LOCKED.remove(ownerId);
            Util.UNTOUCHABLE.remove(ownerId);
        }
        if (age == 0) {
            last = o.position();
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.QUADRIVALENT_DASH.get(), 1.2f, 0.8f);
            pushFx(1, p.x, p.y, p.z, yaw());
        }
        for (int i = 0; i < 4; i++) {
            if (at(age, 0.5 + 0.55 * i)) {
                // a spot where the caster really fits and stands on something: never inside a hill or under the ground
                Vec3 to = stand(o, corner[i].x, p.y, corner[i].z);
                if (to == null) {
                    to = stand(o, p.x, p.y, p.z);
                }
                if (to == null) {
                    to = last != null ? last : o.position();
                }
                pushFx(2, i, last.x, last.y, last.z, to.x, to.y, to.z);
                SpriteFx.beam(sv(), last.add(0, 1.0, 0), to.add(0, 1.0, 0), SpriteFx.Palette.CRIMSON, 0.35f, 6);
                SpriteFx.streak(sv(), last.add(to).scale(0.5).add(0, 1.1, 0), faceCaster(o, p), 18f * i - 25f, SpriteFx.Palette.WHITE, 3.0f);
                SpriteFx.impact(sv(), to.add(0, 1.0, 0), SpriteFx.Palette.CRIMSON, 1.4f);
                SpriteFx.shards(sv(), to.add(0, 1.0, 0), SpriteFx.Palette.CRIMSON, 1.0f);
                Util.snapTo(o, to, Util.yawTo(to, p), 0);
                Util.swing(o);
                Util.sound(level(), to.x, to.y, to.z, ModSounds.QUADRIVALENT_DASH.get(), 1.3f, 1.0f + 0.1f * i);
                for (LivingEntity e : foes(o, c.radius.get())) {
                    strike(o, e, c.dashFlat.get(), c.dashPercent.get(), 1.0, 0, 0);
                    Util.fx(level(), ModParticles.SLASH_V.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.2, 0.3, 0.2, 0);
                    track(e);
                }
                last = to;
            }
        }
        if (in(age, 2.5, 3.3)) {
            hover(o, p.add(0, 4.0, 0), yaw(), 20);
            Util.FALL_IMMUNE.merge(ownerId, level().getGameTime() + 80, Math::max);
        }
        if (at(age, 2.6)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.QUADRIVALENT_EXECUTION.get(), 1.4f, 1.0f);
        }
        if (at(age, 3.0)) {
            Util.swing(o);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.QUADRIVALENT_IMPACT.get(), 1.8f, 1.0f);
            pushFx(4, p.x, p.y, p.z);
            int shown = 0;
            for (LivingEntity e : foes(o, c.radius.get())) {
                if (Nihility.elite(e)) {
                    strike(o, e, c.eliteFlat.get(), c.elitePercent.get(), 1.0, 0, 0);
                } else if (Util.consume(o, e, c.executeThresholdPercent.get(), 0)) {
                    executed++;
                    if (shown++ < 12) {
                        pushFx(3, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
                        SpriteFx.tendrils(sv(), e.position().add(0, e.getBbHeight() * 0.5, 0), SpriteFx.Palette.CRIMSON, 1.6f);
                        SpriteFx.shards(sv(), e.position().add(0, e.getBbHeight() * 0.5, 0), SpriteFx.Palette.WHITE, 1.2f);
                    }
                } else {
                    strike(o, e, c.ordinaryFlat.get(), c.ordinaryPercent.get(), 1.0, 0, 0);
                }
                if (e.isAlive()) {
                    Util.stagger(e, 10);
                }
                track(e);
            }
            Util.voidBurst(level(), p.x, p.y + 1, p.z, 3.0, (int) (20 * density()));
            SpriteFx.beam(sv(), p.add(0, 26, 0), p.add(0, 0.1, 0), SpriteFx.Palette.WHITE, 1.0f, 5);
            SpriteFx.fracture(sv(), p.add(0, 1.2, 0), SpriteFx.Palette.CRIMSON, 5.0f);
            SpriteFx.cracks(sv(), p, SpriteFx.Palette.CRIMSON, r1() * 2f / 7f);
            SpriteFx.ring(sv(), p, SpriteFx.Palette.CRIMSON, r1() / 3f);
            SpriteFx.shards(sv(), p.add(0, 1.2, 0), SpriteFx.Palette.WHITE, 2.5f);
            award(o, c.dreams.get());
        }
        if (at(age, 3.4)) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.ACHERON_SHEATH.get(), 1.0f, 1.0f);
        }
    }
}
