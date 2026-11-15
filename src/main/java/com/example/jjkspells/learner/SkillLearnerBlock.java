package com.example.jjkspells.learner;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/** Right-click to open the skill list. */
public class SkillLearnerBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    private final boolean prime;

    public SkillLearnerBlock(Properties props) {
        this(props, false);
    }

    public SkillLearnerBlock(Properties props, boolean prime) {
        super(props);
        this.prime = prime;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            var entries = LearnerEntries.build(prime);
            NetworkHooks.openScreen(sp,
                    new SimpleMenuProvider((id, inv, p) -> new SkillLearnerMenu(id, inv, ContainerLevelAccess.create(level, pos), entries, prime),
                            Component.translatable(prime ? "block.jjk_spells.prime_divinity_skill_learner" : "block.jjk_spells.divine_skill_learner")),
                    buf -> {
                        buf.writeBoolean(prime);
                        LearnerEntries.write(buf, entries);
                    });
        }
        return InteractionResult.CONSUME;
    }
}
