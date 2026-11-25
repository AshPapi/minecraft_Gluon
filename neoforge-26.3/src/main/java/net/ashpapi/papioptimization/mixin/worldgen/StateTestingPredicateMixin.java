package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StateTestingPredicate.class, priority = 1500)
public abstract class StateTestingPredicateMixin {

    @Shadow @Final protected Vec3i offset;

    @Shadow protected abstract boolean test(BlockState state);

    @Unique
    private boolean papi$zeroOffset;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$checkZeroOffset(Vec3i offset, CallbackInfo ci) {
        this.papi$zeroOffset = offset.getX() == 0 && offset.getY() == 0 && offset.getZ() == 0;
    }

    @WrapMethod(method = "test(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Z")
    private boolean papi$fastTest(LevelAccessor level, BlockPos pos, Operation<Boolean> original) {
        if (this.papi$zeroOffset) {
            return this.test(level.getBlockState(pos));
        }
        return original.call(level, pos);
    }
}
