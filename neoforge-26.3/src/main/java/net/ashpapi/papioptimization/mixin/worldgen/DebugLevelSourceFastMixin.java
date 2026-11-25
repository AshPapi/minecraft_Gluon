package net.ashpapi.papioptimization.mixin.worldgen;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DebugLevelSource.class, priority = 1500)
public abstract class DebugLevelSourceFastMixin {

    @Shadow private static List<BlockState> ALL_BLOCKS;
    @Shadow private static int GRID_WIDTH;
    @Shadow private static int GRID_HEIGHT;
    @Shadow @Final protected static BlockState AIR;

    @Unique
    private static volatile BlockState[] papi$allBlocksArray;

    @Inject(method = "initValidStates", at = @At("RETURN"))
    private static void papi$cacheValidStatesArray(CallbackInfo ci) {
        if (ALL_BLOCKS != null) {
            papi$allBlocksArray = ALL_BLOCKS.toArray(new BlockState[0]);
        }
    }

    @Inject(method = "getBlockStateFor", at = @At("HEAD"), cancellable = true)
    private static void papi$fastGetBlockStateFor(int x, int z, CallbackInfoReturnable<BlockState> cir) {
        if (x <= 0 || z <= 0 || (x & 1) == 0 || (z & 1) == 0) {
            cir.setReturnValue(AIR);
            return;
        }
        int gx = x >> 1;
        int gz = z >> 1;
        if (gx <= GRID_WIDTH && gz <= GRID_HEIGHT) {
            int idx = Mth.abs(gx * GRID_WIDTH + gz);
            BlockState[] arr = papi$allBlocksArray;
            if (arr == null && ALL_BLOCKS != null) {
                arr = ALL_BLOCKS.toArray(new BlockState[0]);
                papi$allBlocksArray = arr;
            }
            if (arr != null && idx < arr.length) {
                cir.setReturnValue(arr[idx]);
                return;
            }
        }
        cir.setReturnValue(AIR);
    }
}
