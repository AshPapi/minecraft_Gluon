package net.ashpapi.papioptimization.mixin.lighting;

import net.ashpapi.papioptimization.access.PapiLightBlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LightEngine.class, priority = 1500)
public abstract class LightEngineMixin {

    @Inject(method = "shapeOccludes", at = @At("HEAD"), cancellable = true)
    private void papi$fastShapeOccludes(BlockState fromState, BlockState toState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        boolean fromEmpty = papi$isEmptyShape(fromState);
        boolean toEmpty = papi$isEmptyShape(toState);

        if (fromEmpty && toEmpty) {
            cir.setReturnValue(false);
            return;
        }

        if (fromEmpty) {
            VoxelShape toShape = toState.getFaceOcclusionShape(direction.getOpposite());
            if (toShape == Shapes.empty()) {
                cir.setReturnValue(false);
            } else if (toShape == Shapes.block()) {
                cir.setReturnValue(true);
            }
            return;
        }

        if (toEmpty) {
            VoxelShape fromShape = fromState.getFaceOcclusionShape(direction);
            if (fromShape == Shapes.empty()) {
                cir.setReturnValue(false);
            } else if (fromShape == Shapes.block()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private static boolean papi$isEmptyShape(BlockState state) {
        return (((PapiLightBlockState) state).papi$getLightFlags() & 1) != 0;
    }
}
