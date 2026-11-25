package net.ashpapi.papioptimization.mixin.redstone;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.ashpapi.papioptimization.redstone.PapiRedstoneDirections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.RedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RedstoneWireEvaluator.class, priority = 1500)
public abstract class RedstoneWireEvaluatorFastMixin {

    @Shadow
    @Final
    protected RedstoneWireBlock wireBlock;

    @Shadow
    protected abstract int getWireSignal(BlockPos pos, BlockState state);

    @WrapMethod(method = "getIncomingWireSignal")
    private int papi$fastGetIncomingWireSignal(Level level, BlockPos pos, Operation<Integer> original) {
        int mark = BlockPosPool.mark();
        try {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            BlockPos.MutableBlockPos mutPos = BlockPosPool.get(x, y + 1, z);
            boolean aboveConductor = level.getBlockState(mutPos).isRedstoneConductor(level, mutPos);
            BlockPos.MutableBlockPos mutStep = BlockPosPool.get();

            int maxWire = 0;
            Direction[] horiz = PapiRedstoneDirections.HORIZONTAL;
            for (int i = 0; i < 4; i++) {
                Direction dir = horiz[i];
                int nx = x + dir.getStepX();
                int nz = z + dir.getStepZ();
                mutPos.set(nx, y, nz);
                BlockState sideState = level.getBlockState(mutPos);
                int sig = this.getWireSignal(mutPos, sideState);
                if (sig > maxWire) {
                    maxWire = sig;
                    if (maxWire >= 15) {
                        return 14;
                    }
                }
                if (sideState.is(this.wireBlock)) {
                    continue;
                }
                boolean sideConductor = sideState.isRedstoneConductor(level, mutPos);
                if (sideConductor && !aboveConductor) {
                    mutStep.set(nx, y + 1, nz);
                    int upSig = this.getWireSignal(mutStep, level.getBlockState(mutStep));
                    if (upSig > maxWire) {
                        maxWire = upSig;
                        if (maxWire >= 15) {
                            return 14;
                        }
                    }
                } else if (!sideConductor) {
                    mutStep.set(nx, y - 1, nz);
                    int downSig = this.getWireSignal(mutStep, level.getBlockState(mutStep));
                    if (downSig > maxWire) {
                        maxWire = downSig;
                        if (maxWire >= 15) {
                            return 14;
                        }
                    }
                }
            }
            return maxWire > 0 ? maxWire - 1 : 0;
        } finally {
            BlockPosPool.reset(mark);
        }
    }
}
