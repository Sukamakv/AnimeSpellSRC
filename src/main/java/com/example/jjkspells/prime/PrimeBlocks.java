package com.example.jjkspells.prime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;

/** Terrain erasing shared by the PRIME spells. Protection mods are asked once per 4x4x4 cell (a whole mountain would otherwise fire a million events). */
public final class PrimeBlocks {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final Map<Long, Boolean> cells = new HashMap<>();
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
    private int debris;
    public int erased;

    public PrimeBlocks(ServerLevel level, LivingEntity caster) {
        this.level = level;
        this.player = caster instanceof ServerPlayer sp ? sp : null;
    }

    /** Call at the start of every tick so each tick may throw its own debris. */
    public void newTick() {
        debris = 0;
    }

    private boolean allowed(BlockPos pos, BlockState state) {
        if (player == null) return true;
        long key = ((long) (pos.getX() >> 2) & 0x3FFFFFFL) << 38 | ((long) (pos.getZ() >> 2) & 0x3FFFFFFL) << 12 | ((long) (pos.getY() >> 2) & 0xFFFL);
        Boolean ok = cells.get(key);
        if (ok == null) {
            ok = !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos.immutable(), state, player));
            cells.put(key, ok);
        }
        return ok;
    }

    /** Erases the block at (x, y, z) if it can be erased. Debris flies away from {@code from}. */
    public boolean erase(int x, int y, int z, Vec3 from, double debrisSpeed) {
        mp.set(x, y, z);
        if (level.isOutsideBuildHeight(mp)) return false;
        BlockState st = level.getBlockState(mp);
        if (st.isAir() || st.getDestroySpeed(level, mp) < 0f) return false;
        if (!allowed(mp, st)) return false;
        RandomSource rng = level.random;
        if (debris < 70 && rng.nextInt(40) == 0 && st.getFluidState().isEmpty()) {
            debris++;
            Vec3 bp = new Vec3(x + 0.5, y + 0.5, z + 0.5);
            Vec3 out = bp.subtract(from);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
            PrimeFx.one(level, new BlockParticleOption(ParticleTypes.BLOCK, st), bp, out.scale(debrisSpeed * (0.5 + rng.nextDouble())).add(0, 0.3, 0));
        }
        level.setBlock(mp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        erased++;
        return true;
    }

    public void sphere(Vec3 c, double r, Vec3 debrisFrom, double debrisSpeed) {
        int ext = (int) Math.ceil(r);
        int cx = (int) Math.floor(c.x), cy = (int) Math.floor(c.y), cz = (int) Math.floor(c.z);
        double r2 = r * r;
        for (int x = -ext; x <= ext; x++) {
            for (int y = -ext; y <= ext; y++) {
                for (int z = -ext; z <= ext; z++) {
                    double dx = cx + x + 0.5 - c.x, dy = cy + y + 0.5 - c.y, dz = cz + z + 0.5 - c.z;
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    erase(cx + x, cy + y, cz + z, debrisFrom, debrisSpeed);
                }
            }
        }
    }

    /** A ragged hash in [0,1) for a block position, so rims of craters and cones are not perfectly smooth. */
    public static double jag(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        return (h & 1023) / 1023.0;
    }
}
