package net.ashpapi.papioptimization.mixin.redstone;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.redstone.PapiRedstoneWavefront;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.DefaultRedstoneWireEvaluator;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.redstone.RedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = DefaultRedstoneWireEvaluator.class, priority = 1500)
public abstract class DefaultRedstoneWireEvaluatorMixin extends RedstoneWireEvaluator {

    protected DefaultRedstoneWireEvaluatorMixin(RedstoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @WrapMethod(method = "updatePowerStrength")
    private void papi$wavefrontUpdatePowerStrength(
            Level level,
            BlockPos pos,
            BlockState state,
            Orientation orientation,
            boolean skipShapeUpdates,
            Operation<Void> original
    ) {
        if (!PapiConfig.rawAlternateCurrentRuntime()
                || !PapiRedstoneWavefront.updatePowerStrength(this.wireBlock, level, pos, state, orientation, skipShapeUpdates)) {
            original.call(level, pos, state, orientation, skipShapeUpdates);
        }
    }

    @WrapMethod(method = "calculateTargetStrength")
    private int papi$fastCalculateTargetStrength(Level level, BlockPos pos, Operation<Integer> original) {
        int blockSignal = this.getBlockSignal(level, pos);
        if (blockSignal >= 14) {
            return blockSignal;
        }
        return Math.max(blockSignal, this.getIncomingWireSignal(level, pos));
    }
}
