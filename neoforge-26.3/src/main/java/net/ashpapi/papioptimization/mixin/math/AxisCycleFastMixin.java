package net.ashpapi.papioptimization.mixin.math;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {"net.minecraft.core.AxisCycle$2", "net.minecraft.core.AxisCycle$3"}, priority = 1500)
public abstract class AxisCycleFastMixin {

    @WrapMethod(method = "cycle(Lnet/minecraft/core/Direction$Axis;)Lnet/minecraft/core/Direction$Axis;")
    private Direction.Axis papi$fastCycleAxis(Direction.Axis axis, Operation<Direction.Axis> original) {
        if ((Object) this == AxisCycle.FORWARD) {
            return switch (axis.ordinal()) {
                case 0 -> Direction.Axis.Y;
                case 1 -> Direction.Axis.Z;
                default -> Direction.Axis.X;
            };
        }
        return switch (axis.ordinal()) {
            case 0 -> Direction.Axis.Z;
            case 1 -> Direction.Axis.X;
            default -> Direction.Axis.Y;
        };
    }
}
