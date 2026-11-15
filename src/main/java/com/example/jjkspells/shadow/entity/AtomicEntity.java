package com.example.jjkspells.shadow.entity;

import com.example.jjkspells.shadow.config.ShdConfig;
import com.example.jjkspells.shadow.registry.*;
import com.example.jjkspells.shadow.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * I Am Atomic, anime accurate. The entity stands on the aimed point (where the blast happens); the caster is the owner and the
 * magic gathers around HIM. Everything visual is drawn from the age alone:
 * 0-2 revelation, 2-4.5 formation, 4.5-7 declaration, 7-8.5 release (flash at 7.0, the blast erupts at 7.4),
 * 8.5-11 the delayed shockwave and the illuminated horizon, 11-14.5 aftermath. One decisive hit, nothing repeats.
 */
public class AtomicEntity extends SeqEntity {
    public static final double REVEAL = 2.0, FORM = 4.5, DECLARE = 7.0, RELEASE = 7.0, BLAST = 7.4, SHOCK_A = 8.5, SHOCK_B = 11.0, AFTER = 11.5, TOTAL = 14.5;

    private final Set<UUID> hit = new HashSet<>();
    private final Set<UUID> pushed = new HashSet<>();
    private boolean released;
    private List<int[]> cols;
    private int colIdx;

    public AtomicEntity(EntityType<? extends AtomicEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 60;
    }

    public static AtomicEntity create(Level level, LivingEntity caster) {
        AtomicEntity e = new AtomicEntity(ModEntities.ATOMIC.get(), level);
        e.begin(caster, groundPoint(level, caster, ShdConfig.ATOMIC.range.get()), ShdConfig.ATOMIC.radius.get(), ShdConfig.ATOMIC.shockRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(TOTAL);
    }

    private void say(LivingEntity o, String s) {
        if (o instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal(s).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
        }
    }

    /** Allies, the caster's companions and anything named as protected survive the blast. */
    private boolean protectedFrom(LivingEntity o, LivingEntity e) {
        if (e == o || e.isAlliedTo(o) || e.getTags().contains("atomic_protected")) {
            return true;
        }
        if (e instanceof OwnableEntity oe && oe.getOwnerUUID() != null && oe.getOwnerUUID().equals(o.getUUID())) {
            return true;
        }
        if (e.hasCustomName()) {
            String n = e.getCustomName().getString().toLowerCase(Locale.ROOT);
            for (String s : ShdConfig.ATOMIC.names()) {
                if (!s.isBlank() && n.contains(s.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        String id = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
        for (String s : ShdConfig.ATOMIC.types()) {
            if (id.equals(s)) {
                return true;
            }
        }
        return false;
    }

    private List<LivingEntity> targets(LivingEntity o, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : foes(o, r)) {
            if (!protectedFrom(o, e)) {
                out.add(e);
            }
        }
        return out;
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = ShdConfig.ATOMIC;
        double R = c.radius.get(), S = c.shockRadius.get();
        Vec3 p = position();
        Vec3 at = o.position();
        if (age == 0) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.ATOMIC_CHARGE_START.get(), 2.0f, 1.0f);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ATOMIC_CHARGE_START.get(), 3.0f, 1.0f);
        }
        if (age <= tk(RELEASE + 0.6)) {
            Util.LOCKED.add(ownerId);
        }
        // the channel: Shadow stands unmoved, almost untouchable, never pushed (all of the channel, until the release)
        if (age < tk(RELEASE) && age % 2 == 0) {
            guard(o, c.channelReductionPercent.get());
            if (age % 20 == 0) {
                o.addEffect(new MobEffectInstance(ModEffects.FOCUS.get(), 30, 0, false, false, true));
            }
        }
        // sound: it swells while the magic gathers and goes quiet for the declaration
        if (age < tk(FORM) && age % 50 == 10) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.ATOMIC_COMPRESSION_LOOP.get(), 1.4f, 0.7f + 0.5f * (age / (float) tk(FORM)));
        }
        if (at(age, 2.0) || at(age, 3.4)) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.ATOMIC_HEARTBEAT.get(), 1.6f, 0.8f + age / 250f);
        }
        if (at(age, FORM)) {
            say(o, "I...");
            Util.sound(level(), at.x, at.y, at.z, ModSounds.ATOMIC_DECLARATION.get(), 2.5f, 1.0f);
        }
        if (at(age, 5.6)) {
            say(o, "I...  Am...");
        }
        if (at(age, 6.4)) {
            say(o, "I...  Am...  Atomic.");
        }
        // the release: the sword comes down, the light is let go
        if (age == tk(RELEASE)) {
            Util.sound(level(), at.x, at.y, at.z, ModSounds.ATOMIC_RELEASE.get(), 4.0f, 1.0f);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ATOMIC_RELEASE.get(), 6.0f, 1.0f);
        }
        // the decisive blast: once
        if (age == tk(BLAST) && !released) {
            released = true;
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ATOMIC_EXPLOSION.get(), 9.0f, 1.0f);
            Util.sound(level(), at.x, at.y, at.z, ModSounds.ATOMIC_EXPLOSION.get(), 3.0f, 0.9f);
            for (LivingEntity e : targets(o, R)) {
                Util.hurt(o, e, c.mainFlat.get(), c.mainPercent.get(), 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                track(e);
                hit.add(e.getUUID());
                pushFx(1, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
            }
            if (c.breakBlocks.get()) {
                startCrater();
            }
        }
        // the delayed shockwave only pushes: no second damage
        if (age >= tk(SHOCK_A) && age <= tk(SHOCK_B)) {
            double f = (age - tk(SHOCK_A)) / (double) (tk(SHOCK_B) - tk(SHOCK_A));
            double ring = S * (1.0 - Math.pow(1.0 - f, 2.0));
            if (age == tk(SHOCK_A)) {
                Util.sound(level(), p.x, p.y, p.z, ModSounds.ATOMIC_SHOCKWAVE.get(), 8.0f, 1.0f);
            }
            for (LivingEntity e : targets(o, ring)) {
                if (pushed.add(e.getUUID()) && e.isAlive()) {
                    Util.knock(e, p, 4.0, 0.3);
                }
            }
        }
        if (at(age, AFTER)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ATOMIC_AFTERMATH.get(), 3.0f, 1.0f);
        }
        if (age == tk(RELEASE + 0.6)) {
            Util.LOCKED.remove(ownerId);
        }
        stepCrater(o);
    }

    // ------------------------------------------------------------------ destructive mode (off by default)

    private void startCrater() {
        int r = (int) Math.round(ShdConfig.ATOMIC.craterRadius.get());
        cols = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    cols.add(new int[]{dx, dz});
                }
            }
        }
        // from the middle outwards
        cols.sort(Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        colIdx = 0;
    }

    private void stepCrater(LivingEntity o) {
        if (cols == null || colIdx >= cols.size()) {
            return;
        }
        double r = ShdConfig.ATOMIC.craterRadius.get(), depth = ShdConfig.ATOMIC.craterDepth.get();
        BlockPos c = blockPosition();
        ServerPlayer sp = o instanceof ServerPlayer s ? s : null;
        for (int n = 0; n < 100 && colIdx < cols.size(); n++, colIdx++) {
            int[] d = cols.get(colIdx);
            double k = Math.sqrt(d[0] * d[0] + d[1] * d[1]) / r;
            int low = -(int) Math.round(depth * Math.sqrt(Math.max(0.0, 1.0 - k * k)));
            for (int y = 16; y >= low; y--) {
                BlockPos bp = c.offset(d[0], y - 1, d[1]);
                BlockState st = level().getBlockState(bp);
                if (st.isAir() || st.is(Blocks.BEDROCK) || st.getDestroySpeed(level(), bp) < 0f) {
                    continue;
                }
                if (sp != null && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level(), bp, st, sp))) {
                    continue;
                }
                level().setBlock(bp, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    @Override
    protected void onFinish(boolean normal) {
        if (ownerId != null) {
            Util.LOCKED.remove(ownerId);
        }
    }
}
