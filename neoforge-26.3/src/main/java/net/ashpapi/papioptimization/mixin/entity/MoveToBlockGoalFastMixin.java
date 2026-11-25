package net.ashpapi.papioptimization.mixin.entity;

import java.util.function.Predicate;
import net.ashpapi.papioptimization.ai.FastBlockSearchHelper;
import net.ashpapi.papioptimization.ai.PapiPaletteGoalTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MoveToBlockGoal.class, priority = 1500)
public abstract class MoveToBlockGoalFastMixin {

    @Shadow
    @Final
    protected PathfinderMob mob;

    @Shadow
    protected BlockPos blockPos;

    @Shadow
    @Final
    private int searchRange;

    @Shadow
    @Final
    private int verticalSearchRange;

    @Shadow
    protected int verticalSearchStart;

    @Inject(method = "findNearestBlock", at = @At("HEAD"), cancellable = true)
    private void papi$fastPalettePreCheckedSearch(CallbackInfoReturnable<Boolean> cir) {
        if (this.searchRange <= 0 || this.verticalSearchStart > this.verticalSearchRange) {
            cir.setReturnValue(false);
            return;
        }
        if ((Object) this instanceof PapiPaletteGoalTarget target) {
            Predicate<BlockState> pred = target.papi$getPalettePredicate();
            if (pred != null) {
                int minDy = Integer.MAX_VALUE;
                int maxDy = Integer.MIN_VALUE;
                for (int k = this.verticalSearchStart; k <= this.verticalSearchRange; k = k > 0 ? -k : 1 - k) {
                    int dy = k - 1;
                    if (dy < minDy) {
                        minDy = dy;
                    }
                    if (dy > maxDy) {
                        maxDy = dy;
                    }
                }
                if (minDy > maxDy) {
                    cir.setReturnValue(false);
                    return;
                }
                BlockPos found = FastBlockSearchHelper.searchMoveToBlockSpiral(
                        this.mob,
                        this.searchRange,
                        this.verticalSearchStart,
                        this.verticalSearchRange,
                        minDy,
                        maxDy,
                        pred,
                        target
                );
                if (found != null) {
                    this.blockPos = found;
                    cir.setReturnValue(true);
                } else {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
