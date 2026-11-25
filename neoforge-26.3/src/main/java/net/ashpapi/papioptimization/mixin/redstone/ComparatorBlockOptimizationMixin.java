package net.ashpapi.papioptimization.mixin.redstone;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ComparatorBlock.class, priority = 1500)
public abstract class ComparatorBlockOptimizationMixin extends DiodeBlock {

    protected ComparatorBlockOptimizationMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Shadow
    private @Nullable ItemFrame getItemFrame(Level level, Direction direction, BlockPos pos) {
        return null;
    }

    @WrapMethod(method = "getInputSignal")
    private int papi$fastDirectContainerInputSignal(Level level, BlockPos pos, BlockState state, Operation<Integer> original) {
        Direction dir = state.getValue(FACING);
        BlockPos adjPos = pos.relative(dir);
        BlockState adjState = level.getBlockState(adjPos);

        if (adjState.hasAnalogOutputSignal()) {
            return adjState.getAnalogOutputSignal(level, adjPos, dir.getOpposite());
        }

        int signal = level.getSignal(adjPos, dir);
        if (signal >= 15) {
            return 15;
        }
        if (adjState.is(Blocks.REDSTONE_WIRE)) {
            signal = Math.max(signal, adjState.getValue(RedstoneWireBlock.POWER));
        }

        if (signal < 15 && adjState.isRedstoneConductor(level, adjPos)) {
            BlockPos farPos = adjPos.relative(dir);
            BlockState farState = level.getBlockState(farPos);
            ItemFrame itemFrame = this.getItemFrame(level, dir, farPos);
            int extra = Math.max(
                    itemFrame == null ? Integer.MIN_VALUE : itemFrame.getAnalogOutput(),
                    farState.hasAnalogOutputSignal() ? farState.getAnalogOutputSignal(level, farPos, dir.getOpposite()) : Integer.MIN_VALUE
            );
            if (extra != Integer.MIN_VALUE) {
                signal = extra;
            }
        }

        return signal;
    }
}
