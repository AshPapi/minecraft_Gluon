package net.ashpapi.papioptimization.mixin.piston;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.util.DirectionConstants;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PistonBaseBlock.class, priority = 1500)
public abstract class PistonBaseBlockMixin {

    @WrapOperation(
            method = "getNeighborSignal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;")
    )
    private Direction[] papi$sharedDirections(Operation<Direction[]> original) {
        return DirectionConstants.ALL;
    }
}
