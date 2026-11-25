package net.ashpapi.papioptimization.mixin.redstone;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.ashpapi.papioptimization.redstone.PapiRedstoneDirections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RedstoneWireBlock.class, priority = 1500)
public abstract class RedstoneWireBlockFastMixin {

    @Shadow
    private boolean shouldSignal;

    @WrapMethod(method = "getBlockSignal")
    private int papi$fastGetBlockSignal(Level level, BlockPos pos, Operation<Integer> original) {
        RedstoneWireBlock self = (RedstoneWireBlock) (Object) this;
        boolean prevShouldSignal = this.shouldSignal;
        this.shouldSignal = false;
        int mark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos mutNbr = BlockPosPool.get();
            BlockPos.MutableBlockPos mutDirect = BlockPosPool.get();
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            int best = 0;
            Direction[] dirs = PapiRedstoneDirections.ALL;
            for (int i = 0; i < 6; i++) {
                Direction dir = dirs[i];
                int nx = x + dir.getStepX();
                int ny = y + dir.getStepY();
                int nz = z + dir.getStepZ();
                mutNbr.set(nx, ny, nz);
                BlockState nState = level.getBlockState(mutNbr);
                if (nState.isAir() || nState.is(self)) {
                    continue;
                }
                int sig = nState.getSignal(level, mutNbr, dir);
                if (sig >= 15) {
                    return 15;
                }
                if (nState.shouldCheckWeakPower(level, mutNbr, dir)) {
                    for (int j = 0; j < 6; j++) {
                        Direction d2 = dirs[j];
                        mutDirect.set(nx + d2.getStepX(), ny + d2.getStepY(), nz + d2.getStepZ());
                        BlockState dState = level.getBlockState(mutDirect);
                        if (dState.isAir() || dState.is(self)) {
                            continue;
                        }
                        int dSig = dState.getDirectSignal(level, mutDirect, d2);
                        if (dSig >= 15) {
                            return 15;
                        }
                        if (dSig > sig) {
                            sig = dSig;
                        }
                    }
                }
                if (sig > best) {
                    best = sig;
                }
            }
            return best;
        } finally {
            BlockPosPool.reset(mark);
            this.shouldSignal = prevShouldSignal;
        }
    }

    @WrapOperation(
            method = {"affectNeighborsAfterRemoval", "checkCornerChangeAt"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;")
    )
    private Direction[] papi$reuseAllDirections(Operation<Direction[]> original) {
        return PapiRedstoneDirections.ALL;
    }
}
